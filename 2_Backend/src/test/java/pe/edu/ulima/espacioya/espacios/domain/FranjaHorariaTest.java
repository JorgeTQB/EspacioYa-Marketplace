package pe.edu.ulima.espacioya.espacios.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FranjaHorariaTest {

    @Test
    @DisplayName("HU-08 R6: franjas solapadas del mismo día se superponen; contiguas o de otro día, no")
    void detectaSuperposicion() {
        var manana = new FranjaHoraria(1, 8, 12);

        assertThat(manana.seSuperponeCon(new FranjaHoraria(1, 11, 14))).isTrue();
        assertThat(new FranjaHoraria(1, 11, 14).seSuperponeCon(manana)).isTrue();
        assertThat(manana.seSuperponeCon(new FranjaHoraria(1, 9, 10))).isTrue();
        assertThat(manana.seSuperponeCon(new FranjaHoraria(1, 12, 14))).isFalse();
        assertThat(manana.seSuperponeCon(new FranjaHoraria(1, 6, 8))).isFalse();
        assertThat(manana.seSuperponeCon(new FranjaHoraria(2, 8, 12))).isFalse();
    }

    @Test
    @DisplayName("HU-08 R22: una franja contiene sus horas de inicio hasta la anterior al fin")
    void contieneSusHoras() {
        var franja = new FranjaHoraria(1, 8, 10);

        assertThat(franja.contiene(7)).isFalse();
        assertThat(franja.contiene(8)).isTrue();
        assertThat(franja.contiene(9)).isTrue();
        assertThat(franja.contiene(10)).isFalse();
    }

    @Test
    @DisplayName("HU-08 R41: el texto de una franja es dia:inicio-fin")
    void escribeSuTexto() {
        assertThat(new FranjaHoraria(1, 8, 22).aTexto()).isEqualTo("1:8-22");
        assertThat(new FranjaHoraria(7, 0, 24).aTexto()).isEqualTo("7:0-24");
    }

    @Test
    @DisplayName("HU-08 R1: el orden natural es por día y luego por hora de inicio")
    void ordenaPorDiaEInicio() {
        var franjas = new ArrayList<>(List.of(new FranjaHoraria(6, 15, 24), new FranjaHoraria(1, 14, 16),
                new FranjaHoraria(6, 8, 13), new FranjaHoraria(1, 8, 10)));

        Collections.sort(franjas);

        assertThat(franjas).containsExactly(new FranjaHoraria(1, 8, 10), new FranjaHoraria(1, 14, 16),
                new FranjaHoraria(6, 8, 13), new FranjaHoraria(6, 15, 24));
    }
}
