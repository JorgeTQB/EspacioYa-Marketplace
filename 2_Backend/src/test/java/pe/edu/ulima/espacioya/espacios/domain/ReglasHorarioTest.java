package pe.edu.ulima.espacioya.espacios.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.edu.ulima.espacioya.espacios.domain.ReglasHorario.Problema;
import pe.edu.ulima.espacioya.espacios.domain.ReglasHorario.ProblemaFranja;

class ReglasHorarioTest {

    @Test
    @DisplayName("HU-08 R5: fin igual o anterior al inicio es un problema de rango en su índice")
    void reportaRango() {
        var problemas = ReglasHorario.revisar(List.of(new FranjaHoraria(1, 8, 12), new FranjaHoraria(2, 10, 10),
                new FranjaHoraria(3, 15, 9)));

        assertThat(problemas).containsExactly(new ProblemaFranja(1, Problema.RANGO),
                new ProblemaFranja(2, Problema.RANGO));
    }

    @Test
    @DisplayName("HU-08 R6: la franja que se cruza con otra de índice menor es la superpuesta")
    void reportaSuperpuestaEnElIndiceMayor() {
        var problemas = ReglasHorario.revisar(List.of(new FranjaHoraria(1, 8, 12), new FranjaHoraria(2, 8, 12),
                new FranjaHoraria(1, 11, 14), new FranjaHoraria(1, 10, 11)));

        assertThat(problemas).containsExactly(new ProblemaFranja(2, Problema.SUPERPUESTA),
                new ProblemaFranja(3, Problema.SUPERPUESTA));
    }

    @Test
    @DisplayName("HU-08 R6: franjas contiguas o con las mismas horas en días distintos son válidas")
    void aceptaContiguasYOtrosDias() {
        assertThat(ReglasHorario.revisar(List.of(new FranjaHoraria(1, 8, 12), new FranjaHoraria(1, 12, 14),
                new FranjaHoraria(2, 8, 12)))).isEmpty();
        assertThat(ReglasHorario.revisar(List.of())).isEmpty();
    }

    @Test
    @DisplayName("HU-08 R5, R6: si hay problemas de rango no se reportan superposiciones")
    void rangoAntesQueSuperposicion() {
        var problemas = ReglasHorario.revisar(List.of(new FranjaHoraria(1, 8, 12), new FranjaHoraria(1, 9, 11),
                new FranjaHoraria(1, 14, 13)));

        assertThat(problemas).containsExactly(new ProblemaFranja(2, Problema.RANGO));
    }
}
