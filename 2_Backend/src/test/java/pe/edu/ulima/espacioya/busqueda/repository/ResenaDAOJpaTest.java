package pe.edu.ulima.espacioya.busqueda.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;

import pe.edu.ulima.espacioya.busqueda.domain.EstadoResena;
import pe.edu.ulima.espacioya.busqueda.domain.Resena;
import pe.edu.ulima.espacioya.busqueda.domain.ResumenCalificaciones;

@ExtendWith(MockitoExtension.class)
class ResenaDAOJpaTest {

    private static final long ESPACIO_ID = 40L;
    private static final long USUARIO_ID = 7L;

    @Mock
    private ResenaRepository repositorio;

    private ResenaDAOJpa dao;

    @BeforeEach
    void preparar() {
        dao = new ResenaDAOJpa(repositorio);
    }

    private static TotalesResena totales(long cantidad, long suma) {
        return new TotalesResena() {
            @Override
            public long getCantidad() {
                return cantidad;
            }

            @Override
            public long getSuma() {
                return suma;
            }
        };
    }

    @Test
    @DisplayName("HU-24 R54: la página n pide n - 1, el tamaño y creado_en, id descendentes, solo VISIBLE")
    void paginaConOrdenYFiltro() {
        var resena = Resena.nueva(90L, USUARIO_ID, ESPACIO_ID, 5, "Excelente", Instant.parse("2026-10-28T15:00:00Z"));
        var pedida = ArgumentCaptor.forClass(Pageable.class);
        when(repositorio.findByEspacioIdAndEstado(eq(ESPACIO_ID), eq(EstadoResena.VISIBLE), pedida.capture()))
                .thenReturn(new SliceImpl<>(List.of(resena), PageRequest.of(2, 10), true));

        var trozo = dao.visiblesDeEspacio(ESPACIO_ID, 3, 10);

        assertThat(trozo).isEqualTo(new TrozoResenas(List.of(resena), true));
        assertThat(pedida.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pedida.getValue().getPageSize()).isEqualTo(10);
        assertThat(pedida.getValue().getOffset()).isEqualTo(20);
        assertThat(pedida.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "creadoEn", "id"));
    }

    @Test
    @DisplayName("HU-24 R54: sin más filas, hayMas es false")
    void sinMas() {
        when(repositorio.findByEspacioIdAndEstado(eq(ESPACIO_ID), eq(EstadoResena.VISIBLE), any()))
                .thenReturn(new SliceImpl<>(List.of(), PageRequest.of(0, 10), false));

        assertThat(dao.visiblesDeEspacio(ESPACIO_ID, 1, 10)).isEqualTo(TrozoResenas.VACIO);
    }

    @ParameterizedTest
    @ValueSource(ints = {214748366, Integer.MAX_VALUE})
    @DisplayName("HU-24 R55: si el offset no cabe en un int, página vacía sin consultar la base")
    void offsetEnorme(int pagina) {
        assertThat(dao.visiblesDeEspacio(ESPACIO_ID, pagina, 10)).isEqualTo(TrozoResenas.VACIO);

        verifyNoInteractions(repositorio);
    }

    @Test
    @DisplayName("HU-24 R55: la última página cuyo offset cabe en un int todavía consulta")
    void ultimaQueConsulta() {
        when(repositorio.findByEspacioIdAndEstado(eq(ESPACIO_ID), eq(EstadoResena.VISIBLE), any()))
                .thenReturn(new SliceImpl<>(List.of(), PageRequest.of(214748364, 10), false));

        assertThat(dao.visiblesDeEspacio(ESPACIO_ID, 214748365, 10)).isEqualTo(TrozoResenas.VACIO);

        verify(repositorio).findByEspacioIdAndEstado(eq(ESPACIO_ID), eq(EstadoResena.VISIBLE), any());
    }

    @Test
    @DisplayName("HU-24 R56: el resumen pide los totales solo VISIBLE y los redondea")
    void resumenSoloVisible() {
        when(repositorio.totales(ESPACIO_ID, EstadoResena.VISIBLE)).thenReturn(totales(4, 17));

        assertThat(dao.resumenVisibles(ESPACIO_ID)).isEqualTo(new ResumenCalificaciones(new BigDecimal("4.3"), 4));
    }

    @Test
    @DisplayName("HU-24 R57: guardar hace saveAndFlush, para que la violación salga de la misma llamada")
    void guardarConFlush() {
        var nueva = Resena.nueva(90L, USUARIO_ID, ESPACIO_ID, 4, "Buena", Instant.parse("2026-10-28T15:00:00Z"));
        when(repositorio.saveAndFlush(nueva)).thenReturn(nueva);

        assertThat(dao.guardar(nueva)).isSameAs(nueva);
    }

    @Test
    @DisplayName("HU-24 R53: anonimizarComentarios usa el texto fijo de Resena.COMENTARIO_ELIMINADO")
    void anonimizaConTextoFijo() {
        when(repositorio.reemplazarComentarios(USUARIO_ID, Resena.COMENTARIO_ELIMINADO)).thenReturn(2);

        assertThat(dao.anonimizarComentarios(USUARIO_ID)).isEqualTo(2);
    }
}
