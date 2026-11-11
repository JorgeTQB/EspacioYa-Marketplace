package pe.edu.ulima.espacioya.administracion.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Base de las IT de HU-27. Reutiliza la de HU-26 (reloj fijo en 2026-11-04 10:00 de Lima, doble de correo, limpieza
 * de {@code reporte} antes que {@code resena}, {@code espacio} y {@code usuario}) y agrega reseñas y reportes.
 */
public abstract class ReportesSoporte extends ReclamosSoporte {

    protected static final String REPORTES = "/api/reportes";
    protected static final String ADMIN_REPORTES = "/api/admin/reportes";
    protected static final String COMENTARIO = "Muy buena cancha, volveré.";

    /** Una reseña de {@code autor} sobre {@code enEspacio}, con su reserva confirmada de hace 3 días. */
    protected long resena(long autor, long enEspacio, String estado) {
        return resena(autor, enEspacio, estado, 8);
    }

    /**
     * Igual, con la reserva a la {@code hora} indicada: dos reseñas sobre el mismo espacio necesitan bloques distintos
     * ({@code uq_reserva_bloque_activo}).
     */
    protected long resena(long autor, long enEspacio, String estado, int hora) {
        var reserva = reserva(autor, enEspacio, "CONFIRMADA", hoy.minusDays(3), hora);
        return jdbc.queryForObject("INSERT INTO resena (reserva_id, usuario_id, espacio_id, calificacion, comentario, "
                        + "estado, creado_en) VALUES (?, ?, ?, 4, ?, ?, ?) RETURNING id", Long.class, reserva, autor,
                enEspacio, COMENTARIO, estado, Timestamp.from(reloj.instant().minusSeconds(3600)));
    }

    protected long sembrarReporte(long reportante, String tipo, long objetivo, String motivo, Instant creadoEn) {
        var columna = "ESPACIO".equals(tipo) ? "espacio_id" : "resena_id";
        return jdbc.queryForObject("INSERT INTO reporte (reportante_id, tipo_objetivo, " + columna + ", motivo, estado, "
                        + "creado_en) VALUES (?, ?, ?, ?, 'POR_REVISAR', ?) RETURNING id", Long.class, reportante, tipo,
                objetivo, motivo, Timestamp.from(creadoEn));
    }

    protected long sembrarReporte(long reportante, String tipo, long objetivo) {
        return sembrarReporte(reportante, tipo, objetivo, "SPAM", reloj.instant());
    }

    protected void resolverReportePorJdbc(long reporte, String estado, long admin, Instant resueltoEn) {
        jdbc.update("UPDATE reporte SET estado = ?, admin_id = ?, motivo_decision = 'Revisado', resuelto_en = ? "
                + "WHERE id = ?", estado, admin, Timestamp.from(resueltoEn), reporte);
    }

    protected ResultActions reportar(String token, String cuerpo) throws Exception {
        return mvc.perform(con(token, post(REPORTES)).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    protected ResultActions reportar(String token, String tipo, long objetivo, String motivo) throws Exception {
        return reportar(token, "{\"tipoObjetivo\":\"" + tipo + "\",\"objetivoId\":" + objetivo + ",\"motivo\":\""
                + motivo + "\"}");
    }

    protected ResultActions decidir(String token, Object reporte, String cuerpo) throws Exception {
        return mvc.perform(comoAdmin(token, post(ADMIN_REPORTES + "/" + reporte + "/decision"))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    protected ResultActions decidir(String token, Object reporte, String decision, String motivo) throws Exception {
        return decidir(token, reporte, "{\"decision\":\"" + decision + "\",\"motivoDecision\":\"" + motivo + "\"}");
    }

    protected long reportarPorLaApi(String token, String tipo, long objetivo) throws Exception {
        return numero(cuerpo(reportar(token, tipo, objetivo, "SPAM").andExpect(status().isCreated())).get("id"));
    }

    protected Map<String, Object> reporteGuardado(long id) {
        return jdbc.queryForMap("SELECT * FROM reporte WHERE id = ?", id);
    }

    protected String estadoDe(String tabla, long id) {
        return jdbc.queryForObject("SELECT estado FROM " + tabla + " WHERE id = ?", String.class, id);
    }

    protected List<Map<String, Object>> auditoriasDe(String accion, String entidad) {
        return jdbc.queryForList("SELECT * FROM auditoria WHERE accion = ? AND entidad = ? ORDER BY id", accion,
                entidad);
    }
}
