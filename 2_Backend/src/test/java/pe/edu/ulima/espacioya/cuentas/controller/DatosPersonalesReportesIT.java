package pe.edu.ulima.espacioya.cuentas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;

/** HU-22 con reportes (HU-27 R59–R61, P12) por la API, contra PostgreSQL real. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, DatosPersonalesSoporte.Dobles.class})
class DatosPersonalesReportesIT extends DatosPersonalesSoporte {

    private long arrendador;
    private long espacio;
    private long usuario;

    private void escenario() {
        arrendador = crearArrendador(CORREO_ARRENDADOR, RUC);
        espacio = espacioDe(arrendador, "Cancha Norte");
        usuario = crearUsuario(CORREO_USUARIO);
    }

    private long reporte(long reportante, long enEspacio, String motivo, int horasAtras) {
        return jdbc.queryForObject("INSERT INTO reporte (reportante_id, tipo_objetivo, espacio_id, motivo, estado, "
                        + "creado_en) VALUES (?, 'ESPACIO', ?, ?, 'POR_REVISAR', ?) RETURNING id", Long.class,
                reportante, enEspacio, motivo, Timestamp.from(reloj.instant().minus(Duration.ofHours(horasAtras))));
    }

    private void resolver(long reporte, long admin) {
        jdbc.update("UPDATE reporte SET estado = 'OCULTADO', admin_id = ?, motivo_decision = 'Texto del admin', "
                + "resuelto_en = ?, por_fraude = true WHERE id = ?", admin, Timestamp.from(reloj.instant()), reporte);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> reportes(String correo) throws Exception {
        var exportacion = mapa(texto(exportar(token(correo), CONTRASENA).andExpect(status().isOk())));
        return (List<Map<String, Object>>) ((Map<String, Object>) exportacion.get("secciones")).get("reportes");
    }

    @Test
    @DisplayName("HU-27 R59: el usuario exporta sus reportes, del más antiguo al más nuevo, sin el motivo ni el admin "
            + "de la decisión")
    void usuarioExporta() throws Exception {
        escenario();
        var otro = espacioDe(arrendador, "Sala Sur");
        var resuelto = reporte(usuario, espacio, "FRAUDE", 5);
        reporte(usuario, otro, "SPAM", 1);
        resolver(resuelto, crearAdmin());

        var lista = reportes(CORREO_USUARIO);

        assertThat(lista).hasSize(2);
        assertThat(lista.getFirst()).containsOnlyKeys("tipoObjetivo", "objetivoId", "motivo", "estado", "creadoEn",
                "resueltoEn");
        assertThat(lista.getFirst()).containsEntry("tipoObjetivo", "ESPACIO").containsEntry("motivo", "FRAUDE")
                .containsEntry("estado", "OCULTADO");
        assertThat(((Number) lista.getFirst().get("objetivoId")).longValue()).isEqualTo(espacio);
        assertThat(lista.get(1)).containsEntry("motivo", "SPAM").containsEntry("estado", "POR_REVISAR")
                .containsEntry("resueltoEn", null);
        assertThat(lista.toString()).doesNotContain("Texto del admin");
    }

    @Test
    @DisplayName("HU-27 R59: el arrendador exporta sus reportes; sin reportes la sección aparece vacía")
    void arrendadorYVacia() throws Exception {
        escenario();
        var ajeno = espacioDe(crearArrendador(CORREO_OTRO_ARRENDADOR, RUC_OTRO), "Ajena");
        reporte(arrendador, ajeno, "INFORMACION_FALSA", 2);

        assertThat(reportes(CORREO_ARRENDADOR)).singleElement()
                .satisfies(fila -> assertThat(fila).containsEntry("motivo", "INFORMACION_FALSA"));
        assertThat(reportes(CORREO_USUARIO)).isEmpty();
    }

    @Test
    @DisplayName("HU-27 R60, R61: los reportes no impiden eliminar la cuenta y se conservan sin cambios")
    void eliminarConservaLosReportes() throws Exception {
        escenario();
        var admin = crearAdmin();
        var resuelto = reporte(usuario, espacio, "FRAUDE", 5);
        resolver(resuelto, admin);
        var pendiente = reporte(usuario, espacioDe(arrendador, "Sala Sur"), "SPAM", 1);
        var antes = jdbc.queryForList("SELECT * FROM reporte ORDER BY id");

        eliminar(token(CORREO_USUARIO), CONTRASENA).andExpect(status().isOk());

        assertThat(filaUsuario(usuario).get("estado")).isEqualTo("ELIMINADA");
        assertThat(jdbc.queryForList("SELECT * FROM reporte ORDER BY id")).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT admin_id FROM reporte WHERE id = ?", Long.class, resuelto))
                .isEqualTo(admin);
        assertThat(jdbc.queryForObject("SELECT reportante_id FROM reporte WHERE id = ?", Long.class, pendiente))
                .isEqualTo(usuario);
    }
}
