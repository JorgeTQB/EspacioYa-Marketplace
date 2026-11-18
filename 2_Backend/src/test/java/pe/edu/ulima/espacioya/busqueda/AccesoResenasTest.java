package pe.edu.ulima.espacioya.busqueda;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import pe.edu.ulima.espacioya.busqueda.repository.ResenaDAO;
import pe.edu.ulima.espacioya.busqueda.repository.ResenaRepository;
import pe.edu.ulima.espacioya.busqueda.service.PublicacionAuditada;
import pe.edu.ulima.espacioya.busqueda.service.PublicacionResenaBase;
import pe.edu.ulima.espacioya.busqueda.service.PublicacionResenaDecorador;
import pe.edu.ulima.espacioya.busqueda.service.PublicacionSanitizada;
import pe.edu.ulima.espacioya.busqueda.service.PublicacionTransaccional;
import pe.edu.ulima.espacioya.busqueda.service.ResenaService;
import pe.edu.ulima.espacioya.busqueda.service.impl.DatosPersonalesResenasImpl;
import pe.edu.ulima.espacioya.busqueda.service.impl.ModeracionResenasImpl;

/** ★ DAO de HU-24: la tabla {@code resena} solo se toca por {@link ResenaDAO} (R53). */
class AccesoResenasTest {

    @ParameterizedTest
    @ValueSource(classes = {ResenaService.class, PublicacionSanitizada.class, PublicacionTransaccional.class,
            PublicacionAuditada.class, PublicacionResenaBase.class, PublicacionResenaDecorador.class,
            DatosPersonalesResenasImpl.class, ModeracionResenasImpl.class})
    @DisplayName("HU-24 R53: ninguna clase que lee o escribe reseñas tiene un campo ResenaRepository")
    void sinRepositorio(Class<?> clase) {
        assertThat(tiposDeCampos(clase)).doesNotContain(ResenaRepository.class);
    }

    @ParameterizedTest
    @ValueSource(classes = {ResenaService.class, PublicacionResenaBase.class, DatosPersonalesResenasImpl.class,
            ModeracionResenasImpl.class})
    @DisplayName("HU-24 R53: los BusinessObject de reseñas acceden por ResenaDAO")
    void conDao(Class<?> clase) {
        assertThat(tiposDeCampos(clase)).contains(ResenaDAO.class);
    }

    private static List<Class<?>> tiposDeCampos(Class<?> clase) {
        return Arrays.stream(clase.getDeclaredFields()).<Class<?>>map(Field::getType).toList();
    }
}
