package pe.edu.ulima.espacioya.reservas.service.recordatorio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.edu.ulima.espacioya.comun.correo.MensajeCorreo;

class FabricaRecordatorioUsuarioTest {

    private static final DatosRecordatorio DATOS =
            new DatosRecordatorio("Cancha Los Olivos", LocalDate.of(2026, 11, 2), 18, 20);

    private final List<MensajeCorreo> enviados = new ArrayList<>();

    @Test
    @DisplayName("HU-25 R30, R31: un único mensaje al usuario con el asunto fijo del recordatorio")
    void enviaUnMensajeConAsuntoFijo() {
        new FabricaRecordatorioUsuario(enviados::add).enviar("carla@correo.pe", DATOS);

        assertThat(enviados).hasSize(1);
        assertThat(enviados.getFirst().para()).isEqualTo("carla@correo.pe");
        assertThat(enviados.getFirst().asunto()).isEqualTo("Recordatorio: tu reserva es mañana");
    }

    @Test
    @DisplayName("HU-25 R29: el cuerpo trae espacio, fecha y horario, sin montos ni otros datos")
    void cuerpoMinimo() {
        new FabricaRecordatorioUsuario(enviados::add).enviar("carla@correo.pe", DATOS);

        var cuerpo = enviados.getFirst().cuerpo();
        assertThat(cuerpo).contains("Cancha Los Olivos", "02/11/2026", "18:00 a 20:00");
        assertThat(cuerpo).doesNotContain("S/", "Tarjeta", "Referencia", "@", "Monto");
    }
}
