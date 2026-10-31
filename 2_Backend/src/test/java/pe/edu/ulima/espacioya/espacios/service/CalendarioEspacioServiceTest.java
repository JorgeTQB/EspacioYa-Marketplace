package pe.edu.ulima.espacioya.espacios.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.ErrorCampoDTO;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.error.ValidacionException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.dto.HabilitacionArrendadorDTO;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.espacios.domain.BloqueoFecha;
import pe.edu.ulima.espacioya.espacios.domain.DatosEspacio;
import pe.edu.ulima.espacioya.espacios.domain.Espacio;
import pe.edu.ulima.espacioya.espacios.domain.EstadoEspacio;
import pe.edu.ulima.espacioya.espacios.domain.FranjaHoraria;
import pe.edu.ulima.espacioya.espacios.domain.HorarioSemanal;
import pe.edu.ulima.espacioya.espacios.domain.TipoEspacio;
import pe.edu.ulima.espacioya.espacios.domain.VentanaReserva;
import pe.edu.ulima.espacioya.espacios.dto.BloqueoFechaDTO;
import pe.edu.ulima.espacioya.espacios.dto.FranjaDTO;
import pe.edu.ulima.espacioya.espacios.dto.HorarioDTO;
import pe.edu.ulima.espacioya.espacios.repository.BloqueoFechaRepository;
import pe.edu.ulima.espacioya.espacios.repository.EspacioRepository;
import pe.edu.ulima.espacioya.espacios.repository.HorarioSemanalRepository;
import pe.edu.ulima.espacioya.espacios.service.impl.DisponibilidadServiceImpl;
import pe.edu.ulima.espacioya.espacios.service.puertos.BloqueReservado;

@ExtendWith(MockitoExtension.class)
class CalendarioEspacioServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 28);
    private static final LocalDate FECHA = HOY.plusDays(3);
    private static final long ID = 7L;
    private static final long ESPACIO_ID = 40L;
    private static final String IP = "10.8.0.1";
    private static final UsuarioAutenticado ARRENDADOR = new UsuarioAutenticado(ID, 31L, "ARRENDADOR");

    @Mock
    private EspacioService espacioService;
    @Mock
    private CuentaService cuentas;
    @Mock
    private EspacioRepository espacios;
    @Mock
    private HorarioSemanalRepository horarios;
    @Mock
    private BloqueoFechaRepository bloqueos;
    @Mock
    private DisponibilidadServiceImpl disponibilidad;
    @Mock
    private AuditoriaService auditoria;

    private CalendarioEspacioService servicio;

    @BeforeEach
    void preparar() {
        servicio = new CalendarioEspacioService(espacioService, cuentas, espacios, horarios, bloqueos, disponibilidad,
                auditoria);
        lenient().when(disponibilidad.ventana()).thenReturn(VentanaReserva.desde(HOY));
    }

    private static Espacio espacio(EstadoEspacio estado) {
        var datos = new DatosEspacio("Cancha Los Olivos", TipoEspacio.CANCHA, "Av. Universitaria 1234", "LOS_OLIVOS",
                20, new BigDecimal("80"), "Cancha de grass sintético techada.");
        var espacio = Espacio.crear(ID, datos, AHORA);
        ReflectionTestUtils.setField(espacio, "id", ESPACIO_ID);
        ReflectionTestUtils.setField(espacio, "estado", estado);
        return espacio;
    }

    private void editable(EstadoEspacio estado) {
        when(espacioService.arrendadorHabilitado(ARRENDADOR)).thenReturn(ID);
        when(espacios.findParaActualizar(ESPACIO_ID, ID)).thenReturn(Optional.of(espacio(estado)));
    }

    private void guardadas(FranjaHoraria... franjas) {
        when(horarios.findByEspacioIdOrderByDiaSemanaAscHoraInicioAsc(ESPACIO_ID))
                .thenReturn(List.of(franjas).stream().map(f -> HorarioSemanal.de(ESPACIO_ID, f)).toList());
    }

    private static HorarioDTO horario(FranjaDTO... franjas) {
        return new HorarioDTO(List.of(franjas));
    }

    private EventoAuditoria auditado() {
        var evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        return evento.getValue();
    }

    private static ReglaNegocioException regla(Throwable error) {
        return (ReglaNegocioException) error;
    }

    // ── Horario ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-08 R1, R2, R41: reemplaza el horario, responde ordenado y audita antes y después")
    void reemplazaHorario() {
        editable(EstadoEspacio.PUBLICADO);
        guardadas();

        var respuesta = servicio.reemplazarHorario(ARRENDADOR, ESPACIO_ID,
                horario(new FranjaDTO(2, 8, 22), new FranjaDTO(1, 14, 16), new FranjaDTO(1, 8, 12)), IP);

        assertThat(respuesta.franjas()).containsExactly(new FranjaDTO(1, 8, 12), new FranjaDTO(1, 14, 16),
                new FranjaDTO(2, 8, 22));
        var orden = inOrder(horarios);
        orden.verify(horarios).borrarDelEspacio(ESPACIO_ID);
        orden.verify(horarios).saveAll(any());
        var evento = auditado();
        assertThat(evento).isEqualTo(new EventoAuditoria(ID, CalendarioEspacioService.ACCION_HORARIO, "espacio",
                ESPACIO_ID, IP, "antes=ninguna;despues=1:8-12,1:14-16,2:8-22"));
    }

    @Test
    @DisplayName("HU-08 R3, R41: una lista vacía deja el espacio sin horario y audita despues=ninguna")
    void vaciaElHorario() {
        editable(EstadoEspacio.BORRADOR);
        guardadas(new FranjaHoraria(1, 8, 22));

        var respuesta = servicio.reemplazarHorario(ARRENDADOR, ESPACIO_ID, horario(), IP);

        assertThat(respuesta.franjas()).isEmpty();
        verify(horarios).borrarDelEspacio(ESPACIO_ID);
        assertThat(auditado().detalle()).isEqualTo("antes=1:8-22;despues=ninguna");
    }

    @Test
    @DisplayName("HU-08 R42: el mismo conjunto de franjas en otro orden no se audita")
    void mismoHorarioNoAudita() {
        editable(EstadoEspacio.PAUSADO);
        guardadas(new FranjaHoraria(1, 8, 12), new FranjaHoraria(2, 8, 12));

        servicio.reemplazarHorario(ARRENDADOR, ESPACIO_ID, horario(new FranjaDTO(2, 8, 12), new FranjaDTO(1, 8, 12)),
                IP);

        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-08 R5, R7: fin no posterior al inicio → error en franjas[i] sin tocar el horario")
    void rechazaRango() {
        editable(EstadoEspacio.PUBLICADO);

        assertThatThrownBy(() -> servicio.reemplazarHorario(ARRENDADOR, ESPACIO_ID,
                horario(new FranjaDTO(1, 8, 12), new FranjaDTO(1, 10, 10)), IP))
                .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.getErrores())
                        .containsExactly(new ErrorCampoDTO("franjas[1]",
                                CalendarioEspacioService.MENSAJE_RANGO_HORAS)));
        verify(horarios, never()).borrarDelEspacio(anyLong());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-08 R6, R7: franjas superpuestas → error en la de índice mayor sin tocar el horario")
    void rechazaSuperpuestas() {
        editable(EstadoEspacio.PUBLICADO);

        assertThatThrownBy(() -> servicio.reemplazarHorario(ARRENDADOR, ESPACIO_ID,
                horario(new FranjaDTO(1, 8, 12), new FranjaDTO(1, 11, 14)), IP))
                .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.getErrores())
                        .containsExactly(new ErrorCampoDTO("franjas[1]",
                                CalendarioEspacioService.MENSAJE_FRANJAS_SUPERPUESTAS)));
        verify(horarios, never()).saveAll(any());
    }

    @Test
    @DisplayName("HU-08 R8: consulta el horario de un espacio propio, solo con el rol")
    void consultaHorario() {
        when(cuentas.habilitacionArrendador(ARRENDADOR)).thenReturn(new HabilitacionArrendadorDTO(false, false));
        when(espacios.findByIdAndArrendadorId(ESPACIO_ID, ID)).thenReturn(Optional.of(espacio(EstadoEspacio.BAJA)));
        guardadas(new FranjaHoraria(1, 8, 12), new FranjaHoraria(1, 14, 16));

        assertThat(servicio.consultarHorario(ARRENDADOR, ESPACIO_ID).franjas())
                .containsExactly(new FranjaDTO(1, 8, 12), new FranjaDTO(1, 14, 16));
    }

    // ── Bloqueos ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-08 R10, R11, R43: bloquea una fecha de la ventana sin reservas y lo audita")
    void bloqueaFecha() {
        editable(EstadoEspacio.PUBLICADO);
        when(disponibilidad.reservasDelDia(ESPACIO_ID, FECHA)).thenReturn(List.of());

        var respuesta = servicio.bloquear(ARRENDADOR, ESPACIO_ID, new BloqueoFechaDTO(FECHA.toString()), IP);

        assertThat(respuesta.fecha()).isEqualTo(FECHA.toString());
        var guardado = ArgumentCaptor.forClass(BloqueoFecha.class);
        verify(bloqueos).save(guardado.capture());
        assertThat(guardado.getValue().getFecha()).isEqualTo(FECHA);
        assertThat(guardado.getValue().getEspacioId()).isEqualTo(ESPACIO_ID);
        assertThat(auditado()).isEqualTo(new EventoAuditoria(ID, CalendarioEspacioService.ACCION_BLOQUEO_CREADO,
                "espacio", ESPACIO_ID, IP, "fecha=" + FECHA));
    }

    @Test
    @DisplayName("HU-08 R12: una fecha que no existe en el calendario es inválida en el campo fecha")
    void rechazaFechaInexistente() {
        editable(EstadoEspacio.PUBLICADO);

        assertThatThrownBy(() -> servicio.bloquear(ARRENDADOR, ESPACIO_ID, new BloqueoFechaDTO("2026-02-30"), IP))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                    assertThat(e.getCampo()).isEqualTo("fecha");
                    assertThat(e.getMessage()).isEqualTo(CalendarioEspacioService.MENSAJE_FECHA_INVALIDA);
                });
        verify(bloqueos, never()).save(any());
    }

    @Test
    @DisplayName("HU-08 R12: sin fecha en la consulta del día responde fecha inválida")
    void disponibilidadSinFecha() {
        when(cuentas.habilitacionArrendador(ARRENDADOR)).thenReturn(new HabilitacionArrendadorDTO(true, false));
        when(espacios.findByIdAndArrendadorId(ESPACIO_ID, ID)).thenReturn(Optional.of(espacio(EstadoEspacio.BAJA)));

        assertThatThrownBy(() -> servicio.disponibilidad(ARRENDADOR, ESPACIO_ID, null))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.getCampo()).isEqualTo("fecha"));
    }

    @Test
    @DisplayName("HU-08 R19: con una reserva pagada y otra pendiente gana el aviso de reserva pagada")
    void reservaPagadaImpideBloquear() {
        editable(EstadoEspacio.PUBLICADO);
        when(disponibilidad.reservasDelDia(ESPACIO_ID, FECHA)).thenReturn(List.of(
                new BloqueReservado(FECHA, 10, false), new BloqueReservado(FECHA, 18, true)));

        assertThatThrownBy(() -> servicio.bloquear(ARRENDADOR, ESPACIO_ID, new BloqueoFechaDTO(FECHA.toString()), IP))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                    assertThat(e.getCampo()).isNull();
                    assertThat(e.getMessage()).isEqualTo(CalendarioEspacioService.MENSAJE_RESERVA_PAGADA);
                });
        verify(bloqueos, never()).save(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-08 R20: con solo una reserva pendiente avisa que está en proceso de pago")
    void reservaPendienteImpideBloquear() {
        editable(EstadoEspacio.PUBLICADO);
        when(disponibilidad.reservasDelDia(ESPACIO_ID, FECHA)).thenReturn(List.of(
                new BloqueReservado(FECHA, 10, false)));

        assertThatThrownBy(() -> servicio.bloquear(ARRENDADOR, ESPACIO_ID, new BloqueoFechaDTO(FECHA.toString()), IP))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                    assertThat(e.getCampo()).isNull();
                    assertThat(e.getMessage()).isEqualTo(CalendarioEspacioService.MENSAJE_RESERVA_PENDIENTE);
                });
        verify(bloqueos, never()).save(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-08 R16, R44: desbloquea una fecha bloqueada y lo audita")
    void desbloqueaFecha() {
        editable(EstadoEspacio.PUBLICADO);
        var bloqueo = BloqueoFecha.de(ESPACIO_ID, FECHA);
        when(bloqueos.findByEspacioIdAndFecha(ESPACIO_ID, FECHA)).thenReturn(Optional.of(bloqueo));

        servicio.desbloquear(ARRENDADOR, ESPACIO_ID, FECHA.toString(), IP);

        verify(bloqueos).delete(bloqueo);
        assertThat(auditado()).isEqualTo(new EventoAuditoria(ID, CalendarioEspacioService.ACCION_BLOQUEO_ELIMINADO,
                "espacio", ESPACIO_ID, IP, "fecha=" + FECHA));
    }

    @Test
    @DisplayName("HU-08 R17: desbloquear una fecha no bloqueada es 404 y no borra nada")
    void desbloquearNoBloqueada() {
        editable(EstadoEspacio.PUBLICADO);
        when(bloqueos.findByEspacioIdAndFecha(ESPACIO_ID, FECHA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.desbloquear(ARRENDADOR, ESPACIO_ID, FECHA.toString(), IP))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(bloqueos, never()).delete(any());
    }

    @Test
    @DisplayName("HU-08 R15: la lista de bloqueos trae la ventana y las fechas desde hoy")
    void listaBloqueos() {
        when(cuentas.habilitacionArrendador(ARRENDADOR)).thenReturn(new HabilitacionArrendadorDTO(true, false));
        when(espacios.findByIdAndArrendadorId(ESPACIO_ID, ID)).thenReturn(Optional.of(espacio(EstadoEspacio.BORRADOR)));
        when(bloqueos.findByEspacioIdAndFechaGreaterThanEqualOrderByFechaAsc(ESPACIO_ID, HOY))
                .thenReturn(List.of(BloqueoFecha.de(ESPACIO_ID, HOY), BloqueoFecha.de(ESPACIO_ID, FECHA)));

        var respuesta = servicio.listarBloqueos(ARRENDADOR, ESPACIO_ID);

        assertThat(respuesta.desde()).isEqualTo(HOY);
        assertThat(respuesta.hasta()).isEqualTo(HOY.plusDays(55));
        assertThat(respuesta.fechas()).containsExactly(HOY, FECHA);
    }

    @Test
    @DisplayName("HU-08 R27: consultar un día fuera de la ventana responde fuera de ventana")
    void disponibilidadFueraDeVentana() {
        when(cuentas.habilitacionArrendador(ARRENDADOR)).thenReturn(new HabilitacionArrendadorDTO(true, false));
        when(espacios.findByIdAndArrendadorId(ESPACIO_ID, ID)).thenReturn(Optional.of(espacio(EstadoEspacio.BORRADOR)));

        assertThatThrownBy(() -> servicio.disponibilidad(ARRENDADOR, ESPACIO_ID, HOY.plusDays(56).toString()))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e ->
                        assertThat(e.getMessage()).isEqualTo(CalendarioEspacioService.MENSAJE_FUERA_DE_VENTANA));
        verify(disponibilidad, never()).bloquesDelDia(anyLong(), any());
    }

    // ── Orden de las comprobaciones (R39) ────────────────────────────────

    @Test
    @DisplayName("HU-08 R33, R34, R39: rol y correo se comprueban antes que la propiedad")
    void rolYCorreoAntesQuePropiedad() {
        when(espacioService.arrendadorHabilitado(ARRENDADOR)).thenThrow(new AccesoDenegadoException());

        assertThatThrownBy(() -> servicio.bloquear(ARRENDADOR, ESPACIO_ID, new BloqueoFechaDTO("2026-02-30"), IP))
                .isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(espacios, bloqueos, disponibilidad);
    }

    @Test
    @DisplayName("HU-08 R33, R39: en las lecturas el rol se comprueba antes que la propiedad")
    void rolAntesQuePropiedadEnLecturas() {
        when(cuentas.habilitacionArrendador(ARRENDADOR)).thenThrow(new AccesoDenegadoException());

        assertThatThrownBy(() -> servicio.consultarHorario(ARRENDADOR, ESPACIO_ID))
                .isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(espacios, horarios);
    }

    @Test
    @DisplayName("HU-08 R32, R39: un espacio ajeno es 404 antes de mirar su estado o la fecha")
    void propiedadAntesQueEstado() {
        when(espacioService.arrendadorHabilitado(ARRENDADOR)).thenReturn(ID);
        when(espacios.findParaActualizar(ESPACIO_ID, ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.bloquear(ARRENDADOR, ESPACIO_ID, new BloqueoFechaDTO("2026-02-30"), IP))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(bloqueos, disponibilidad);
    }

    @Test
    @DisplayName("HU-08 R36, R39: el estado no editable se comprueba antes que la fecha y el horario")
    void estadoAntesQueFecha() {
        editable(EstadoEspacio.BAJA);

        assertThatThrownBy(() -> servicio.bloquear(ARRENDADOR, ESPACIO_ID, new BloqueoFechaDTO("2026-02-30"), IP))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e ->
                        assertThat(e.getMessage()).isEqualTo(EspacioService.MENSAJE_ESTADO_NO_EDITABLE));
        assertThatThrownBy(() -> servicio.reemplazarHorario(ARRENDADOR, ESPACIO_ID,
                horario(new FranjaDTO(1, 10, 10)), IP))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e ->
                        assertThat(e.getMessage()).isEqualTo(EspacioService.MENSAJE_ESTADO_NO_EDITABLE));
        verifyNoInteractions(bloqueos, horarios, auditoria);
    }

    @Test
    @DisplayName("HU-08 R13, R39: fuera de la ventana se responde antes de mirar si ya está bloqueada")
    void ventanaAntesQueYaBloqueada() {
        editable(EstadoEspacio.PUBLICADO);

        assertThatThrownBy(() -> servicio.bloquear(ARRENDADOR, ESPACIO_ID,
                new BloqueoFechaDTO(HOY.minusDays(1).toString()), IP))
                .satisfies(e -> assertThat(regla(e).getMessage())
                        .isEqualTo(CalendarioEspacioService.MENSAJE_FUERA_DE_VENTANA));
        verifyNoInteractions(bloqueos);
    }

    @Test
    @DisplayName("HU-08 R14, R39: ya bloqueada se responde antes de consultar las reservas")
    void yaBloqueadaAntesQueReservas() {
        editable(EstadoEspacio.PUBLICADO);
        when(bloqueos.existsByEspacioIdAndFecha(ESPACIO_ID, FECHA)).thenReturn(true);

        assertThatThrownBy(() -> servicio.bloquear(ARRENDADOR, ESPACIO_ID, new BloqueoFechaDTO(FECHA.toString()), IP))
                .satisfies(e -> {
                    assertThat(regla(e).getCampo()).isEqualTo("fecha");
                    assertThat(regla(e).getMessage()).isEqualTo(CalendarioEspacioService.MENSAJE_YA_BLOQUEADA);
                });
        verify(disponibilidad, never()).reservasDelDia(anyLong(), any());
        verify(bloqueos, never()).save(any());
    }
}
