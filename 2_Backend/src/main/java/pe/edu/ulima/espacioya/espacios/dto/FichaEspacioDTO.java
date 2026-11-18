package pe.edu.ulima.espacioya.espacios.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import pe.edu.ulima.espacioya.espacios.domain.Caracteristica;
import pe.edu.ulima.espacioya.espacios.domain.DatosEspacio;
import pe.edu.ulima.espacioya.espacios.domain.TipoEspacio;

/**
 * Ficha de un espacio al crearlo o editarlo (HU-07). No trae {@code id}, {@code arrendadorId} ni {@code estado}:
 * si el cliente los envía, Jackson los descarta (R3). Los textos se recortan antes de validar, para que las
 * longitudes se cuenten sin espacios al inicio ni al final. La zona la comprueba el servicio contra el catálogo.
 * HU-23: {@code caracteristicas} ausente o {@code null} equivale a ninguna, porque el {@code PUT} reemplaza la ficha
 * completa (P1).
 */
public record FichaEspacioDTO(
        @NotBlank(message = FichaEspacioDTO.M_NOMBRE)
        @Size(min = 3, max = 100, message = FichaEspacioDTO.M_NOMBRE)
        String nombre,

        @NotNull(message = FichaEspacioDTO.M_TIPO)
        @Pattern(regexp = "CANCHA|ESTADIO|AUDITORIO", message = FichaEspacioDTO.M_TIPO)
        String tipo,

        @NotBlank(message = FichaEspacioDTO.M_DIRECCION)
        @Size(min = 5, max = 200, message = FichaEspacioDTO.M_DIRECCION)
        String direccion,

        @NotBlank(message = FichaEspacioDTO.M_ZONA)
        @Size(max = 40, message = FichaEspacioDTO.M_ZONA)
        String zona,

        @NotNull(message = FichaEspacioDTO.M_CAPACIDAD)
        @Min(value = 1, message = FichaEspacioDTO.M_CAPACIDAD)
        @Max(value = 100_000, message = FichaEspacioDTO.M_CAPACIDAD)
        @JsonDeserialize(using = FichaEspacioDTO.SoloEntero.class)
        Integer capacidad,

        @NotNull(message = FichaEspacioDTO.M_PRECIO)
        @DecimalMin(value = "1.00", message = FichaEspacioDTO.M_PRECIO)
        @DecimalMax(value = "100000.00", message = FichaEspacioDTO.M_PRECIO)
        @Digits(integer = 6, fraction = 2, message = FichaEspacioDTO.M_PRECIO)
        BigDecimal precioHora,

        @NotBlank(message = FichaEspacioDTO.M_DESCRIPCION)
        @Size(min = 10, max = 2000, message = FichaEspacioDTO.M_DESCRIPCION)
        String descripcion,

        @CaracteristicasDeLista(message = FichaEspacioDTO.M_CARACTERISTICAS)
        List<String> caracteristicas) {

    public static final String M_NOMBRE = "Escribe un nombre de 3 a 100 caracteres.";
    public static final String M_TIPO = "Elige el tipo de espacio.";
    public static final String M_DIRECCION = "Escribe una dirección de 5 a 200 caracteres.";
    public static final String M_ZONA = "Elige una zona de la lista.";
    public static final String M_CAPACIDAD = "La capacidad debe ser un número entero entre 1 y 100 000.";
    public static final String M_PRECIO =
            "El precio por hora debe estar entre S/ 1.00 y S/ 100 000.00, con hasta 2 decimales.";
    public static final String M_DESCRIPCION = "Escribe una descripción de 10 a 2000 caracteres.";
    public static final String M_CARACTERISTICAS = "Elige características de la lista, sin repetir.";

    public FichaEspacioDTO {
        nombre = recortar(nombre);
        direccion = recortar(direccion);
        zona = recortar(zona);
        descripcion = recortar(descripcion);
    }

    /** Ficha de HU-07 sin características (HU-23 DT-15). */
    public FichaEspacioDTO(String nombre, String tipo, String direccion, String zona, Integer capacidad,
                           BigDecimal precioHora, String descripcion) {
        this(nombre, tipo, direccion, zona, capacidad, precioHora, descripcion, null);
    }

    public DatosEspacio aDatos() {
        return new DatosEspacio(nombre, TipoEspacio.valueOf(tipo), direccion, zona, capacidad, precioHora,
                descripcion, Caracteristica.deCodigos(caracteristicas == null ? List.of() : caracteristicas));
    }

    private static String recortar(String texto) {
        return texto == null ? null : texto.strip();
    }

    /**
     * Lee {@code capacidad} solo si llega como número JSON entero (R4). Jackson, por defecto, truncaría
     * {@code 20.7} a {@code 20} y aceptaría {@code "20"}; aquí cualquier otro valor (decimal, texto, booleano,
     * objeto o un entero fuera del rango de {@code int}) se lee como {@code null}, y {@code @NotNull} lo
     * rechaza con el error por campo y el mensaje de R4. Acotado a este campo para no cambiar la lectura de
     * JSON del resto de la API.
     */
    public static final class SoloEntero extends ValueDeserializer<Integer> {

        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext contexto) {
            if (parser.hasToken(JsonToken.VALUE_NUMBER_INT)
                    && parser.getNumberType() == JsonParser.NumberType.INT) {
                return parser.getIntValue();
            }
            parser.skipChildren();
            return null;
        }
    }
}
