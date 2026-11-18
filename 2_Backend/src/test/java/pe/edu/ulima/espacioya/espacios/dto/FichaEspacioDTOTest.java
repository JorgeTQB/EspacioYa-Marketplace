package pe.edu.ulima.espacioya.espacios.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import pe.edu.ulima.espacioya.espacios.domain.Caracteristica;
import pe.edu.ulima.espacioya.espacios.domain.TipoEspacio;

class FichaEspacioDTOTest {

    private static final String DESCRIPCION = "Cancha de grass sintético techada.";

    private static ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void crearValidador() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrarValidador() {
        fabrica.close();
    }

    private static FichaEspacioDTO ficha(String nombre, String tipo, String direccion, String zona, Integer capacidad,
                                         BigDecimal precio, String descripcion) {
        return new FichaEspacioDTO(nombre, tipo, direccion, zona, capacidad, precio, descripcion);
    }

    private static FichaEspacioDTO valida() {
        return ficha("Cancha Los Olivos", "CANCHA", "Av. Universitaria 1234", "LOS_OLIVOS", 20,
                new BigDecimal("80.00"), DESCRIPCION);
    }

    private static Map<String, String> errores(FichaEspacioDTO ficha) {
        return validador.validate(ficha).stream().collect(Collectors.toMap(
                v -> v.getPropertyPath().toString(), ConstraintViolation::getMessage, (a, b) -> a));
    }

    private static FichaEspacioDTO conNombre(String nombre) {
        var v = valida();
        return ficha(nombre, v.tipo(), v.direccion(), v.zona(), v.capacidad(), v.precioHora(), v.descripcion());
    }

    private static FichaEspacioDTO conTipo(String tipo) {
        var v = valida();
        return ficha(v.nombre(), tipo, v.direccion(), v.zona(), v.capacidad(), v.precioHora(), v.descripcion());
    }

    private static FichaEspacioDTO conDireccion(String direccion) {
        var v = valida();
        return ficha(v.nombre(), v.tipo(), direccion, v.zona(), v.capacidad(), v.precioHora(), v.descripcion());
    }

    private static FichaEspacioDTO conZona(String zona) {
        var v = valida();
        return ficha(v.nombre(), v.tipo(), v.direccion(), zona, v.capacidad(), v.precioHora(), v.descripcion());
    }

    private static FichaEspacioDTO conCapacidad(Integer capacidad) {
        var v = valida();
        return ficha(v.nombre(), v.tipo(), v.direccion(), v.zona(), capacidad, v.precioHora(), v.descripcion());
    }

    private static FichaEspacioDTO conPrecio(String precio) {
        var v = valida();
        return ficha(v.nombre(), v.tipo(), v.direccion(), v.zona(), v.capacidad(),
                precio == null ? null : new BigDecimal(precio), v.descripcion());
    }

    private static FichaEspacioDTO conDescripcion(String descripcion) {
        var v = valida();
        return ficha(v.nombre(), v.tipo(), v.direccion(), v.zona(), v.capacidad(), v.precioHora(), descripcion);
    }

    @Test
    @DisplayName("HU-07 R4: una ficha completa y dentro de los límites es válida")
    void fichaValida() {
        assertThat(errores(valida())).isEmpty();
    }

    @Test
    @DisplayName("HU-07 R4: nombre de 3 y 100 caracteres válido; vacío, 2 o 101 caracteres inválido")
    void nombre() {
        assertThat(errores(conNombre("abc"))).isEmpty();
        assertThat(errores(conNombre("a".repeat(100)))).isEmpty();
        for (var nombre : new String[] {null, "", "   ", "ab", "a".repeat(101)}) {
            assertThat(errores(conNombre(nombre))).containsExactly(Map.entry("nombre", FichaEspacioDTO.M_NOMBRE));
        }
    }

    @Test
    @DisplayName("HU-07 R4: el nombre se recorta antes de validar y queda recortado")
    void nombreRecortado() {
        assertThat(errores(conNombre("  ab  "))).containsOnlyKeys("nombre");
        var ficha = conNombre("  Cancha Sur  ");
        assertThat(ficha.nombre()).isEqualTo("Cancha Sur");
        assertThat(errores(ficha)).isEmpty();
    }

    @Test
    @DisplayName("HU-07 R4: tipo CANCHA, ESTADIO o AUDITORIO; ausente u otro valor inválido")
    void tipo() {
        for (var tipo : TipoEspacio.values()) {
            assertThat(errores(conTipo(tipo.name()))).isEmpty();
        }
        for (var tipo : new String[] {null, "", "PISCINA", "cancha"}) {
            assertThat(errores(conTipo(tipo))).containsExactly(Map.entry("tipo", FichaEspacioDTO.M_TIPO));
        }
    }

    @Test
    @DisplayName("HU-07 R4: dirección de 5 y 200 caracteres válida; vacía, 4 o 201 caracteres inválida")
    void direccion() {
        assertThat(errores(conDireccion("Av. 1"))).isEmpty();
        assertThat(errores(conDireccion("d".repeat(200)))).isEmpty();
        for (var direccion : new String[] {null, " ", "Av 1", "  Av 1  ", "d".repeat(201)}) {
            assertThat(errores(conDireccion(direccion)))
                    .containsExactly(Map.entry("direccion", FichaEspacioDTO.M_DIRECCION));
        }
    }

    @Test
    @DisplayName("HU-07 R4: zona ausente, vacía o de más de 40 caracteres es inválida")
    void zona() {
        assertThat(errores(conZona("z".repeat(40)))).isEmpty();
        for (var zona : new String[] {null, "", "   ", "z".repeat(41)}) {
            assertThat(errores(conZona(zona))).containsExactly(Map.entry("zona", FichaEspacioDTO.M_ZONA));
        }
        assertThat(conZona("  MIRAFLORES ").zona()).isEqualTo("MIRAFLORES");
    }

    @Test
    @DisplayName("HU-07 R4: capacidad de 1 a 100 000; ausente, 0 o 100 001 inválida")
    void capacidad() {
        assertThat(errores(conCapacidad(1))).isEmpty();
        assertThat(errores(conCapacidad(100_000))).isEmpty();
        for (var capacidad : new Integer[] {null, 0, -5, 100_001}) {
            assertThat(errores(conCapacidad(capacidad)))
                    .containsExactly(Map.entry("capacidad", FichaEspacioDTO.M_CAPACIDAD));
        }
    }

    private static FichaEspacioDTO leer(String capacidad) {
        var cuerpo = """
                {"nombre":"Cancha Los Olivos","tipo":"CANCHA","direccion":"Av. Universitaria 1234",
                 "zona":"LOS_OLIVOS","capacidad":%s,"precioHora":80,"descripcion":"%s"}
                """.formatted(capacidad, DESCRIPCION);
        return JsonMapper.builder().build().readValue(cuerpo, FichaEspacioDTO.class);
    }

    @Test
    @DisplayName("HU-07 R4: la capacidad del JSON solo se acepta como número entero, sin truncar decimales")
    void capacidadDesdeJson() {
        assertThat(leer("20").capacidad()).isEqualTo(20);
        assertThat(errores(leer("100000"))).isEmpty();
        for (var capacidad : new String[] {"20.7", "0.5", "20.0", "1e2", "\"20\"", "true", "{\"v\":20}", "[20]",
                "3000000000", "null"}) {
            var ficha = leer(capacidad);
            assertThat(ficha.capacidad()).as(capacidad).isNull();
            assertThat(errores(ficha)).containsExactly(Map.entry("capacidad", FichaEspacioDTO.M_CAPACIDAD));
        }
    }

    @Test
    @DisplayName("HU-07 R4, R24: precio de 1.00 a 100000.00 con hasta 2 decimales")
    void precio() {
        for (var precio : new String[] {"1.00", "1", "80.5", "100000.00"}) {
            assertThat(errores(conPrecio(precio))).isEmpty();
        }
        for (var precio : new String[] {null, "0.99", "0", "-10", "100000.01", "80.555", "1000000"}) {
            assertThat(errores(conPrecio(precio)))
                    .containsExactly(Map.entry("precioHora", FichaEspacioDTO.M_PRECIO));
        }
    }

    @Test
    @DisplayName("HU-07 R4: descripción ausente, vacía, en blanco, de 9 o de 2001 caracteres inválida")
    void descripcionInvalida() {
        for (var descripcion : new String[] {null, "", "     ", "d".repeat(9), "  " + "d".repeat(9) + "  ",
                "d".repeat(2001)}) {
            assertThat(errores(conDescripcion(descripcion)))
                    .containsExactly(Map.entry("descripcion", FichaEspacioDTO.M_DESCRIPCION));
        }
    }

    @Test
    @DisplayName("HU-07 R4: descripción de 10 y 2000 caracteres válida y recortada")
    void descripcionValida() {
        assertThat(errores(conDescripcion("d".repeat(10)))).isEmpty();
        assertThat(errores(conDescripcion("d".repeat(2000)))).isEmpty();
        assertThat(conDescripcion("  " + DESCRIPCION + "  ").descripcion()).isEqualTo(DESCRIPCION);
    }

    @Test
    @DisplayName("HU-07 R4: con varios campos inválidos hay un error por cada campo")
    void variosCampos() {
        var ficha = ficha("ab", "PISCINA", "Av 1", "", 0, new BigDecimal("0.50"), "corta");

        assertThat(errores(ficha)).containsOnlyKeys("nombre", "tipo", "direccion", "zona", "capacidad", "precioHora",
                "descripcion");
    }

    @Test
    @DisplayName("HU-07 R3: aDatos convierte el tipo y conserva los valores recortados")
    void aDatos() {
        var datos = ficha(" Cancha Sur ", "ESTADIO", " Av. Arequipa 100 ", " MIRAFLORES ", 50,
                new BigDecimal("120.50"), " " + DESCRIPCION + " ").aDatos();

        assertThat(datos.nombre()).isEqualTo("Cancha Sur");
        assertThat(datos.tipo()).isEqualTo(TipoEspacio.ESTADIO);
        assertThat(datos.direccion()).isEqualTo("Av. Arequipa 100");
        assertThat(datos.zona()).isEqualTo("MIRAFLORES");
        assertThat(datos.capacidad()).isEqualTo(50);
        assertThat(datos.precioHora()).isEqualByComparingTo("120.50");
        assertThat(datos.descripcion()).isEqualTo(DESCRIPCION);
    }

    private static FichaEspacioDTO conCaracteristicas(List<String> caracteristicas) {
        var v = valida();
        return new FichaEspacioDTO(v.nombre(), v.tipo(), v.direccion(), v.zona(), v.capacidad(), v.precioHora(),
                v.descripcion(), caracteristicas);
    }

    @Test
    @DisplayName("HU-23 R1: una lista de códigos de la lista fija sin repetir es válida y llega a aDatos")
    void caracteristicasValidas() {
        var ficha = conCaracteristicas(List.of("VESTUARIOS", "TECHADO"));

        assertThat(errores(ficha)).isEmpty();
        assertThat(errores(conCaracteristicas(List.of()))).isEmpty();
        assertThat(errores(conCaracteristicas(List.of("TECHADO", "ILUMINACION", "VESTUARIOS", "ESTACIONAMIENTO"))))
                .isEmpty();
        assertThat(ficha.aDatos().caracteristicas())
                .containsExactlyInAnyOrder(Caracteristica.TECHADO, Caracteristica.VESTUARIOS);
    }

    @Test
    @DisplayName("HU-23 R3: sin características (null o ficha de HU-07) es válida y deja la lista vacía")
    void caracteristicasAusentes() {
        assertThat(errores(conCaracteristicas(null))).isEmpty();
        assertThat(conCaracteristicas(null).aDatos().caracteristicas()).isEmpty();
        assertThat(valida().caracteristicas()).isNull();
        assertThat(valida().aDatos().caracteristicas()).isEmpty();
    }

    @Test
    @DisplayName("HU-23 R3: el JSON sin el campo caracteristicas se lee como null")
    void caracteristicasAusentesEnJson() {
        assertThat(leer("20").caracteristicas()).isNull();
    }

    @Test
    @DisplayName("HU-23 R4: un código desconocido, en minúsculas, vacío o null da error en caracteristicas")
    void caracteristicaDesconocida() {
        for (var codigo : new String[] {"PISCINA", "techado", "", " TECHADO", "TECHADO,VESTUARIOS", null}) {
            var lista = Arrays.asList("ILUMINACION", codigo);
            assertThat(errores(conCaracteristicas(lista))).as(String.valueOf(codigo))
                    .containsExactly(Map.entry("caracteristicas", FichaEspacioDTO.M_CARACTERISTICAS));
        }
    }

    @Test
    @DisplayName("HU-23 R5: un código repetido da error en caracteristicas")
    void caracteristicaRepetida() {
        assertThat(errores(conCaracteristicas(List.of("TECHADO", "VESTUARIOS", "TECHADO"))))
                .containsExactly(Map.entry("caracteristicas", FichaEspacioDTO.M_CARACTERISTICAS));
    }
}
