package pe.edu.ulima.espacioya.reservas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.espacios.service.DisponibilidadService;
import pe.edu.ulima.espacioya.espacios.service.EspacioBloqueado;
import pe.edu.ulima.espacioya.pagos.service.PagoService;
import pe.edu.ulima.espacioya.pagos.service.ReembolsoPorCancelacion;
import pe.edu.ulima.espacioya.reservas.domain.EstadoReserva;
import pe.edu.ulima.espacioya.reservas.domain.QuienCancela;
import pe.edu.ulima.espacioya.reservas.domain.ReembolsoCalculado;
import pe.edu.ulima.espacioya.reservas.domain.Reserva;
import pe.edu.ulima.espacioya.reservas.domain.ReservaBloque;
import pe.edu.ulima.espacioya.reservas.domain.ReservaConHorario;
import pe.edu.ulima.espacioya.reservas.dto.CancelacionDTO;
import pe.edu.ulima.espacioya.reservas.dto.ConfirmarCancelacionDTO;
import pe.edu.ulima.espacioya.reservas.dto.VistaCancelacionDTO;
import pe.edu.ulima.espacioya.reservas.repository.ReservaBloqueRepository;
import pe.edu.ulima.espacioya.reservas.repository.ReservaRepository;

@ExtendWith(MockitoExtension.class)
class CancelacionReservaServiceTest {

    // Miércoles 2026-10-28 a las 10:00 de Lima.
    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final LocalDate LEJOS = LocalDate.of(2026, 11, 2);
    private static final LocalDate CERCA = LocalDate.of(2026, 10, 29);
    private static final long RESERVA_ID = 90L;
    private static final long USUARIO_ID = 7L;
    private static final long OTRO_USUARIO_ID = 8L;
    private static final long ESPACIO_ID = 40L;
    private static final long ARRENDADOR_ID = 3L;
    private static final long OTRO_ARRENDADOR_ID = 4L;
    private static final String NOMBRE = "Cancha Los Olivos";
    private static final String IP = "10.17.0.9";
    private static final String ID = String.valueOf(RESERVA_ID);
    private static final UsuarioAutenticado USUARIO = new UsuarioAutenticado(USUARIO_ID, 1L, "USUARIO");
    private static final UsuarioAutenticado ARRENDADOR = new UsuarioAutenticado(ARRENDADOR_ID, 2L, "ARRENDADOR");
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(99L, 3L, "ADMIN");
    private static final ConfirmarCancelacionDTO CIEN = new ConfirmarCancelacionDTO(new BigDecimal("161.00"));
    private static final ConfirmarCancelacionDTO CERO = new ConfirmarCancelacionDTO(new BigDecimal("0.00"));

    @Mock
    private ReservaRepository reservas;
    @Mock
    private ReservaBloqueRepository bloques;
    @Mock
    private DisponibilidadService disponibilidad;
    @Mock
    private CatalogoEspaciosService catalogo;
    @Mock
    private PagoService pagos;
    @Mock
    private AvisosCancelacion avisos;
    @Mock
    private AuditoriaService auditoria;

    private CancelacionReservaService servicio;

    @BeforeEach
    void preparar() {
        servicio = nuevoServicio(Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private CancelacionReservaService nuevoServicio(Clock reloj) {
        return new CancelacionReservaService(reservas, bloques, disponibilidad, catalogo, pagos, avisos, auditoria,
                reloj);
    }

    private static Reserva confirmada(long usuario) {
        return conEstado(usuario, EstadoReserva.CONFIRMADA);
    }

    private static Reserva conEstado(long usuario, EstadoReserva estado) {
        var reserva = Reserva.pendiente(usuario, ESPACIO_ID, new BigDecimal("80.50"), 2,
                AHORA.minusSeconds(3600));
        ReflectionTestUtils.setField(reserva, "id", RESERVA_ID);
        ReflectionTestUtils.setField(reserva, "estado", estado);
        return reserva;
    }

    private void conBloques(LocalDate dia) {
        when(bloques.findByReservaIdOrderByHoraInicioAsc(RESERVA_ID)).thenReturn(List.of(
                new ReservaBloque(RESERVA_ID, ESPACIO_ID, dia, 18),
                new ReservaBloque(RESERVA_ID, ESPACIO_ID, dia, 19)));
    }

    private Reserva paraConfirmar(Reserva reserva, long arrendadorDelEspacio, LocalDate dia) {
        when(reservas.findEspacioIdById(RESERVA_ID)).thenReturn(Optional.of(ESPACIO_ID));
        when(disponibilidad.bloquearEspacio(ESPACIO_ID)).thenReturn(new EspacioBloqueado(arrendadorDelEspacio, NOMBRE));
        when(reservas.findParaActualizar(RESERVA_ID)).thenReturn(Optional.of(reserva));
        if (dia != null) {
            conBloques(dia);
        }
        return reserva;
    }

    private void sinEfectos() {
        verify(bloques, never()).desactivarDeReserva(anyLong());
        verifyNoInteractions(pagos, auditoria, avisos);
    }

    private static void esErrorDeMonto(Runnable accion, String mensaje) {
        assertThatThrownBy(accion::run)
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(mensaje)
                .extracting(e -> ((ReglaNegocioException) e).getCampo())
                .isEqualTo("montoReembolsoVisto");
    }

    // ── Rol, id y propiedad ──────────────────────────────────────────────

    @Test
    @DisplayName("HU-15 R2, R3: un rol ajeno en cada ruta recibe 403 sin tocar repositorios ni servicios")
    void rolAjenoEsAccesoDenegado() {
        var ajenos = Map.of(QuienCancela.USUARIO, List.of(ARRENDADOR, ADMIN),
                QuienCancela.ARRENDADOR, List.of(USUARIO, ADMIN));

        ajenos.forEach((quien, sesiones) -> sesiones.forEach(sesion -> {
            assertThatThrownBy(() -> servicio.vistaPrevia(sesion, ID, quien))
                    .isInstanceOf(AccesoDenegadoException.class);
            assertThatThrownBy(() -> servicio.confirmar(sesion, ID, quien, CIEN, IP))
                    .isInstanceOf(AccesoDenegadoException.class);
            assertThatThrownBy(() -> servicio.confirmar(sesion, ID, quien, null, IP))
                    .isInstanceOf(AccesoDenegadoException.class);
        }));

        verifyNoInteractions(reservas, bloques, disponibilidad, catalogo, pagos, avisos, auditoria);
    }

    @Test
    @DisplayName("HU-15 R4: un id mal escrito es 404 sin tocar repositorios, aunque el cuerpo esté mal")
    void idMalEscrito() {
        assertThatThrownBy(() -> servicio.vistaPrevia(USUARIO, "abc", QuienCancela.USUARIO))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.confirmar(ARRENDADOR, "0", QuienCancela.ARRENDADOR, null, IP))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(reservas, bloques, disponibilidad, catalogo, pagos, avisos, auditoria);
    }

    @Test
    @DisplayName("HU-15 R5: la reserva de otro usuario es 404 en la vista previa y en la confirmación, sin cancelar")
    void ajenaDelUsuario() {
        when(reservas.findByIdAndUsuarioId(RESERVA_ID, USUARIO_ID)).thenReturn(Optional.empty());
        var ajena = paraConfirmar(confirmada(OTRO_USUARIO_ID), ARRENDADOR_ID, null);

        assertThatThrownBy(() -> servicio.vistaPrevia(USUARIO, ID, QuienCancela.USUARIO))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.confirmar(USUARIO, ID, QuienCancela.USUARIO, CIEN, IP))
                .isInstanceOf(RecursoNoEncontradoException.class);

        assertThat(ajena.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        sinEfectos();
    }

    @Test
    @DisplayName("HU-15 R5: una reserva inexistente es 404 sin tomar el bloqueo del espacio")
    void inexistente() {
        when(reservas.findEspacioIdById(RESERVA_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.confirmar(USUARIO, ID, QuienCancela.USUARIO, CIEN, IP))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(disponibilidad);
        sinEfectos();
    }

    @Test
    @DisplayName("HU-15 R6: la reserva de un espacio de otro arrendador es 404 en la vista previa y en la confirmación")
    void ajenaDelArrendador() {
        when(reservas.findById(RESERVA_ID)).thenReturn(Optional.of(confirmada(USUARIO_ID)));
        when(catalogo.nombresDelArrendador(ARRENDADOR_ID)).thenReturn(Map.of(41L, "Otro espacio"));
        var ajena = paraConfirmar(confirmada(USUARIO_ID), OTRO_ARRENDADOR_ID, null);

        assertThatThrownBy(() -> servicio.vistaPrevia(ARRENDADOR, ID, QuienCancela.ARRENDADOR))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.confirmar(ARRENDADOR, ID, QuienCancela.ARRENDADOR, CIEN, IP))
                .isInstanceOf(RecursoNoEncontradoException.class);

        assertThat(ajena.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        sinEfectos();
    }

    // ── Vista previa ─────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-15 R17, R30: la vista previa del usuario devuelve la política, el % y el monto calculados")
    void vistaPreviaDelUsuario() {
        when(reservas.findByIdAndUsuarioId(RESERVA_ID, USUARIO_ID)).thenReturn(Optional.of(confirmada(USUARIO_ID)));
        when(catalogo.nombres(List.of(ESPACIO_ID))).thenReturn(Map.of(ESPACIO_ID, NOMBRE));
        conBloques(LEJOS);

        var vista = servicio.vistaPrevia(USUARIO, ID, QuienCancela.USUARIO);

        assertThat(vista).isEqualTo(new VistaCancelacionDTO(RESERVA_ID, NOMBRE, LEJOS, 18, 20,
                new BigDecimal("161.00"), "USUARIO_CON_ANTICIPACION", 100, new BigDecimal("161.00")));
    }

    @Test
    @DisplayName("HU-15 R13, R17: la vista previa del arrendador da ARRENDADOR al 100 % aunque falten menos de 48 h")
    void vistaPreviaDelArrendador() {
        when(reservas.findById(RESERVA_ID)).thenReturn(Optional.of(confirmada(USUARIO_ID)));
        when(catalogo.nombresDelArrendador(ARRENDADOR_ID)).thenReturn(Map.of(ESPACIO_ID, NOMBRE));
        conBloques(CERCA);

        var vista = servicio.vistaPrevia(ARRENDADOR, ID, QuienCancela.ARRENDADOR);

        assertThat(vista.politica()).isEqualTo("ARRENDADOR");
        assertThat(vista.porcentaje()).isEqualTo(100);
        assertThat(vista.montoReembolso()).isEqualTo(new BigDecimal("161.00"));
        assertThat(vista.espacioNombre()).isEqualTo(NOMBRE);
    }

    @Test
    @DisplayName("HU-15 R18: la vista previa no bloquea, no cancela, no libera, no reembolsa ni audita")
    void vistaPreviaNoEscribe() {
        var reserva = confirmada(USUARIO_ID);
        when(reservas.findByIdAndUsuarioId(RESERVA_ID, USUARIO_ID)).thenReturn(Optional.of(reserva));
        when(catalogo.nombres(List.of(ESPACIO_ID))).thenReturn(Map.of(ESPACIO_ID, NOMBRE));
        conBloques(LEJOS);

        servicio.vistaPrevia(USUARIO, ID, QuienCancela.USUARIO);

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        verify(reservas, never()).findParaActualizar(any());
        verifyNoInteractions(disponibilidad);
        sinEfectos();
    }

    @Test
    @DisplayName("HU-15 R8, R9, R10: la vista previa de una no cancelable responde 400 con su mensaje")
    void vistaPreviaNoCancelable() {
        when(catalogo.nombres(List.of(ESPACIO_ID))).thenReturn(Map.of(ESPACIO_ID, NOMBRE));
        conBloques(LEJOS);
        when(reservas.findByIdAndUsuarioId(RESERVA_ID, USUARIO_ID))
                .thenReturn(Optional.of(conEstado(USUARIO_ID, EstadoReserva.CANCELADA)))
                .thenReturn(Optional.of(conEstado(USUARIO_ID, EstadoReserva.EXPIRADA)));

        assertThatThrownBy(() -> servicio.vistaPrevia(USUARIO, ID, QuienCancela.USUARIO))
                .isInstanceOf(ReglaNegocioException.class).hasMessage(CancelacionReservaService.MENSAJE_YA_CANCELADA);
        assertThatThrownBy(() -> servicio.vistaPrevia(USUARIO, ID, QuienCancela.USUARIO))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(CancelacionReservaService.MENSAJE_NO_CONFIRMADA);
    }

    // ── Confirmación ─────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-15 R33: bloquea por proyección, luego el espacio, luego la reserva, y solo entonces lee el reloj")
    void ordenDeLosBloqueos() {
        var reloj = mock(Clock.class);
        when(reloj.instant()).thenReturn(AHORA);
        paraConfirmar(confirmada(USUARIO_ID), ARRENDADOR_ID, LEJOS);

        nuevoServicio(reloj).confirmar(USUARIO, ID, QuienCancela.USUARIO, CIEN, IP);

        var orden = inOrder(reservas, disponibilidad, reloj);
        orden.verify(reservas).findEspacioIdById(RESERVA_ID);
        orden.verify(disponibilidad).bloquearEspacio(ESPACIO_ID);
        orden.verify(reservas).findParaActualizar(RESERVA_ID);
        orden.verify(reloj).instant();
        verify(reservas, never()).findById(any());
        verify(reservas, never()).findByIdAndUsuarioId(any(), any());
    }

    @Test
    @DisplayName("HU-15 R19, R20, R21: el usuario con más de 48 h cancela: CANCELADA con sus datos y bloques liberados")
    void usuarioCancelaConAnticipacion() {
        var reserva = paraConfirmar(confirmada(USUARIO_ID), ARRENDADOR_ID, LEJOS);

        var resultado = servicio.confirmar(USUARIO, ID, QuienCancela.USUARIO, CIEN, IP);

        assertThat(resultado).isEqualTo(new CancelacionDTO(RESERVA_ID, "CANCELADA", "USUARIO", AHORA,
                "USUARIO_CON_ANTICIPACION", 100, new BigDecimal("161.00")));
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(reserva.getCanceladaPor()).isEqualTo(QuienCancela.USUARIO);
        assertThat(reserva.getCanceladaEn()).isEqualTo(AHORA);
        assertThat(reserva.getMontoReembolso()).isEqualTo(new BigDecimal("161.00"));
        verify(bloques).desactivarDeReserva(RESERVA_ID);
    }

    @Test
    @DisplayName("HU-15 R22: con monto mayor que 0 dispara reembolsarCancelacion con id, monto, sesión e IP")
    void disparaElReembolso() {
        paraConfirmar(confirmada(USUARIO_ID), ARRENDADOR_ID, LEJOS);

        servicio.confirmar(USUARIO, ID, QuienCancela.USUARIO, CIEN, IP);

        verify(pagos).reembolsarCancelacion(
                new ReembolsoPorCancelacion(RESERVA_ID, new BigDecimal("161.00"), USUARIO_ID, IP));
    }

    @Test
    @DisplayName("HU-15 R12, R23: el usuario con menos de 48 h cancela al 0 % y no se dispara ningún reembolso")
    void ceroPorCientoNoReembolsa() {
        var reserva = paraConfirmar(confirmada(USUARIO_ID), ARRENDADOR_ID, CERCA);

        var resultado = servicio.confirmar(USUARIO, ID, QuienCancela.USUARIO, CERO, IP);

        assertThat(resultado.politica()).isEqualTo("USUARIO_SIN_ANTICIPACION");
        assertThat(resultado.porcentaje()).isZero();
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(reserva.getMontoReembolso()).isEqualTo(new BigDecimal("0.00"));
        verify(bloques).desactivarDeReserva(RESERVA_ID);
        verifyNoInteractions(pagos);
    }

    @Test
    @DisplayName("HU-15 R13: el arrendador cancela al 100 % aunque falten menos de 48 h, con canceladaPor ARRENDADOR")
    void arrendadorCancela() {
        var reserva = paraConfirmar(confirmada(USUARIO_ID), ARRENDADOR_ID, CERCA);

        var resultado = servicio.confirmar(ARRENDADOR, ID, QuienCancela.ARRENDADOR, CIEN, IP);

        assertThat(resultado.canceladaPor()).isEqualTo("ARRENDADOR");
        assertThat(resultado.politica()).isEqualTo("ARRENDADOR");
        assertThat(reserva.getCanceladaPor()).isEqualTo(QuienCancela.ARRENDADOR);
        verify(pagos).reembolsarCancelacion(
                new ReembolsoPorCancelacion(RESERVA_ID, new BigDecimal("161.00"), ARRENDADOR_ID, IP));
    }

    @Test
    @DisplayName("HU-15 R35: audita RESERVA_CANCELADA con la sesión, la IP y quién, espacio, política, % y monto")
    void auditaLaCancelacion() {
        paraConfirmar(confirmada(USUARIO_ID), ARRENDADOR_ID, LEJOS);

        servicio.confirmar(USUARIO, ID, QuienCancela.USUARIO, CIEN, IP);

        verify(auditoria).registrar(new EventoAuditoria(USUARIO_ID, "RESERVA_CANCELADA", "reserva", RESERVA_ID, IP,
                "canceladaPor=USUARIO;espacioId=40;politica=USUARIO_CON_ANTICIPACION;porcentaje=100;monto=161.00"));
    }

    @Test
    @DisplayName("HU-15 R26, R27: programa los correos al usuario y al arrendador del espacio bloqueado")
    void programaLosAvisos() {
        var reserva = paraConfirmar(confirmada(USUARIO_ID), ARRENDADOR_ID, LEJOS);

        servicio.confirmar(USUARIO, ID, QuienCancela.USUARIO, CIEN, IP);

        verify(avisos).programar(USUARIO_ID, ARRENDADOR_ID, NOMBRE, new ReservaConHorario(reserva, LEJOS, 18, 20),
                QuienCancela.USUARIO, new ReembolsoCalculado("USUARIO_CON_ANTICIPACION", 100,
                        new BigDecimal("161.00")));
    }

    @Test
    @DisplayName("HU-15 R31: sin cuerpo, sin monto visto, negativo o con más de 2 decimales → 400 sin leer la reserva")
    void montoVistoInvalido() {
        var invalidos = new ArrayList<ConfirmarCancelacionDTO>();
        invalidos.add(null);
        invalidos.add(new ConfirmarCancelacionDTO(null));
        invalidos.add(new ConfirmarCancelacionDTO(new BigDecimal("-1")));
        invalidos.add(new ConfirmarCancelacionDTO(new BigDecimal("1.001")));

        for (var datos : invalidos) {
            esErrorDeMonto(() -> servicio.confirmar(USUARIO, ID, QuienCancela.USUARIO, datos, IP),
                    CancelacionReservaService.MENSAJE_MONTO_VISTO);
        }
        verifyNoInteractions(reservas, disponibilidad);
        sinEfectos();
    }

    @Test
    @DisplayName("HU-15 R31, R32: 161 sin decimales es un monto visto bien escrito e igual a 161.00")
    void montoVistoSinCerosEsValido() {
        paraConfirmar(confirmada(USUARIO_ID), ARRENDADOR_ID, LEJOS);

        var resultado = servicio.confirmar(USUARIO, ID, QuienCancela.USUARIO,
                new ConfirmarCancelacionDTO(new BigDecimal("161")), IP);

        assertThat(resultado.estado()).isEqualTo("CANCELADA");
    }

    @Test
    @DisplayName("HU-15 R32, R36: si el monto visto no es el recalculado → 400 sin cancelar, liberar, reembolsar, "
            + "auditar ni avisar")
    void montoCambio() {
        var reserva = paraConfirmar(confirmada(USUARIO_ID), ARRENDADOR_ID, CERCA);

        esErrorDeMonto(() -> servicio.confirmar(USUARIO, ID, QuienCancela.USUARIO, CIEN, IP),
                CancelacionReservaService.MENSAJE_MONTO_CAMBIO);

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(reserva.getCanceladaPor()).isNull();
        sinEfectos();
    }

    @Test
    @DisplayName("HU-15 R8, R34, R36: confirmar una ya CANCELADA → 400 ya cancelada sin efectos")
    void confirmarYaCancelada() {
        paraConfirmar(conEstado(USUARIO_ID, EstadoReserva.CANCELADA), ARRENDADOR_ID, LEJOS);

        assertThatThrownBy(() -> servicio.confirmar(USUARIO, ID, QuienCancela.USUARIO, CIEN, IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(CancelacionReservaService.MENSAJE_YA_CANCELADA);
        sinEfectos();
    }

    @Test
    @DisplayName("HU-15 R9, R36: confirmar una PENDIENTE_PAGO → 400 no confirmada sin efectos")
    void confirmarPendiente() {
        paraConfirmar(conEstado(USUARIO_ID, EstadoReserva.PENDIENTE_PAGO), ARRENDADOR_ID, LEJOS);

        assertThatThrownBy(() -> servicio.confirmar(USUARIO, ID, QuienCancela.USUARIO, CIEN, IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(CancelacionReservaService.MENSAJE_NO_CONFIRMADA);
        sinEfectos();
    }

    @Test
    @DisplayName("HU-15 R10, R36: confirmar una CONFIRMADA que ya empezó → 400 ya iniciada sin efectos")
    void confirmarYaIniciada() {
        paraConfirmar(confirmada(USUARIO_ID), ARRENDADOR_ID, LocalDate.of(2026, 10, 28));
        // Bloques de 18 a 20 de hoy: aún no empieza; con el reloj a las 19:00 de Lima ya empezó.
        var reloj = Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneOffset.UTC);

        assertThatThrownBy(() -> nuevoServicio(reloj).confirmar(USUARIO, ID, QuienCancela.USUARIO, CIEN, IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(CancelacionReservaService.MENSAJE_YA_INICIADA);
        sinEfectos();
    }
}
