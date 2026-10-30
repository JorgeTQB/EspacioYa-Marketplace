package pe.edu.ulima.espacioya.reservas.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Solicitud de reserva (HU-12). Solo trae lo que elige el usuario: el usuario sale de la sesión y el monto, el estado y
 * el vencimiento los calcula el servidor; lo demás que envíe el cliente se descarta (R5). Un valor de tipo equivocado
 * se lee como {@code null} para que se reporte en su campo y no como un cuerpo ilegible sin campo (R29, R30, R32).
 */
public record CrearReservaDTO(
        @NotNull(message = CrearReservaDTO.M_ESPACIO)
        @Positive(message = CrearReservaDTO.M_ESPACIO)
        @JsonDeserialize(using = CrearReservaDTO.SoloIdEntero.class)
        Long espacioId,

        @NotBlank(message = CrearReservaDTO.M_FECHA)
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = CrearReservaDTO.M_FECHA)
        @JsonDeserialize(using = CrearReservaDTO.SoloTexto.class)
        String fecha,

        @NotEmpty(message = CrearReservaDTO.M_HORAS)
        @JsonDeserialize(using = CrearReservaDTO.SoloListaDeEnteros.class)
        List<Integer> horas) {

    public static final String M_ESPACIO = "Elige un espacio válido.";
    public static final String M_FECHA = "Elige una fecha válida.";
    public static final String M_HORAS = "Elige uno o más bloques consecutivos.";

    /** Solo un número JSON entero que quepa en un {@code long}; cualquier otro valor se lee como {@code null}. */
    public static final class SoloIdEntero extends ValueDeserializer<Long> {

        @Override
        public Long deserialize(JsonParser parser, DeserializationContext contexto) {
            if (parser.hasToken(JsonToken.VALUE_NUMBER_INT)
                    && parser.getNumberType() != JsonParser.NumberType.BIG_INTEGER) {
                return parser.getLongValue();
            }
            parser.skipChildren();
            return null;
        }
    }

    /** Solo un texto JSON; un número o un objeto se leen como {@code null}. */
    public static final class SoloTexto extends ValueDeserializer<String> {

        @Override
        public String deserialize(JsonParser parser, DeserializationContext contexto) {
            if (parser.hasToken(JsonToken.VALUE_STRING)) {
                return parser.getString();
            }
            parser.skipChildren();
            return null;
        }
    }

    /**
     * Solo un arreglo JSON; otro valor se lee como {@code null}. Dentro del arreglo, un elemento que no es un entero
     * (decimal, texto o nulo) se lee como {@code null}: {@code SeleccionBloques} lo rechaza con los demás errores de
     * {@code horas}, en ese campo y sin rutas {@code horas[i]}.
     */
    public static final class SoloListaDeEnteros extends ValueDeserializer<List<Integer>> {

        @Override
        public List<Integer> deserialize(JsonParser parser, DeserializationContext contexto) {
            if (!parser.hasToken(JsonToken.START_ARRAY)) {
                parser.skipChildren();
                return null;
            }
            var horas = new ArrayList<Integer>();
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (parser.hasToken(JsonToken.VALUE_NUMBER_INT)
                        && parser.getNumberType() == JsonParser.NumberType.INT) {
                    horas.add(parser.getIntValue());
                } else {
                    parser.skipChildren();
                    horas.add(null);
                }
            }
            return horas;
        }
    }
}
