package pe.edu.ulima.espacioya.busqueda.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Solicitud de reseña (HU-24). El usuario sale de la sesión y el espacio de la reserva: cualquier otro campo se
 * descarta (R3). Un valor de tipo equivocado se lee como {@code null} para reportarlo en su campo y no como cuerpo
 * ilegible (DT-15).
 */
public record CrearResenaDTO(
        @NotNull(message = CrearResenaDTO.M_RESERVA)
        @Positive(message = CrearResenaDTO.M_RESERVA)
        @JsonDeserialize(using = CrearResenaDTO.SoloEnteroLargo.class)
        Long reservaId,

        @NotNull(message = CrearResenaDTO.M_CALIFICACION)
        @Min(value = 1, message = CrearResenaDTO.M_CALIFICACION)
        @Max(value = 5, message = CrearResenaDTO.M_CALIFICACION)
        @JsonDeserialize(using = CrearResenaDTO.SoloEnteroCorto.class)
        Integer calificacion,

        @NotBlank(message = CrearResenaDTO.M_COMENTARIO)
        @Size(max = 500, message = CrearResenaDTO.M_COMENTARIO)
        @JsonDeserialize(using = CrearResenaDTO.SoloTexto.class)
        String comentario) {

    public static final String M_RESERVA = "Elige una reserva válida.";
    public static final String M_CALIFICACION = "Elige una calificación de 1 a 5.";
    public static final String M_COMENTARIO = "Escribe un comentario de hasta 500 caracteres.";

    /** Solo un número JSON entero que quepa en un {@code long}; cualquier otro valor se lee como {@code null}. */
    public static final class SoloEnteroLargo extends ValueDeserializer<Long> {

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

    /** Solo un número JSON entero que quepa en un {@code int}; un texto, un decimal o uno mayor son {@code null}. */
    public static final class SoloEnteroCorto extends ValueDeserializer<Integer> {

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
}
