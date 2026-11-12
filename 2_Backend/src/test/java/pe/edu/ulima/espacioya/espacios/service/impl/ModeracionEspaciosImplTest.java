package pe.edu.ulima.espacioya.espacios.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.espacios.domain.DatosEspacio;
import pe.edu.ulima.espacioya.espacios.domain.Espacio;
import pe.edu.ulima.espacioya.espacios.domain.EstadoEspacio;
import pe.edu.ulima.espacioya.espacios.domain.TipoEspacio;
import pe.edu.ulima.espacioya.espacios.repository.EspacioRepository;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.espacios.service.EspacioModerado;
import pe.edu.ulima.espacioya.espacios.service.EspacioOcultado;
import pe.edu.ulima.espacioya.espacios.service.EspacioReportable;
import pe.edu.ulima.espacioya.espacios.service.EstadoEspacioService;

@ExtendWith(MockitoExtension.class)
class ModeracionEspaciosImplTest {

    private static final long ESPACIO_ID = 40L;
    private static final long ARRENDADOR_ID = 7L;
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(1L, 33L, "ADMIN");
    private static final DatosEspacio DATOS = new DatosEspacio("Cancha Los Olivos", TipoEspacio.CANCHA,
            "Av. Universitaria 1234", "LOS_OLIVOS", 20, new BigDecimal("80"), "Cancha techada.");

    @Mock
    private CatalogoEspaciosService catalogo;
    @Mock
    private EspacioRepository espacios;
    @Mock
    private EstadoEspacioService estados;

    private ModeracionEspaciosImpl moderacion;

    @BeforeEach
    void preparar() {
        moderacion = new ModeracionEspaciosImpl(catalogo, espacios, estados);
    }

    private static Espacio espacio(EstadoEspacio estado) {
        var espacio = Espacio.crear(ARRENDADOR_ID, DATOS, Instant.parse("2026-11-01T15:00:00Z"));
        ReflectionTestUtils.setField(espacio, "id", ESPACIO_ID);
        ReflectionTestUtils.setField(espacio, "estado", estado);
        return espacio;
    }

    @Test
    @DisplayName("HU-27 R7: un espacio visible es reportable con su arrendador")
    void visibleEsReportable() {
        when(catalogo.esVisible(ESPACIO_ID)).thenReturn(true);
        when(espacios.findById(ESPACIO_ID)).thenReturn(Optional.of(espacio(EstadoEspacio.PUBLICADO)));

        assertThat(moderacion.reportable(ESPACIO_ID)).contains(new EspacioReportable(ESPACIO_ID, ARRENDADOR_ID));
    }

    @Test
    @DisplayName("HU-27 R7: un espacio no visible (no PUBLICADO, arrendador no ACTIVA o inexistente) o null no es "
            + "reportable")
    void noVisibleNoEsReportable() {
        when(catalogo.esVisible(ESPACIO_ID)).thenReturn(false);

        assertThat(moderacion.reportable(ESPACIO_ID)).isEmpty();
        assertThat(moderacion.reportable(null)).isEmpty();
        verifyNoInteractions(espacios);
    }

    @Test
    @DisplayName("HU-27 R24: describir devuelve el espacio en cualquier estado, sin consultar si la lista está vacía")
    void describe() {
        when(espacios.findAllById(Set.of(ESPACIO_ID))).thenReturn(List.of(espacio(EstadoEspacio.OCULTO)));

        var descritos = moderacion.describir(List.of(ESPACIO_ID));

        assertThat(descritos).containsExactlyEntriesOf(Map.of(ESPACIO_ID, new EspacioModerado(ESPACIO_ID,
                ARRENDADOR_ID, "Cancha Los Olivos", "CANCHA", "LOS_OLIVOS", "Av. Universitaria 1234", 20,
                new BigDecimal("80.00"), "Cancha techada.", "OCULTO")));
        assertThat(moderacion.describir(List.of())).isEmpty();
        verify(espacios).findAllById(any());
    }

    @Test
    @DisplayName("HU-27 R32: ocultar delega en EstadoEspacioService.ocultarComoAdmin")
    void ocultarDelega() {
        var ocultado = new EspacioOcultado(ESPACIO_ID, ARRENDADOR_ID, "Cancha Los Olivos", "PUBLICADO");
        when(estados.ocultarComoAdmin(ADMIN, ESPACIO_ID, "1.2.3.4")).thenReturn(ocultado);

        assertThat(moderacion.ocultar(ADMIN, ESPACIO_ID, "1.2.3.4")).isEqualTo(ocultado);
    }
}
