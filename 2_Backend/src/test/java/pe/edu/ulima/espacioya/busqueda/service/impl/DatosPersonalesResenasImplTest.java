package pe.edu.ulima.espacioya.busqueda.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.Order;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.busqueda.domain.EstadoResena;
import pe.edu.ulima.espacioya.busqueda.domain.Resena;
import pe.edu.ulima.espacioya.busqueda.dto.ResenaExportadaDTO;
import pe.edu.ulima.espacioya.busqueda.repository.ResenaRepository;
import pe.edu.ulima.espacioya.cuentas.service.puertos.SeccionDatos;
import pe.edu.ulima.espacioya.cuentas.service.puertos.TitularDatos;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;

@ExtendWith(MockitoExtension.class)
class DatosPersonalesResenasImplTest {

    private static final long USUARIO_ID = 7L;
    private static final long ESPACIO_ID = 40L;
    private static final Instant CREADA = Instant.parse("2026-10-29T01:00:00Z");
    private static final TitularDatos USUARIO = new TitularDatos(USUARIO_ID, TitularDatos.ROL_USUARIO);
    private static final TitularDatos ARRENDADOR = new TitularDatos(USUARIO_ID, TitularDatos.ROL_ARRENDADOR);

    @Mock
    private ResenaRepository resenas;
    @Mock
    private CatalogoEspaciosService catalogo;

    private DatosPersonalesResenasImpl participante;

    @BeforeEach
    void preparar() {
        participante = new DatosPersonalesResenasImpl(resenas, catalogo);
    }

    private static Resena resena(long reservaId, int calificacion, String comentario) {
        return Resena.nueva(reservaId, USUARIO_ID, ESPACIO_ID, calificacion, comentario, CREADA);
    }

    @Test
    @DisplayName("HU-24 R39: exporta todas las reseñas del usuario, visibles u ocultas, con el nombre del espacio")
    void exportaLasResenas() {
        var oculta = resena(91L, 2, "Regular");
        ReflectionTestUtils.setField(oculta, "estado", EstadoResena.OCULTA);
        when(resenas.findByUsuarioIdOrderByCreadoEnAscIdAsc(USUARIO_ID))
                .thenReturn(List.of(resena(90L, 5, "Excelente"), oculta));
        when(catalogo.nombres(List.of(ESPACIO_ID))).thenReturn(Map.of(ESPACIO_ID, "Cancha Norte"));

        var dia = LocalDate.of(2026, 10, 28);
        assertThat(participante.exportar(USUARIO)).containsExactly(new SeccionDatos("resenas", List.of(
                new ResenaExportadaDTO(90L, "Cancha Norte", 5, "Excelente", "VISIBLE", dia),
                new ResenaExportadaDTO(91L, "Cancha Norte", 2, "Regular", "OCULTA", dia))));
    }

    @Test
    @DisplayName("HU-24 R39: sin reseñas, la sección resenas igual aparece, vacía")
    void seccionVacia() {
        when(resenas.findByUsuarioIdOrderByCreadoEnAscIdAsc(USUARIO_ID)).thenReturn(List.of());
        when(catalogo.nombres(List.of())).thenReturn(Map.of());

        assertThat(participante.exportar(USUARIO)).containsExactly(new SeccionDatos("resenas", List.of()));
    }

    @Test
    @DisplayName("HU-24 R40: un titular que no es USUARIO no agrega la sección")
    void arrendadorSinSeccion() {
        assertThat(participante.exportar(ARRENDADOR)).isEmpty();

        verifyNoInteractions(resenas, catalogo);
    }

    @Test
    @DisplayName("HU-24 R41: las reseñas nunca impiden eliminar la cuenta")
    void sinImpedimentos() {
        assertThat(participante.impedimentos(USUARIO, CREADA)).isEmpty();
        assertThat(participante.impedimentos(ARRENDADOR, CREADA)).isEmpty();

        verifyNoInteractions(resenas, catalogo);
    }

    @Test
    @DisplayName("HU-24 R42: al eliminar la cuenta reemplaza los comentarios del titular por el mensaje fijo")
    void anonimiza() {
        participante.anonimizar(USUARIO);

        verify(resenas).reemplazarComentarios(USUARIO_ID, "Comentario eliminado.");
    }

    @Test
    @DisplayName("HU-24 R39: el participante va después de reservas y pagos (@Order 3)")
    void vaDespuesDePagos() {
        assertThat(DatosPersonalesResenasImpl.class.getAnnotation(Order.class).value()).isEqualTo(3);
    }
}
