package pe.edu.ulima.espacioya.administracion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.Order;

import pe.edu.ulima.espacioya.administracion.domain.DecisionReporte;
import pe.edu.ulima.espacioya.administracion.domain.MotivoReporte;
import pe.edu.ulima.espacioya.administracion.domain.Reporte;
import pe.edu.ulima.espacioya.administracion.dto.ReporteExportadoDTO;
import pe.edu.ulima.espacioya.administracion.repository.ReporteRepository;
import pe.edu.ulima.espacioya.cuentas.service.puertos.SeccionDatos;
import pe.edu.ulima.espacioya.cuentas.service.puertos.TitularDatos;

@ExtendWith(MockitoExtension.class)
class DatosPersonalesReportesImplTest {

    private static final Instant CREADO = Instant.parse("2026-11-01T15:00:00Z");
    private static final Instant RESUELTO = Instant.parse("2026-11-02T15:00:00Z");

    @Mock
    private ReporteRepository reportes;

    private DatosPersonalesReportesImpl participante;

    @BeforeEach
    void preparar() {
        participante = new DatosPersonalesReportesImpl(reportes);
    }

    private static TitularDatos titular(String rol) {
        return new TitularDatos(7L, rol);
    }

    @Test
    @DisplayName("HU-27 R59: exporta los reportes del titular sin el motivo ni el admin de la decisión")
    void exporta() {
        var resuelto = Reporte.deEspacio(7L, 40L, MotivoReporte.FRAUDE, CREADO);
        resuelto.resolver(DecisionReporte.OCULTAR_POR_FRAUDE, 99L, "Motivo secreto", RESUELTO);
        var pendiente = Reporte.deResena(7L, 60L, MotivoReporte.SPAM, RESUELTO);
        when(reportes.findByReportanteIdOrderByCreadoEnAscIdAsc(7L)).thenReturn(List.of(resuelto, pendiente));

        var secciones = participante.exportar(titular("USUARIO"));

        assertThat(secciones).containsExactly(new SeccionDatos("reportes", List.of(
                new ReporteExportadoDTO("ESPACIO", 40L, "FRAUDE", "OCULTADO", CREADO, RESUELTO),
                new ReporteExportadoDTO("RESENA", 60L, "SPAM", "POR_REVISAR", RESUELTO, null))));
        assertThat(ReporteExportadoDTO.class.getRecordComponents()).extracting(c -> c.getName())
                .doesNotContain("motivoDecision", "adminId", "decision");
    }

    @Test
    @DisplayName("HU-27 R59: un arrendador sin reportes exporta la sección vacía; un admin no la exporta")
    void seccionVaciaYAdmin() {
        when(reportes.findByReportanteIdOrderByCreadoEnAscIdAsc(7L)).thenReturn(List.of());

        assertThat(participante.exportar(titular("ARRENDADOR")))
                .containsExactly(new SeccionDatos("reportes", List.of()));
        assertThat(participante.exportar(titular("ADMIN"))).isEmpty();
    }

    @Test
    @DisplayName("HU-27 R60, R61: no genera impedimentos y no cambia nada al anonimizar")
    void sinImpedimentosNiCambios() {
        assertThat(participante.impedimentos(titular("USUARIO"), CREADO)).isEmpty();
        participante.anonimizar(titular("USUARIO"));

        verifyNoInteractions(reportes);
        assertThat(DatosPersonalesReportesImpl.class.getAnnotation(Order.class).value()).isEqualTo(5);
    }
}
