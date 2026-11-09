package pe.edu.ulima.espacioya.administracion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.administracion.domain.EstadoReclamo;
import pe.edu.ulima.espacioya.administracion.domain.MensajeReclamo;
import pe.edu.ulima.espacioya.administracion.domain.MotivoReclamo;
import pe.edu.ulima.espacioya.administracion.domain.PapelReclamo;
import pe.edu.ulima.espacioya.administracion.domain.Reclamo;
import pe.edu.ulima.espacioya.administracion.dto.CrearReclamoDTO;
import pe.edu.ulima.espacioya.administracion.dto.NuevoMensajeDTO;
import pe.edu.ulima.espacioya.administracion.repository.MensajeReclamoRepository;
import pe.edu.ulima.espacioya.administracion.repository.ReclamoRepository;
import pe.edu.ulima.espacioya.administracion.service.correo.DatosReclamoAbierto;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.pagos.service.PagoService;
import pe.edu.ulima.espacioya.reservas.service.ReservaParaReclamo;
import pe.edu.ulima.espacioya.reservas.service.ReservasParaReclamo;

@ExtendWith(MockitoExtension.class)
class ReclamoServiceTest {

    // 10:00 del miércoles 2026-11-04 en Lima; la reserva fue ayer de 14:00 a 16:00.
    private static final Instant AHORA = Instant.parse("2026-11-04T15:00:00Z");
    private static final Instant INICIO = Instant.parse("2026-11-03T19:00:00Z");
    private static final Instant FIN = Instant.parse("2026-11-03T21:00:00Z");
    private static final LocalDate FECHA = LocalDate.of(2026, 11, 3);
    private static final long USUARIO_ID = 7L;
    private static final long ARRENDADOR_ID = 3L;
    private static final long OTRO_ID = 8L;
    private static final long RESERVA_ID = 90L;
    private static final long RECLAMO_ID = 5L;
    private static final String IP = "10.26.0.1";
    private static final UsuarioAutenticado USUARIO = new UsuarioAutenticado(USUARIO_ID, 1L, "USUARIO");
    private static final UsuarioAutenticado ARRENDADOR = new UsuarioAutenticado(ARRENDADOR_ID, 2L, "ARRENDADOR");
    private static final UsuarioAutenticado OTRO = new UsuarioAutenticado(OTRO_ID, 3L, "USUARIO");
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(1L, 4L, "ADMIN");
    private static final String CORREO_ARRENDADOR = "ana@correo.pe";

    @Mock
    private ReclamoRepository reclamos;
    @Mock
    private MensajeReclamoRepository mensajes;
    @Mock
    private ReservasParaReclamo reservas;
    @Mock
    private PagoService pagos;
    @Mock
    private CuentaService cuentas;
    @Mock
    private AvisosReclamo avisos;
    @Mock
    private AuditoriaService auditoria;
    @Mock
    private TransactionTemplate transacciones;
    @Mock
    private Clock reloj;

    private ReclamoService servicio;

    @BeforeEach
    void preparar() {
        var vista = new VistaReclamos(reservas, mensajes, cuentas, pagos);
        servicio = new ReclamoService(reclamos, mensajes, reservas, pagos, cuentas, vista, avisos, auditoria,
                transacciones, reloj);
        lenient().when(reloj.instant()).thenReturn(AHORA);
        lenient().when(transacciones.execute(any())).thenAnswer(invocacion -> {
            TransactionCallback<?> accion = invocacion.getArgument(0);
            return accion.doInTransaction(null);
        });
        lenient().when(reclamos.saveAndFlush(any())).thenAnswer(invocacion -> {
            Reclamo reclamo = invocacion.getArgument(0);
            ReflectionTestUtils.setField(reclamo, "id", RECLAMO_ID);
            return reclamo;
        });
        lenient().when(cuentas.correoDe(ARRENDADOR_ID)).thenReturn(CORREO_ARRENDADOR);
    }

    private static ReservaParaReclamo reserva(boolean confirmada, Instant inicio, Instant fin) {
        return new ReservaParaReclamo(RESERVA_ID, USUARIO_ID, 40L, "Cancha Olivos", ARRENDADOR_ID, confirmada, FECHA,
                14, 16, inicio, fin);
    }

    private void conReserva(ReservaParaReclamo reserva) {
        when(reservas.delUsuario(USUARIO_ID, RESERVA_ID)).thenReturn(reserva);
    }

    private static CrearReclamoDTO datos(String descripcion) {
        return new CrearReclamoDTO(RESERVA_ID, "ESPACIO_CERRADO", descripcion);
    }

    private static CrearReclamoDTO datos() {
        return datos("Llegamos y estaba cerrado");
    }

    private static Reclamo reclamoAbierto() {
        var reclamo = Reclamo.abrir(RESERVA_ID, USUARIO_ID, ARRENDADOR_ID, MotivoReclamo.ESPACIO_CERRADO,
                "Estaba cerrado", AHORA.minusSeconds(3600));
        ReflectionTestUtils.setField(reclamo, "id", RECLAMO_ID);
        return reclamo;
    }

    private void sinEfectos() {
        verify(reclamos, never()).saveAndFlush(any());
        verifyNoInteractions(auditoria, avisos);
    }

    private void rechaza(String mensaje) {
        assertThatThrownBy(() -> servicio.abrir(USUARIO, datos(), IP))
                .isInstanceOf(ReglaNegocioException.class).hasMessage(mensaje);
        sinEfectos();
    }

    // ── Abrir ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-26 R1, R2: abre el reclamo ABIERTO con motivo, descripción y abierto_en = ahora, y responde el "
            + "detalle")
    void abre() {
        conReserva(reserva(true, INICIO, FIN));

        var detalle = servicio.abrir(USUARIO, datos(), IP);

        var guardado = ArgumentCaptor.forClass(Reclamo.class);
        verify(reclamos).saveAndFlush(guardado.capture());
        var reclamo = guardado.getValue();
        assertThat(reclamo.getEstado()).isEqualTo(EstadoReclamo.ABIERTO);
        assertThat(reclamo.getMotivo()).isEqualTo(MotivoReclamo.ESPACIO_CERRADO);
        assertThat(reclamo.getDescripcion()).isEqualTo("Llegamos y estaba cerrado");
        assertThat(reclamo.getAbiertoEn()).isEqualTo(AHORA);
        assertThat(detalle.id()).isEqualTo(RECLAMO_ID);
        assertThat(detalle.reservaId()).isEqualTo(RESERVA_ID);
        assertThat(detalle.espacioNombre()).isEqualTo("Cancha Olivos");
        assertThat(detalle.fecha()).isEqualTo(FECHA);
        assertThat(detalle.horaInicio()).isEqualTo(14);
        assertThat(detalle.horaFin()).isEqualTo(16);
        assertThat(detalle.estado()).isEqualTo("ABIERTO");
        assertThat(detalle.abiertoEn()).isEqualTo(AHORA);
        assertThat(detalle.mensajes()).isEmpty();
        assertThat(detalle.papel()).isEqualTo("USUARIO");
    }

    @Test
    @DisplayName("HU-26 R3: el usuario sale de la sesión y el arrendador del espacio de la reserva")
    void partesDelServidor() {
        conReserva(reserva(true, INICIO, FIN));

        servicio.abrir(USUARIO, datos(), IP);

        var guardado = ArgumentCaptor.forClass(Reclamo.class);
        verify(reclamos).saveAndFlush(guardado.capture());
        assertThat(guardado.getValue().getUsuarioId()).isEqualTo(USUARIO_ID);
        assertThat(guardado.getValue().getArrendadorId()).isEqualTo(ARRENDADOR_ID);
        verify(reservas).delUsuario(USUARIO_ID, RESERVA_ID);
    }

    @Test
    @DisplayName("HU-26 R4: en el instante exacto del inicio se puede reclamar")
    void enElInicio() {
        conReserva(reserva(true, AHORA, AHORA.plus(Duration.ofHours(2))));

        servicio.abrir(USUARIO, datos(), IP);

        verify(reclamos).saveAndFlush(any());
    }

    @Test
    @DisplayName("HU-26 R5: una reserva no CONFIRMADA responde no reclamable")
    void noConfirmada() {
        conReserva(reserva(false, INICIO, FIN));

        rechaza(ReclamoService.MENSAJE_NO_RECLAMABLE);
    }

    @Test
    @DisplayName("HU-26 R6: una reserva que no empezó responde no reclamable")
    void antesDelInicio() {
        conReserva(reserva(true, AHORA.plusSeconds(1), AHORA.plus(Duration.ofHours(2))));

        rechaza(ReclamoService.MENSAJE_NO_RECLAMABLE);
    }

    @Test
    @DisplayName("HU-26 R7: en fin + 48 h responde plazo vencido")
    void plazoVencido() {
        var fin = AHORA.minus(Duration.ofHours(48));
        conReserva(reserva(true, fin.minus(Duration.ofHours(2)), fin));

        rechaza(ReclamoService.MENSAJE_PLAZO_VENCIDO);
    }

    @Test
    @DisplayName("HU-26 R8: una reserva ya liquidada responde plazo vencido")
    void yaLiquidada() {
        conReserva(reserva(true, INICIO, FIN));
        when(pagos.reservaLiquidada(RESERVA_ID)).thenReturn(true);

        rechaza(ReclamoService.MENSAJE_PLAZO_VENCIDO);
    }

    @Test
    @DisplayName("HU-26 R9: una reserva ajena o inexistente propaga el 404 genérico sin crear nada")
    void ajena() {
        when(reservas.delUsuario(USUARIO_ID, RESERVA_ID)).thenThrow(new RecursoNoEncontradoException());

        assertThatThrownBy(() -> servicio.abrir(USUARIO, datos(), IP))
                .isInstanceOf(RecursoNoEncontradoException.class);
        sinEfectos();
        verifyNoInteractions(pagos);
    }

    @Test
    @DisplayName("HU-26 R10: ARRENDADOR y ADMIN reciben 403 al abrir y en reclamables, sin leer reservas")
    void otroRol() {
        for (var sesion : List.of(ARRENDADOR, ADMIN)) {
            assertThatThrownBy(() -> servicio.abrir(sesion, datos(), IP)).isInstanceOf(AccesoDenegadoException.class);
            assertThatThrownBy(() -> servicio.reclamables(sesion)).isInstanceOf(AccesoDenegadoException.class);
        }
        verifyNoInteractions(reservas, reclamos, transacciones, pagos);
    }

    @Test
    @DisplayName("HU-26 R12: si la reserva ya tiene un reclamo responde ya reclamada antes del plazo")
    void yaReclamada() {
        conReserva(reserva(false, INICIO, FIN));
        when(reclamos.existsByReservaId(RESERVA_ID)).thenReturn(true);

        rechaza(ReclamoService.MENSAJE_YA_RECLAMADA);
        verifyNoInteractions(pagos);
    }

    @Test
    @DisplayName("HU-26 R14: la violación de uq_reclamo_reserva se traduce a ya reclamada; otra se relanza")
    void carrera() {
        doThrow(new DataIntegrityViolationException("x",
                new SQLException("duplicate key value violates unique constraint \"uq_reclamo_reserva\"")))
                .when(transacciones).execute(any());

        assertThatThrownBy(() -> servicio.abrir(USUARIO, datos(), IP))
                .isInstanceOf(ReglaNegocioException.class).hasMessage(ReclamoService.MENSAJE_YA_RECLAMADA);

        doThrow(new DataIntegrityViolationException("x",
                new SQLException("violates foreign key constraint \"fk_reclamo_reserva\"")))
                .when(transacciones).execute(any());
        assertThatThrownBy(() -> servicio.abrir(USUARIO, datos(), IP))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("HU-26 R18: la descripción se guarda sanitizada, sin < ni >")
    void sanitiza() {
        conReserva(reserva(true, INICIO, FIN));

        servicio.abrir(USUARIO, datos("Estaba <b>cerrado</b> <script>x</script>"), IP);

        var guardado = ArgumentCaptor.forClass(Reclamo.class);
        verify(reclamos).saveAndFlush(guardado.capture());
        assertThat(guardado.getValue().getDescripcion()).doesNotContain("<", ">").startsWith("Estaba cerrado");
    }

    @Test
    @DisplayName("HU-26 R19: una descripción que queda vacía responde 400 en descripcion sin leer la reserva")
    void quedaVacia() {
        assertThatThrownBy(() -> servicio.abrir(USUARIO, datos("<b></b>"), IP))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                    assertThat(e.getCampo()).isEqualTo("descripcion");
                    assertThat(e.getMessage()).isEqualTo(CrearReclamoDTO.M_DESCRIPCION);
                });
        verifyNoInteractions(reservas, transacciones, pagos);
    }

    @Test
    @DisplayName("HU-26 R21: toma el bloqueo de generación antes de leer el reloj y antes de consultar la liquidación")
    void bloqueaAntesDelReloj() {
        conReserva(reserva(true, INICIO, FIN));

        servicio.abrir(USUARIO, datos(), IP);

        var orden = inOrder(pagos, reloj, reclamos);
        orden.verify(pagos).bloquearGeneracionLiquidaciones();
        orden.verify(reloj).instant();
        orden.verify(pagos).reservaLiquidada(RESERVA_ID);
        orden.verify(reclamos).saveAndFlush(any());
    }

    @Test
    @DisplayName("HU-26 R22: pide el aviso al arrendador con espacio, fecha, horario y motivo, sin la descripción")
    void avisaAlArrendador() {
        conReserva(reserva(true, INICIO, FIN));

        servicio.abrir(USUARIO, datos(), IP);

        verify(avisos).reclamoAbierto(CORREO_ARRENDADOR, new DatosReclamoAbierto("Cancha Olivos", FECHA, 14, 16,
                MotivoReclamo.ESPACIO_CERRADO));
    }

    @Test
    @DisplayName("HU-26 R60: audita RECLAMO_ABIERTO con usuario, IP y detalle, sin la descripción")
    void auditaApertura() {
        conReserva(reserva(true, INICIO, FIN));

        servicio.abrir(USUARIO, datos(), IP);

        verify(auditoria).registrar(new EventoAuditoria(USUARIO_ID, "RECLAMO_ABIERTO", "reclamo", RECLAMO_ID, IP,
                "reservaId=90;motivo=ESPACIO_CERRADO"));
    }

    @Test
    @DisplayName("HU-26 R63: los rechazos al abrir, al escribir y por rol no auditan")
    void rechazosSinAuditoria() {
        conReserva(reserva(false, INICIO, FIN));
        assertThatThrownBy(() -> servicio.abrir(USUARIO, datos(), IP)).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> servicio.abrir(ADMIN, datos(), IP)).isInstanceOf(AccesoDenegadoException.class);
        var cerrado = reclamoAbierto();
        cerrado.resolverAFavorDelArrendador(1L, "No", AHORA);
        when(reclamos.findParaActualizar(RECLAMO_ID)).thenReturn(Optional.of(cerrado));
        assertThatThrownBy(() -> servicio.enviarMensaje(USUARIO, RECLAMO_ID, new NuevoMensajeDTO("Hola"), IP))
                .isInstanceOf(ReglaNegocioException.class);

        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-26 R33: reclamables deja solo las propias dentro de la ventana, sin reclamo ni liquidación, "
            + "ascendentes")
    void reclamables() {
        var vencida = AHORA.minus(Duration.ofHours(48));
        when(reservas.confirmadasDe(USUARIO_ID)).thenReturn(List.of(
                new ReservaParaReclamo(9L, USUARIO_ID, 40L, "A", ARRENDADOR_ID, true, FECHA, 8, 9, INICIO, FIN),
                new ReservaParaReclamo(4L, USUARIO_ID, 40L, "A", ARRENDADOR_ID, true, FECHA, 8, 9, INICIO, FIN),
                new ReservaParaReclamo(5L, USUARIO_ID, 40L, "A", ARRENDADOR_ID, true, FECHA, 8, 9, INICIO, FIN),
                new ReservaParaReclamo(6L, USUARIO_ID, 40L, "A", ARRENDADOR_ID, true, FECHA, 8, 9, INICIO, FIN),
                new ReservaParaReclamo(7L, USUARIO_ID, 40L, "A", ARRENDADOR_ID, true, FECHA, 8, 9,
                        vencida.minusSeconds(3600), vencida),
                new ReservaParaReclamo(8L, USUARIO_ID, 40L, "A", ARRENDADOR_ID, true, FECHA, 8, 9,
                        AHORA.plusSeconds(60), AHORA.plusSeconds(3600))));
        when(reclamos.reservasConReclamo(any())).thenReturn(Set.of(5L));
        when(pagos.reservaLiquidada(anyLong())).thenAnswer(invocacion -> invocacion.getArgument(0).equals(6L));

        assertThat(servicio.reclamables(USUARIO).reservaIds()).containsExactly(4L, 9L);
    }

    // ── Mensajes ─────────────────────────────────────────────────────────

    private void conReclamoBloqueado(Reclamo reclamo) {
        when(reclamos.findParaActualizar(RECLAMO_ID)).thenReturn(Optional.of(reclamo));
    }

    private void conMensajeGuardado() {
        when(mensajes.saveAndFlush(any())).thenAnswer(invocacion -> {
            MensajeReclamo mensaje = invocacion.getArgument(0);
            ReflectionTestUtils.setField(mensaje, "id", 33L);
            return mensaje;
        });
    }

    @Test
    @DisplayName("HU-26 R23: cada parte escribe con su papel, el texto sanitizado y la fecha del reloj")
    void escribenLasDosPartes() {
        conReclamoBloqueado(reclamoAbierto());
        conMensajeGuardado();

        var delUsuario = servicio.enviarMensaje(USUARIO, RECLAMO_ID, new NuevoMensajeDTO("Sigue <i>cerrado</i>"), IP);
        var delArrendador = servicio.enviarMensaje(ARRENDADOR, RECLAMO_ID, new NuevoMensajeDTO("Lo revisamos"), IP);

        assertThat(delUsuario.papel()).isEqualTo("USUARIO");
        assertThat(delUsuario.texto()).isEqualTo("Sigue cerrado");
        assertThat(delUsuario.fecha()).isEqualTo(AHORA);
        assertThat(delUsuario.id()).isEqualTo(33L);
        assertThat(delArrendador.papel()).isEqualTo("ARRENDADOR");
        var guardados = ArgumentCaptor.forClass(MensajeReclamo.class);
        verify(mensajes, times(2)).saveAndFlush(guardados.capture());
        assertThat(guardados.getAllValues()).extracting(MensajeReclamo::getAutorId)
                .containsExactly(USUARIO_ID, ARRENDADOR_ID);
    }

    @Test
    @DisplayName("HU-26 R24: un reclamo resuelto responde reclamo cerrado sin crear el mensaje")
    void reclamoCerrado() {
        var reclamo = reclamoAbierto();
        reclamo.resolverAFavorDelUsuario(1L, "Ok", BigDecimal.TEN, AHORA);
        conReclamoBloqueado(reclamo);

        assertThatThrownBy(() -> servicio.enviarMensaje(USUARIO, RECLAMO_ID, new NuevoMensajeDTO("Hola"), IP))
                .isInstanceOf(ReglaNegocioException.class).hasMessage(ReclamoService.MENSAJE_RECLAMO_CERRADO);
        verify(mensajes, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("HU-26 R25: un tercero y un reclamo inexistente reciben el mismo 404 al leer y al escribir")
    void terceroOInexistente() {
        conReclamoBloqueado(reclamoAbierto());
        when(reclamos.findById(RECLAMO_ID)).thenReturn(Optional.of(reclamoAbierto()));
        when(reclamos.findById(RECLAMO_ID + 1)).thenReturn(Optional.empty());
        when(reclamos.findParaActualizar(RECLAMO_ID + 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.detalle(OTRO, RECLAMO_ID)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.detalle(USUARIO, RECLAMO_ID + 1))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.enviarMensaje(OTRO, RECLAMO_ID, new NuevoMensajeDTO("Hola"), IP))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.enviarMensaje(USUARIO, RECLAMO_ID + 1, new NuevoMensajeDTO("Hola"), IP))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(mensajes, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("HU-26 R26: el admin recibe 403 en la lista, el detalle y los mensajes sin leer reclamos")
    void adminNoEsParte() {
        assertThatThrownBy(() -> servicio.listar(ADMIN, null)).isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.detalle(ADMIN, RECLAMO_ID)).isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.enviarMensaje(ADMIN, RECLAMO_ID, new NuevoMensajeDTO("Hola"), IP))
                .isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(reclamos, mensajes);
    }

    @Test
    @DisplayName("HU-26 R27: con 10 mensajes propios responde límite de mensajes")
    void limite() {
        conReclamoBloqueado(reclamoAbierto());
        when(mensajes.countByReclamoIdAndAutorId(RECLAMO_ID, USUARIO_ID)).thenReturn(10L);

        assertThatThrownBy(() -> servicio.enviarMensaje(USUARIO, RECLAMO_ID, new NuevoMensajeDTO("Hola"), IP))
                .isInstanceOf(ReglaNegocioException.class).hasMessage(ReclamoService.MENSAJE_LIMITE_MENSAJES);
        verify(mensajes, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("HU-26 R27: con 9 mensajes propios todavía se escribe el décimo")
    void decimoMensaje() {
        conReclamoBloqueado(reclamoAbierto());
        when(mensajes.countByReclamoIdAndAutorId(RECLAMO_ID, USUARIO_ID)).thenReturn(9L);
        conMensajeGuardado();

        servicio.enviarMensaje(USUARIO, RECLAMO_ID, new NuevoMensajeDTO("Hola"), IP);

        verify(mensajes).saveAndFlush(any());
    }

    @Test
    @DisplayName("HU-26 R28: un texto que queda vacío responde 400 en texto sin leer el reclamo")
    void textoVacio() {
        assertThatThrownBy(() -> servicio.enviarMensaje(USUARIO, RECLAMO_ID, new NuevoMensajeDTO("<p></p>"), IP))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                    assertThat(e.getCampo()).isEqualTo("texto");
                    assertThat(e.getMessage()).isEqualTo(NuevoMensajeDTO.M_TEXTO);
                });
        verifyNoInteractions(reclamos, mensajes);
    }

    @Test
    @DisplayName("HU-26 R29: el estado y el conteo se leen con la fila bloqueada (findParaActualizar)")
    void bajoBloqueo() {
        conReclamoBloqueado(reclamoAbierto());
        conMensajeGuardado();

        servicio.enviarMensaje(USUARIO, RECLAMO_ID, new NuevoMensajeDTO("Hola"), IP);

        var orden = inOrder(reclamos, mensajes);
        orden.verify(reclamos).findParaActualizar(RECLAMO_ID);
        orden.verify(mensajes).countByReclamoIdAndAutorId(RECLAMO_ID, USUARIO_ID);
        orden.verify(mensajes).saveAndFlush(any());
        verify(reclamos, never()).findById(any());
    }

    @Test
    @DisplayName("HU-26 R61: audita RECLAMO_MENSAJE con autor, IP y detalle, sin el texto")
    void auditaMensaje() {
        conReclamoBloqueado(reclamoAbierto());
        conMensajeGuardado();

        servicio.enviarMensaje(ARRENDADOR, RECLAMO_ID, new NuevoMensajeDTO("Texto privado"), IP);

        verify(auditoria).registrar(new EventoAuditoria(ARRENDADOR_ID, "RECLAMO_MENSAJE", "reclamo", RECLAMO_ID, IP,
                "mensajeId=33;papel=ARRENDADOR"));
    }

    // ── Consulta ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-26 R31: el usuario lista los que abrió y el arrendador los de sus espacios, de a 10 y más nuevos "
            + "primero")
    void listarPorParte() {
        var orden = PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "abiertoEn", "id"));
        when(reclamos.findByUsuarioId(USUARIO_ID, orden))
                .thenReturn(new SliceImpl<>(List.of(reclamoAbierto()), orden, true));
        when(reclamos.findByArrendadorId(ARRENDADOR_ID, PageRequest.of(0, 10, orden.getSort())))
                .thenReturn(new SliceImpl<>(List.of(), PageRequest.of(0, 10), false));
        when(reservas.describir(List.of(RESERVA_ID))).thenReturn(List.of(reserva(true, INICIO, FIN)));

        var delUsuario = servicio.listar(USUARIO, "2");
        var delArrendador = servicio.listar(ARRENDADOR, null);

        assertThat(delUsuario.pagina()).isEqualTo(2);
        assertThat(delUsuario.hayMas()).isTrue();
        assertThat(delUsuario.reclamos()).singleElement().satisfies(fila -> {
            assertThat(fila.id()).isEqualTo(RECLAMO_ID);
            assertThat(fila.espacioNombre()).isEqualTo("Cancha Olivos");
            assertThat(fila.estado()).isEqualTo("ABIERTO");
            assertThat(fila.motivo()).isEqualTo("ESPACIO_CERRADO");
        });
        assertThat(delArrendador.pagina()).isEqualTo(1);
        assertThat(delArrendador.reclamos()).isEmpty();
    }

    @Test
    @DisplayName("HU-26 R32: el usuario ve el monto del reembolso; el arrendador no, y puedeEscribir depende del cupo")
    void detallePorParte() {
        var reclamo = reclamoAbierto();
        reclamo.resolverAFavorDelUsuario(1L, "Procede", new BigDecimal("80.00"), AHORA);
        when(reclamos.findById(RECLAMO_ID)).thenReturn(Optional.of(reclamo));
        when(reservas.describir(List.of(RESERVA_ID))).thenReturn(List.of(reserva(true, INICIO, FIN)));
        when(mensajes.findByReclamoIdOrderByFechaAscIdAsc(RECLAMO_ID)).thenReturn(List.of(
                MensajeReclamo.nuevo(RECLAMO_ID, USUARIO_ID, PapelReclamo.USUARIO, "Uno", AHORA.minusSeconds(60)),
                MensajeReclamo.nuevo(RECLAMO_ID, ARRENDADOR_ID, PapelReclamo.ARRENDADOR, "Dos", AHORA)));

        var delUsuario = servicio.detalle(USUARIO, RECLAMO_ID);
        var delArrendador = servicio.detalle(ARRENDADOR, RECLAMO_ID);

        assertThat(delUsuario.montoReembolso()).isEqualByComparingTo("80.00");
        assertThat(delUsuario.aFavorDe()).isEqualTo("USUARIO");
        assertThat(delUsuario.resolucion()).isEqualTo("Procede");
        assertThat(delUsuario.resueltoEn()).isEqualTo(AHORA);
        assertThat(delUsuario.mensajes()).extracting(m -> m.texto()).containsExactly("Uno", "Dos");
        assertThat(delUsuario.papel()).isEqualTo("USUARIO");
        assertThat(delUsuario.mensajesRestantes()).isEqualTo(9);
        assertThat(delUsuario.puedeEscribir()).isFalse();
        assertThat(delArrendador.montoReembolso()).isNull();
        assertThat(delArrendador.papel()).isEqualTo("ARRENDADOR");
        assertThat(delArrendador.mensajesRestantes()).isEqualTo(9);
    }

    @Test
    @DisplayName("HU-26 R32: abierto y con cupo, puedeEscribir es true; sin cupo es false; resolución nula")
    void puedeEscribir() {
        when(reclamos.findById(RECLAMO_ID)).thenReturn(Optional.of(reclamoAbierto()));
        when(reservas.describir(List.of(RESERVA_ID))).thenReturn(List.of(reserva(true, INICIO, FIN)));
        var diez = IntStream.range(0, 10).mapToObj(i -> MensajeReclamo.nuevo(RECLAMO_ID,
                ARRENDADOR_ID, PapelReclamo.ARRENDADOR, "m" + i, AHORA)).toList();
        when(mensajes.findByReclamoIdOrderByFechaAscIdAsc(RECLAMO_ID)).thenReturn(diez);

        var delUsuario = servicio.detalle(USUARIO, RECLAMO_ID);
        var delArrendador = servicio.detalle(ARRENDADOR, RECLAMO_ID);

        assertThat(delUsuario.puedeEscribir()).isTrue();
        assertThat(delUsuario.mensajesRestantes()).isEqualTo(10);
        assertThat(delUsuario.aFavorDe()).isNull();
        assertThat(delUsuario.resolucion()).isNull();
        assertThat(delUsuario.montoReembolso()).isNull();
        assertThat(delArrendador.puedeEscribir()).isFalse();
        assertThat(delArrendador.mensajesRestantes()).isZero();
    }

    @Test
    @DisplayName("HU-26 R34: una página inválida responde 400 en pagina sin consultar")
    void paginaInvalida() {
        for (var pagina : List.of("0", "-1", "abc", "", "1.5", "2147483648")) {
            assertThatThrownBy(() -> servicio.listar(USUARIO, pagina))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                        assertThat(e.getCampo()).isEqualTo("pagina");
                        assertThat(e.getMessage()).isEqualTo("La página no es válida.");
                    });
        }
        verifyNoInteractions(reclamos);
    }

    @Test
    @DisplayName("HU-26 R34: una página válida enorme responde vacía sin consultar")
    void paginaEnorme() {
        var pagina = servicio.listar(USUARIO, "2147483647");

        assertThat(pagina.reclamos()).isEmpty();
        assertThat(pagina.hayMas()).isFalse();
        verifyNoInteractions(reclamos);
    }
}
