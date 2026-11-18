package pe.edu.ulima.espacioya.busqueda;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import pe.edu.ulima.espacioya.busqueda.controller.ResenaController;
import pe.edu.ulima.espacioya.busqueda.domain.Resena;
import pe.edu.ulima.espacioya.busqueda.dto.CrearResenaDTO;
import pe.edu.ulima.espacioya.busqueda.dto.FichaPublicaDTO;
import pe.edu.ulima.espacioya.busqueda.dto.PaginaResenasDTO;
import pe.edu.ulima.espacioya.busqueda.dto.ResenaCreadaDTO;
import pe.edu.ulima.espacioya.busqueda.dto.ResenaExportadaDTO;
import pe.edu.ulima.espacioya.busqueda.dto.ResenaPublicaDTO;
import pe.edu.ulima.espacioya.busqueda.dto.ResenasPendientesDTO;
import pe.edu.ulima.espacioya.busqueda.service.ModeracionResenas;
import pe.edu.ulima.espacioya.busqueda.service.ResenaService;
import pe.edu.ulima.espacioya.busqueda.service.impl.DatosPersonalesResenasImpl;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;

/**
 * ★ Reapertura de HU-24: Decorator y DAO no cambian el contrato público (R58). Fija rutas, DTO, códigos HTTP, mensajes y
 * las firmas que usan HU-22 y HU-27. El esquema de la base lo sigue fijando {@code ResenaRepositoryIT} sin cambios, y el
 * comportamiento HTTP, {@code ResenaControllerIT}.
 */
class ContratoResenasTest {

    private static List<String> componentes(Class<? extends Record> dto) {
        return Arrays.stream(dto.getRecordComponents())
                .map(componente -> componente.getType().getSimpleName() + " " + componente.getName())
                .toList();
    }

    private static Method metodo(Class<?> clase, String nombre, Class<?>... parametros) throws NoSuchMethodException {
        return clase.getMethod(nombre, parametros);
    }

    @Test
    @DisplayName("HU-24 R58: las rutas y el 201 de ResenaController no cambian")
    void rutasDelControlador() throws NoSuchMethodException {
        var crear = Arrays.stream(ResenaController.class.getMethods()).filter(m -> m.getName().equals("crear"))
                .findFirst().orElseThrow();
        var pendientes = Arrays.stream(ResenaController.class.getMethods())
                .filter(m -> m.getName().equals("pendientes")).findFirst().orElseThrow();
        var deEspacio = metodo(ResenaController.class, "deEspacio", String.class, String.class);

        assertThat(crear.getAnnotation(PostMapping.class).value()).containsExactly("/api/resenas");
        assertThat(crear.getAnnotation(ResponseStatus.class).value()).isEqualTo(HttpStatus.CREATED);
        assertThat(crear.getReturnType()).isEqualTo(ResenaCreadaDTO.class);
        assertThat(pendientes.getAnnotation(GetMapping.class).value()).containsExactly("/api/resenas/pendientes");
        assertThat(pendientes.getReturnType()).isEqualTo(ResenasPendientesDTO.class);
        assertThat(deEspacio.getAnnotation(GetMapping.class).value()).containsExactly("/api/espacios/{id}/resenas");
        assertThat(deEspacio.getReturnType()).isEqualTo(PaginaResenasDTO.class);
    }

    @Test
    @DisplayName("HU-24 R58: los DTO de reseñas conservan sus campos, en el mismo orden y tipo")
    void camposDeLosDto() {
        assertThat(componentes(CrearResenaDTO.class))
                .containsExactly("Long reservaId", "Integer calificacion", "String comentario");
        assertThat(componentes(ResenaCreadaDTO.class))
                .containsExactly("Long id", "Long reservaId", "int calificacion", "String comentario", "LocalDate fecha");
        assertThat(componentes(ResenasPendientesDTO.class)).containsExactly("List reservaIds");
        assertThat(componentes(ResenaPublicaDTO.class))
                .containsExactly("Long id", "int calificacion", "String comentario", "LocalDate fecha");
        assertThat(componentes(PaginaResenasDTO.class)).containsExactly("int pagina", "List resenas", "boolean hayMas");
        assertThat(componentes(ResenaExportadaDTO.class)).containsExactly("Long reservaId", "String espacioNombre",
                "int calificacion", "String comentario", "String estado", "LocalDate fecha");
        var ficha = FichaPublicaDTO.class.getRecordComponents();
        assertThat(Arrays.stream(ficha).map(RecordComponent::getName).toList())
                .endsWith("calificacionPromedio", "totalResenas");
        assertThat(ficha[ficha.length - 2].getType()).isEqualTo(BigDecimal.class);
        assertThat(ficha[ficha.length - 1].getType()).isEqualTo(long.class);
        assertThat(ResenaCreadaDTO.class.getRecordComponents()[4].getType()).isEqualTo(LocalDate.class);
    }

    @Test
    @DisplayName("HU-24 R58: los mensajes de reseñas son los mismos textos")
    void mensajes() {
        assertThat(ResenaService.MENSAJE_NO_RESENABLE)
                .isEqualTo("Solo puedes reseñar reservas confirmadas que ya pasaron.");
        assertThat(ResenaService.MENSAJE_PLAZO_VENCIDO)
                .isEqualTo("El plazo de 14 días para reseñar esta reserva ya terminó.");
        assertThat(ResenaService.MENSAJE_YA_RESENADA).isEqualTo("Ya reseñaste esta reserva.");
        assertThat(CrearResenaDTO.M_RESERVA).isEqualTo("Elige una reserva válida.");
        assertThat(CrearResenaDTO.M_CALIFICACION).isEqualTo("Elige una calificación de 1 a 5.");
        assertThat(CrearResenaDTO.M_COMENTARIO).isEqualTo("Escribe un comentario de hasta 500 caracteres.");
        assertThat(Resena.COMENTARIO_ELIMINADO).isEqualTo("Comentario eliminado.");
        assertThat(ResenaService.ACCION_RESENA_CREADA).isEqualTo("RESENA_CREADA");
        assertThat(ResenaService.TAMANO_PAGINA).isEqualTo(10);
    }

    @Test
    @DisplayName("HU-24 R58: las firmas públicas que usan el controlador, HU-22 y HU-27 no cambian")
    void firmasPublicas() throws NoSuchMethodException {
        assertThat(metodo(ResenaService.class, "crear", UsuarioAutenticado.class, CrearResenaDTO.class, String.class)
                .getReturnType()).isEqualTo(ResenaCreadaDTO.class);
        assertThat(metodo(ResenaService.class, "pendientes", UsuarioAutenticado.class).getReturnType())
                .isEqualTo(ResenasPendientesDTO.class);
        assertThat(metodo(ResenaService.class, "deEspacio", String.class, String.class).getReturnType())
                .isEqualTo(PaginaResenasDTO.class);
        assertThat(metodo(ModeracionResenas.class, "reportable", Long.class).getReturnType())
                .isEqualTo(Optional.class);
        assertThat(metodo(ModeracionResenas.class, "describir", Collection.class).getReturnType())
                .isEqualTo(Map.class);
        assertThat(metodo(ModeracionResenas.class, "ocultar", UsuarioAutenticado.class, Long.class, String.class)
                .getReturnType()).isEqualTo(void.class);
        assertThat(DatosPersonalesResenasImpl.SECCION_RESENAS).isEqualTo("resenas");
    }
}
