package pe.edu.ulima.espacioya.cuentas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import pe.edu.ulima.espacioya.administracion.service.impl.DatosPersonalesReclamosImpl;

/** HU-22 con reclamos (HU-26 R67–R70, P10) por la API, contra PostgreSQL real. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, DatosPersonalesSoporte.Dobles.class})
class DatosPersonalesReclamosIT extends DatosPersonalesSoporte {

    private long arrendador;
    private long usuario;
    private long reserva;

    private void escenario() {
        arrendador = crearArrendador(CORREO_ARRENDADOR, RUC);
        var espacio = espacioDe(arrendador, "Cancha Norte");
        usuario = crearUsuario(CORREO_USUARIO);
        reserva = reserva(usuario, espacio, "CONFIRMADA", hoy().minusDays(1), 8);
    }

    private long reclamo(String estado) {
        var id = jdbc.queryForObject("INSERT INTO reclamo (reserva_id, usuario_id, arrendador_id, motivo, descripcion, "
                        + "estado, abierto_en) VALUES (?, ?, ?, 'ESPACIO_CERRADO', 'Mi descripción', 'ABIERTO', ?) "
                        + "RETURNING id", Long.class, reserva, usuario, arrendador,
                Timestamp.from(reloj.instant().minus(Duration.ofHours(3))));
        if (!"ABIERTO".equals(estado)) {
            var admin = crearAdmin();
            jdbc.update("UPDATE reclamo SET estado = ?, resolucion = 'Texto del admin', resuelto_por = ?, "
                    + "resuelto_en = ?, monto_reembolso = ? WHERE id = ?", estado, admin,
                    Timestamp.from(reloj.instant()), "RESUELTO_USUARIO".equals(estado) ? 50 : null, id);
        }
        return id;
    }

    private void mensaje(long reclamo, long autor, String papel, String texto, int minutos) {
        jdbc.update("INSERT INTO mensaje_reclamo (reclamo_id, autor_id, papel, texto, fecha) VALUES (?, ?, ?, ?, ?)",
                reclamo, autor, papel, texto, Timestamp.from(reloj.instant().minus(Duration.ofMinutes(minutos))));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> reclamos(String correo) throws Exception {
        var exportacion = mapa(texto(exportar(token(correo), CONTRASENA).andExpect(status().isOk())));
        return (List<Map<String, Object>>) ((Map<String, Object>) exportacion.get("secciones")).get("reclamos");
    }

    @Test
    @DisplayName("HU-26 R67, R68: el usuario exporta su reclamo con su descripción y sus mensajes, sin los del "
            + "arrendador ni la resolución")
    void usuarioExporta() throws Exception {
        escenario();
        var id = reclamo("RESUELTO_USUARIO");
        mensaje(id, usuario, "USUARIO", "Mensaje mío", 30);
        mensaje(id, arrendador, "ARRENDADOR", "Respuesta ajena", 20);

        var lista = reclamos(CORREO_USUARIO);

        assertThat(lista).singleElement().satisfies(fila -> {
            assertThat(fila).containsOnlyKeys("reservaId", "espacioNombre", "motivo", "estado", "abiertoEn",
                    "resueltoEn", "aFavorDe", "misTextos");
            assertThat(((Number) fila.get("reservaId")).longValue()).isEqualTo(reserva);
            assertThat(fila).containsEntry("espacioNombre", "Cancha Norte").containsEntry("motivo", "ESPACIO_CERRADO")
                    .containsEntry("estado", "RESUELTO_USUARIO").containsEntry("aFavorDe", "USUARIO");
            assertThat(fila.get("misTextos")).isEqualTo(List.of("Mi descripción", "Mensaje mío"));
        });
        assertThat(lista.toString()).doesNotContain("Respuesta ajena", "Texto del admin");
    }

    @Test
    @DisplayName("HU-26 R67, R68: el arrendador exporta solo sus mensajes, sin la descripción del usuario")
    void arrendadorExporta() throws Exception {
        escenario();
        var id = reclamo("ABIERTO");
        mensaje(id, usuario, "USUARIO", "Mensaje del usuario", 30);
        mensaje(id, arrendador, "ARRENDADOR", "Mi respuesta", 20);

        var lista = reclamos(CORREO_ARRENDADOR);

        assertThat(lista).singleElement().satisfies(fila -> {
            assertThat(fila.get("misTextos")).isEqualTo(List.of("Mi respuesta"));
            assertThat(fila.get("aFavorDe")).isNull();
            assertThat(fila.get("resueltoEn")).isNull();
        });
        assertThat(lista.toString()).doesNotContain("Mi descripción", "Mensaje del usuario");
    }

    @Test
    @DisplayName("HU-26 R67: sin reclamos, la sección reclamos aparece vacía")
    void seccionVacia() throws Exception {
        crearUsuario(CORREO_USUARIO);

        assertThat(reclamos(CORREO_USUARIO)).isEmpty();
    }

    @Test
    @DisplayName("HU-26 R69: un reclamo abierto impide eliminar la cuenta del usuario y la del arrendador")
    void abiertoImpideEliminar() throws Exception {
        escenario();
        reclamo("ABIERTO");

        for (var correo : List.of(CORREO_USUARIO, CORREO_ARRENDADOR)) {
            eliminar(token(correo), CONTRASENA).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.length()").value(1))
                    .andExpect(jsonPath("$.errores[0].campo").value("reclamos"))
                    .andExpect(jsonPath("$.errores[0].mensaje")
                            .value(DatosPersonalesReclamosImpl.MENSAJE_RECLAMO_ABIERTO));
        }
        assertThat(filaUsuario(usuario).get("estado")).isEqualTo("ACTIVA");
        assertThat(filaUsuario(arrendador).get("estado")).isEqualTo("ACTIVA");
    }

    @Test
    @DisplayName("HU-26 R70: al eliminar la cuenta, la descripción y los mensajes del titular pasan a \"Texto "
            + "eliminado.\" y se conserva lo demás")
    void eliminarReemplazaTextos() throws Exception {
        escenario();
        var id = reclamo("RESUELTO_USUARIO");
        mensaje(id, usuario, "USUARIO", "Mi teléfono es 912", 30);
        mensaje(id, arrendador, "ARRENDADOR", "Respuesta ajena", 20);

        eliminar(token(CORREO_USUARIO), CONTRASENA).andExpect(status().isOk());

        var fila = jdbc.queryForMap("SELECT * FROM reclamo WHERE id = ?", id);
        assertThat(fila.get("descripcion")).isEqualTo("Texto eliminado.");
        assertThat(fila.get("estado")).isEqualTo("RESUELTO_USUARIO");
        assertThat(fila.get("motivo")).isEqualTo("ESPACIO_CERRADO");
        assertThat(fila.get("resolucion")).isEqualTo("Texto del admin");
        assertThat(fila.get("monto_reembolso").toString()).isEqualTo("50.00");
        assertThat(jdbc.queryForList("SELECT texto FROM mensaje_reclamo WHERE reclamo_id = ? ORDER BY fecha",
                String.class, id)).containsExactly("Texto eliminado.", "Respuesta ajena");
    }
}
