package pe.edu.ulima.espacioya.busqueda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.edu.ulima.espacioya.busqueda.domain.ResumenCalificaciones;
import pe.edu.ulima.espacioya.busqueda.dto.BloqueLibreDTO;
import pe.edu.ulima.espacioya.busqueda.dto.DisponibilidadPublicaDTO;
import pe.edu.ulima.espacioya.busqueda.dto.FichaPublicaDTO;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.espacios.service.DisponibilidadService;
import pe.edu.ulima.espacioya.espacios.service.FichaPublicada;

@ExtendWith(MockitoExtension.class)
class FichaServiceTest {

    // 10:00 del miércoles 2026-10-28 en Lima.
    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 28);
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 2);

    private static final FichaPublicada FICHA = new FichaPublicada(12L, "Cancha Los Olivos", "CANCHA",
            "Av. Universitaria 1234, Los Olivos", "LOS_OLIVOS", "Los Olivos", 22, new BigDecimal("80.00"),
            "Cancha de grass sintético con iluminación.", List.of("/api/fotos/a", "/api/fotos/b"));

    @Mock
    private CatalogoEspaciosService catalogo;
    @Mock
    private DisponibilidadService disponibilidad;
    @Mock
    private ResenaService resenas;

    private FichaService servicio;

    @BeforeEach
    void preparar() {
        servicio = new FichaService(catalogo, disponibilidad, resenas, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private ReglaNegocioException errorDeFecha(String fecha) {
        when(catalogo.esVisible(12L)).thenReturn(true);
        var error = catchThrowableOfType(ReglaNegocioException.class, () -> servicio.disponibilidad("12", fecha));
        verifyNoInteractions(disponibilidad);
        return error;
    }

    @Test
    @DisplayName("HU-11 R1: la ficha de un espacio visible copia todos los datos de FichaPublicada al DTO")
    void fichaDeUnEspacioVisible() {
        when(catalogo.publicado(12L)).thenReturn(Optional.of(FICHA));
        when(resenas.resumen(12L)).thenReturn(new ResumenCalificaciones(null, 0));

        assertThat(servicio.ficha("12")).isEqualTo(new FichaPublicaDTO(12L, "Cancha Los Olivos", "CANCHA",
                "Av. Universitaria 1234, Los Olivos", "LOS_OLIVOS", "Los Olivos", 22, new BigDecimal("80.00"),
                "Cancha de grass sintético con iluminación.", List.of("/api/fotos/a", "/api/fotos/b"), null, 0));
    }

    @Test
    @DisplayName("HU-24 R27: la ficha de un espacio visible lleva el promedio y el total de sus reseñas")
    void fichaConResumen() {
        when(catalogo.publicado(12L)).thenReturn(Optional.of(FICHA));
        when(resenas.resumen(12L)).thenReturn(new ResumenCalificaciones(new BigDecimal("4.3"), 4));

        var ficha = servicio.ficha("12");

        assertThat(ficha.calificacionPromedio()).isEqualTo(new BigDecimal("4.3"));
        assertThat(ficha.totalResenas()).isEqualTo(4);
    }

    @Test
    @DisplayName("HU-24 R29: sin reseñas visibles, calificacionPromedio es nulo y totalResenas 0")
    void fichaSinResenas() {
        when(catalogo.publicado(12L)).thenReturn(Optional.of(FICHA));
        when(resenas.resumen(12L)).thenReturn(new ResumenCalificaciones(null, 0));

        var ficha = servicio.ficha("12");

        assertThat(ficha.calificacionPromedio()).isNull();
        assertThat(ficha.totalResenas()).isZero();
    }

    @Test
    @DisplayName("HU-11 R1: acepta el id más grande que cabe en un long")
    void aceptaElIdMaximo() {
        when(catalogo.publicado(Long.MAX_VALUE)).thenReturn(Optional.of(FICHA));
        when(resenas.resumen(Long.MAX_VALUE)).thenReturn(new ResumenCalificaciones(null, 0));

        assertThat(servicio.ficha(String.valueOf(Long.MAX_VALUE))).isNotNull();
    }

    @Test
    @DisplayName("HU-11 R9: un espacio no visible (catálogo vacío) responde 404")
    void fichaNoVisible() {
        when(catalogo.publicado(13L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.ficha("13")).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(resenas);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "0", "00", "-1", "+1", "1.5", "007a", " 7", "7 ", "1e3", "١٢",
            "99999999999999999999", "9223372036854775808"})
    @DisplayName("HU-11 R11: un id mal escrito en la ficha responde 404 sin consultar el catálogo")
    void fichaConIdMalEscrito(String id) {
        assertThatThrownBy(() -> servicio.ficha(id)).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(catalogo, disponibilidad);
    }

    @Test
    @DisplayName("HU-11 R13, R14: las horas libres salen como bloques de una hora en orden ascendente")
    void bloquesLibresDelDia() {
        when(catalogo.esVisible(12L)).thenReturn(true);
        when(disponibilidad.horasLibres(12L, LUNES)).thenReturn(List.of(8, 9, 14));

        assertThat(servicio.disponibilidad("12", "2026-11-02")).isEqualTo(new DisponibilidadPublicaDTO(LUNES,
                List.of(new BloqueLibreDTO(8, 9), new BloqueLibreDTO(9, 10), new BloqueLibreDTO(14, 15))));
    }

    @Test
    @DisplayName("HU-11 R13: hoy y hoy + 55 están dentro de la ventana")
    void bordesDeLaVentana() {
        when(catalogo.esVisible(12L)).thenReturn(true);
        when(disponibilidad.horasLibres(12L, HOY)).thenReturn(List.of(11));
        when(disponibilidad.horasLibres(12L, HOY.plusDays(55))).thenReturn(List.of());

        assertThat(servicio.disponibilidad("12", HOY.toString()).fecha()).isEqualTo(HOY);
        assertThat(servicio.disponibilidad("12", HOY.plusDays(55).toString()).fecha()).isEqualTo(HOY.plusDays(55));
    }

    @Test
    @DisplayName("HU-11 R15, R18: sin horas libres (día sin horario o bloqueado) la lista sale vacía")
    void sinHorasLibres() {
        when(catalogo.esVisible(12L)).thenReturn(true);
        when(disponibilidad.horasLibres(12L, LUNES)).thenReturn(List.of());

        assertThat(servicio.disponibilidad("12", "2026-11-02").bloquesLibres()).isEmpty();
    }

    @Test
    @DisplayName("HU-11 R20: un espacio no visible en la disponibilidad responde 404 sin calcularla")
    void disponibilidadDeEspacioNoVisible() {
        when(catalogo.esVisible(13L)).thenReturn(false);

        assertThatThrownBy(() -> servicio.disponibilidad("13", "2026-11-02"))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(disponibilidad);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "1.5", "99999999999999999999"})
    @DisplayName("HU-11 R20: un id mal escrito en la disponibilidad responde 404 sin consultar nada")
    void disponibilidadConIdMalEscrito(String id) {
        assertThatThrownBy(() -> servicio.disponibilidad(id, "2026-11-02"))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(catalogo, disponibilidad);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "2026-02-30", "02/11/2026", "2026-11-2", "mañana"})
    @DisplayName("HU-11 R21: una fecha ausente, vacía o mal escrita responde 400 en fecha con 'fecha inválida'")
    void fechaInvalida(String fecha) {
        var error = errorDeFecha(fecha);

        assertThat(error.getCampo()).isEqualTo("fecha").isEqualTo(BusquedaService.CAMPO_FECHA);
        assertThat(error.getMessage()).isEqualTo("Elige una fecha válida.")
                .isEqualTo(BusquedaService.MENSAJE_FECHA_INVALIDA);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-27", "2026-12-23", "2027-03-01"})
    @DisplayName("HU-11 R22: una fecha antes de hoy o después de hoy + 55 responde 400 en fecha con 'fuera de ventana'")
    void fechaFueraDeVentana(String fecha) {
        var error = errorDeFecha(fecha);

        assertThat(error.getCampo()).isEqualTo(BusquedaService.CAMPO_FECHA);
        assertThat(error.getMessage()).isEqualTo("Elige una fecha entre hoy y las próximas 8 semanas.")
                .isEqualTo(BusquedaService.MENSAJE_FUERA_DE_VENTANA);
    }

    @Test
    @DisplayName("HU-11 R23: un espacio no visible con una fecha inválida responde 404, no 400")
    void noVisibleYFechaInvalida() {
        when(catalogo.esVisible(13L)).thenReturn(false);

        assertThatThrownBy(() -> servicio.disponibilidad("13", "2026-02-30"))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.disponibilidad("abc", null))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(disponibilidad);
    }
}
