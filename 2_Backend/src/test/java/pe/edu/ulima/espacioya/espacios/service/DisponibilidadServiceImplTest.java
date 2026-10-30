package pe.edu.ulima.espacioya.espacios.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
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

    private static Espacio espacio(long id, EstadoEspacio estado, long arrendadorId) {
        var espacio = espacio(estado);
        ReflectionTestUtils.setField(espacio, "id", id);
        ReflectionTestUtils.setField(espacio, "arrendadorId", arrendadorId);
        return espacio;
    }

    private static HorarioSemanal franja(long espacioId, int dia, int desde, int hasta) {
        return HorarioSemanal.de(espacioId, new FranjaHoraria(dia, desde, hasta));
    }

    @Test
    @DisplayName("HU-10 R31: con tres espacios consulta horario, bloqueos y el puerto una sola vez para todo el lote")
    void loteConsultaUnaVez() {
        when(espacios.findAllById(any())).thenReturn(List.of(espacio(1L, EstadoEspacio.PUBLICADO, ARRENDADOR_ID),
                espacio(2L, EstadoEspacio.PUBLICADO, ARRENDADOR_ID), espacio(3L, EstadoEspacio.PUBLICADO, 8L)));
        when(cuentas.arrendadoresNoActivos()).thenReturn(Set.of());
        when(horarios.findByEspacioIdInAndDiaSemana(anyCollection(), eq((short) 1)))
                .thenReturn(List.of(franja(1L, 1, 8, 10), franja(2L, 1, 8, 10), franja(3L, 1, 18, 20)));
        when(bloqueos.espaciosBloqueados(anyCollection(), eq(LUNES))).thenReturn(List.of(2L));
        when(proveedor.getIfAvailable()).thenReturn(puerto);
        when(puerto.bloquesReservadosDelDia(anyCollection(), eq(LUNES))).thenReturn(Map.of());

        assertThat(servicio.conBloquesLibres(List.of(1L, 2L, 3L), LUNES)).containsExactlyInAnyOrder(1L, 3L);
        verify(espacios, times(1)).findAllById(any());
        verify(horarios, times(1)).findByEspacioIdInAndDiaSemana(anyCollection(), anyShort());
        verify(bloqueos, times(1)).espaciosBloqueados(anyCollection(), any());
        verify(puerto, times(1)).bloquesReservadosDelDia(argThat(ids -> Set.copyOf(ids).equals(Set.of(1L, 3L))),
                eq(LUNES));
        verify(puerto, never()).bloquesReservados(anyLong(), any(), any());
        verify(horarios, never()).findByEspacioIdAndDiaSemanaOrderByHoraInicioAsc(anyLong(), anyShort());
        verify(bloqueos, never()).existsByEspacioIdAndFecha(anyLong(), any());
    }

    @Test
    @DisplayName("HU-10 R32: con una colección vacía o una fecha fuera de la ventana devuelve vacío sin consultar")
    void loteVacioOFueraDeVentana() {
        assertThat(servicio.conBloquesLibres(List.of(), LUNES)).isEmpty();
        assertThat(servicio.conBloquesLibres(List.of(1L, 2L), HOY.minusDays(1))).isEmpty();
        assertThat(servicio.conBloquesLibres(List.of(1L, 2L), HOY.plusDays(56))).isEmpty();

        verifyNoInteractions(espacios, horarios, bloqueos, proveedor, cuentas);
    }

    @Test
    @DisplayName("HU-10 R5, R6: excluye el espacio sin franjas ese día y el que tiene la fecha bloqueada")
    void excluyeSinHorarioYBloqueado() {
        when(espacios.findAllById(any())).thenReturn(List.of(espacio(1L, EstadoEspacio.PUBLICADO, ARRENDADOR_ID),
                espacio(2L, EstadoEspacio.PUBLICADO, ARRENDADOR_ID),
                espacio(3L, EstadoEspacio.PUBLICADO, ARRENDADOR_ID)));
        when(cuentas.arrendadoresNoActivos()).thenReturn(Set.of());
        when(horarios.findByEspacioIdInAndDiaSemana(anyCollection(), eq((short) 1)))
                .thenReturn(List.of(franja(1L, 1, 8, 10), franja(3L, 1, 8, 10)));
        when(bloqueos.espaciosBloqueados(anyCollection(), eq(LUNES))).thenReturn(List.of(3L));
        when(proveedor.getIfAvailable()).thenReturn(null);

        assertThat(servicio.conBloquesLibres(List.of(1L, 2L, 3L), LUNES)).containsExactly(1L);
    }

    @Test
    @DisplayName("HU-10 R7: excluye el espacio cuyas horas del día están todas reservadas según el puerto")
    void excluyeTodoReservado() {
        when(espacios.findAllById(any())).thenReturn(List.of(espacio(1L, EstadoEspacio.PUBLICADO, ARRENDADOR_ID),
                espacio(2L, EstadoEspacio.PUBLICADO, ARRENDADOR_ID)));
        when(cuentas.arrendadoresNoActivos()).thenReturn(Set.of());
        when(horarios.findByEspacioIdInAndDiaSemana(anyCollection(), eq((short) 1)))
                .thenReturn(List.of(franja(1L, 1, 8, 10), franja(2L, 1, 8, 10)));
        when(bloqueos.espaciosBloqueados(anyCollection(), eq(LUNES))).thenReturn(List.of());
        when(proveedor.getIfAvailable()).thenReturn(puerto);
        when(puerto.bloquesReservadosDelDia(anyCollection(), eq(LUNES))).thenReturn(Map.of(
                1L, List.of(new BloqueReservado(LUNES, 8, true), new BloqueReservado(LUNES, 9, false)),
                2L, List.of(new BloqueReservado(LUNES, 8, true))));

        assertThat(servicio.conBloquesLibres(List.of(1L, 2L), LUNES)).containsExactly(2L);
    }

    @Test
    @DisplayName("HU-10 R8: hoy a las 10:00 en Lima excluye la franja 8–11 (la hora 10 ya empezó) y conserva la 8–12")
    void hoyExcluyeHorasEmpezadas() {
        // 10:00 del miércoles 2026-10-28 en Lima.
        var servicioALasDiez = new DisponibilidadServiceImpl(espacios, horarios, bloqueos, proveedor, cuentas,
                Clock.fixed(Instant.parse("2026-10-28T15:00:00Z"), ZoneOffset.UTC));
        when(espacios.findAllById(any())).thenReturn(List.of(espacio(1L, EstadoEspacio.PUBLICADO, ARRENDADOR_ID),
                espacio(2L, EstadoEspacio.PUBLICADO, ARRENDADOR_ID)));
        when(cuentas.arrendadoresNoActivos()).thenReturn(Set.of());
        when(horarios.findByEspacioIdInAndDiaSemana(anyCollection(), eq((short) 3)))
                .thenReturn(List.of(franja(1L, 3, 8, 11), franja(2L, 3, 8, 12)));
        when(bloqueos.espaciosBloqueados(anyCollection(), eq(HOY))).thenReturn(List.of());
        when(proveedor.getIfAvailable()).thenReturn(null);

        assertThat(servicioALasDiez.conBloquesLibres(List.of(1L, 2L), HOY)).containsExactly(2L);
    }

    @Test
    @DisplayName("HU-10 R30: excluye los no PUBLICADO y los de arrendadores no ACTIVA sin consultar su horario")
    void excluyeNoVisibles() {
        when(espacios.findAllById(any())).thenReturn(List.of(espacio(1L, EstadoEspacio.PUBLICADO, ARRENDADOR_ID),
                espacio(2L, EstadoEspacio.BORRADOR, ARRENDADOR_ID), espacio(3L, EstadoEspacio.PUBLICADO, 99L)));
        when(cuentas.arrendadoresNoActivos()).thenReturn(Set.of(99L));
        when(horarios.findByEspacioIdInAndDiaSemana(anyCollection(), eq((short) 1)))
                .thenReturn(List.of(franja(1L, 1, 8, 10)));
        when(bloqueos.espaciosBloqueados(anyCollection(), eq(LUNES))).thenReturn(List.of());
        when(proveedor.getIfAvailable()).thenReturn(null);

        assertThat(servicio.conBloquesLibres(List.of(1L, 2L, 3L), LUNES)).containsExactly(1L);
        verify(horarios).findByEspacioIdInAndDiaSemana(argThat(ids -> List.copyOf(ids).equals(List.of(1L))),
                eq((short) 1));
    }

    @Test
    @DisplayName("HU-08 R29: la ventana empieza en el hoy de Lima, no en el de UTC")
    void ventanaEnLima() {
        var ventana = servicio.ventana();

        assertThat(ventana.desde()).isEqualTo(HOY);
        assertThat(ventana.hasta()).isEqualTo(HOY.plusDays(55));
    }

    private void retenibleElLunes() {
        when(espacios.findParaReservar(ESPACIO_ID)).thenReturn(Optional.of(espacio(EstadoEspacio.PUBLICADO)));
        when(cuentas.cuentaActiva(ARRENDADOR_ID)).thenReturn(true);
        horarioDelLunes(new FranjaHoraria(1, 8, 10));
    }

    @Test
    @DisplayName("HU-12 R3: con las horas libres devuelve el nombre y el precio leídos bajo el bloqueo")
    void retenerDevuelveNombreYPrecio() {
        retenibleElLunes();
        when(proveedor.getIfAvailable()).thenReturn(null);

        var retenido = servicio.retenerSiLibres(ESPACIO_ID, LUNES, List.of(8, 9));

        assertThat(retenido.nombre()).isEqualTo("Cancha Los Olivos");
        assertThat(retenido.precioHora()).isEqualByComparingTo("80.00");
    }

    @Test
    @DisplayName("HU-12 R18: bloquea con findParaReservar antes de calcular y nunca lee el espacio sin bloqueo")
    void retenerBloqueaAntesDeCalcular() {
        retenibleElLunes();
        when(proveedor.getIfAvailable()).thenReturn(null);

        servicio.retenerSiLibres(ESPACIO_ID, LUNES, List.of(8));

        var orden = inOrder(espacios, horarios);
        orden.verify(espacios).findParaReservar(ESPACIO_ID);
        orden.verify(horarios).findByEspacioIdAndDiaSemanaOrderByHoraInicioAsc(ESPACIO_ID, (short) 1);
        verify(espacios, never()).findById(any());
    }

    @Test
    @DisplayName("HU-12 R33: espacio inexistente, en borrador o pausado es 404 sin consultar el horario")
    void retenerNoPublicado() {
        when(espacios.findParaReservar(ESPACIO_ID)).thenReturn(Optional.empty(),
                Optional.of(espacio(EstadoEspacio.BORRADOR)), Optional.of(espacio(EstadoEspacio.PAUSADO)));

        for (var intento = 0; intento < 3; intento++) {
            assertThatThrownBy(() -> servicio.retenerSiLibres(ESPACIO_ID, LUNES, List.of(8)))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }
        verifyNoInteractions(horarios, bloqueos, proveedor, cuentas);
    }

    @Test
    @DisplayName("HU-12 R33: un espacio PUBLICADO de un arrendador no ACTIVA es 404")
    void retenerArrendadorNoActivo() {
        when(espacios.findParaReservar(ESPACIO_ID)).thenReturn(Optional.of(espacio(EstadoEspacio.PUBLICADO)));
        when(cuentas.cuentaActiva(ARRENDADOR_ID)).thenReturn(false);

        assertThatThrownBy(() -> servicio.retenerSiLibres(ESPACIO_ID, LUNES, List.of(8)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(horarios, bloqueos, proveedor);
    }

    @Test
    @DisplayName("HU-12 R18: una hora reservada responde horario no disponible")
    void retenerHoraReservada() {
        retenibleElLunes();
        when(proveedor.getIfAvailable()).thenReturn(puerto);
        when(puerto.bloquesReservados(ESPACIO_ID, LUNES, LUNES))
                .thenReturn(List.of(new BloqueReservado(LUNES, 9, false)));

        assertThatThrownBy(() -> servicio.retenerSiLibres(ESPACIO_ID, LUNES, List.of(8, 9)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(DisponibilidadService.MENSAJE_HORARIO_NO_DISPONIBLE);
    }

    @Test
    @DisplayName("HU-12 R18: en una fecha bloqueada ninguna hora es libre")
    void retenerFechaBloqueada() {
        retenibleElLunes();
        when(bloqueos.existsByEspacioIdAndFecha(ESPACIO_ID, LUNES)).thenReturn(true);

        assertThatThrownBy(() -> servicio.retenerSiLibres(ESPACIO_ID, LUNES, List.of(8)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(DisponibilidadService.MENSAJE_HORARIO_NO_DISPONIBLE);
    }

    @Test
    @DisplayName("HU-12 R18: una hora ya empezada o fuera del horario responde horario no disponible")
    void retenerHoraPasadaOFueraDeHorario() {
        when(espacios.findParaReservar(ESPACIO_ID)).thenReturn(Optional.of(espacio(EstadoEspacio.PUBLICADO)));
        when(cuentas.cuentaActiva(ARRENDADOR_ID)).thenReturn(true);
        when(proveedor.getIfAvailable()).thenReturn(null);
        // 2026-10-28 es miércoles (3) y son las 22:00 en Lima: la 21 ya pasó y la 23 está libre.
        when(horarios.findByEspacioIdAndDiaSemanaOrderByHoraInicioAsc(ESPACIO_ID, (short) 3))
                .thenReturn(List.of(HorarioSemanal.de(ESPACIO_ID, new FranjaHoraria(3, 20, 24))));
        horarioDelLunes(new FranjaHoraria(1, 8, 10));

        assertThatThrownBy(() -> servicio.retenerSiLibres(ESPACIO_ID, HOY, List.of(21)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(DisponibilidadService.MENSAJE_HORARIO_NO_DISPONIBLE);
        assertThatThrownBy(() -> servicio.retenerSiLibres(ESPACIO_ID, LUNES, List.of(10)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(DisponibilidadService.MENSAJE_HORARIO_NO_DISPONIBLE);
    }
}
