package pe.edu.ulima.espacioya.comun.correo;

/**
 * ★ Factory Method · Product (HU-25 D9): un correo ya armado para un evento. Cada ConcreteProduct es un {@code record}
 * que recibe su evento y arma un asunto fijo (R31) y un cuerpo de texto plano con solo los datos de la tabla C1.
 */
public interface Correo {

    String asunto();

    String cuerpo();
}
