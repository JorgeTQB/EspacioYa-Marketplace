package pe.edu.ulima.espacioya.espacios.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.espacios.domain.Bloque;
import pe.edu.ulima.espacioya.espacios.domain.DatosEspacio;
import pe.edu.ulima.espacioya.espacios.domain.EstadoBloque;
import pe.edu.ulima.espacioya.espacios.domain.Espacio;
import pe.edu.ulima.espacioya.espacios.domain.EstadoEspacio;
import pe.edu.ulima.espacioya.espacios.domain.FranjaHoraria;
import pe.edu.ulima.espacioya.espacios.domain.HorarioSemanal;
import pe.edu.ulima.espacioya.espacios.domain.TipoEspacio;
import pe.edu.ulima.espacioya.espacios.repository.BloqueoFechaRepository;
import pe.edu.ulima.espacioya.espacios.repository.EspacioRepository;
import pe.edu.ulima.espacioya.espacios.repository.HorarioSemanalRepository;
import pe.edu.ulima.espacioya.espacios.service.puertos.BloqueReservado;
import pe.edu.ulima.espacioya.espacios.service.puertos.ReservasDelEspacio;

@ExtendWith(MockitoExtension.class)
class DisponibilidadServiceImplTest {

    // 22:00 del miércoles 2026-10-28 en Lima.
    private static final Instant AHORA = Instant.parse("2026-10-29T03:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 28);
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 2);
    private static final long ESPACIO_ID = 40L;
    private static final long ARRENDADOR_ID = 7L;

    @Mock
    private EspacioRepository espacios;
    @Mock
    private HorarioSemanalRepository horarios;
    @Mock
    private BloqueoFechaRepository bloqueos;
    @Mock
    private ObjectProvider<ReservasDelEspacio> proveedor;
    @Mock
    private CuentaService cuentas;

    private final ReservasDelEspacio puerto = mock(ReservasDelEspacio.class);
    private DisponibilidadServiceImpl servicio;

    @BeforeEach
    void preparar() {
        servicio = new DisponibilidadServiceImpl(espacios, horarios, bloqueos, proveedor, cuentas,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static Espacio espacio(EstadoEspacio estado) {
        var datos = new DatosEspacio("Cancha Los Olivos", TipoEspacio.CANCHA, "Av. Universitaria 1234", "LOS_OLIVOS",
                20, new BigDecimal("80"), "Cancha de grass sintético techada.");
        var espacio = Espacio.crear(ARRENDADOR_ID, datos, AHORA);
        ReflectionTestUtils.setField(espacio, "id", ESPACIO_ID);
        ReflectionTestUtils.setField(espacio, "estado", estado);
        return espacio;
    }

    private void horarioDelLunes(FranjaHoraria... franjas) {
        lenient().when(horarios.findByEspacioIdAndDiaSemanaOrderByHoraInicioAsc(ESPACIO_ID, (short) 1))
                .thenReturn(Arrays.stream(franjas).map(f -> HorarioSemanal.de(ESPACIO_ID, f)).toList());
    }

    @Test
    @DisplayName("HU-08 R21: sin implementación del puerto no hay reservas y los bloques quedan LIBRE")
    void sinPuertoNoHayReservas() {
        when(proveedor.getIfAvailable()).thenReturn(null);
        horarioDelLunes(new FranjaHoraria(1, 8, 10));

        assertThat(servicio.reservasDelDia(ESPACIO_ID, LUNES)).isEmpty();
        assertThat(servicio.bloquesDelDia(ESPACIO_ID, LUNES)).extracting(Bloque::estado)
                .containsOnly(EstadoBloque.LIBRE);
    }

    @Test
    @DisplayName("HU-08 R24, R30: un espacio publicado devuelve solo las horas LIBRE, sin las reservadas")
    void publicadoDevuelveSoloLibres() {
        when(espacios.findById(ESPACIO_ID)).thenReturn(Optional.of(espacio(EstadoEspacio.PUBLICADO)));
        when(cuentas.cuentaActiva(ARRENDADOR_ID)).thenReturn(true);
        horarioDelLunes(new FranjaHoraria(1, 14, 16), new FranjaHoraria(1, 8, 10));
        when(bloqueos.existsByEspacioIdAndFecha(ESPACIO_ID, LUNES)).thenReturn(false);
        when(proveedor.getIfAvailable()).thenReturn(puerto);
        when(puerto.bloquesReservados(ESPACIO_ID, LUNES, LUNES))
                .thenReturn(List.of(new BloqueReservado(LUNES, 9, true), new BloqueReservado(LUNES, 14, false)));

        assertThat(servicio.horasLibres(ESPACIO_ID, LUNES)).containsExactly(8, 15);
    }

    @Test
    @DisplayName("HU-08 R23, R30: una fecha bloqueada no tiene horas libres y no consulta el puerto")
    void bloqueadaNoConsultaPuerto() {
        when(espacios.findById(ESPACIO_ID)).thenReturn(Optional.of(espacio(EstadoEspacio.PUBLICADO)));
        when(cuentas.cuentaActiva(ARRENDADOR_ID)).thenReturn(true);
        horarioDelLunes(new FranjaHoraria(1, 8, 10));
        when(bloqueos.existsByEspacioIdAndFecha(ESPACIO_ID, LUNES)).thenReturn(true);

        assertThat(servicio.horasLibres(ESPACIO_ID, LUNES)).isEmpty();
        assertThat(servicio.bloquesDelDia(ESPACIO_ID, LUNES)).extracting(Bloque::estado)
                .containsOnly(EstadoBloque.BLOQUEADO);
        verifyNoInteractions(proveedor);
    }

    @Test
    @DisplayName("HU-08 R25, R30: hoy a las 22:00 en Lima solo queda libre la hora de las 23")
    void hoyDescartaHorasEmpezadas() {
        when(espacios.findById(ESPACIO_ID)).thenReturn(Optional.of(espacio(EstadoEspacio.PUBLICADO)));
        when(cuentas.cuentaActiva(ARRENDADOR_ID)).thenReturn(true);
        // 2026-10-28 es miércoles (3).
        when(horarios.findByEspacioIdAndDiaSemanaOrderByHoraInicioAsc(ESPACIO_ID, (short) 3))
                .thenReturn(List.of(HorarioSemanal.de(ESPACIO_ID, new FranjaHoraria(3, 20, 24))));
        when(proveedor.getIfAvailable()).thenReturn(null);

        assertThat(servicio.horasLibres(ESPACIO_ID, HOY)).containsExactly(23);
    }

    @Test
    @DisplayName("HU-08 R31: espacio inexistente, en borrador o pausado devuelve una lista vacía")
    void noPublicadoDevuelveVacio() {
        when(espacios.findById(ESPACIO_ID)).thenReturn(Optional.empty(),
                Optional.of(espacio(EstadoEspacio.BORRADOR)), Optional.of(espacio(EstadoEspacio.PAUSADO)));

        assertThat(servicio.horasLibres(ESPACIO_ID, LUNES)).isEmpty();
        assertThat(servicio.horasLibres(ESPACIO_ID, LUNES)).isEmpty();
        assertThat(servicio.horasLibres(ESPACIO_ID, LUNES)).isEmpty();
        verify(horarios, never()).findByEspacioIdAndDiaSemanaOrderByHoraInicioAsc(anyLong(), anyShort());
        verifyNoInteractions(proveedor, cuentas);
    }

    @Test
    @DisplayName("HU-09 R23: un espacio PUBLICADO cuyo arrendador no está ACTIVA devuelve una lista vacía sin "
            + "consultar horarios, bloqueos ni reservas")
    void arrendadorNoActivoDevuelveVacio() {
        when(espacios.findById(ESPACIO_ID)).thenReturn(Optional.of(espacio(EstadoEspacio.PUBLICADO)));
        when(cuentas.cuentaActiva(ARRENDADOR_ID)).thenReturn(false);

        assertThat(servicio.horasLibres(ESPACIO_ID, LUNES)).isEmpty();
        verifyNoInteractions(horarios, bloqueos, proveedor);
    }

    @Test
    @DisplayName("HU-08 R31: una fecha fuera de la ventana devuelve una lista vacía sin consultar nada")
    void fueraDeVentanaDevuelveVacio() {
        assertThat(servicio.horasLibres(ESPACIO_ID, HOY.minusDays(1))).isEmpty();
        assertThat(servicio.horasLibres(ESPACIO_ID, HOY.plusDays(56))).isEmpty();
        verify(espacios, never()).findById(any());
        verifyNoInteractions(proveedor, horarios, bloqueos);
    }

    @Test
    @DisplayName("HU-08 R29: la ventana empieza en el hoy de Lima, no en el de UTC")
    void ventanaEnLima() {
        var ventana = servicio.ventana();

        assertThat(ventana.desde()).isEqualTo(HOY);
        assertThat(ventana.hasta()).isEqualTo(HOY.plusDays(55));
    }
}
