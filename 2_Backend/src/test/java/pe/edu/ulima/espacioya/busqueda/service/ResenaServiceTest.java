package pe.edu.ulima.espacioya.busqueda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.busqueda.domain.EstadoResena;
import pe.edu.ulima.espacioya.busqueda.domain.Resena;
import pe.edu.ulima.espacioya.busqueda.domain.ResumenCalificaciones;
import pe.edu.ulima.espacioya.busqueda.dto.CrearResenaDTO;
import pe.edu.ulima.espacioya.busqueda.dto.PaginaResenasDTO;
import pe.edu.ulima.espacioya.busqueda.dto.ResenaCreadaDTO;
import pe.edu.ulima.espacioya.busqueda.dto.ResenaPublicaDTO;
import pe.edu.ulima.espacioya.busqueda.repository.ResenaDAO;
import pe.edu.ulima.espacioya.busqueda.repository.TrozoResenas;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.reservas.service.ReservaParaResena;
import pe.edu.ulima.espacioya.reservas.service.ReservasParaResena;

@ExtendWith(MockitoExtension.class)
class ResenaServiceTest {

    // 10:00 del miércoles 2026-10-28 en Lima.
    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 28);
    private static final Instant FIN_RECIENTE = AHORA.minus(Duration.ofDays(3));
    private static final long USUARIO_ID = 7L;
    private static final long ESPACIO_ID = 40L;
    private static final long RESERVA_ID = 90L;
    private static final long RESENA_ID = 300L;
    private static final String IP = "10.24.0.1";
    private static final String CONFLICTO = "ERROR: duplicate key value violates unique constraint "
            + "\"uq_resena_reserva\"";

    private static final UsuarioAutenticado USUARIO = new UsuarioAutenticado(USUARIO_ID, 31L, "USUARIO");
    private static final CrearResenaDTO SOLICITUD = new CrearResenaDTO(RESERVA_ID, 4, "Buena cancha");

    @Mock
    private ResenaDAO resenas;
    @Mock
    private ReservasParaResena reservas;
    @Mock
    private CatalogoEspaciosService catalogo;
    @Mock
    private AuditoriaService auditoria;

    private ResenaService servicio;

    @BeforeEach
    void preparar() {
        servicio = conReloj(Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    // La cadena real de decoradores (★ reapertura): los tests de R1–R24 la recorren de punta a punta.
    private ResenaService conReloj(Clock reloj) {
        var publicacion = ConfiguracionPublicacionResena.cadena(resenas, reservas, auditoria,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), reloj);
        return new ResenaService(publicacion, resenas, reservas, catalogo, reloj);
    }

    private static ReservaParaResena reserva(boolean confirmada, Instant fin) {
        return new ReservaParaResena(RESERVA_ID, ESPACIO_ID, confirmada, fin);
    }

    private void conReserva(boolean confirmada, Instant fin) {
        when(reservas.delUsuario(USUARIO_ID, RESERVA_ID)).thenReturn(reserva(confirmada, fin));
    }

    private void seGuarda() {
        when(resenas.guardar(any(Resena.class))).thenAnswer(llamada -> {
            Resena resena = llamada.getArgument(0);
            ReflectionTestUtils.setField(resena, "id", RESENA_ID);
            return resena;
        });
    }

    private void sinCrear() {
        verify(resenas, never()).guardar(any());
        verifyNoInteractions(auditoria);
    }

    private static Resena guardada(long id, int calificacion, String comentario, Instant creadoEn) {
        var resena = Resena.nueva(RESERVA_ID + id, USUARIO_ID, ESPACIO_ID, calificacion, comentario, creadoEn);
        ReflectionTestUtils.setField(resena, "id", id);
        return resena;
    }

    // ── Crear ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-24 R1, R2: una reserva reseñable crea la fila VISIBLE y responde sus cinco campos")
    void creaLaResena() {
        conReserva(true, FIN_RECIENTE);
        seGuarda();

        var creada = servicio.crear(USUARIO, SOLICITUD, IP);

        assertThat(creada).isEqualTo(new ResenaCreadaDTO(RESENA_ID, RESERVA_ID, 4, "Buena cancha", HOY));
        var fila = ArgumentCaptor.forClass(Resena.class);
        verify(resenas).guardar(fila.capture());
        assertThat(fila.getValue().getEstado()).isEqualTo(EstadoResena.VISIBLE);
        assertThat(fila.getValue().getCreadoEn()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("HU-24 R2: la fecha es el día en Lima, no en UTC")
    void fechaEnLima() {
        // 01:00 UTC del 29 son las 20:00 del 28 en Lima.
        var deNoche = Instant.parse("2026-10-29T01:00:00Z");
        var nocturno = conReloj(Clock.fixed(deNoche, ZoneOffset.UTC));
        conReserva(true, FIN_RECIENTE);
        seGuarda();

        assertThat(nocturno.crear(USUARIO, SOLICITUD, IP).fecha()).isEqualTo(HOY);
    }

    @Test
    @DisplayName("HU-24 R1: no consulta el catálogo: un espacio que ya no es visible también se reseña")
    void espacioNoVisible() {
        conReserva(true, FIN_RECIENTE);
        seGuarda();

        assertThat(servicio.crear(USUARIO, SOLICITUD, IP).id()).isEqualTo(RESENA_ID);

        verifyNoInteractions(catalogo);
    }

    @Test
    @DisplayName("HU-24 R3: el usuario sale de la sesión y el espacio de la reserva")
    void identidadDelServidor() {
        conReserva(true, FIN_RECIENTE);
        seGuarda();

        servicio.crear(USUARIO, SOLICITUD, IP);

        var fila = ArgumentCaptor.forClass(Resena.class);
        verify(resenas).guardar(fila.capture());
        assertThat(fila.getValue().getUsuarioId()).isEqualTo(USUARIO_ID);
        assertThat(fila.getValue().getEspacioId()).isEqualTo(ESPACIO_ID);
        assertThat(fila.getValue().getReservaId()).isEqualTo(RESERVA_ID);
    }

    @Test
    @DisplayName("HU-24 R5: en el instante exacto del fin ya se puede reseñar")
    void enElInstanteDelFin() {
        conReserva(true, AHORA);
        seGuarda();

        assertThat(servicio.crear(USUARIO, SOLICITUD, IP).id()).isEqualTo(RESENA_ID);
    }

    @Test
    @DisplayName("HU-24 R6: una reserva no confirmada responde no reseñable sin crear nada")
    void noConfirmada() {
        conReserva(false, FIN_RECIENTE);

        assertThatThrownBy(() -> servicio.crear(USUARIO, SOLICITUD, IP)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage(ResenaService.MENSAJE_NO_RESENABLE);

        sinCrear();
    }

    @Test
    @DisplayName("HU-24 R7: una reserva confirmada que aún no termina responde no reseñable sin crear nada")
    void sinTerminar() {
        conReserva(true, AHORA.plusSeconds(1));

        assertThatThrownBy(() -> servicio.crear(USUARIO, SOLICITUD, IP)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage(ResenaService.MENSAJE_NO_RESENABLE);

        sinCrear();
    }

    @Test
    @DisplayName("HU-24 R8: en fin + 14 días exactos responde plazo vencido sin crear nada")
    void plazoVencido() {
        conReserva(true, AHORA.minus(Duration.ofDays(14)));

        assertThatThrownBy(() -> servicio.crear(USUARIO, SOLICITUD, IP)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage(ResenaService.MENSAJE_PLAZO_VENCIDO);

        sinCrear();
    }

    @Test
    @DisplayName("HU-24 R9: una reserva ajena o inexistente responde 404 sin crear nada")
    void ajena() {
        when(reservas.delUsuario(USUARIO_ID, RESERVA_ID)).thenThrow(new RecursoNoEncontradoException());

        assertThatThrownBy(() -> servicio.crear(USUARIO, SOLICITUD, IP))
                .isInstanceOf(RecursoNoEncontradoException.class);

        sinCrear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ARRENDADOR", "ADMIN"})
    @DisplayName("HU-24 R10: un rol distinto de USUARIO responde 403 sin leer la reserva")
    void otroRol(String rol) {
        var cuenta = new UsuarioAutenticado(USUARIO_ID, 31L, rol);

        assertThatThrownBy(() -> servicio.crear(cuenta, SOLICITUD, IP)).isInstanceOf(AccesoDenegadoException.class);

        verifyNoInteractions(reservas, resenas, auditoria);
    }

    @Test
    @DisplayName("HU-24 R12: una reserva ya reseñada responde ya reseñada sin crear otra")
    void yaResenada() {
        conReserva(true, FIN_RECIENTE);
        when(resenas.existeParaReserva(RESERVA_ID)).thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(USUARIO, SOLICITUD, IP)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage(ResenaService.MENSAJE_YA_RESENADA);

        sinCrear();
    }

    @Test
    @DisplayName("HU-24 R12: con el plazo vencido y ya reseñada, la causa es que ya la reseñó")
    void yaResenadaConPlazoVencido() {
        conReserva(true, AHORA.minus(Duration.ofDays(30)));
        when(resenas.existeParaReserva(RESERVA_ID)).thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(USUARIO, SOLICITUD, IP)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage(ResenaService.MENSAJE_YA_RESENADA);

        sinCrear();
    }

    @Test
    @DisplayName("HU-24 R14: si el INSERT choca con uq_resena_reserva, responde ya reseñada")
    void carreraContraElIndice() {
        conReserva(true, FIN_RECIENTE);
        when(resenas.guardar(any(Resena.class)))
                .thenThrow(new DataIntegrityViolationException("x", new SQLException(CONFLICTO)));

        assertThatThrownBy(() -> servicio.crear(USUARIO, SOLICITUD, IP)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage(ResenaService.MENSAJE_YA_RESENADA);

        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-24 R14: otra violación de integridad se relanza y no se disfraza de reseña repetida")
    void otraViolacionSeRelanza() {
        conReserva(true, FIN_RECIENTE);
        var otra = new DataIntegrityViolationException("x", new SQLException("viola fk_resena_espacio"));
        when(resenas.guardar(any(Resena.class))).thenThrow(otra);

        assertThatThrownBy(() -> servicio.crear(USUARIO, SOLICITUD, IP)).isSameAs(otra);
    }

    @Test
    @DisplayName("HU-24 R21: guarda y responde el comentario sanitizado, sin < ni >")
    void comentarioSanitizado() {
        conReserva(true, FIN_RECIENTE);
        seGuarda();

        var creada = servicio.crear(USUARIO, new CrearResenaDTO(RESERVA_ID, 5, "<b>Buena</b> cancha <script>"), IP);

        assertThat(creada.comentario()).isEqualTo("Buena cancha");
        var fila = ArgumentCaptor.forClass(Resena.class);
        verify(resenas).guardar(fila.capture());
        assertThat(fila.getValue().getComentario()).isEqualTo("Buena cancha").doesNotContain("<", ">");
    }

    @Test
    @DisplayName("HU-24 R22: un comentario que queda vacío falla en su campo sin leer la reserva")
    void comentarioQuedaVacio() {
        var solicitud = new CrearResenaDTO(RESERVA_ID, 5, "<script></script>");

        var error = catchThrowableOfType(ReglaNegocioException.class, () -> servicio.crear(USUARIO, solicitud, IP));

        assertThat(error.getCampo()).isEqualTo("comentario");
        assertThat(error).hasMessage(CrearResenaDTO.M_COMENTARIO);
        verifyNoInteractions(reservas, resenas, auditoria);
    }

    @Test
    @DisplayName("HU-24 R23: audita RESENA_CREADA con el usuario, la IP y el detalle, sin el comentario")
    void auditaLaCreacion() {
        conReserva(true, FIN_RECIENTE);
        seGuarda();

        servicio.crear(USUARIO, SOLICITUD, IP);

        var evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue()).isEqualTo(new EventoAuditoria(USUARIO_ID, "RESENA_CREADA", "resena", RESENA_ID,
                IP, "reservaId=90;espacioId=40;calificacion=4"));
    }

    @Test
    @DisplayName("HU-24 R24: ningún rechazo registra RESENA_CREADA")
    void rechazoSinAuditoria() {
        conReserva(false, FIN_RECIENTE);

        assertThatThrownBy(() -> servicio.crear(USUARIO, SOLICITUD, IP)).isInstanceOf(ReglaNegocioException.class);

        verifyNoInteractions(auditoria);
    }

    // ── Pendientes ───────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-24 R25: solo las reseñables, en orden ascendente, sin las ya reseñadas ni las fuera de plazo")
    void pendientes() {
        when(reservas.confirmadasDe(USUARIO_ID)).thenReturn(List.of(
                new ReservaParaResena(9L, ESPACIO_ID, true, FIN_RECIENTE),
                new ReservaParaResena(4L, ESPACIO_ID, true, AHORA),
                new ReservaParaResena(5L, ESPACIO_ID, true, FIN_RECIENTE),
                new ReservaParaResena(6L, ESPACIO_ID, true, AHORA.plusSeconds(1)),
                new ReservaParaResena(7L, ESPACIO_ID, true, AHORA.minus(Duration.ofDays(14)))));
        when(resenas.reservasResenadasPor(USUARIO_ID)).thenReturn(Set.of(5L));

        assertThat(servicio.pendientes(USUARIO).reservaIds()).containsExactly(4L, 9L);
    }

    @Test
    @DisplayName("HU-24 R25: sin reservas confirmadas la lista queda vacía")
    void sinPendientes() {
        when(reservas.confirmadasDe(USUARIO_ID)).thenReturn(List.of());
        when(resenas.reservasResenadasPor(USUARIO_ID)).thenReturn(Set.of());

        assertThat(servicio.pendientes(USUARIO).reservaIds()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ARRENDADOR", "ADMIN"})
    @DisplayName("HU-24 R26: en pendientes, un rol distinto de USUARIO responde 403 sin leer las reservas")
    void pendientesConOtroRol(String rol) {
        var cuenta = new UsuarioAutenticado(USUARIO_ID, 31L, rol);

        assertThatThrownBy(() -> servicio.pendientes(cuenta)).isInstanceOf(AccesoDenegadoException.class);

        verifyNoInteractions(reservas, resenas);
    }

    // ── Ficha ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-24 R27, R35: el resumen se calcula solo con las reseñas VISIBLE")
    void resumenDeVisibles() {
        // El filtro VISIBLE lo fija el DAO (R56, ResenaDAOJpaTest); el servicio solo le pide el resumen visible.
        when(resenas.resumenVisibles(ESPACIO_ID)).thenReturn(ResumenCalificaciones.de(4, 17));

        assertThat(servicio.resumen(ESPACIO_ID)).isEqualTo(new ResumenCalificaciones(new BigDecimal("4.3"), 4));
    }

    @Test
    @DisplayName("HU-24 R29: sin reseñas visibles, promedio nulo y total 0")
    void resumenVacio() {
        when(resenas.resumenVisibles(ESPACIO_ID)).thenReturn(ResumenCalificaciones.de(0, 0));

        assertThat(servicio.resumen(ESPACIO_ID)).isEqualTo(new ResumenCalificaciones(null, 0));
    }

    @Test
    @DisplayName("HU-24 R30, R31: la primera página pide 10 por creado_en e id descendentes y solo expone 4 campos")
    void primeraPagina() {
        // El orden y el PageRequest los fija el DAO (R54, ResenaDAOJpaTest); aquí, la página 1 y el tamaño 10.
        when(catalogo.esVisible(ESPACIO_ID)).thenReturn(true);
        var deNoche = Instant.parse("2026-10-29T01:00:00Z");
        var resena = guardada(3L, 5, "Excelente", deNoche);
        when(resenas.visiblesDeEspacio(ESPACIO_ID, 1, 10)).thenReturn(new TrozoResenas(List.of(resena), true));

        var pagina = servicio.deEspacio("40", null);

        assertThat(pagina).isEqualTo(new PaginaResenasDTO(1,
                List.of(new ResenaPublicaDTO(3L, 5, "Excelente", HOY)), true));
    }

    @Test
    @DisplayName("HU-24 R30: la página pedida se respeta y sin más filas hayMas es false")
    void paginaPedida() {
        when(catalogo.esVisible(ESPACIO_ID)).thenReturn(true);
        when(resenas.visiblesDeEspacio(ESPACIO_ID, 3, 10)).thenReturn(new TrozoResenas(List.of(), false));

        assertThat(servicio.deEspacio("40", "3")).isEqualTo(new PaginaResenasDTO(3, List.of(), false));
        verify(resenas).visiblesDeEspacio(ESPACIO_ID, 3, ResenaService.TAMANO_PAGINA);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "0", "-1", "1.5", "99999999999999999999"})
    @DisplayName("HU-24 R33: un id mal escrito responde 404 antes de validar pagina y sin consultar")
    void idMalEscrito(String id) {
        assertThatThrownBy(() -> servicio.deEspacio(id, "abc")).isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(catalogo, resenas);
    }

    @Test
    @DisplayName("HU-24 R33: un espacio no visible responde 404 antes de validar pagina")
    void espacioInvisible() {
        when(catalogo.esVisible(ESPACIO_ID)).thenReturn(false);

        assertThatThrownBy(() -> servicio.deEspacio("40", "abc")).isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(resenas);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "-1", "abc", "1.5", "2147483648"})
    @DisplayName("HU-24 R34: una página que no es un entero de 1 a Integer.MAX_VALUE falla en su campo")
    void paginaInvalida(String pagina) {
        when(catalogo.esVisible(ESPACIO_ID)).thenReturn(true);

        var error = catchThrowableOfType(ReglaNegocioException.class, () -> servicio.deEspacio("40", pagina));

        assertThat(error.getCampo()).isEqualTo("pagina");
        assertThat(error).hasMessage(BusquedaService.MENSAJE_PAGINA_INVALIDA);
        verifyNoInteractions(resenas);
    }

    @ParameterizedTest
    @ValueSource(strings = {"214748366", "2147483647"})
    @DisplayName("HU-24 R34: una página válida cuyo offset no cabe en un int responde vacía, sin llegar a Spring Data")
    void paginaEnorme(String pagina) {
        // El tope de offset vive en ResenaDAOJpa (R55, ResenaDAOJpaTest): el servicio solo pasa n y 10 al DAO.
        when(catalogo.esVisible(ESPACIO_ID)).thenReturn(true);
        var numero = Integer.parseInt(pagina);
        when(resenas.visiblesDeEspacio(ESPACIO_ID, numero, 10)).thenReturn(TrozoResenas.VACIO);

        assertThat(servicio.deEspacio("40", pagina)).isEqualTo(new PaginaResenasDTO(numero, List.of(), false));
    }

    @Test
    @DisplayName("HU-24 R34: la última página cuyo offset cabe en un int todavía consulta")
    void ultimaPaginaConsultable() {
        when(catalogo.esVisible(ESPACIO_ID)).thenReturn(true);
        when(resenas.visiblesDeEspacio(ESPACIO_ID, 214748365, 10)).thenReturn(TrozoResenas.VACIO);

        assertThat(servicio.deEspacio("40", "214748365").resenas()).isEmpty();

        verify(resenas).visiblesDeEspacio(ESPACIO_ID, 214748365, 10);
    }
}
