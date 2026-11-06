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
import java.util.Set;
import java.util.stream.LongStream;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.edu.ulima.espacioya.busqueda.dto.ResultadoBusquedaDTO;
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
    private static final Set<String> CARACTERISTICAS =
            Set.of("TECHADO", "ILUMINACION", "VESTUARIOS", "ESTACIONAMIENTO");

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
        lenient().when(catalogo.esCaracteristicaValida(any()))
                .thenAnswer(i -> CARACTERISTICAS.contains(i.<String>getArgument(0)));
    }

    private static EspacioPublicado publicado(long id) {
        return new EspacioPublicado(id, "Cancha " + id, "CANCHA", "SURCO", "Santiago de Surco",
                new BigDecimal(String.valueOf(10 + id)), 20, Set.of());
    }

    private ResultadoBusquedaDTO buscar(String tipo, String zona, String fecha, String pagina) {
        return servicio.buscar(new ConsultaBusqueda(tipo, zona, fecha, pagina, null, null, List.of()));
    }

    private static List<EspacioPublicado> disponibles(int cuantos) {
        return LongStream.rangeClosed(1, cuantos).mapToObj(BusquedaServiceTest::publicado).toList();
    }

    private List<ErrorCampoDTO> errores(String tipo, String zona, String fecha, String pagina) {
        return catchValidacion(() -> buscar(tipo, zona, fecha, pagina)).getErrores();
    }

    @Test
    @DisplayName("HU-10 R18, R20: con 45 disponibles, las páginas 1, 2 y 3 traen 20, 20 y 5 con hayMas correcto")
    void paginasDeVeinte() {
        when(busqueda.buscar(CRITERIOS)).thenReturn(disponibles(45));
        when(catalogo.fotosPrincipales(any())).thenReturn(Map.of());

        var primera = buscar("CANCHA", "SURCO", FECHA, "1");
        var segunda = buscar("CANCHA", "SURCO", FECHA, "2");
        var tercera = buscar("CANCHA", "SURCO", FECHA, "3");

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

        var segunda = buscar("CANCHA", "SURCO", FECHA, "2");

        assertThat(segunda.resultados()).hasSize(20);
        assertThat(segunda.hayMas()).isFalse();
    }

    @Test
    @DisplayName("HU-10 R21: una página posterior a la última responde vacía, sin hayMas y con la página pedida")
    void paginaPosteriorALaUltima() {
        when(busqueda.buscar(CRITERIOS)).thenReturn(disponibles(45));

        var cuarta = buscar("CANCHA", "SURCO", FECHA, "4");
        var lejana = buscar("CANCHA", "SURCO", FECHA, String.valueOf(Integer.MAX_VALUE));

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

        var resultado = buscar("CANCHA", "SURCO", FECHA, null);

        assertThat(resultado.pagina()).isEqualTo(1);
        assertThat(resultado.resultados()).hasSize(20);
        assertThat(resultado.hayMas()).isTrue();
    }

    @Test
    @DisplayName("HU-10 R11: sin disponibles responde resultados vacío y hayMas false")
    void sinDisponibles() {
        when(busqueda.buscar(CRITERIOS)).thenReturn(List.of());

        var resultado = buscar("CANCHA", "SURCO", FECHA, null);

        assertThat(resultado.resultados()).isEmpty();
        assertThat(resultado.hayMas()).isFalse();
        assertThat(resultado.pagina()).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-10 R10, R14, R15, R16: conserva el orden recibido y pide las fotos solo de los ids de la página")
    void ordenYFotosDeLaPagina() {
        when(busqueda.buscar(CRITERIOS)).thenReturn(List.of(publicado(7), publicado(3), publicado(9)));
        when(catalogo.fotosPrincipales(List.of(7L, 3L, 9L))).thenReturn(Map.of(7L, "/api/fotos/a", 9L, "/api/fotos/c"));

        var resultado = buscar("CANCHA", "SURCO", FECHA, "1");

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

        assertThat(buscar("CANCHA", "SURCO", FECHA, "2").resultados()).hasSize(20);
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

        assertThat(buscar("CANCHA", "SURCO", HOY.toString(), null).resultados()).isEmpty();
        assertThat(buscar("CANCHA", "SURCO", HOY.plusDays(55).toString(), null).resultados()).isEmpty();
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
        var error = catchValidacion(() -> buscar("PISCINA", "", "2026-02-30", "0"));

        assertThat(error.getMessage()).isEqualTo("Revisa los datos del formulario.");
        assertThat(error.getErrores()).extracting(ErrorCampoDTO::campo).containsExactly("tipo", "zona", "fecha",
                "pagina");
    }

    @Test
    @DisplayName("HU-10 R29: si la búsqueda no es válida no se llama a la búsqueda ni se calcula la disponibilidad")
    void invalidaNoBusca() {
        assertThatThrownBy(() -> buscar("CANCHA", "SURCO", "2026-02-30", null))
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

    // ── Filtros (HU-23) ──────────────────────────────────────────────────

    private static EspacioPublicado publicado(long id, String precio, int capacidad, String... caracteristicas) {
        return new EspacioPublicado(id, "Cancha " + id, "CANCHA", "SURCO", "Santiago de Surco", new BigDecimal(precio),
                capacidad, Set.of(caracteristicas));
    }

    private ResultadoBusquedaDTO filtrar(String pagina, String precioMaximo, String capacidadMinima,
                                         String... caracteristicas) {
        return servicio.buscar(new ConsultaBusqueda("CANCHA", "SURCO", FECHA, pagina, precioMaximo, capacidadMinima,
                List.of(caracteristicas)));
    }

    private List<ErrorCampoDTO> erroresDeFiltros(String precioMaximo, String capacidadMinima,
                                                 String... caracteristicas) {
        return catchValidacion(() -> filtrar(null, precioMaximo, capacidadMinima, caracteristicas)).getErrores();
    }

    @Test
    @DisplayName("HU-23 R12, R13, R14, R15: con los tres filtros devuelve solo los disponibles que cumplen todos")
    void tresFiltrosCumplenTodo() {
        when(busqueda.buscar(CRITERIOS)).thenReturn(List.of(
                publicado(1, "40.00", 12, "TECHADO", "VESTUARIOS"),
                publicado(2, "60.00", 30, "TECHADO", "VESTUARIOS", "ILUMINACION"),
                publicado(3, "80.00", 10, "TECHADO", "VESTUARIOS"),
                publicado(4, "80.01", 50, "TECHADO", "VESTUARIOS"),
                publicado(5, "50.00", 9, "TECHADO", "VESTUARIOS"),
                publicado(6, "50.00", 40, "TECHADO")));
        when(catalogo.fotosPrincipales(any())).thenReturn(Map.of());

        var resultado = filtrar(null, "80", "10", "VESTUARIOS", "TECHADO");

        assertThat(resultado.resultados()).extracting(ResultadoEspacioDTO::id).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("HU-23 R17: pagina sobre la lista filtrada: de 25 candidatos cumplen 21, páginas de 20 y 1")
    void paginaSobreLaListaFiltrada() {
        var candidatos = LongStream.rangeClosed(1, 25)
                .mapToObj(id -> publicado(id, String.valueOf(10 + id), id % 6 == 0 ? 5 : 30))
                .toList();
        when(busqueda.buscar(CRITERIOS)).thenReturn(candidatos);
        when(catalogo.fotosPrincipales(any())).thenReturn(Map.of());
        var cumplen = candidatos.stream().filter(e -> e.capacidad() >= 10).map(EspacioPublicado::id).toList();
        assertThat(cumplen).hasSize(21);

        var primera = filtrar("1", null, "10");
        var segunda = filtrar("2", null, "10");

        assertThat(primera.resultados()).extracting(ResultadoEspacioDTO::id)
                .containsExactlyElementsOf(cumplen.subList(0, 20));
        assertThat(primera.hayMas()).isTrue();
        assertThat(segunda.resultados()).extracting(ResultadoEspacioDTO::id).containsExactly(cumplen.get(20));
        assertThat(segunda.hayMas()).isFalse();
    }

    @Test
    @DisplayName("HU-23 R19: si ninguno cumple los filtros responde vacío, sin hayMas y con la página pedida")
    void ningunoCumpleLosFiltros() {
        when(busqueda.buscar(CRITERIOS)).thenReturn(disponibles(30));

        var resultado = filtrar("1", "5.00", null, "ESTACIONAMIENTO");

        assertThat(resultado.resultados()).isEmpty();
        assertThat(resultado.hayMas()).isFalse();
        assertThat(resultado.pagina()).isEqualTo(1);
        verify(catalogo, never()).fotosPrincipales(any());
    }

    @Test
    @DisplayName("HU-23 R16: sin filtros busca con la base misma, sin envolverla")
    void sinFiltrosUsaLaBase() {
        when(busqueda.buscar(CRITERIOS)).thenReturn(disponibles(3));
        when(catalogo.fotosPrincipales(any())).thenReturn(Map.of());

        assertThat(filtrar(null, null, null).resultados()).hasSize(3);
        verify(busqueda).buscar(CRITERIOS);
    }

    @Test
    @DisplayName("HU-23 R12, R13: los límites son inclusivos y se aceptan los bordes del rango")
    void bordesValidos() {
        when(busqueda.buscar(CRITERIOS)).thenReturn(List.of(publicado(1, "1.00", 100_000),
                publicado(2, "100000.00", 1)));
        when(catalogo.fotosPrincipales(any())).thenReturn(Map.of());

        assertThat(filtrar(null, "1", null).resultados()).extracting(ResultadoEspacioDTO::id).containsExactly(1L);
        assertThat(filtrar(null, "100000.00", "100000").resultados()).extracting(ResultadoEspacioDTO::id)
                .containsExactly(1L);
        assertThat(filtrar(null, "100000", "1").resultados()).extracting(ResultadoEspacioDTO::id)
                .containsExactly(1L, 2L);
        assertThat(filtrar(null, "1.5", null).resultados()).extracting(ResultadoEspacioDTO::id).containsExactly(1L);
    }

    @Test
    @DisplayName("HU-23 R20: un precio máximo vacío, fuera de rango o mal escrito responde 400 en precioMaximo")
    void precioMaximoInvalido() {
        for (var precio : new String[] {"", "0", "0.99", "100000.01", "12.345", "1e3", "-5", "abc", " 5", "+5",
                "1000000", "5.", ".5", "50,60"}) {
            assertThat(erroresDeFiltros(precio, null)).as("precioMaximo=%s", precio)
                    .containsExactly(new ErrorCampoDTO("precioMaximo", BusquedaService.MENSAJE_PRECIO_MAXIMO_INVALIDO));
        }
        verifyNoInteractions(busqueda);
    }

    @Test
    @DisplayName("HU-23 R21: una capacidad mínima vacía, fuera de rango o no entera responde 400 en capacidadMinima")
    void capacidadMinimaInvalida() {
        for (var capacidad : new String[] {"", "0", "100001", "2.5", "-1", "abc", "+3", " 3", "1000000"}) {
            assertThat(erroresDeFiltros(null, capacidad)).as("capacidadMinima=%s", capacidad)
                    .containsExactly(new ErrorCampoDTO("capacidadMinima",
                            BusquedaService.MENSAJE_CAPACIDAD_MINIMA_INVALIDA));
        }
        verifyNoInteractions(busqueda);
    }

    @Test
    @DisplayName("HU-23 R22: una característica fuera de la lista fija responde 400 en caracteristica")
    void caracteristicaInvalida() {
        for (var caracteristica : new String[] {"", "techado", "PISCINA", "TECHADO,VESTUARIOS", " TECHADO"}) {
            assertThat(erroresDeFiltros(null, null, "ILUMINACION", caracteristica))
                    .as("caracteristica=%s", caracteristica)
                    .containsExactly(new ErrorCampoDTO("caracteristica",
                            BusquedaService.MENSAJE_CARACTERISTICA_INVALIDA));
        }
        verifyNoInteractions(busqueda);
    }

    @Test
    @DisplayName("HU-23 R23: la misma característica repetida responde 400 en caracteristica")
    void caracteristicaRepetida() {
        assertThat(erroresDeFiltros(null, null, "TECHADO", "VESTUARIOS", "TECHADO"))
                .containsExactly(new ErrorCampoDTO("caracteristica", BusquedaService.MENSAJE_CARACTERISTICA_INVALIDA));
        verifyNoInteractions(busqueda);
    }

    @Test
    @DisplayName("HU-23 R24: con todo inválido hay un error por campo en orden tipo, zona, fecha, pagina, "
            + "precioMaximo, capacidadMinima, caracteristica")
    void erroresDeFiltrosEnOrden() {
        var error = catchValidacion(() -> servicio.buscar(new ConsultaBusqueda("PISCINA", "", "2026-02-30", "0",
                "0", "abc", List.of("PISCINA", "techado", "TECHADO", "TECHADO"))));

        assertThat(error.getMessage()).isEqualTo("Revisa los datos del formulario.");
        assertThat(error.getErrores()).extracting(ErrorCampoDTO::campo).containsExactly("tipo", "zona", "fecha",
                "pagina", "precioMaximo", "capacidadMinima", "caracteristica");
    }

    @Test
    @DisplayName("HU-23 R25: con un filtro inválido no se llama a la búsqueda ni se calcula la disponibilidad")
    void filtroInvalidoNoBusca() {
        for (var consulta : List.of(
                new ConsultaBusqueda("CANCHA", "SURCO", FECHA, null, "0", null, List.of()),
                new ConsultaBusqueda("CANCHA", "SURCO", FECHA, null, null, "0", List.of()),
                new ConsultaBusqueda("CANCHA", "SURCO", FECHA, null, "50", "10", List.of("PISCINA")))) {
            assertThatThrownBy(() -> servicio.buscar(consulta)).isInstanceOf(ValidacionException.class);
        }

        verifyNoInteractions(busqueda);
        verify(catalogo, never()).publicados(any(), any());
        verify(catalogo, never()).fotosPrincipales(any());
    }
}
