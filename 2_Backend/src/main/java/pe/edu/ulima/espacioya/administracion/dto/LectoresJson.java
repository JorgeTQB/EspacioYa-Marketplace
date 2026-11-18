package pe.edu.ulima.espacioya.administracion.dto;

import java.math.BigDecimal;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Lectores estrictos de los DTO de reclamos (HU-26 DT-20): un valor de tipo equivocado se lee como {@code null} para
 * reportarlo en su campo y no como cuerpo ilegible.
 */
public final class LectoresJson {

    private LectoresJson() {
    }

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

    /** Solo un número JSON, entero o decimal, con su escala exacta; un texto o un booleano se leen como {@code null}. */
    public static final class SoloNumero extends ValueDeserializer<BigDecimal> {

        @Override
        public BigDecimal deserialize(JsonParser parser, DeserializationContext contexto) {
            if (parser.hasToken(JsonToken.VALUE_NUMBER_INT) || parser.hasToken(JsonToken.VALUE_NUMBER_FLOAT)) {
                return parser.getDecimalValue();
            }
            parser.skipChildren();
            return null;
        }
    }
}
