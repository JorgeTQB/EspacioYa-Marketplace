package pe.edu.ulima.espacioya.administracion.service.correo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CorreosModeracionTest {

    private static CorreoEspacioOcultado correo(boolean porFraude, int canceladas) {
        return new CorreoEspacioOcultado(new DatosEspacioOcultado("Cancha Olivos", "Las fotos no son del local.",
                porFraude, canceladas));
    }

    @Test
    @DisplayName("HU-27 R47: asunto fijo y cuerpo con el nombre del espacio y el motivo de la decisión, sin el reporte")
    void asuntoYCuerpo() {
        var sinFraude = correo(false, 0);

        assertThat(sinFraude.asunto()).isEqualTo("Ocultamos tu espacio en EspacioYa");
        assertThat(sinFraude.cuerpo()).contains("\"Cancha Olivos\"", "decidimos ocultarlo",
                "ya no aparece en la búsqueda ni recibe reservas nuevas", "Motivo: Las fotos no son del local.",
                "Las reservas que ya estaban confirmadas se mantienen.");
        assertThat(sinFraude.cuerpo()).doesNotContain("FRAUDE", "SPAM", "INFORMACION_FALSA", "CONTENIDO_INAPROPIADO",
                "reportó", "@", "fraude");
    }

    @Test
    @DisplayName("HU-27 R47, R70: por fraude con reservas canceladas dice cuántas, el 100 % y que no se liquidan")
    void porFraudeConReservas() {
        var cuerpo = correo(true, 3).cuerpo();

        assertThat(cuerpo).contains("Motivo: Las fotos no son del local.",
                "Como lo ocultamos por fraude, cancelamos sus 3 reservas confirmadas que aún no empezaban",
                "devolvimos el 100 % a quienes reservaron",
                "Esas reservas no se incluirán en tus liquidaciones.");
        assertThat(cuerpo).doesNotContain("se mantienen");
    }

    @Test
    @DisplayName("HU-27 R47: por fraude sin reservas por empezar lo dice así")
    void porFraudeSinReservas() {
        var cuerpo = correo(true, 0).cuerpo();

        assertThat(cuerpo).contains("Como lo ocultamos por fraude, no recibirá más reservas. "
                + "No tenía reservas confirmadas por empezar.");
        assertThat(cuerpo).doesNotContain("cancelamos sus", "se mantienen");
    }
}
