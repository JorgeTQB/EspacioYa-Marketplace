package pe.edu.ulima.espacioya.administracion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.administracion.domain.EstadoReporte;
import pe.edu.ulima.espacioya.administracion.domain.MotivoReporte;
import pe.edu.ulima.espacioya.administracion.domain.Reporte;
import pe.edu.ulima.espacioya.administracion.domain.TipoObjetivoReporte;
import pe.edu.ulima.espacioya.administracion.dto.CrearReporteDTO;
import pe.edu.ulima.espacioya.administracion.dto.ReporteCreadoDTO;
import pe.edu.ulima.espacioya.administracion.repository.ReporteRepository;
import pe.edu.ulima.espacioya.busqueda.service.ModeracionResenas;
import pe.edu.ulima.espacioya.busqueda.service.ResenaReportable;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.DemasiadosIntentosException;
import pe.edu.ulima.espacioya.comun.error.ErrorDTO;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.espacios.service.EspacioReportable;
import pe.edu.ulima.espacioya.espacios.service.ModeracionEspacios;

@ExtendWith(MockitoExtension.class)
class ReporteServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-04T15:00:00Z");
    private static final Instant INICIO_VENTANA = Instant.parse("2026-11-03T15:00:00Z");
    private static final long USUARIO_ID = 7L;
    private static final long ARRENDADOR_ID = 3L;
    private static final long ESPACIO_ID = 40L;
    private static final long RESENA_ID = 60L;
    private static final long REPORTE_ID = 500L;
    private static final String IP = "10.27.0.1";
    private static final UsuarioAutenticado USUARIO = new UsuarioAutenticado(USUARIO_ID, 1L, "USUARIO");
    private static final UsuarioAutenticado ARRENDADOR = new UsuarioAutenticado(ARRENDADOR_ID, 2L, "ARRENDADOR");
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(99L, 3L, "ADMIN");

    @Mock
    private ReporteRepository reportes;
    @Mock
    private ModeracionEspacios espacios;
    @Mock
    private ModeracionResenas resenas;
    @Mock
    private CuentaService cuentas;
    @Mock
    private AuditoriaService auditoria;
    @Mock
    private TransactionTemplate transacciones;

    private ReporteService servicio;

    @BeforeEach
    void preparar() {
        servicio = new ReporteService(reportes, espacios, resenas, cuentas, auditoria, transacciones,
                Clock.fixed(AHORA, ZoneOffset.UTC));
        lenient().when(transacciones.execute(any())).thenAnswer(invocacion -> {
            TransactionCallback<?> accion = invocacion.getArgument(0);
            return accion.doInTransaction(null);
        });
        lenient().when(reportes.saveAndFlush(any())).thenAnswer(invocacion -> {
            Reporte reporte = invocacion.getArgument(0);
            ReflectionTestUtils.setField(reporte, "id", REPORTE_ID);
            return reporte;
        });
    }

    private static CrearReporteDTO datos(String tipo, long objetivo, String motivo) {
        return new CrearReporteDTO(tipo, objetivo, motivo);
    }

    private void previos(long cantidad) {
        when(reportes.countByReportanteIdAndCreadoEnAfter(USUARIO_ID, INICIO_VENTANA)).thenReturn(cantidad);
    }

    private void espacioDe(long arrendador) {
        when(espacios.reportable(ESPACIO_ID)).thenReturn(Optional.of(new EspacioReportable(ESPACIO_ID, arrendador)));
    }

    private void resenaDe(long autor) {
        when(resenas.reportable(RESENA_ID)).thenReturn(Optional.of(new ResenaReportable(RESENA_ID, autor,
                ESPACIO_ID)));
    }

    private Reporte guardado() {
        var captor = ArgumentCaptor.forClass(Reporte.class);
        verify(reportes).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private void sinAlta() {
        verify(reportes, never()).saveAndFlush(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-27 R1, R3: reportar un espacio ajeno visible crea el reporte POR_REVISAR y responde sus datos")
    void reportaUnEspacio() {
        previos(0);
        espacioDe(ARRENDADOR_ID);

        var creado = servicio.reportar(USUARIO, datos("ESPACIO", ESPACIO_ID, "FRAUDE"), IP);

        var reporte = guardado();
        assertThat(reporte.getTipoObjetivo()).isEqualTo(TipoObjetivoReporte.ESPACIO);
        assertThat(reporte.getEspacioId()).isEqualTo(ESPACIO_ID);
        assertThat(reporte.getResenaId()).isNull();
        assertThat(reporte.getMotivo()).isEqualTo(MotivoReporte.FRAUDE);
        assertThat(reporte.getReportanteId()).isEqualTo(USUARIO_ID);
        assertThat(reporte.getEstado()).isEqualTo(EstadoReporte.POR_REVISAR);
        assertThat(reporte.getCreadoEn()).isEqualTo(AHORA);
        assertThat(creado).isEqualTo(new ReporteCreadoDTO(REPORTE_ID, "ESPACIO", ESPACIO_ID, "FRAUDE",
                "POR_REVISAR", AHORA));
    }

    @Test
    @DisplayName("HU-27 R2: un arrendador reporta una reseña ajena sobre su propio espacio")
    void reportaUnaResena() {
        when(reportes.countByReportanteIdAndCreadoEnAfter(ARRENDADOR_ID, INICIO_VENTANA)).thenReturn(0L);
        resenaDe(USUARIO_ID);

        var creado = servicio.reportar(ARRENDADOR, datos("RESENA", RESENA_ID, "CONTENIDO_INAPROPIADO"), IP);

        var reporte = guardado();
        assertThat(reporte.getTipoObjetivo()).isEqualTo(TipoObjetivoReporte.RESENA);
        assertThat(reporte.getResenaId()).isEqualTo(RESENA_ID);
        assertThat(reporte.getEspacioId()).isNull();
        assertThat(reporte.getReportanteId()).isEqualTo(ARRENDADOR_ID);
        assertThat(creado.objetivoId()).isEqualTo(RESENA_ID);
        assertThat(creado.tipoObjetivo()).isEqualTo("RESENA");
    }

    @Test
    @DisplayName("HU-27 R4: el reportante sale de la sesión, no del cuerpo")
    void reportanteDeLaSesion() {
        previos(0);
        espacioDe(ARRENDADOR_ID);

        servicio.reportar(USUARIO, datos("ESPACIO", ESPACIO_ID, "SPAM"), IP);

        assertThat(guardado().getReportanteId()).isEqualTo(USUARIO_ID);
        verify(cuentas).bloquearCuenta(USUARIO_ID);
    }

    @Test
    @DisplayName("HU-27 R5: un ADMIN o una sesión nula reciben 403 sin bloquear la cuenta ni leer el contenido")
    void adminNoReporta() {
        for (var sesion : new UsuarioAutenticado[] {ADMIN, null}) {
            assertThatThrownBy(() -> servicio.reportar(sesion, datos("ESPACIO", ESPACIO_ID, "SPAM"), IP))
                    .isInstanceOf(AccesoDenegadoException.class);
        }
        verifyNoInteractions(cuentas, reportes, espacios, resenas, auditoria, transacciones);
    }

    @Test
    @DisplayName("HU-27 R7: un espacio o una reseña no reportables dan 404 sin crear el reporte")
    void noReportableDa404() {
        previos(0);
        when(espacios.reportable(ESPACIO_ID)).thenReturn(Optional.empty());
        when(resenas.reportable(RESENA_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.reportar(USUARIO, datos("ESPACIO", ESPACIO_ID, "SPAM"), IP))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.reportar(USUARIO, datos("RESENA", RESENA_ID, "SPAM"), IP))
                .isInstanceOf(RecursoNoEncontradoException.class);
        sinAlta();
    }

    @Test
    @DisplayName("HU-27 R8: reportar el espacio propio da 400 contenido propio")
    void espacioPropio() {
        when(reportes.countByReportanteIdAndCreadoEnAfter(ARRENDADOR_ID, INICIO_VENTANA)).thenReturn(0L);
        espacioDe(ARRENDADOR_ID);

        assertThatThrownBy(() -> servicio.reportar(ARRENDADOR, datos("ESPACIO", ESPACIO_ID, "SPAM"), IP))
                .isInstanceOf(ReglaNegocioException.class).hasMessage(ReporteService.MENSAJE_CONTENIDO_PROPIO);
        sinAlta();
    }

    @Test
    @DisplayName("HU-27 R9: reportar la reseña propia da 400 contenido propio")
    void resenaPropia() {
        previos(0);
        resenaDe(USUARIO_ID);

        assertThatThrownBy(() -> servicio.reportar(USUARIO, datos("RESENA", RESENA_ID, "SPAM"), IP))
                .isInstanceOf(ReglaNegocioException.class).hasMessage(ReporteService.MENSAJE_CONTENIDO_PROPIO);
        sinAlta();
    }

    @Test
    @DisplayName("HU-27 R10: si ya reportó el mismo contenido da 400 ya reportado sin crear otro")
    void yaReportado() {
        previos(1);
        espacioDe(ARRENDADOR_ID);
        resenaDe(99L);
        when(reportes.existsByReportanteIdAndEspacioId(USUARIO_ID, ESPACIO_ID)).thenReturn(true);
        when(reportes.existsByReportanteIdAndResenaId(USUARIO_ID, RESENA_ID)).thenReturn(true);

        assertThatThrownBy(() -> servicio.reportar(USUARIO, datos("ESPACIO", ESPACIO_ID, "SPAM"), IP))
                .isInstanceOf(ReglaNegocioException.class).hasMessage(ReporteService.MENSAJE_YA_REPORTADO);
        assertThatThrownBy(() -> servicio.reportar(USUARIO, datos("RESENA", RESENA_ID, "SPAM"), IP))
                .isInstanceOf(ReglaNegocioException.class).hasMessage(ReporteService.MENSAJE_YA_REPORTADO);
        sinAlta();
    }

    @Test
    @DisplayName("HU-27 R12: la violación de uq_reporte_espacio o uq_reporte_resena es ya reportado; otra se relanza")
    void carrera() {
        doThrow(new DataIntegrityViolationException("x",
                new SQLException("duplicate key value violates unique constraint \"uq_reporte_espacio\"")))
                .when(transacciones).execute(any());
        assertThatThrownBy(() -> servicio.reportar(USUARIO, datos("ESPACIO", ESPACIO_ID, "SPAM"), IP))
                .isInstanceOf(ReglaNegocioException.class).hasMessage(ReporteService.MENSAJE_YA_REPORTADO);

        doThrow(new DataIntegrityViolationException("x",
                new SQLException("duplicate key value violates unique constraint \"uq_reporte_resena\"")))
                .when(transacciones).execute(any());
        assertThatThrownBy(() -> servicio.reportar(USUARIO, datos("RESENA", RESENA_ID, "SPAM"), IP))
                .isInstanceOf(ReglaNegocioException.class).hasMessage(ReporteService.MENSAJE_YA_REPORTADO);

        doThrow(new DataIntegrityViolationException("x",
                new SQLException("violates foreign key constraint \"fk_reporte_espacio\"")))
                .when(transacciones).execute(any());
        assertThatThrownBy(() -> servicio.reportar(USUARIO, datos("ESPACIO", ESPACIO_ID, "SPAM"), IP))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("HU-27 R16: con 10 reportes en la ventana responde 429 sin crear el reporte")
    void cupoAgotado() {
        previos(10);

        assertThatThrownBy(() -> servicio.reportar(USUARIO, datos("ESPACIO", ESPACIO_ID, "SPAM"), IP))
                .isInstanceOf(DemasiadosIntentosException.class)
                .hasMessage(ErrorDTO.MENSAJE_DEMASIADOS_INTENTOS);
        sinAlta();
    }

    @Test
    @DisplayName("HU-27 R17: con 9 previos en la ventana (desde ahora − 24 h, exclusivo) el décimo se acepta")
    void decimoSeAcepta() {
        previos(9);
        espacioDe(ARRENDADOR_ID);

        servicio.reportar(USUARIO, datos("ESPACIO", ESPACIO_ID, "SPAM"), IP);

        verify(reportes).countByReportanteIdAndCreadoEnAfter(USUARIO_ID, INICIO_VENTANA);
        assertThat(guardado().getEstado()).isEqualTo(EstadoReporte.POR_REVISAR);
    }

    @Test
    @DisplayName("HU-27 R18: bloquea la cuenta del reportante antes de contar el cupo, en la misma transacción")
    void bloqueaAntesDeContar() {
        previos(0);
        espacioDe(ARRENDADOR_ID);

        servicio.reportar(USUARIO, datos("ESPACIO", ESPACIO_ID, "SPAM"), IP);

        var orden = inOrder(transacciones, cuentas, reportes);
        orden.verify(transacciones).execute(any());
        orden.verify(cuentas).bloquearCuenta(USUARIO_ID);
        orden.verify(reportes).countByReportanteIdAndCreadoEnAfter(anyLong(), any());
        orden.verify(reportes).saveAndFlush(any());
    }

    @Test
    @DisplayName("HU-27 R19: con el cupo agotado da 429 antes de mirar si el contenido existe")
    void cupoAntesQueElContenido() {
        previos(12);

        assertThatThrownBy(() -> servicio.reportar(USUARIO, datos("ESPACIO", 123456L, "SPAM"), IP))
                .isInstanceOf(DemasiadosIntentosException.class);
        assertThatThrownBy(() -> servicio.reportar(USUARIO, datos("RESENA", 123456L, "SPAM"), IP))
                .isInstanceOf(DemasiadosIntentosException.class);
        verifyNoInteractions(espacios, resenas);
        verify(reportes, never()).existsByReportanteIdAndEspacioId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("HU-27 R50: audita REPORTE_CREADO con el reportante, la IP y tipo, objetivo y motivo")
    void auditaLaCreacion() {
        previos(0);
        resenaDe(99L);

        servicio.reportar(USUARIO, datos("RESENA", RESENA_ID, "SPAM"), IP);

        verify(auditoria).registrar(new EventoAuditoria(USUARIO_ID, "REPORTE_CREADO", "reporte", REPORTE_ID, IP,
                "tipo=RESENA;objetivoId=" + RESENA_ID + ";motivo=SPAM"));
    }

    @Test
    @DisplayName("HU-27 R54: un reporte rechazado (403, 404, 400 o 429) no audita nada")
    void rechazoNoAudita() {
        previos(0);
        when(espacios.reportable(ESPACIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.reportar(ADMIN, datos("ESPACIO", ESPACIO_ID, "SPAM"), IP))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.reportar(USUARIO, datos("ESPACIO", ESPACIO_ID, "SPAM"), IP))
                .isInstanceOf(RecursoNoEncontradoException.class);
        previos(10);
        assertThatThrownBy(() -> servicio.reportar(USUARIO, datos("ESPACIO", ESPACIO_ID, "SPAM"), IP))
                .isInstanceOf(DemasiadosIntentosException.class);
        verify(auditoria, never()).registrar(any(EventoAuditoria.class));
    }
}
