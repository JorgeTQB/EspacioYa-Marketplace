package pe.edu.ulima.espacioya.administracion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.administracion.domain.EstadoReclamo;
import pe.edu.ulima.espacioya.administracion.domain.MotivoReclamo;
import pe.edu.ulima.espacioya.administracion.domain.PapelReclamo;
import pe.edu.ulima.espacioya.administracion.domain.Reclamo;
import pe.edu.ulima.espacioya.administracion.dto.ResolucionReclamoDTO;
import pe.edu.ulima.espacioya.administracion.repository.MensajeReclamoRepository;
import pe.edu.ulima.espacioya.administracion.repository.ReclamoRepository;
import pe.edu.ulima.espacioya.administracion.service.correo.DatosResolucion;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.ErrorCampoDTO;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.error.ValidacionException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.pagos.service.PagoDeReserva;
import pe.edu.ulima.espacioya.pagos.service.PagoService;
import pe.edu.ulima.espacioya.pagos.service.ReembolsoPorReclamo;
import pe.edu.ulima.espacioya.reservas.service.ReservaParaReclamo;
import pe.edu.ulima.espacioya.reservas.service.ReservasParaReclamo;

@ExtendWith(MockitoExtension.class)
class ReclamoAdminServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-04T15:00:00Z");
    private static final LocalDate FECHA = LocalDate.of(2026, 11, 3);
    private static final long ADMIN_ID = 1L;
    private static final long USUARIO_ID = 7L;
    private static final long ARRENDADOR_ID = 3L;
    private static final long RESERVA_ID = 90L;
    private static final long RECLAMO_ID = 5L;
    private static final String IP = "127.0.0.1";
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(ADMIN_ID, 1L, "ADMIN");

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

    private ReclamoAdminService servicio;

    @BeforeEach
    void preparar() {
        servicio = new ReclamoAdminService(reclamos, pagos, cuentas, new VistaReclamos(reservas, mensajes, cuentas,
                pagos), avisos, auditoria, Clock.fixed(AHORA, ZoneOffset.UTC));
        lenient().when(reservas.describir(List.of(RESERVA_ID))).thenReturn(List.of(new ReservaParaReclamo(RESERVA_ID,
                USUARIO_ID, 40L, "Cancha Olivos", ARRENDADOR_ID, true, FECHA, 14, 16, AHORA, AHORA)));
        lenient().when(pagos.pagosDeReservas(List.of(RESERVA_ID))).thenReturn(List.of(
                new PagoDeReserva(RESERVA_ID, new BigDecimal("50.00"), "RECHAZADO", AHORA, List.of()),
                new PagoDeReserva(RESERVA_ID, new BigDecimal("241.50"), "PAGADO", AHORA, List.of())));
        lenient().when(cuentas.nombres(List.of(USUARIO_ID, ARRENDADOR_ID)))
                .thenReturn(Map.of(USUARIO_ID, "Carla", ARRENDADOR_ID, "Ana"));
        lenient().when(cuentas.correoDe(USUARIO_ID)).thenReturn("carla@correo.pe");
        lenient().when(cuentas.correoDe(ARRENDADOR_ID)).thenReturn("ana@correo.pe");
    }

    private static Reclamo abierto() {
        var reclamo = Reclamo.abrir(RESERVA_ID, USUARIO_ID, ARRENDADOR_ID, MotivoReclamo.ESPACIO_CERRADO, "Cerrado",
                AHORA.minusSeconds(3600));
        ReflectionTestUtils.setField(reclamo, "id", RECLAMO_ID);
        return reclamo;
    }

    private Reclamo bloqueado() {
        var reclamo = abierto();
        when(reclamos.findParaActualizar(RECLAMO_ID)).thenReturn(Optional.of(reclamo));
        return reclamo;
    }

    private static ResolucionReclamoDTO aFavorUsuario(String reembolso, String monto) {
        return new ResolucionReclamoDTO("USUARIO", reembolso, monto == null ? null : new BigDecimal(monto),
                "Se comprobó");
    }

    private static ResolucionReclamoDTO aFavorArrendador() {
        return new ResolucionReclamoDTO("ARRENDADOR", "TOTAL", new BigDecimal("10.00"), "No se comprobó");
    }

    private void sinResolver(Reclamo reclamo) {
        assertThat(reclamo.getEstado()).isEqualTo(EstadoReclamo.ABIERTO);
        verify(pagos, never()).reembolsarReclamo(any());
        verifyNoInteractions(auditoria, avisos);
    }

    private static void esErrorDe(Throwable error, String campo, String mensaje) {
        assertThat(error).isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
            assertThat(e.getCampo()).isEqualTo(campo);
            assertThat(e.getMessage()).isEqualTo(mensaje);
        });
    }

    // ── Listas y detalle ─────────────────────────────────────────────────

    @Test
    @DisplayName("HU-26 R35: los abiertos salen de a 10, el más antiguo primero")
    void abiertos() {
        var orden = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "abiertoEn", "id"));
        when(reclamos.findByEstado(EstadoReclamo.ABIERTO, orden))
                .thenReturn(new SliceImpl<>(List.of(abierto()), orden, true));

        var pagina = servicio.listar(ADMIN, "abiertos", null);

        assertThat(pagina.pagina()).isEqualTo(1);
        assertThat(pagina.hayMas()).isTrue();
        assertThat(pagina.reclamos()).singleElement().satisfies(fila -> {
            assertThat(fila.id()).isEqualTo(RECLAMO_ID);
            assertThat(fila.espacioNombre()).isEqualTo("Cancha Olivos");
        });
    }

    @Test
    @DisplayName("HU-26 R36: los resueltos salen de a 10, por resuelto_en e id descendentes")
    void resueltos() {
        var orden = PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "resueltoEn", "id"));
        when(reclamos.findByEstadoIn(List.of(EstadoReclamo.RESUELTO_USUARIO, EstadoReclamo.RESUELTO_ARRENDADOR),
                orden)).thenReturn(new SliceImpl<>(List.of(), orden, false));

        var pagina = servicio.listar(ADMIN, "resueltos", "2");

        assertThat(pagina.pagina()).isEqualTo(2);
        assertThat(pagina.reclamos()).isEmpty();
        assertThat(pagina.hayMas()).isFalse();
    }

    @Test
    @DisplayName("HU-26 R37: el detalle trae nombres de las partes, monto pagado y monto del reembolso")
    void detalle() {
        var reclamo = abierto();
        reclamo.resolverAFavorDelUsuario(ADMIN_ID, "Procede", new BigDecimal("100.00"), AHORA);
        when(reclamos.findById(RECLAMO_ID)).thenReturn(Optional.of(reclamo));

        var detalle = servicio.detalle(ADMIN, RECLAMO_ID);

        assertThat(detalle.usuarioNombre()).isEqualTo("Carla");
        assertThat(detalle.arrendadorNombre()).isEqualTo("Ana");
        assertThat(detalle.montoPagado()).isEqualByComparingTo("241.50");
        assertThat(detalle.montoReembolso()).isEqualByComparingTo("100.00");
        assertThat(detalle.aFavorDe()).isEqualTo("USUARIO");
        assertThat(detalle.espacioNombre()).isEqualTo("Cancha Olivos");
    }

    @Test
    @DisplayName("HU-26 R38: USUARIO y ARRENDADOR reciben 403 en las tres operaciones sin leer reclamos")
    void otroRol() {
        for (var rol : List.of("USUARIO", "ARRENDADOR")) {
            var sesion = new UsuarioAutenticado(USUARIO_ID, 2L, rol);
            assertThatThrownBy(() -> servicio.listar(sesion, "abiertos", null))
                    .isInstanceOf(AccesoDenegadoException.class);
            assertThatThrownBy(() -> servicio.detalle(sesion, RECLAMO_ID)).isInstanceOf(AccesoDenegadoException.class);
            assertThatThrownBy(() -> servicio.resolver(sesion, RECLAMO_ID, aFavorArrendador(), IP))
                    .isInstanceOf(AccesoDenegadoException.class);
        }
        verifyNoInteractions(reclamos, pagos, auditoria, avisos);
    }

    @Test
    @DisplayName("HU-26 R39, R34: grupo ausente o desconocido y página inválida responden 400 en su campo")
    void grupoYPagina() {
        assertThatThrownBy(() -> servicio.listar(ADMIN, null, "0"))
                .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.getErrores()).containsExactly(
                        new ErrorCampoDTO("grupo", ReclamoAdminService.MENSAJE_GRUPO),
                        new ErrorCampoDTO("pagina", "La página no es válida.")));
        assertThatThrownBy(() -> servicio.listar(ADMIN, "todos", null))
                .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.getErrores()).containsExactly(
                        new ErrorCampoDTO("grupo", ReclamoAdminService.MENSAJE_GRUPO)));
        verifyNoInteractions(reclamos);
    }

    @Test
    @DisplayName("HU-26 R40: un reclamo inexistente responde 404")
    void inexistente() {
        when(reclamos.findById(RECLAMO_ID)).thenReturn(Optional.empty());
        when(reclamos.findParaActualizar(RECLAMO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.detalle(ADMIN, RECLAMO_ID)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.resolver(ADMIN, RECLAMO_ID, aFavorArrendador(), IP))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── Resolver ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-26 R42, R44: TOTAL deja RESUELTO_USUARIO con el monto pagado y pide el reembolso por ese monto")
    void total() {
        var reclamo = bloqueado();

        servicio.resolver(ADMIN, RECLAMO_ID, aFavorUsuario("TOTAL", "1.00"), IP);

        assertThat(reclamo.getEstado()).isEqualTo(EstadoReclamo.RESUELTO_USUARIO);
        assertThat(reclamo.getMontoReembolso()).isEqualByComparingTo("241.50");
        assertThat(reclamo.getResueltoPor()).isEqualTo(ADMIN_ID);
        assertThat(reclamo.getResueltoEn()).isEqualTo(AHORA);
        assertThat(reclamo.getResolucion()).isEqualTo("Se comprobó");
        verify(pagos).reembolsarReclamo(new ReembolsoPorReclamo(RESERVA_ID, new BigDecimal("241.50"), ADMIN_ID, IP));
    }

    @Test
    @DisplayName("HU-26 R43: PARCIAL deja el monto pedido y pide ese reembolso")
    void parcial() {
        var reclamo = bloqueado();

        servicio.resolver(ADMIN, RECLAMO_ID, aFavorUsuario("PARCIAL", "80.5"), IP);

        assertThat(reclamo.getMontoReembolso()).isEqualTo(new BigDecimal("80.50"));
        verify(pagos).reembolsarReclamo(new ReembolsoPorReclamo(RESERVA_ID, new BigDecimal("80.50"), ADMIN_ID, IP));
    }

    @Test
    @DisplayName("HU-26 R46: un parcial ausente, de 0, igual al pagado, mayor o con 3 decimales responde 400 en "
            + "montoReembolso")
    void montoInvalido() {
        for (var monto : new String[] {null, "0", "-1", "241.50", "300", "10.005"}) {
            var reclamo = bloqueado();
            var error = catchThrowable(
                    () -> servicio.resolver(ADMIN, RECLAMO_ID, aFavorUsuario("PARCIAL", monto), IP));
            esErrorDe(error, "montoReembolso", ReclamoAdminService.MENSAJE_MONTO);
            sinResolver(reclamo);
        }
    }

    @Test
    @DisplayName("HU-26 R47: a favor del usuario sin reembolso válido responde 400 en reembolso")
    void reembolsoInvalido() {
        for (var tipo : new String[] {null, "MITAD", "total"}) {
            var reclamo = bloqueado();
            var error = catchThrowable(
                    () -> servicio.resolver(ADMIN, RECLAMO_ID, aFavorUsuario(tipo, "10.00"), IP));
            esErrorDe(error, "reembolso", ReclamoAdminService.MENSAJE_REEMBOLSO);
            sinResolver(reclamo);
        }
    }

    @Test
    @DisplayName("HU-26 R49, R51: a favor del arrendador queda RESUELTO_ARRENDADOR sin monto y sin reembolso")
    void aFavorDelArrendador() {
        var reclamo = bloqueado();

        servicio.resolver(ADMIN, RECLAMO_ID, aFavorArrendador(), IP);

        assertThat(reclamo.getEstado()).isEqualTo(EstadoReclamo.RESUELTO_ARRENDADOR);
        assertThat(reclamo.getMontoReembolso()).isNull();
        assertThat(reclamo.getResolucion()).isEqualTo("No se comprobó");
        verify(pagos, never()).reembolsarReclamo(any());
    }

    @Test
    @DisplayName("HU-26 R52: una resolución que queda vacía responde 400 en resolucion sin leer el reclamo")
    void resolucionVacia() {
        var error = catchThrowable(() -> servicio.resolver(ADMIN, RECLAMO_ID,
                new ResolucionReclamoDTO("ARRENDADOR", null, null, "<p> </p>"), IP));

        esErrorDe(error, "resolucion", ResolucionReclamoDTO.M_RESOLUCION);
        verifyNoInteractions(reclamos, pagos, auditoria, avisos);
    }

    @Test
    @DisplayName("HU-26 R18: la resolución se guarda sanitizada")
    void resolucionSanitizada() {
        var reclamo = bloqueado();

        servicio.resolver(ADMIN, RECLAMO_ID, new ResolucionReclamoDTO("ARRENDADOR", null, null,
                "No <b>procede</b>"), IP);

        assertThat(reclamo.getResolucion()).isEqualTo("No procede").doesNotContain("<", ">");
    }

    @Test
    @DisplayName("HU-26 R54: un reclamo ya resuelto responde ya resuelto sin reembolso, auditoría ni correos")
    void yaResuelto() {
        var reclamo = abierto();
        reclamo.resolverAFavorDelArrendador(2L, "Antes", AHORA);
        when(reclamos.findParaActualizar(RECLAMO_ID)).thenReturn(Optional.of(reclamo));

        assertThatThrownBy(() -> servicio.resolver(ADMIN, RECLAMO_ID, aFavorUsuario("TOTAL", null), IP))
                .isInstanceOf(ReglaNegocioException.class).hasMessage(ReclamoAdminService.MENSAJE_YA_RESUELTO);
        verify(pagos, never()).reembolsarReclamo(any());
        verifyNoInteractions(auditoria, avisos);
    }

    @Test
    @DisplayName("HU-26 R55: resuelve sobre la fila bloqueada y nunca con findById")
    void bajoBloqueo() {
        bloqueado();

        servicio.resolver(ADMIN, RECLAMO_ID, aFavorArrendador(), IP);

        verify(reclamos).findParaActualizar(RECLAMO_ID);
        verify(reclamos, never()).findById(any());
    }

    @Test
    @DisplayName("HU-26 R56: responde el detalle del admin ya resuelto")
    void respondeDetalle() {
        bloqueado();

        var detalle = servicio.resolver(ADMIN, RECLAMO_ID, aFavorUsuario("TOTAL", null), IP);

        assertThat(detalle.estado()).isEqualTo("RESUELTO_USUARIO");
        assertThat(detalle.aFavorDe()).isEqualTo("USUARIO");
        assertThat(detalle.montoReembolso()).isEqualByComparingTo("241.50");
        assertThat(detalle.montoPagado()).isEqualByComparingTo("241.50");
        assertThat(detalle.resueltoEn()).isEqualTo(AHORA);
        assertThat(detalle.usuarioNombre()).isEqualTo("Carla");
    }

    @Test
    @DisplayName("HU-26 R57, R58: pide los correos a las dos partes con la resolución y el monto")
    void correos() {
        bloqueado();

        servicio.resolver(ADMIN, RECLAMO_ID, aFavorUsuario("PARCIAL", "20.00"), IP);

        verify(avisos).reclamoResuelto("carla@correo.pe", "ana@correo.pe", new DatosResolucion("Cancha Olivos",
                FECHA, 14, 16, PapelReclamo.USUARIO, "Se comprobó", new BigDecimal("20.00")));
    }

    @Test
    @DisplayName("HU-26 R59: si el reembolso falla dentro de la transacción no se programan correos ni auditoría")
    void falloSinCorreos() {
        bloqueado();
        doThrow(new IllegalStateException("sin pago")).when(pagos).reembolsarReclamo(any());

        assertThatThrownBy(() -> servicio.resolver(ADMIN, RECLAMO_ID, aFavorUsuario("TOTAL", null), IP))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(avisos, auditoria);
    }

    @Test
    @DisplayName("HU-26 R62: audita RECLAMO_RESUELTO con el detalle del reembolso, sin el texto; y sin él a favor "
            + "del arrendador")
    void auditoria() {
        bloqueado();
        servicio.resolver(ADMIN, RECLAMO_ID, aFavorUsuario("PARCIAL", "20.00"), IP);

        var eventos = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(eventos.capture());
        assertThat(eventos.getValue()).isEqualTo(new EventoAuditoria(ADMIN_ID, "RECLAMO_RESUELTO", "reclamo",
                RECLAMO_ID, IP, "reservaId=90;aFavorDe=USUARIO;reembolso=PARCIAL;monto=20.00"));
        assertThat(eventos.getValue().detalle()).doesNotContain("Se comprobó");
    }

    @Test
    @DisplayName("HU-26 R62: a favor del arrendador el detalle no lleva reembolso")
    void auditoriaArrendador() {
        bloqueado();

        servicio.resolver(ADMIN, RECLAMO_ID, aFavorArrendador(), IP);

        verify(auditoria).registrar(new EventoAuditoria(ADMIN_ID, "RECLAMO_RESUELTO", "reclamo", RECLAMO_ID, IP,
                "reservaId=90;aFavorDe=ARRENDADOR"));
    }

    @Test
    @DisplayName("HU-26 R44, R62: el reembolso se pide después de resolver y antes de auditar y avisar")
    void orden() {
        bloqueado();

        servicio.resolver(ADMIN, RECLAMO_ID, aFavorUsuario("TOTAL", null), IP);

        var orden = inOrder(reclamos, pagos, auditoria, avisos);
        orden.verify(reclamos).findParaActualizar(RECLAMO_ID);
        orden.verify(reclamos).saveAndFlush(any());
        orden.verify(pagos).reembolsarReclamo(any());
        orden.verify(auditoria).registrar(any());
        orden.verify(avisos).reclamoResuelto(any(), any(), any());
    }
}
