package pe.edu.ulima.espacioya.busqueda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.edu.ulima.espacioya.busqueda.dto.ResultadoEspacioDTO;
import pe.edu.ulima.espacioya.comun.error.ErrorCampoDTO;
import pe.edu.ulima.espacioya.comun.error.ValidacionException;
import pe.edu.ulima.espacioya.espacios.service.CalendarioEspacioService;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.espacios.service.EspacioPublicado;

@ExtendWith(MockitoExtension.class)
class BusquedaServiceTest {

    // 22:00 del miércoles 2026-10-28 en Lima (03:00 del jueves en UTC).
    private static final Instant AHORA = Instant.parse("2026-10-29T03:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 28);
    private static final String FECHA = "2026-11-02";
    private static final CriteriosBusqueda CRITERIOS = new CriteriosBusqueda("CANCHA", "SURCO", LocalDate.parse(FECHA));

    @Mock
    private BusquedaEspacios busqueda;
    @Mock
    private CatalogoEspaciosService catalogo;

    private BusquedaService servicio;

    @BeforeEach
    void preparar() {
        servicio = new BusquedaService(busqueda, catalogo, Clock.fixed(AHORA, ZoneOffset.UTC));
        lenient().when(catalogo.esTipoValido(any())).thenAnswer(i -> "CANCHA".equals(i.getArgument(0)));
        lenient().when(catalogo.existeZona(any())).thenAnswer(i -> "SURCO".equals(i.getArgument(0)));
    }

    private static EspacioPublicado publicado(long id) {
        return new EspacioPublicado(id, "Cancha " + id, "CANCHA", "SURCO", "Santiago de Surco",
                new BigDecimal(String.valueOf(10 + id)));
    }

    private static List<EspacioPublicado> disponibles(int cuantos) {
        return LongStream.rangeClosed(1, cuantos).mapToObj(BusquedaServiceTest::publicado).toList();
    }

    private List<ErrorCampoDTO> errores(String tipo, String zona, String fecha, String pagina) {
        return catchValidacion(() -> servicio.buscar(tipo, zona, fecha, pagina)).getErrores();
    }

    @Test
    @DisplayName("HU-10 R18, R20: con 45 disponibles, las páginas 1, 2 y 3 traen 20, 20 y 5 con hayMas correcto")
    void paginasDeVeinte() {
        when(busqueda.buscar(CRITERIOS)).thenReturn(disponibles(45));
        when(catalogo.fotosPrincipales(any())).thenReturn(Map.of());

        var primera = servicio.buscar("CANCHA", "SURCO", FECHA, "1");
        var segunda = servicio.buscar("CANCHA", "SURCO", FECHA, "2");
        var tercera = servicio.buscar("CANCHA", "SURCO", FECHA, "3");

        assertThat(primera.resultados()).extracting(ResultadoEspacioDTO::id)
                .containsExactlyElementsOf(LongStream.rangeClosed(1, 20).boxed().toList());
        assertThat(primera.hayMas()).isTrue();
        assertThat(segunda.resultados()).extracting(ResultadoEspacioDTO::id)
                .containsExactlyElementsOf(LongStream.rangeClosed(21, 40).boxed().toList());
        assertThat(segunda.hayMas()).isTrue();
        assertThat(tercera.resultados()).extracting(ResultadoEspacioDTO::id)
                .containsExactlyElementsOf(LongStream.rangeClosed(41, 45).boxed().toList());
        assertThat(tercera.hayMas()).isFalse();
        assertThat(List.of(primera.pagina(), segunda.pagina(), tercera.pagina())).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("HU-10 R20: con exactamente 40 disponibles la página 2 trae 20 y hayMas es false")
    void hayMasEnElBorde() {
        when(busqueda.buscar(CRITERIOS)).thenReturn(disponibles(40));
        when(catalogo.fotosPrincipales(any())).thenReturn(Map.of());

        var segunda = servicio.buscar("CANCHA", "SURCO", FECHA, "2");

        assertThat(segunda.resultados()).hasSize(20);
        assertThat(segunda.hayMas()).isFalse();
    }

    @Test
    @DisplayName("HU-10 R21: una página posterior a la última responde vacía, sin hayMas y con la página pedida")
    void paginaPosteriorALaUltima() {
        when(busqueda.buscar(CRITERIOS)).thenReturn(disponibles(45));

        var cuarta = servicio.buscar("CANCHA", "SURCO", FECHA, "4");
        var lejana = servicio.buscar("CANCHA", "SURCO", FECHA, String.valueOf(Integer.MAX_VALUE));

        assertThat(cuarta.resultados()).isEmpty();
        assertThat(cuarta.hayMas()).isFalse();
        assertThat(cuarta.pagina()).isEqualTo(4);
        assertThat(lejana.resultados()).isEmpty();
        assertThat(lejana.pagina()).isEqualTo(Integer.MAX_VALUE);
        verify(catalogo, never()).fotosPrincipales(any());
    }

    @Test
    @DisplayName("HU-10 R19: sin el parámetro pagina responde la página 1")
    void sinPaginaEsLaPrimera() {
        when(busqueda.buscar(CRITERIOS)).thenReturn(disponibles(25));
        when(catalogo.fotosPrincipales(any())).thenReturn(Map.of());

        var resultado = servicio.buscar("CANCHA", "SURCO", FECHA, null);

        assertThat(resultado.pagina()).isEqualTo(1);
        assertThat(resultado.resultados()).hasSize(20);
        assertThat(resultado.hayMas()).isTrue();
    }

    @Test
    @DisplayName("HU-10 R11: sin disponibles responde resultados vacío y hayMas false")
    void sinDisponibles() {
        when(busqueda.buscar(CRITERIOS)).thenReturn(List.of());

        var resultado = servicio.buscar("CANCHA", "SURCO", FECHA, null);

        assertThat(resultado.resultados()).isEmpty();
        assertThat(resultado.hayMas()).isFalse();
        assertThat(resultado.pagina()).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-10 R10, R14, R15, R16: conserva el orden recibido y pide las fotos solo de los ids de la página")
    void ordenYFotosDeLaPagina() {
        when(busqueda.buscar(CRITERIOS)).thenReturn(List.of(publicado(7), publicado(3), publicado(9)));
        when(catalogo.fotosPrincipales(List.of(7L, 3L, 9L))).thenReturn(Map.of(7L, "/api/fotos/a", 9L, "/api/fotos/c"));

        var resultado = servicio.buscar("CANCHA", "SURCO", FECHA, "1");

        assertThat(resultado.resultados()).containsExactly(
                new ResultadoEspacioDTO(7L, "Cancha 7", "SURCO", "Santiago de Surco", new BigDecimal("17"),
                        "/api/fotos/a"),
                new ResultadoEspacioDTO(3L, "Cancha 3", "SURCO", "Santiago de Surco", new BigDecimal("13"), null),
                new ResultadoEspacioDTO(9L, "Cancha 9", "SURCO", "Santiago de Surco", new BigDecimal("19"),
                        "/api/fotos/c"));
    }

    @Test
    @DisplayName("HU-10 R17: pide las fotos solo de los 20 ids de la página 2")
    void fotosSoloDeLaPaginaPedida() {
        when(busqueda.buscar(CRITERIOS)).thenReturn(disponibles(45));
        when(catalogo.fotosPrincipales(LongStream.rangeClosed(21, 40).boxed().toList())).thenReturn(Map.of());

        assertThat(servicio.buscar("CANCHA", "SURCO", FECHA, "2").resultados()).hasSize(20);
    }

    @Test
    @DisplayName("HU-10 R23: tipo ausente, vacío o inexistente responde 400 en el campo tipo")
    void tipoInvalido() {
        for (var tipo : new String[] {null, "", "PISCINA", "cancha"}) {
            assertThat(errores(tipo, "SURCO", FECHA, null))
                    .containsExactly(new ErrorCampoDTO("tipo", BusquedaService.MENSAJE_TIPO_INVALIDO));
        }
        verifyNoInteractions(busqueda);
    }

    @Test
    @DisplayName("HU-10 R24: zona ausente, vacía o inexistente responde 400 en el campo zona")
    void zonaInvalida() {
        for (var zona : new String[] {null, "", "  ", "ATLANTIDA", "surco"}) {
            assertThat(errores("CANCHA", zona, FECHA, null))
                    .containsExactly(new ErrorCampoDTO("zona", BusquedaService.MENSAJE_ZONA_INVALIDA));
        }
        verifyNoInteractions(busqueda);
    }

    @Test
    @DisplayName("HU-10 R25: fecha ausente o mal escrita responde 400 en el campo fecha con fecha inválida")
    void fechaMalEscrita() {
        for (var fecha : new String[] {null, "", "2026-02-30", "02/11/2026", "2026-11-2", "mañana"}) {
            assertThat(errores("CANCHA", "SURCO", fecha, null))
                    .containsExactly(new ErrorCampoDTO("fecha", BusquedaService.MENSAJE_FECHA_INVALIDA));
        }
        verifyNoInteractions(busqueda);
    }

    @Test
    @DisplayName("HU-10 R26: una fecha fuera de hoy…hoy+55 en Lima responde 400 con fuera de ventana")
    void fechaFueraDeVentana() {
        for (var fecha : new LocalDate[] {HOY.minusDays(1), HOY.plusDays(56)}) {
            assertThat(errores("CANCHA", "SURCO", fecha.toString(), null))
                    .containsExactly(new ErrorCampoDTO("fecha", BusquedaService.MENSAJE_FUERA_DE_VENTANA));
        }
        verifyNoInteractions(busqueda);
    }

    @Test
    @DisplayName("HU-10 R26: hoy y hoy+55 (en Lima, no en UTC) están dentro de la ventana")
    void bordesDeLaVentana() {
        when(busqueda.buscar(any())).thenReturn(List.of());

        assertThat(servicio.buscar("CANCHA", "SURCO", HOY.toString(), null).resultados()).isEmpty();
        assertThat(servicio.buscar("CANCHA", "SURCO", HOY.plusDays(55).toString(), null).resultados()).isEmpty();
    }

    @Test
    @DisplayName("HU-10 R25, R26: los textos de fecha son los mismos que los de HU-08")
    void textosDeFechaComoHu08() {
        assertThat(BusquedaService.MENSAJE_FECHA_INVALIDA).isEqualTo(CalendarioEspacioService.MENSAJE_FECHA_INVALIDA);
        assertThat(BusquedaService.MENSAJE_FUERA_DE_VENTANA)
                .isEqualTo(CalendarioEspacioService.MENSAJE_FUERA_DE_VENTANA);
    }

    @Test
    @DisplayName("HU-10 R27: cada página no válida responde 400 en el campo pagina")
    void paginaInvalida() {
        for (var pagina : new String[] {"0", "-1", "1.5", "abc", "", " ", "+1", "99999999999", "2147483648"}) {
            assertThat(errores("CANCHA", "SURCO", FECHA, pagina))
                    .as("pagina=%s", pagina)
                    .containsExactly(new ErrorCampoDTO("pagina", BusquedaService.MENSAJE_PAGINA_INVALIDA));
        }
        verifyNoInteractions(busqueda);
    }

    @Test
    @DisplayName("HU-10 R28: con varios parámetros inválidos responde un error por campo en orden tipo, zona, fecha, "
            + "pagina")
    void variosErroresEnOrden() {
        var error = catchValidacion(() -> servicio.buscar("PISCINA", "", "2026-02-30", "0"));

        assertThat(error.getMessage()).isEqualTo("Revisa los datos del formulario.");
        assertThat(error.getErrores()).extracting(ErrorCampoDTO::campo).containsExactly("tipo", "zona", "fecha",
                "pagina");
    }

    @Test
    @DisplayName("HU-10 R29: si la búsqueda no es válida no se llama a la búsqueda ni se calcula la disponibilidad")
    void invalidaNoBusca() {
        assertThatThrownBy(() -> servicio.buscar("CANCHA", "SURCO", "2026-02-30", null))
                .isInstanceOf(ValidacionException.class);

        verifyNoInteractions(busqueda);
        verify(catalogo, never()).publicados(any(), any());
        verify(catalogo, never()).fotosPrincipales(any());
    }

    private static ValidacionException catchValidacion(ThrowingCallable accion) {
        var error = catchThrowable(accion);
        assertThat(error).isInstanceOf(ValidacionException.class);
        return (ValidacionException) error;
    }
}
