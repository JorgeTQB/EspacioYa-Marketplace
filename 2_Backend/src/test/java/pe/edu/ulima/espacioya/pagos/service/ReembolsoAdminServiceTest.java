package pe.edu.ulima.espacioya.pagos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.pagos.domain.EstadoPago;
import pe.edu.ulima.espacioya.pagos.domain.EstadoReembolso;
import pe.edu.ulima.espacioya.pagos.domain.OrigenReembolso;
import pe.edu.ulima.espacioya.pagos.domain.Pago;
import pe.edu.ulima.espacioya.pagos.domain.Reembolso;
import pe.edu.ulima.espacioya.pagos.domain.ReembolsoPorAtender;
import pe.edu.ulima.espacioya.pagos.dto.ReembolsoAdminDTO;
import pe.edu.ulima.espacioya.pagos.repository.PagoRepository;
import pe.edu.ulima.espacioya.pagos.repository.ReembolsoRepository;
import pe.edu.ulima.espacioya.pagos.service.reembolso.Disparo;
import pe.edu.ulima.espacioya.pagos.service.reembolso.EjecutorReembolsos;
import pe.edu.ulima.espacioya.pagos.service.reembolso.ResultadoReembolso;
import pe.edu.ulima.espacioya.pagos.service.reembolso.SolicitudReembolso;
import pe.edu.ulima.espacioya.pagos.service.reembolso.SolicitudesReembolso;

@ExtendWith(MockitoExtension.class)
class ReembolsoAdminServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:10:00Z");
    private static final Instant CREADO = Instant.parse("2026-10-28T15:00:00Z");
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(99L, 5L, "ADMIN");
    private static final String IP = "127.0.0.1";
    private static final long PAGO_ID = 31L;

    @Mock
    private ReembolsoRepository reembolsos;
    @Mock
    private PagoRepository pagos;
    @Mock
    private SolicitudesReembolso solicitudes;
    @Mock
    private EjecutorReembolsos ejecutor;

    private final SolicitudReembolso comando = mock(SolicitudReembolso.class);
    private ReembolsoAdminService servicio;

    @BeforeEach
    void preparar() {
        servicio = new ReembolsoAdminService(reembolsos, pagos, solicitudes, ejecutor,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    /** Reembolso 4 creado a las 15:00, con un intento a {@code intentoEn} y el resultado indicado. */
    private static Reembolso reembolso(EstadoReembolso estado, Instant intentoEn) {
        var reembolso = Reembolso.porCancelacion(PAGO_ID, new BigDecimal("161.00"), CREADO);
        ReflectionTestUtils.setField(reembolso, "id", 4L);
        if (intentoEn != null) {
            reembolso.iniciarIntento(intentoEn);
            if (estado == EstadoReembolso.REEMBOLSADO) {
                reembolso.marcarReembolsado("sim-reembolso-espacioya-reembolso-4");
            } else if (estado == EstadoReembolso.FALLIDO) {
                reembolso.marcarFallido();
            }
        }
        return reembolso;
    }

    private static Reembolso fallido() {
        return reembolso(EstadoReembolso.FALLIDO, CREADO.plus(Duration.ofMinutes(2)));
    }

    private static Pago pago() {
        return Pago.de(7L, "sim-7-APROBADO-1", new BigDecimal("161.00"), EstadoPago.REEMBOLSO_FALLIDO, "4242", CREADO);
    }

    @Test
    @DisplayName("HU-16 R26: un USUARIO o ARRENDADOR recibe 403 sin tocar reembolsos, pagos ni el comando")
    void rolDistintoDeAdmin() {
        for (var rol : List.of("USUARIO", "ARRENDADOR")) {
            var cuenta = new UsuarioAutenticado(12L, 3L, rol);

            assertThatThrownBy(() -> servicio.porAtender(cuenta)).isInstanceOf(AccesoDenegadoException.class);
            assertThatThrownBy(() -> servicio.reintentar(cuenta, "4", IP)).isInstanceOf(AccesoDenegadoException.class);
        }
        verifyNoInteractions(reembolsos, pagos, solicitudes, ejecutor);
    }

    @Test
    @DisplayName("HU-16 R28: porAtender pide los reembolsos con el límite ahora − 2 min y los devuelve como DTO")
    void porAtenderUsaElMargen() {
        var fila = new ReembolsoPorAtender(4L, 7L, new BigDecimal("161.00"), OrigenReembolso.CANCELACION,
                EstadoReembolso.FALLIDO, 1, CREADO, CREADO);
        when(reembolsos.porAtender(AHORA.minus(Duration.ofMinutes(2)))).thenReturn(List.of(fila));

        assertThat(servicio.porAtender(ADMIN)).containsExactly(new ReembolsoAdminDTO(4L, 7L,
                new BigDecimal("161.00"), "CANCELACION", "FALLIDO", 1, CREADO, CREADO));
        verifyNoInteractions(solicitudes, ejecutor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "12345678901234567890"})
    @DisplayName("HU-16 R32: un id mal escrito responde 404 sin leer reembolsos ni crear el comando")
    void idMalEscrito(String id) {
        assertThatThrownBy(() -> servicio.reintentar(ADMIN, id, IP)).isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(reembolsos, solicitudes, ejecutor);
    }

    @Test
    @DisplayName("HU-16 R32: un reembolso inexistente responde 404 sin crear el comando")
    void inexistente() {
        when(reembolsos.findById(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.reintentar(ADMIN, "4", IP)).isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(solicitudes, ejecutor);
    }

    @Test
    @DisplayName("HU-16 R33: un REEMBOLSADO responde 400 ya reembolsado sin crear el comando")
    void yaReembolsado() {
        when(reembolsos.findById(4L)).thenReturn(Optional.of(reembolso(EstadoReembolso.REEMBOLSADO,
                CREADO.plus(Duration.ofMinutes(2)))));

        assertThatThrownBy(() -> servicio.reintentar(ADMIN, "4", IP)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage(ReembolsoAdminService.MENSAJE_YA_REEMBOLSADO);

        verifyNoInteractions(solicitudes, ejecutor);
    }

    @Test
    @DisplayName("HU-16 R34: un PENDIENTE de hace 1 min responde 400 en proceso sin crear el comando")
    void enProceso() {
        when(reembolsos.findById(4L)).thenReturn(Optional.of(reembolso(EstadoReembolso.PENDIENTE,
                AHORA.minus(Duration.ofMinutes(1)))));

        assertThatThrownBy(() -> servicio.reintentar(ADMIN, "4", IP)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage(ReembolsoAdminService.MENSAJE_EN_PROCESO);

        verifyNoInteractions(solicitudes, ejecutor);
    }

    @Test
    @DisplayName("HU-16 R30, R35, R40: crea un comando ADMIN con el id del admin y su IP, lo ejecuta ya y devuelve "
            + "el DTO releído")
    void reintentaConElComandoDelAdmin() {
        var despues = reembolso(EstadoReembolso.REEMBOLSADO, AHORA);
        when(reembolsos.findById(4L)).thenReturn(Optional.of(fallido()), Optional.of(despues));
        when(solicitudes.crear(4L, Disparo.ADMIN, 99L, IP)).thenReturn(comando);
        when(ejecutor.ejecutarAhora(comando)).thenReturn(ResultadoReembolso.REEMBOLSADO);
        when(pagos.findById(PAGO_ID)).thenReturn(Optional.of(pago()));

        var dto = servicio.reintentar(ADMIN, "4", IP);

        assertThat(dto).isEqualTo(new ReembolsoAdminDTO(4L, 7L, new BigDecimal("161.00"), "CANCELACION",
                "REEMBOLSADO", 1, CREADO, AHORA));
        verify(ejecutor).ejecutarAhora(comando);
        verify(ejecutor, never()).ejecutarDespuesDelCommit(any());
    }

    @Test
    @DisplayName("HU-16 R23: si el comando no se ejecutó porque otro reintento ganó, responde 400 en proceso")
    void otroReintentoGano() {
        var enCurso = reembolso(EstadoReembolso.PENDIENTE, AHORA);
        when(reembolsos.findById(4L)).thenReturn(Optional.of(fallido()), Optional.of(enCurso));
        when(solicitudes.crear(4L, Disparo.ADMIN, 99L, IP)).thenReturn(comando);
        when(ejecutor.ejecutarAhora(comando)).thenReturn(ResultadoReembolso.NO_EJECUTADO);

        assertThatThrownBy(() -> servicio.reintentar(ADMIN, "4", IP)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage(ReembolsoAdminService.MENSAJE_EN_PROCESO);
        verifyNoInteractions(pagos);
    }

    @Test
    @DisplayName("HU-16 R31: si la pasarela vuelve a fallar, devuelve el DTO en FALLIDO sin lanzar")
    void reintentoQueVuelveAFallar() {
        var despues = fallido();
        despues.iniciarIntento(AHORA);
        despues.marcarFallido();
        when(reembolsos.findById(4L)).thenReturn(Optional.of(fallido()), Optional.of(despues));
        when(solicitudes.crear(4L, Disparo.ADMIN, 99L, IP)).thenReturn(comando);
        when(ejecutor.ejecutarAhora(comando)).thenReturn(ResultadoReembolso.FALLIDO);
        when(pagos.findById(PAGO_ID)).thenReturn(Optional.of(pago()));

        var dto = servicio.reintentar(ADMIN, "4", IP);

        assertThat(dto.estado()).isEqualTo("FALLIDO");
        assertThat(dto.intentos()).isEqualTo(2);
    }
}
