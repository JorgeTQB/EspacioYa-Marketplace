package pe.edu.ulima.espacioya.pagos.service.reembolso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.edu.ulima.espacioya.comun.error.ServicioExternoException;
import pe.edu.ulima.espacioya.pagos.service.pasarela.PasarelaPago;
import pe.edu.ulima.espacioya.pagos.service.pasarela.ReembolsoRealizado;
import pe.edu.ulima.espacioya.pagos.service.pasarela.SolicitudReembolsoPasarela;

@ExtendWith(MockitoExtension.class)
class SolicitudReembolsoTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final IntentoReembolso INTENTO = new IntentoReembolso(4L, 31L, 7L, "sim-7-APROBADO-1",
            new BigDecimal("161.00"), 1, "espacioya-reembolso-4");
    private static final ReembolsoRealizado REALIZADO = new ReembolsoRealizado("sim-reembolso-espacioya-reembolso-4");

    @Mock
    private PasarelaPago pasarela;
    @Mock
    private RegistroReembolsos registro;

    private SolicitudReembolso comando;

    @BeforeEach
    void preparar() {
        comando = new SolicitudesReembolso(pasarela, registro, Clock.fixed(AHORA, ZoneOffset.UTC))
                .crear(4L, Disparo.AUTOMATICO, 12L, "10.17.0.1");
    }

    @Test
    @DisplayName("HU-16 R16, R40: reclama el intento, llama a la pasarela fuera del registro y registra el éxito, "
            + "en ese orden")
    void ordenDelIntento() {
        when(registro.iniciar(4L, AHORA)).thenReturn(Optional.of(INTENTO));
        when(pasarela.reembolsar(any())).thenReturn(REALIZADO);

        assertThat(comando.ejecutar()).isEqualTo(ResultadoReembolso.REEMBOLSADO);

        var orden = inOrder(registro, pasarela);
        orden.verify(registro).iniciar(4L, AHORA);
        orden.verify(pasarela).reembolsar(any());
        orden.verify(registro).registrarExito(INTENTO, REALIZADO, comando);
        verify(registro, never()).registrarFallo(any(), any());
    }

    @Test
    @DisplayName("HU-16 R8, R9: pide a la pasarela la transacción, el monto y la clave del intento reclamado")
    void solicitudConLosDatosDelIntento() {
        when(registro.iniciar(4L, AHORA)).thenReturn(Optional.of(INTENTO));
        when(pasarela.reembolsar(any())).thenReturn(REALIZADO);

        comando.ejecutar();

        verify(pasarela).reembolsar(new SolicitudReembolsoPasarela("sim-7-APROBADO-1", new BigDecimal("161.00"),
                "espacioya-reembolso-4"));
    }

    @Test
    @DisplayName("HU-16 R17: si la pasarela lanza, registra el fallo y devuelve FALLIDO sin registrar éxito")
    void falloDeLaPasarela() {
        when(registro.iniciar(4L, AHORA)).thenReturn(Optional.of(INTENTO));
        when(pasarela.reembolsar(any())).thenThrow(new ServicioExternoException());

        assertThat(comando.ejecutar()).isEqualTo(ResultadoReembolso.FALLIDO);

        verify(registro).registrarFallo(INTENTO, comando);
        verify(registro, never()).registrarExito(any(), any(), any());
    }

    @Test
    @DisplayName("HU-16 R21, R22: si la reclamación sale vacía, devuelve NO_EJECUTADO sin llamar a la pasarela")
    void reclamacionVacia() {
        when(registro.iniciar(4L, AHORA)).thenReturn(Optional.empty());

        assertThat(comando.ejecutar()).isEqualTo(ResultadoReembolso.NO_EJECUTADO);

        verifyNoInteractions(pasarela);
        verify(registro, never()).registrarExito(any(), any(), any());
        verify(registro, never()).registrarFallo(any(), any());
    }

    @Test
    @DisplayName("HU-16 R24: dos ejecuciones del mismo reembolso envían la misma clave de idempotencia")
    void mismaClaveEnCadaEjecucion() {
        var segundo = new IntentoReembolso(4L, 31L, 7L, "sim-7-APROBADO-1", new BigDecimal("161.00"), 2,
                "espacioya-reembolso-4");
        when(registro.iniciar(4L, AHORA)).thenReturn(Optional.of(INTENTO), Optional.of(segundo));
        when(pasarela.reembolsar(any())).thenThrow(new ServicioExternoException()).thenReturn(REALIZADO);

        assertThat(comando.ejecutar()).isEqualTo(ResultadoReembolso.FALLIDO);
        assertThat(comando.ejecutar()).isEqualTo(ResultadoReembolso.REEMBOLSADO);

        var enviadas = ArgumentCaptor.forClass(SolicitudReembolsoPasarela.class);
        verify(pasarela, times(2)).reembolsar(enviadas.capture());
        assertThat(enviadas.getAllValues()).extracting(SolicitudReembolsoPasarela::claveIdempotencia)
                .containsExactly("espacioya-reembolso-4", "espacioya-reembolso-4");
    }

    @Test
    @DisplayName("HU-16 R38, R40: el comando guarda el reembolso, el disparo, quién lo pidió y su IP")
    void guardaQuienYComo() {
        var delAdmin = new SolicitudesReembolso(pasarela, registro, Clock.fixed(AHORA, ZoneOffset.UTC))
                .crear(9L, Disparo.ADMIN, 99L, "127.0.0.1");

        assertThat(comando.reembolsoId()).isEqualTo(4L);
        assertThat(comando.disparo()).isEqualTo(Disparo.AUTOMATICO);
        assertThat(comando.solicitadoPor()).isEqualTo(12L);
        assertThat(comando.ip()).isEqualTo("10.17.0.1");
        assertThat(delAdmin.disparo()).isEqualTo(Disparo.ADMIN);
        assertThat(delAdmin.solicitadoPor()).isEqualTo(99L);
        verifyNoInteractions(pasarela, registro);
    }
}
