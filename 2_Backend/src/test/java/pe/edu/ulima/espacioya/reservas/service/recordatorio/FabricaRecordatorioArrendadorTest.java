package pe.edu.ulima.espacioya.reservas.service.recordatorio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.edu.ulima.espacioya.comun.correo.MensajeCorreo;

class FabricaRecordatorioArrendadorTest {

    private static final DatosRecordatorio DATOS =
            new DatosRecordatorio("Cancha Los Olivos", LocalDate.of(2026, 11, 2), 22, 24);

    private final List<MensajeCorreo> enviados = new ArrayList<>();

    @Test
    @DisplayName("HU-25 R30, R31: un único mensaje al arrendador con el asunto fijo del recordatorio")
    void enviaUnMensajeConAsuntoFijo() {
        new FabricaRecordatorioArrendador(enviados::add).enviar("ana@correo.pe", DATOS);

        assertThat(enviados).hasSize(1);
        assertThat(enviados.getFirst().para()).isEqualTo("ana@correo.pe");
        assertThat(enviados.getFirst().asunto()).isEqualTo("Recordatorio: reserva en tu espacio");
    }

    @Test
    @DisplayName("HU-25 R29: el cuerpo trae espacio, fecha y horario, sin datos de quien reservó ni montos")
    void cuerpoMinimo() {
        new FabricaRecordatorioArrendador(enviados::add).enviar("ana@correo.pe", DATOS);

        var cuerpo = enviados.getFirst().cuerpo();
        assertThat(cuerpo).contains("Cancha Los Olivos", "02/11/2026", "22:00 a 24:00");
        assertThat(cuerpo).doesNotContain("S/", "Tarjeta", "Referencia", "@", "Monto", "Teléfono");
    }
}
