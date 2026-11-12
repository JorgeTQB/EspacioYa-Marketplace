package pe.edu.ulima.espacioya.espacios.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
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
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.dto.HabilitacionArrendadorDTO;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.espacios.domain.DatosEspacio;
import pe.edu.ulima.espacioya.espacios.domain.Espacio;
import pe.edu.ulima.espacioya.espacios.domain.EstadoEspacio;
import pe.edu.ulima.espacioya.espacios.domain.TipoEspacio;
import pe.edu.ulima.espacioya.espacios.dto.EstadoEspacioDTO;
import pe.edu.ulima.espacioya.espacios.repository.EspacioRepository;
import pe.edu.ulima.espacioya.espacios.repository.FotoEspacioRepository;
import pe.edu.ulima.espacioya.espacios.repository.ZonaRepository;
import pe.edu.ulima.espacioya.espacios.service.puertos.BloqueReservado;
import pe.edu.ulima.espacioya.espacios.service.puertos.ReservasDelEspacio;

@ExtendWith(MockitoExtension.class)
class EstadoEspacioServiceTest {

    // 15:00 en Lima del 2026-11-04.
    private static final Instant AHORA = Instant.parse("2026-11-04T20:00:00Z");
    private static final Instant ANTES = AHORA.minusSeconds(3600);
    private static final LocalDate HOY = LocalDate.of(2026, 11, 4);
    private static final LocalDate ULTIMO_DIA = HOY.plusDays(55);
    private static final long ARRENDADOR_ID = 7L;
    private static final long ADMIN_ID = 1L;
    private static final long ESPACIO_ID = 40L;
    private static final String IP = "10.21.0.1";
    private static final UsuarioAutenticado ARRENDADOR = new UsuarioAutenticado(ARRENDADOR_ID, 31L, "ARRENDADOR");
    private static final UsuarioAutenticado USUARIO = new UsuarioAutenticado(9L, 32L, "USUARIO");
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(ADMIN_ID, 33L, "ADMIN");
    private static final DatosEspacio DATOS = new DatosEspacio("Cancha Los Olivos", TipoEspacio.CANCHA,
            "Av. Universitaria 1234", "LOS_OLIVOS", 20, new BigDecimal("80"), "Cancha de grass sintético techada.");

    @Mock
    private EspacioRepository espacios;
    @Mock
    private FotoEspacioRepository fotos;
    @Mock
    private ZonaRepository zonas;
    @Mock
    private CuentaService cuentas;
    @Mock
    private AuditoriaService auditoria;
    @Mock
    private ObjectProvider<ReservasDelEspacio> proveedor;
    @Mock
    private ReservasDelEspacio puerto;

    private EstadoEspacioService servicio;

    @BeforeEach
    void preparar() {
        var reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
        var espacioService = new EspacioService(espacios, fotos, zonas, cuentas, auditoria, reloj);
        servicio = new EstadoEspacioService(espacioService, espacios, fotos, proveedor, auditoria, reloj);
        lenient().when(proveedor.getIfAvailable()).thenReturn(puerto);
    }

    private static Espacio espacio(EstadoEspacio estado) {
        var espacio = Espacio.crear(ARRENDADOR_ID, DATOS, ANTES);
        ReflectionTestUtils.setField(espacio, "id", ESPACIO_ID);
        ReflectionTestUtils.setField(espacio, "estado", estado);
        return espacio;
    }

    private void habilitado(boolean correo, boolean identidad) {
        when(cuentas.habilitacionArrendador(ARRENDADOR)).thenReturn(new HabilitacionArrendadorDTO(correo, identidad));
    }

    private Espacio propio(EstadoEspacio estado) {
        var espacio = espacio(estado);
        when(espacios.findParaActualizar(ESPACIO_ID, ARRENDADOR_ID)).thenReturn(Optional.of(espacio));
        return espacio;
    }

    private Espacio deCualquiera(EstadoEspacio estado) {
        var espacio = espacio(estado);
        when(espacios.findParaReservar(ESPACIO_ID)).thenReturn(Optional.of(espacio));
        return espacio;
    }

    private void reservas(BloqueReservado... bloques) {
        when(puerto.bloquesReservados(ESPACIO_ID, HOY, ULTIMO_DIA)).thenReturn(List.of(bloques));
    }

    private EventoAuditoria auditado() {
        var evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        return evento.getValue();
    }

    private static void sinCambio(Espacio espacio, EstadoEspacio estado) {
        assertThat(espacio.getEstado()).isEqualTo(estado);
        assertThat(espacio.getActualizadoEn()).isEqualTo(ANTES);
    }

    private static void reglaCon(Throwable error, String campo, String mensaje) {
        assertThat(error).isInstanceOf(ReglaNegocioException.class).hasMessage(mensaje);
        assertThat(((ReglaNegocioException) error).getCampo()).isEqualTo(campo);
    }

    // ── Pausar ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-21 R1: pausar un espacio propio PUBLICADO lo deja PAUSADO y devuelve su EspacioDTO")
    void pausaUnPublicado() {
        habilitado(true, false);
        var espacio = propio(EstadoEspacio.PUBLICADO);

        var dto = servicio.pausar(ARRENDADOR, ESPACIO_ID, IP);

        assertThat(dto.id()).isEqualTo(ESPACIO_ID);
        assertThat(dto.estado()).isEqualTo("PAUSADO");
        assertThat(espacio.getEstado()).isEqualTo(EstadoEspacio.PAUSADO);
    }

    @Test
    @DisplayName("HU-21 R6: pausar un espacio que no está PUBLICADO da 400 sin cambiarlo ni auditar")
    void pausarFueraDePublicadoRechaza() {
        habilitado(true, true);
        for (var estado : new EstadoEspacio[] {EstadoEspacio.BORRADOR, EstadoEspacio.PAUSADO, EstadoEspacio.BAJA,
                EstadoEspacio.OCULTO}) {
            var espacio = propio(estado);

            assertThatThrownBy(() -> servicio.pausar(ARRENDADOR, ESPACIO_ID, IP))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(EstadoEspacioService.MENSAJE_SOLO_PUBLICADO);
            sinCambio(espacio, estado);
        }
        verifyNoInteractions(auditoria);
    }

    // ── Reanudar ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-21 R7: reanudar un PAUSADO con identidad verificada y foto lo deja PUBLICADO")
    void reanudaUnPausado() {
        habilitado(true, true);
        var espacio = propio(EstadoEspacio.PAUSADO);
        when(fotos.countByEspacioId(ESPACIO_ID)).thenReturn(1L);

        var dto = servicio.reanudar(ARRENDADOR, ESPACIO_ID, IP);

        assertThat(dto.estado()).isEqualTo("PUBLICADO");
        assertThat(espacio.getEstado()).isEqualTo(EstadoEspacio.PUBLICADO);
    }

    @Test
    @DisplayName("HU-21 R8: reanudar un espacio que no está PAUSADO da 400 sin cambiarlo")
    void reanudarFueraDePausadoRechaza() {
        habilitado(true, true);
        for (var estado : new EstadoEspacio[] {EstadoEspacio.BORRADOR, EstadoEspacio.PUBLICADO, EstadoEspacio.BAJA,
                EstadoEspacio.OCULTO}) {
            var espacio = propio(estado);

            assertThatThrownBy(() -> servicio.reanudar(ARRENDADOR, ESPACIO_ID, IP))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(EstadoEspacioService.MENSAJE_SOLO_PAUSADO);
            sinCambio(espacio, estado);
        }
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-21 R9: reanudar sin identidad VERIFICADA da 400 con el mensaje de HU-07")
    void reanudarSinIdentidadRechaza() {
        habilitado(true, false);
        var espacio = propio(EstadoEspacio.PAUSADO);

        assertThatThrownBy(() -> servicio.reanudar(ARRENDADOR, ESPACIO_ID, IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(EspacioService.MENSAJE_IDENTIDAD_NO_VERIFICADA);
        sinCambio(espacio, EstadoEspacio.PAUSADO);
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-21 R10: reanudar un espacio sin fotos da 400 con el mensaje de HU-07")
    void reanudarSinFotosRechaza() {
        habilitado(true, true);
        var espacio = propio(EstadoEspacio.PAUSADO);
        when(fotos.countByEspacioId(ESPACIO_ID)).thenReturn(0L);

        assertThatThrownBy(() -> servicio.reanudar(ARRENDADOR, ESPACIO_ID, IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(EspacioService.MENSAJE_SIN_FOTOS);
        sinCambio(espacio, EstadoEspacio.PAUSADO);
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-21 R11: con varias reglas incumplidas gana estado, luego identidad, luego fotos")
    void reanudarOrdenDeReglas() {
        when(cuentas.habilitacionArrendador(ARRENDADOR)).thenReturn(new HabilitacionArrendadorDTO(true, false));
        propio(EstadoEspacio.PUBLICADO);
        assertThatThrownBy(() -> servicio.reanudar(ARRENDADOR, ESPACIO_ID, IP))
                .hasMessage(EstadoEspacioService.MENSAJE_SOLO_PAUSADO);

        propio(EstadoEspacio.PAUSADO);
        assertThatThrownBy(() -> servicio.reanudar(ARRENDADOR, ESPACIO_ID, IP))
                .hasMessage(EspacioService.MENSAJE_IDENTIDAD_NO_VERIFICADA);
        verify(fotos, never()).countByEspacioId(anyLong());
    }

    // ── Dar de baja ──────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-21 R12: dar de baja un PUBLICADO o PAUSADO propio sin reservas lo deja en BAJA")
    void daDeBajaSinReservas() {
        habilitado(true, false);
        reservas();
        for (var estado : new EstadoEspacio[] {EstadoEspacio.PUBLICADO, EstadoEspacio.PAUSADO}) {
            var espacio = propio(estado);

            var dto = servicio.darDeBaja(ARRENDADOR, ESPACIO_ID, IP);

            assertThat(dto.estado()).isEqualTo("BAJA");
            assertThat(espacio.getEstado()).isEqualTo(EstadoEspacio.BAJA);
        }
    }

    @Test
    @DisplayName("HU-21 R13: dar de baja con una reserva CONFIRMADA futura da 400 en el campo reservas")
    void bajaConReservaFuturaRechaza() {
        habilitado(true, true);
        var espacio = propio(EstadoEspacio.PAUSADO);
        reservas(new BloqueReservado(HOY.plusDays(3), 10, true));

        var error = catchThrowable(
                () -> servicio.darDeBaja(ARRENDADOR, ESPACIO_ID, IP));

        reglaCon(error, EstadoEspacioService.CAMPO_RESERVAS, EstadoEspacioService.MENSAJE_RESERVAS_POR_TERMINAR);
        sinCambio(espacio, EstadoEspacio.PAUSADO);
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-21 R14: una reserva en curso o una PENDIENTE_PAGO vigente también impiden la baja")
    void bajaConReservaEnCursoOPendienteRechaza() {
        habilitado(true, true);
        var espacio = propio(EstadoEspacio.PUBLICADO);

        reservas(new BloqueReservado(HOY, 15, true));
        reglaCon(catchThrowable(() -> servicio.darDeBaja(ARRENDADOR, ESPACIO_ID, IP)),
                EstadoEspacioService.CAMPO_RESERVAS, EstadoEspacioService.MENSAJE_RESERVAS_POR_TERMINAR);

        reservas(new BloqueReservado(HOY, 18, false));
        reglaCon(catchThrowable(() -> servicio.darDeBaja(ARRENDADOR, ESPACIO_ID, IP)),
                EstadoEspacioService.CAMPO_RESERVAS, EstadoEspacioService.MENSAJE_RESERVAS_POR_TERMINAR);

        sinCambio(espacio, EstadoEspacio.PUBLICADO);
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-21 R15: los bloques ya terminados hoy no impiden la baja")
    void bajaConBloquesTerminadosSeCompleta() {
        habilitado(true, true);
        var espacio = propio(EstadoEspacio.PUBLICADO);
        reservas(new BloqueReservado(HOY, 9, true), new BloqueReservado(HOY, 14, true));

        servicio.darDeBaja(ARRENDADOR, ESPACIO_ID, IP);

        assertThat(espacio.getEstado()).isEqualTo(EstadoEspacio.BAJA);
    }

    @Test
    @DisplayName("HU-21 R16: dar de baja un BORRADOR, BAJA u OCULTO da 400 sin consultar reservas")
    void bajaDeEstadoNoAdmitidoRechazaSinConsultarReservas() {
        habilitado(true, true);
        for (var estado : new EstadoEspacio[] {EstadoEspacio.BORRADOR, EstadoEspacio.BAJA, EstadoEspacio.OCULTO}) {
            var espacio = propio(estado);

            assertThatThrownBy(() -> servicio.darDeBaja(ARRENDADOR, ESPACIO_ID, IP))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(EstadoEspacioService.MENSAJE_BAJA_NO_PERMITIDA);
            sinCambio(espacio, estado);
        }
        verifyNoInteractions(puerto, auditoria);
    }

    @Test
    @DisplayName("HU-21 R12: sin bean del puerto de reservas, la baja se completa")
    void bajaSinPuertoSeCompleta() {
        habilitado(true, true);
        var espacio = propio(EstadoEspacio.PUBLICADO);
        when(proveedor.getIfAvailable()).thenReturn(null);

        servicio.darDeBaja(ARRENDADOR, ESPACIO_ID, IP);

        assertThat(espacio.getEstado()).isEqualTo(EstadoEspacio.BAJA);
    }

    @Test
    @DisplayName("HU-21 R17: las reservas se leen después de tomar el bloqueo de la fila del espacio")
    void reservasSeLeenTrasElBloqueo() {
        habilitado(true, true);
        propio(EstadoEspacio.PUBLICADO);
        reservas();

        servicio.darDeBaja(ARRENDADOR, ESPACIO_ID, IP);

        var orden = inOrder(espacios, puerto);
        orden.verify(espacios).findParaActualizar(ESPACIO_ID, ARRENDADOR_ID);
        orden.verify(puerto).bloquesReservados(ESPACIO_ID, HOY, ULTIMO_DIA);
    }

    // ── Control de acceso del arrendador ─────────────────────────────────

    @Test
    @DisplayName("HU-21 R25: una sesión sin rol ARRENDADOR recibe 403 sin leer el espacio")
    void otroRolRecibe403() {
        when(cuentas.habilitacionArrendador(USUARIO)).thenThrow(new AccesoDenegadoException());

        assertThatThrownBy(() -> servicio.pausar(USUARIO, ESPACIO_ID, IP)).isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.reanudar(USUARIO, ESPACIO_ID, IP))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.darDeBaja(USUARIO, ESPACIO_ID, IP))
                .isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(espacios, auditoria);
    }

    @Test
    @DisplayName("HU-21 R26: un arrendador sin correo verificado recibe 403 sin cambiar el espacio")
    void sinCorreoVerificadoRecibe403() {
        habilitado(false, true);

        assertThatThrownBy(() -> servicio.pausar(ARRENDADOR, ESPACIO_ID, IP))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.reanudar(ARRENDADOR, ESPACIO_ID, IP))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.darDeBaja(ARRENDADOR, ESPACIO_ID, IP))
                .isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(espacios, auditoria);
    }

    @Test
    @DisplayName("HU-21 R27: un espacio ajeno o inexistente da 404 sin auditar")
    void ajenoOInexistenteDa404() {
        habilitado(true, true);
        when(espacios.findParaActualizar(ESPACIO_ID, ARRENDADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.pausar(ARRENDADOR, ESPACIO_ID, IP))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.reanudar(ARRENDADOR, ESPACIO_ID, IP))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.darDeBaja(ARRENDADOR, ESPACIO_ID, IP))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(puerto, auditoria);
    }

    // ── Admin ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-21 R28: el admin pausa un PUBLICADO de cualquier arrendador y recibe EstadoEspacioDTO")
    void adminPausa() {
        var espacio = deCualquiera(EstadoEspacio.PUBLICADO);

        var dto = servicio.pausarComoAdmin(ADMIN, ESPACIO_ID, IP);

        assertThat(dto.id()).isEqualTo(ESPACIO_ID);
        assertThat(dto.estado()).isEqualTo("PAUSADO");
        assertThat(espacio.getEstado()).isEqualTo(EstadoEspacio.PAUSADO);
        verifyNoInteractions(cuentas);
    }

    @Test
    @DisplayName("HU-21 R6: el admin no puede pausar un espacio que no está PUBLICADO")
    void adminPausaFueraDePublicadoRechaza() {
        var espacio = deCualquiera(EstadoEspacio.BORRADOR);

        assertThatThrownBy(() -> servicio.pausarComoAdmin(ADMIN, ESPACIO_ID, IP))
                .hasMessage(EstadoEspacioService.MENSAJE_SOLO_PUBLICADO);
        sinCambio(espacio, EstadoEspacio.BORRADOR);
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-21 R29: el admin da de baja un PUBLICADO o PAUSADO sin reservas por terminar")
    void adminDaDeBaja() {
        reservas();
        for (var estado : new EstadoEspacio[] {EstadoEspacio.PUBLICADO, EstadoEspacio.PAUSADO}) {
            var espacio = deCualquiera(estado);

            var dto = servicio.darDeBajaComoAdmin(ADMIN, ESPACIO_ID, IP);

            assertThat(dto).isEqualTo(new EstadoEspacioDTO(ESPACIO_ID, "BAJA"));
            assertThat(espacio.getEstado()).isEqualTo(EstadoEspacio.BAJA);
        }
    }

    @Test
    @DisplayName("HU-21 R30: la baja del admin con reservas por terminar da 400 en reservas con su mensaje")
    void adminBajaConReservasRechaza() {
        var espacio = deCualquiera(EstadoEspacio.PUBLICADO);
        reservas(new BloqueReservado(HOY.plusDays(1), 10, true));

        var error = catchThrowable(
                () -> servicio.darDeBajaComoAdmin(ADMIN, ESPACIO_ID, IP));

        reglaCon(error, EstadoEspacioService.CAMPO_RESERVAS,
                EstadoEspacioService.MENSAJE_RESERVAS_POR_TERMINAR_ADMIN);
        sinCambio(espacio, EstadoEspacio.PUBLICADO);
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-21 R16: el admin no puede dar de baja un BAJA y no consulta reservas")
    void adminBajaDeEstadoNoAdmitidoRechaza() {
        deCualquiera(EstadoEspacio.BAJA);

        assertThatThrownBy(() -> servicio.darDeBajaComoAdmin(ADMIN, ESPACIO_ID, IP))
                .hasMessage(EstadoEspacioService.MENSAJE_BAJA_NO_PERMITIDA);
        verifyNoInteractions(puerto, auditoria);
    }

    @Test
    @DisplayName("HU-21 R31: un espacio inexistente en una ruta del admin da 404")
    void adminInexistenteDa404() {
        when(espacios.findParaReservar(ESPACIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.pausarComoAdmin(ADMIN, ESPACIO_ID, IP))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.darDeBajaComoAdmin(ADMIN, ESPACIO_ID, IP))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-21 R32: una sesión sin rol ADMIN recibe 403 en las rutas del admin sin leer el repositorio")
    void adminSinRolRecibe403() {
        for (var sesion : new UsuarioAutenticado[] {ARRENDADOR, USUARIO, null}) {
            assertThatThrownBy(() -> servicio.pausarComoAdmin(sesion, ESPACIO_ID, IP))
                    .isInstanceOf(AccesoDenegadoException.class);
            assertThatThrownBy(() -> servicio.darDeBajaComoAdmin(sesion, ESPACIO_ID, IP))
                    .isInstanceOf(AccesoDenegadoException.class);
        }
        verifyNoInteractions(espacios, puerto, auditoria);
    }

    // ── Ocultar (HU-27) ──────────────────────────────────────────────────

    @Test
    @DisplayName("HU-27 R32, R52: ocultar un PUBLICADO o PAUSADO lo deja OCULTO y audita OCULTACION_ESPACIO")
    void adminOculta() {
        for (var estado : new EstadoEspacio[] {EstadoEspacio.PUBLICADO, EstadoEspacio.PAUSADO}) {
            var espacio = deCualquiera(estado);

            var ocultado = servicio.ocultarComoAdmin(ADMIN, ESPACIO_ID, IP);

            assertThat(ocultado).isEqualTo(new EspacioOcultado(ESPACIO_ID, ARRENDADOR_ID, "Cancha Los Olivos",
                    estado.name()));
            assertThat(espacio.getEstado()).isEqualTo(EstadoEspacio.OCULTO);
            assertThat(espacio.getActualizadoEn()).isEqualTo(AHORA);
            var evento = ArgumentCaptor.forClass(EventoAuditoria.class);
            verify(auditoria, atLeastOnce()).registrar(evento.capture());
            assertThat(evento.getValue()).isEqualTo(new EventoAuditoria(ADMIN_ID, "OCULTACION_ESPACIO", "espacio",
                    ESPACIO_ID, IP, "estado=" + estado + "->OCULTO;actor=ADMIN;arrendadorId=" + ARRENDADOR_ID));
        }
    }

    @Test
    @DisplayName("HU-27 R39, R54: ocultar un BORRADOR, BAJA u OCULTO da 400 no ocultable sin cambiarlo ni auditar")
    void adminOcultarNoOcultableRechaza() {
        for (var estado : new EstadoEspacio[] {EstadoEspacio.BORRADOR, EstadoEspacio.BAJA, EstadoEspacio.OCULTO}) {
            var espacio = deCualquiera(estado);

            assertThatThrownBy(() -> servicio.ocultarComoAdmin(ADMIN, ESPACIO_ID, IP))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(EstadoEspacioService.MENSAJE_NO_OCULTABLE);
            sinCambio(espacio, estado);
        }
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-27 R62: ocultar con una sesión que no es ADMIN da 403 sin leer ni bloquear el espacio")
    void ocultarSinRolAdminRecibe403() {
        for (var sesion : new UsuarioAutenticado[] {ARRENDADOR, USUARIO, null}) {
            assertThatThrownBy(() -> servicio.ocultarComoAdmin(sesion, ESPACIO_ID, IP))
                    .isInstanceOf(AccesoDenegadoException.class);
        }
        verifyNoInteractions(espacios, puerto, auditoria);
    }

    @Test
    @DisplayName("HU-27 R46: ocultar no consulta las reservas del espacio")
    void ocultarNoConsultaReservas() {
        deCualquiera(EstadoEspacio.PUBLICADO);

        servicio.ocultarComoAdmin(ADMIN, ESPACIO_ID, IP);

        verifyNoInteractions(puerto);
        verify(proveedor, never()).getIfAvailable();
    }

    // ── Auditoría ────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-21 R34: pausar audita PAUSA_ESPACIO con entidad, id, usuario, IP y detalle")
    void auditaPausa() {
        habilitado(true, true);
        propio(EstadoEspacio.PUBLICADO);

        servicio.pausar(ARRENDADOR, ESPACIO_ID, IP);

        assertThat(auditado()).isEqualTo(new EventoAuditoria(ARRENDADOR_ID, "PAUSA_ESPACIO", "espacio", ESPACIO_ID, IP,
                "estado=PUBLICADO->PAUSADO"));
    }

    @Test
    @DisplayName("HU-21 R35: reanudar audita REANUDACION_ESPACIO con detalle PAUSADO->PUBLICADO")
    void auditaReanudacion() {
        habilitado(true, true);
        propio(EstadoEspacio.PAUSADO);
        when(fotos.countByEspacioId(ESPACIO_ID)).thenReturn(2L);

        servicio.reanudar(ARRENDADOR, ESPACIO_ID, IP);

        assertThat(auditado()).isEqualTo(new EventoAuditoria(ARRENDADOR_ID, "REANUDACION_ESPACIO", "espacio",
                ESPACIO_ID, IP, "estado=PAUSADO->PUBLICADO"));
    }

    @Test
    @DisplayName("HU-21 R36: dar de baja audita BAJA_ESPACIO con el estado anterior")
    void auditaBaja() {
        habilitado(true, true);
        propio(EstadoEspacio.PAUSADO);
        reservas();

        servicio.darDeBaja(ARRENDADOR, ESPACIO_ID, IP);

        assertThat(auditado()).isEqualTo(new EventoAuditoria(ARRENDADOR_ID, "BAJA_ESPACIO", "espacio", ESPACIO_ID, IP,
                "estado=PAUSADO->BAJA"));
    }

    @Test
    @DisplayName("HU-21 R37: si actúa el admin, el detalle agrega actor=ADMIN y el arrendador dueño")
    void auditaActorAdmin() {
        deCualquiera(EstadoEspacio.PUBLICADO);

        servicio.pausarComoAdmin(ADMIN, ESPACIO_ID, IP);

        assertThat(auditado()).isEqualTo(new EventoAuditoria(ADMIN_ID, "PAUSA_ESPACIO", "espacio", ESPACIO_ID, IP,
                "estado=PUBLICADO->PAUSADO;actor=ADMIN;arrendadorId=" + ARRENDADOR_ID));
    }

    @Test
    @DisplayName("HU-21 R37: la baja del admin audita BAJA_ESPACIO con actor=ADMIN y el arrendador dueño")
    void auditaBajaActorAdmin() {
        deCualquiera(EstadoEspacio.PAUSADO);
        reservas();

        servicio.darDeBajaComoAdmin(ADMIN, ESPACIO_ID, IP);

        assertThat(auditado()).isEqualTo(new EventoAuditoria(ADMIN_ID, "BAJA_ESPACIO", "espacio", ESPACIO_ID, IP,
                "estado=PAUSADO->BAJA;actor=ADMIN;arrendadorId=" + ARRENDADOR_ID));
    }

    @Test
    @DisplayName("HU-21 R38: un rechazo (403, 404 o 400) no audita ni cambia estado ni actualizadoEn")
    void rechazosNoAuditan() {
        habilitado(true, false);
        var espacio = propio(EstadoEspacio.PAUSADO);

        assertThatThrownBy(() -> servicio.reanudar(ARRENDADOR, ESPACIO_ID, IP))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> servicio.pausar(ARRENDADOR, ESPACIO_ID, IP))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> servicio.pausarComoAdmin(USUARIO, ESPACIO_ID, IP))
                .isInstanceOf(AccesoDenegadoException.class);

        sinCambio(espacio, EstadoEspacio.PAUSADO);
        verify(auditoria, never()).registrar(any());
    }

    @Test
    @DisplayName("HU-21 R39: pausa, reanudación y baja fijan actualizadoEn en el instante del Clock")
    void fijaActualizadoEn() {
        habilitado(true, true);
        when(fotos.countByEspacioId(ESPACIO_ID)).thenReturn(1L);
        reservas();

        var pausado = propio(EstadoEspacio.PUBLICADO);
        servicio.pausar(ARRENDADOR, ESPACIO_ID, IP);
        assertThat(pausado.getActualizadoEn()).isEqualTo(AHORA);

        var reanudado = propio(EstadoEspacio.PAUSADO);
        servicio.reanudar(ARRENDADOR, ESPACIO_ID, IP);
        assertThat(reanudado.getActualizadoEn()).isEqualTo(AHORA);

        var dadoDeBaja = propio(EstadoEspacio.PUBLICADO);
        var dto = servicio.darDeBaja(ARRENDADOR, ESPACIO_ID, IP);
        assertThat(dadoDeBaja.getActualizadoEn()).isEqualTo(AHORA);
        assertThat(dto.actualizadoEn()).isEqualTo(AHORA);
    }
}
