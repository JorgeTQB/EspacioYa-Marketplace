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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.administracion.domain.DecisionReporte;
import pe.edu.ulima.espacioya.administracion.domain.EstadoReporte;
import pe.edu.ulima.espacioya.administracion.domain.MotivoReporte;
import pe.edu.ulima.espacioya.administracion.domain.Reporte;
import pe.edu.ulima.espacioya.administracion.dto.DecisionReporteDTO;
import pe.edu.ulima.espacioya.administracion.dto.DetalleReporteAdminDTO;
import pe.edu.ulima.espacioya.administracion.dto.EspacioReportadoDTO;
import pe.edu.ulima.espacioya.administracion.dto.ResenaReportadaDTO;
import pe.edu.ulima.espacioya.administracion.dto.ReporteResumenDTO;
import pe.edu.ulima.espacioya.administracion.repository.ReporteRepository;
import pe.edu.ulima.espacioya.administracion.service.correo.DatosEspacioOcultado;
import pe.edu.ulima.espacioya.busqueda.service.ModeracionResenas;
import pe.edu.ulima.espacioya.busqueda.service.ResenaModerada;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.error.ValidacionException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.espacios.service.EspacioModerado;
import pe.edu.ulima.espacioya.espacios.service.EspacioOcultado;
import pe.edu.ulima.espacioya.espacios.service.ModeracionEspacios;
import pe.edu.ulima.espacioya.reservas.service.CancelacionPorModeracion;
import pe.edu.ulima.espacioya.reservas.service.ReservasCanceladasPorModeracion;

@ExtendWith(MockitoExtension.class)
class ReporteAdminServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-04T15:00:00Z");
    private static final Instant CREADO = AHORA.minusSeconds(7200);
    private static final long ADMIN_ID = 99L;
    private static final long ARRENDADOR_ID = 3L;
    private static final long ESPACIO_ID = 40L;
    private static final long RESENA_ID = 60L;
    private static final long REPORTE_ID = 500L;
    private static final long HERMANO_ID = 501L;
    private static final String IP = "127.0.0.1";
    private static final String MOTIVO = "Las fotos no corresponden al local.";
    private static final String NOMBRE = "Cancha Olivos";
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(ADMIN_ID, 5L, "ADMIN");
    private static final EspacioModerado MODERADO = new EspacioModerado(ESPACIO_ID, ARRENDADOR_ID, NOMBRE, "CANCHA",
            "LOS_OLIVOS", "Av. Universitaria 1234", 22, new BigDecimal("80.50"), "Cancha de grass.", "PUBLICADO");
    private static final ResenaModerada RESENA = new ResenaModerada(RESENA_ID, ESPACIO_ID, 2, "Pésimo",
            LocalDate.of(2026, 11, 3), "VISIBLE");

    @Mock
    private ReporteRepository reportes;
    @Mock
    private ModeracionEspacios espacios;
    @Mock
    private ModeracionResenas resenas;
    @Mock
    private CancelacionPorModeracion cancelacion;
    @Mock
    private CuentaService cuentas;
    @Mock
    private CatalogoEspaciosService catalogo;
    @Mock
    private AvisosModeracion avisos;
    @Mock
    private AuditoriaService auditoria;

    private ReporteAdminService servicio;

    @BeforeEach
    void preparar() {
        var vista = new VistaReportes(reportes, espacios, resenas, catalogo, cuentas);
        servicio = new ReporteAdminService(reportes, espacios, resenas, cancelacion, cuentas, vista, avisos, auditoria,
                Clock.fixed(AHORA, ZoneOffset.UTC));
        lenient().when(espacios.describir(List.of(ESPACIO_ID))).thenReturn(Map.of(ESPACIO_ID, MODERADO));
        lenient().when(resenas.describir(List.of(RESENA_ID))).thenReturn(Map.of(RESENA_ID, RESENA));
        lenient().when(cuentas.nombres(List.of(ARRENDADOR_ID))).thenReturn(Map.of(ARRENDADOR_ID, "Ana Propietaria"));
        lenient().when(catalogo.nombres(any())).thenReturn(Map.of(ESPACIO_ID, NOMBRE));
        lenient().when(cuentas.correoDe(ARRENDADOR_ID)).thenReturn("ana@correo.pe");
    }

    private static Reporte deEspacio(long id) {
        var reporte = Reporte.deEspacio(7L, ESPACIO_ID, MotivoReporte.FRAUDE, CREADO);
        ReflectionTestUtils.setField(reporte, "id", id);
        return reporte;
    }

    private static Reporte deResena(long id) {
        var reporte = Reporte.deResena(7L, RESENA_ID, MotivoReporte.SPAM, CREADO);
        ReflectionTestUtils.setField(reporte, "id", id);
        return reporte;
    }

    private static DecisionReporteDTO decision(String decision) {
        return new DecisionReporteDTO(decision, "  <b>" + MOTIVO + "</b>  ");
    }

    /** El reporte decidido y un hermano del mismo espacio, ambos POR_REVISAR. */
    private List<Reporte> hermanosDeEspacio() {
        var reporte = deEspacio(REPORTE_ID);
        var hermanos = List.of(reporte, deEspacio(HERMANO_ID));
        when(reportes.findById(REPORTE_ID)).thenReturn(Optional.of(reporte));
        when(reportes.bloquearPorRevisarDeEspacio(ESPACIO_ID)).thenReturn(hermanos);
        return hermanos;
    }

    private List<Reporte> hermanosDeResena() {
        var reporte = deResena(REPORTE_ID);
        var hermanos = List.of(reporte, deResena(HERMANO_ID));
        when(reportes.findById(REPORTE_ID)).thenReturn(Optional.of(reporte));
        when(reportes.bloquearPorRevisarDeResena(RESENA_ID)).thenReturn(hermanos);
        return hermanos;
    }

    private void espacioOcultable() {
        when(espacios.ocultar(ADMIN, ESPACIO_ID, IP)).thenReturn(new EspacioOcultado(ESPACIO_ID, ARRENDADOR_ID, NOMBRE,
                "PUBLICADO"));
    }

    private static void sigueSinResolver(List<Reporte> hermanos) {
        assertThat(hermanos).allSatisfy(hermano -> {
            assertThat(hermano.getEstado()).isEqualTo(EstadoReporte.POR_REVISAR);
            assertThat(hermano.getAdminId()).isNull();
        });
    }

    // ── Cola y detalle (CA2) ─────────────────────────────────────────────

    @Test
    @DisplayName("HU-27 R20: la cola por revisar pide POR_REVISAR de a 10, más antiguos primero, con el nombre del "
            + "espacio")
    void listaPendientes() {
        var deEspacio = deEspacio(REPORTE_ID);
        var deResena = deResena(HERMANO_ID);
        var solicitud = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "creadoEn", "id"));
        when(reportes.findByEstado(EstadoReporte.POR_REVISAR, solicitud))
                .thenReturn(new SliceImpl<>(List.of(deEspacio, deResena), solicitud, true));

        var pagina = servicio.listar(ADMIN, "pendientes", null);

        assertThat(pagina.pagina()).isEqualTo(1);
        assertThat(pagina.hayMas()).isTrue();
        assertThat(pagina.reportes()).containsExactly(
                new ReporteResumenDTO(REPORTE_ID, "ESPACIO", ESPACIO_ID, NOMBRE, "FRAUDE", "POR_REVISAR", CREADO,
                        null),
                new ReporteResumenDTO(HERMANO_ID, "RESENA", RESENA_ID, NOMBRE, "SPAM", "POR_REVISAR", CREADO, null));
    }

    @Test
    @DisplayName("HU-27 R21: resueltos pide MANTENIDO u OCULTADO de a 10 por resuelto_en e id descendentes")
    void listaResueltos() {
        var resuelto = deEspacio(REPORTE_ID);
        resuelto.resolver(DecisionReporte.MANTENER, ADMIN_ID, "Ok", AHORA);
        var solicitud = PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "resueltoEn", "id"));
        when(reportes.findByEstadoIn(List.of(EstadoReporte.MANTENIDO, EstadoReporte.OCULTADO), solicitud))
                .thenReturn(new SliceImpl<>(List.of(resuelto), solicitud, false));

        var pagina = servicio.listar(ADMIN, "resueltos", "2");

        assertThat(pagina.pagina()).isEqualTo(2);
        assertThat(pagina.hayMas()).isFalse();
        assertThat(pagina.reportes()).singleElement().satisfies(fila -> {
            assertThat(fila.estado()).isEqualTo("MANTENIDO");
            assertThat(fila.resueltoEn()).isEqualTo(AHORA);
        });
    }

    @Test
    @DisplayName("HU-27 R22: un grupo ausente o fuera de la lista da 400 en grupo sin leer reportes")
    void grupoInvalido() {
        for (var grupo : new String[] {null, "abiertos", "PENDIENTES"}) {
            assertThatThrownBy(() -> servicio.listar(ADMIN, grupo, null))
                    .isInstanceOfSatisfying(ValidacionException.class, error -> assertThat(error.getErrores())
                            .extracting(e -> e.campo()).containsExactly("grupo"));
        }
        verifyNoInteractions(reportes);
    }

    @Test
    @DisplayName("HU-27 R23: una página que no es un entero de 1 a Integer.MAX_VALUE da 400 en pagina; la última no "
            + "consulta")
    void paginaInvalida() {
        for (var pagina : new String[] {"0", "-1", "abc", "1.5", "2147483648"}) {
            assertThatThrownBy(() -> servicio.listar(ADMIN, "pendientes", pagina))
                    .isInstanceOfSatisfying(ValidacionException.class, error -> assertThat(error.getErrores())
                            .extracting(e -> e.campo()).containsExactly("pagina"));
        }
        assertThat(servicio.listar(ADMIN, "resueltos", "2147483647").reportes()).isEmpty();
        verifyNoInteractions(reportes);
    }

    @Test
    @DisplayName("HU-27 R24: el detalle de un espacio trae el reporte, los pendientes y el espacio sin correo ni "
            + "reportante")
    void detalleDeEspacio() {
        when(reportes.findById(REPORTE_ID)).thenReturn(Optional.of(deEspacio(REPORTE_ID)));
        when(reportes.countByEspacioIdAndEstado(ESPACIO_ID, EstadoReporte.POR_REVISAR)).thenReturn(3L);

        var detalle = servicio.detalle(ADMIN, REPORTE_ID);

        assertThat(detalle).isEqualTo(new DetalleReporteAdminDTO(REPORTE_ID, "ESPACIO", ESPACIO_ID, "FRAUDE",
                "POR_REVISAR", CREADO, null, null, null, 3, new EspacioReportadoDTO(NOMBRE, "CANCHA", "LOS_OLIVOS",
                "Av. Universitaria 1234", 22, new BigDecimal("80.50"), "Cancha de grass.", "PUBLICADO",
                "Ana Propietaria"), null, null));
        verify(cuentas, never()).correoDe(anyLong());
        assertThat(DetalleReporteAdminDTO.class.getRecordComponents()).extracting(c -> c.getName())
                .doesNotContain("reportanteId", "reportante", "adminId");
        assertThat(EspacioReportadoDTO.class.getRecordComponents()).extracting(c -> c.getName())
                .doesNotContain("correo", "telefono", "arrendadorCorreo");
    }

    @Test
    @DisplayName("HU-27 R25: el detalle de una reseña trae la reseña y su espacio, sin su autor")
    void detalleDeResena() {
        when(reportes.findById(REPORTE_ID)).thenReturn(Optional.of(deResena(REPORTE_ID)));
        when(reportes.countByResenaIdAndEstado(RESENA_ID, EstadoReporte.POR_REVISAR)).thenReturn(1L);

        var detalle = servicio.detalle(ADMIN, REPORTE_ID);

        assertThat(detalle.espacio()).isNull();
        assertThat(detalle.reportesPendientes()).isEqualTo(1);
        assertThat(detalle.resena()).isEqualTo(new ResenaReportadaDTO(2, "Pésimo", LocalDate.of(2026, 11, 3),
                "VISIBLE", ESPACIO_ID, NOMBRE));
        assertThat(detalle.reservasCanceladas()).isNull();
        assertThat(ResenaReportadaDTO.class.getRecordComponents()).extracting(c -> c.getName())
                .doesNotContain("usuarioId", "autorId", "autor");
    }

    @Test
    @DisplayName("HU-27 R26: una sesión que no es ADMIN recibe 403 en las tres operaciones sin leer reportes")
    void sinRolAdmin() {
        for (var sesion : new UsuarioAutenticado[] {new UsuarioAutenticado(7L, 1L, "USUARIO"),
                new UsuarioAutenticado(3L, 2L, "ARRENDADOR"), null}) {
            assertThatThrownBy(() -> servicio.listar(sesion, "pendientes", null))
                    .isInstanceOf(AccesoDenegadoException.class);
            assertThatThrownBy(() -> servicio.detalle(sesion, REPORTE_ID)).isInstanceOf(AccesoDenegadoException.class);
            assertThatThrownBy(() -> servicio.decidir(sesion, REPORTE_ID, decision("OCULTAR"), IP))
                    .isInstanceOf(AccesoDenegadoException.class);
        }
        verifyNoInteractions(reportes, espacios, resenas, cancelacion, auditoria, avisos);
    }

    @Test
    @DisplayName("HU-27 R27: un reporte inexistente da 404 en el detalle y en la decisión")
    void inexistente() {
        when(reportes.findById(REPORTE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.detalle(ADMIN, REPORTE_ID)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.decidir(ADMIN, REPORTE_ID, decision("MANTENER"), IP))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(auditoria, avisos);
    }

    // ── Decidir (CA2, CA3) ───────────────────────────────────────────────

    @Test
    @DisplayName("HU-27 R31: mantener deja el reporte y sus hermanos MANTENIDO, sin tocar el contenido")
    void mantiene() {
        var hermanos = hermanosDeEspacio();

        var detalle = servicio.decidir(ADMIN, REPORTE_ID, decision("MANTENER"), IP);

        assertThat(hermanos).allSatisfy(hermano -> {
            assertThat(hermano.getEstado()).isEqualTo(EstadoReporte.MANTENIDO);
            assertThat(hermano.getAdminId()).isEqualTo(ADMIN_ID);
            assertThat(hermano.getMotivoDecision()).isEqualTo(MOTIVO);
            assertThat(hermano.getResueltoEn()).isEqualTo(AHORA);
        });
        assertThat(detalle.decision()).isEqualTo("MANTENER");
        verify(reportes).saveAllAndFlush(hermanos);
        verifyNoInteractions(cancelacion);
        verify(espacios, never()).ocultar(any(), anyLong(), any());
        verify(resenas, never()).ocultar(any(), anyLong(), any());
    }

    @Test
    @DisplayName("HU-27 R32, R47: ocultar un espacio llama a ModeracionEspacios.ocultar y avisa al arrendador")
    void ocultaEspacio() {
        hermanosDeEspacio();
        espacioOcultable();

        servicio.decidir(ADMIN, REPORTE_ID, decision("OCULTAR"), IP);

        verify(espacios).ocultar(ADMIN, ESPACIO_ID, IP);
        verify(avisos).espacioOcultado("ana@correo.pe", new DatosEspacioOcultado(NOMBRE, MOTIVO, false, 0));
    }

    @Test
    @DisplayName("HU-27 R33: ocultar deja los hermanos OCULTADO sin por_fraude; por fraude, con por_fraude")
    void ocultarResuelveHermanos() {
        var hermanos = hermanosDeEspacio();
        espacioOcultable();

        servicio.decidir(ADMIN, REPORTE_ID, decision("OCULTAR"), IP);

        assertThat(hermanos).allSatisfy(hermano -> {
            assertThat(hermano.getEstado()).isEqualTo(EstadoReporte.OCULTADO);
            assertThat(hermano.isPorFraude()).isFalse();
            assertThat(hermano.getMotivoDecision()).isEqualTo(MOTIVO);
        });

        var porFraude = new ArrayList<Reporte>(List.of(deEspacio(600L)));
        when(reportes.findById(600L)).thenReturn(Optional.of(porFraude.getFirst()));
        when(reportes.bloquearPorRevisarDeEspacio(ESPACIO_ID)).thenReturn(porFraude);
        when(cancelacion.cancelarPorFraude(ADMIN, ESPACIO_ID, IP)).thenReturn(new ReservasCanceladasPorModeracion(0));
        servicio.decidir(ADMIN, 600L, decision("OCULTAR_POR_FRAUDE"), IP);
        assertThat(porFraude.getFirst().getEstado()).isEqualTo(EstadoReporte.OCULTADO);
        assertThat(porFraude.getFirst().isPorFraude()).isTrue();
    }

    @Test
    @DisplayName("HU-27 R34, R49: ocultar una reseña llama a ModeracionResenas.ocultar y no envía correo")
    void ocultaResena() {
        var hermanos = hermanosDeResena();

        servicio.decidir(ADMIN, REPORTE_ID, decision("OCULTAR"), IP);

        verify(resenas).ocultar(ADMIN, RESENA_ID, IP);
        assertThat(hermanos).allSatisfy(h -> assertThat(h.getEstado()).isEqualTo(EstadoReporte.OCULTADO));
        verifyNoInteractions(avisos, cancelacion);
    }

    @Test
    @DisplayName("HU-27 R39: un espacio no ocultable da 400 sin resolver, auditar, cancelar ni avisar")
    void espacioNoOcultable() {
        var hermanos = hermanosDeEspacio();
        when(espacios.ocultar(ADMIN, ESPACIO_ID, IP))
                .thenThrow(new ReglaNegocioException("Solo puedes ocultar un espacio publicado o pausado."));

        for (var tipo : new String[] {"OCULTAR", "OCULTAR_POR_FRAUDE"}) {
            assertThatThrownBy(() -> servicio.decidir(ADMIN, REPORTE_ID, decision(tipo), IP))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage("Solo puedes ocultar un espacio publicado o pausado.");
        }
        sigueSinResolver(hermanos);
        verifyNoInteractions(auditoria, avisos, cancelacion);
    }

    @Test
    @DisplayName("HU-27 R40: una reseña que ya no está VISIBLE da 400 sin resolver ni auditar")
    void resenaNoOcultable() {
        var hermanos = hermanosDeResena();
        doThrow(new ReglaNegocioException("Esta reseña ya está oculta.")).when(resenas).ocultar(ADMIN, RESENA_ID, IP);

        assertThatThrownBy(() -> servicio.decidir(ADMIN, REPORTE_ID, decision("OCULTAR"), IP))
                .isInstanceOf(ReglaNegocioException.class).hasMessage("Esta reseña ya está oculta.");
        sigueSinResolver(hermanos);
        verifyNoInteractions(auditoria, avisos);
    }

    @Test
    @DisplayName("HU-27 R41: si el reporte ya no está POR_REVISAR da 400 ya resuelto sin ocultar, auditar ni avisar")
    void yaResuelto() {
        var reporte = deEspacio(REPORTE_ID);
        when(reportes.findById(REPORTE_ID)).thenReturn(Optional.of(reporte));
        when(reportes.bloquearPorRevisarDeEspacio(ESPACIO_ID)).thenReturn(List.of(deEspacio(HERMANO_ID)));

        assertThatThrownBy(() -> servicio.decidir(ADMIN, REPORTE_ID, decision("OCULTAR"), IP))
                .isInstanceOf(ReglaNegocioException.class).hasMessage(ReporteAdminService.MENSAJE_YA_RESUELTO);
        verifyNoInteractions(espacios, auditoria, avisos, cancelacion);
    }

    @Test
    @DisplayName("HU-27 R42: bloquea los hermanos antes de comprobar el estado y antes de ocultar el contenido")
    void ordenDeBloqueos() {
        hermanosDeEspacio();
        espacioOcultable();

        servicio.decidir(ADMIN, REPORTE_ID, decision("OCULTAR"), IP);

        var orden = inOrder(reportes, espacios, auditoria);
        orden.verify(reportes).findById(REPORTE_ID);
        orden.verify(reportes).bloquearPorRevisarDeEspacio(ESPACIO_ID);
        orden.verify(espacios).ocultar(ADMIN, ESPACIO_ID, IP);
        orden.verify(reportes).saveAllAndFlush(any());
        orden.verify(auditoria).registrar(any());
    }

    @Test
    @DisplayName("HU-27 R44: un motivo que queda vacío al sanitizarlo da 400 en motivoDecision sin leer reportes")
    void motivoSoloEtiquetas() {
        assertThatThrownBy(() -> servicio.decidir(ADMIN, REPORTE_ID, new DecisionReporteDTO("OCULTAR",
                " <b></b> <i> </i> "), IP))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.getCampo()).isEqualTo("motivoDecision");
                    assertThat(error.getMessage()).isEqualTo(DecisionReporteDTO.M_MOTIVO_DECISION);
                });
        verifyNoInteractions(reportes, espacios, auditoria);
    }

    @Test
    @DisplayName("HU-27 R45: la decisión responde el detalle resuelto con reservasCanceladas = 0 sin fraude")
    void respuestaDeLaDecision() {
        hermanosDeEspacio();
        espacioOcultable();
        when(reportes.countByEspacioIdAndEstado(ESPACIO_ID, EstadoReporte.POR_REVISAR)).thenReturn(0L);

        var detalle = servicio.decidir(ADMIN, REPORTE_ID, decision("OCULTAR"), IP);

        assertThat(detalle.id()).isEqualTo(REPORTE_ID);
        assertThat(detalle.estado()).isEqualTo("OCULTADO");
        assertThat(detalle.decision()).isEqualTo("OCULTAR");
        assertThat(detalle.motivoDecision()).isEqualTo(MOTIVO);
        assertThat(detalle.resueltoEn()).isEqualTo(AHORA);
        assertThat(detalle.reportesPendientes()).isZero();
        assertThat(detalle.reservasCanceladas()).isZero();
    }

    @Test
    @DisplayName("HU-27 R46: ocultar sin fraude nunca llama a CancelacionPorModeracion")
    void ocultarSinFraudeNoCancela() {
        hermanosDeEspacio();
        espacioOcultable();

        servicio.decidir(ADMIN, REPORTE_ID, decision("OCULTAR"), IP);

        verifyNoInteractions(cancelacion);
    }

    @Test
    @DisplayName("HU-27 R48: si ocultar lanza, no se registra ningún correo")
    void sinCorreoSiFalla() {
        hermanosDeEspacio();
        when(espacios.ocultar(ADMIN, ESPACIO_ID, IP)).thenThrow(new IllegalStateException("falla"));

        assertThatThrownBy(() -> servicio.decidir(ADMIN, REPORTE_ID, decision("OCULTAR"), IP))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(avisos);
    }

    @Test
    @DisplayName("HU-27 R49: mantener no envía ningún correo")
    void mantenerSinCorreo() {
        hermanosDeEspacio();

        servicio.decidir(ADMIN, REPORTE_ID, decision("MANTENER"), IP);

        verifyNoInteractions(avisos);
        verify(cuentas, never()).correoDe(anyLong());
    }

    @Test
    @DisplayName("HU-27 R51: audita REPORTE_RESUELTO con tipo, objetivo, decisión, reportes y reservas, sin el motivo")
    void auditaLaDecision() {
        hermanosDeEspacio();
        espacioOcultable();
        when(cancelacion.cancelarPorFraude(ADMIN, ESPACIO_ID, IP)).thenReturn(new ReservasCanceladasPorModeracion(2));

        servicio.decidir(ADMIN, REPORTE_ID, decision("OCULTAR_POR_FRAUDE"), IP);

        verify(auditoria).registrar(new EventoAuditoria(ADMIN_ID, "REPORTE_RESUELTO", "reporte", REPORTE_ID, IP,
                "tipo=ESPACIO;objetivoId=" + ESPACIO_ID + ";decision=OCULTAR_POR_FRAUDE;reportes=2;"
                        + "reservasCanceladas=2"));
    }

    @Test
    @DisplayName("HU-27 R54: una decisión rechazada no audita nada")
    void rechazoNoAudita() {
        var hermanos = hermanosDeEspacio();
        when(espacios.ocultar(ADMIN, ESPACIO_ID, IP)).thenThrow(new ReglaNegocioException("no ocultable"));

        assertThatThrownBy(() -> servicio.decidir(ADMIN, REPORTE_ID, decision("OCULTAR"), IP))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> servicio.decidir(null, REPORTE_ID, decision("MANTENER"), IP))
                .isInstanceOf(AccesoDenegadoException.class);
        sigueSinResolver(hermanos);
        verify(auditoria, never()).registrar(any());
    }

    @Test
    @DisplayName("HU-27 R63: OCULTAR_POR_FRAUDE sobre una reseña da 400 en decision sin bloquear, ocultar ni auditar")
    void fraudeEnResena() {
        var reporte = deResena(REPORTE_ID);
        when(reportes.findById(REPORTE_ID)).thenReturn(Optional.of(reporte));

        assertThatThrownBy(() -> servicio.decidir(ADMIN, REPORTE_ID, decision("OCULTAR_POR_FRAUDE"), IP))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.getCampo()).isEqualTo("decision");
                    assertThat(error.getMessage()).isEqualTo(ReporteAdminService.MENSAJE_FRAUDE_SOLO_ESPACIOS);
                });
        verify(reportes, never()).bloquearPorRevisarDeResena(anyLong());
        assertThat(reporte.estaPorRevisar()).isTrue();
        verifyNoInteractions(resenas, cancelacion, auditoria, avisos);
    }

    @Test
    @DisplayName("HU-27 R64, R78: por fraude cancela tras ocultar y pasa la cantidad al correo y a la respuesta")
    void ocultaPorFraude() {
        hermanosDeEspacio();
        espacioOcultable();
        when(cancelacion.cancelarPorFraude(ADMIN, ESPACIO_ID, IP)).thenReturn(new ReservasCanceladasPorModeracion(2));

        var detalle = servicio.decidir(ADMIN, REPORTE_ID, decision("OCULTAR_POR_FRAUDE"), IP);

        assertThat(detalle.reservasCanceladas()).isEqualTo(2);
        assertThat(detalle.decision()).isEqualTo("OCULTAR_POR_FRAUDE");
        verify(avisos).espacioOcultado("ana@correo.pe", new DatosEspacioOcultado(NOMBRE, MOTIVO, true, 2));
    }

    @Test
    @DisplayName("HU-27 R72: si cancelarPorFraude lanza, se propaga sin resolver, auditar ni registrar correos")
    void falloAlCancelar() {
        var hermanos = hermanosDeEspacio();
        espacioOcultable();
        when(cancelacion.cancelarPorFraude(ADMIN, ESPACIO_ID, IP)).thenThrow(new IllegalStateException("sin pago"));

        assertThatThrownBy(() -> servicio.decidir(ADMIN, REPORTE_ID, decision("OCULTAR_POR_FRAUDE"), IP))
                .isInstanceOf(IllegalStateException.class).hasMessage("sin pago");
        sigueSinResolver(hermanos);
        verifyNoInteractions(auditoria, avisos);
    }

    @Test
    @DisplayName("HU-27 R73: el orden es hermanos → ocultar el espacio → cancelar sus reservas")
    void ordenPorFraude() {
        hermanosDeEspacio();
        espacioOcultable();
        when(cancelacion.cancelarPorFraude(ADMIN, ESPACIO_ID, IP)).thenReturn(new ReservasCanceladasPorModeracion(1));

        servicio.decidir(ADMIN, REPORTE_ID, decision("OCULTAR_POR_FRAUDE"), IP);

        var orden = inOrder(reportes, espacios, cancelacion);
        orden.verify(reportes).bloquearPorRevisarDeEspacio(ESPACIO_ID);
        orden.verify(espacios).ocultar(ADMIN, ESPACIO_ID, IP);
        orden.verify(cancelacion).cancelarPorFraude(ADMIN, ESPACIO_ID, IP);
    }
}
