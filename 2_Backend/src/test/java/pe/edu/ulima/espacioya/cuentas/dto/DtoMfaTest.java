package pe.edu.ulima.espacioya.cuentas.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.edu.ulima.espacioya.cuentas.domain.PasoMfa;

class DtoMfaTest {

    private static final String DESAFIO = "DesafioSecretoDeCuarentaYTresCaracteres_xyz";
    private static final String TOKEN = "TokenSecretoDeSesionDeCuarentaYTresCaract_q";
    private static final String CODIGO = "287082";
    private static final String CONTRASENA = "Segura123!";
    private static final String SECRETO = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    private static final String URI = "otpauth://totp/EspacioYa:ana%40correo.pe?secret=" + SECRETO;
    private static final List<String> RESPALDO = List.of("ABCDE-FGHIJ", "KLMNO-PQRST");

    @Test
    @DisplayName("HU-17 R57: DesafioMfaDTO no muestra el desafío")
    void desafioMfa() {
        assertThat(new DesafioMfaDTO(DESAFIO, PasoMfa.CODIGO).toString())
                .doesNotContain(DESAFIO)
                .isEqualTo("DesafioMfaDTO[desafio=***, paso=CODIGO]");
    }

    @Test
    @DisplayName("HU-17 R57: DesafioDTO no muestra el desafío")
    void desafio() {
        assertThat(new DesafioDTO(DESAFIO).toString()).doesNotContain(DESAFIO).isEqualTo("DesafioDTO[desafio=***]");
    }

    @Test
    @DisplayName("HU-17 R57: VerificacionMfaDTO no muestra el desafío ni el código")
    void verificacion() {
        assertThat(new VerificacionMfaDTO(DESAFIO, CODIGO).toString())
                .doesNotContain(DESAFIO)
                .doesNotContain(CODIGO)
                .isEqualTo("VerificacionMfaDTO[desafio=***, codigo=***]");
    }

    @Test
    @DisplayName("HU-17 R57: SesionVerificadaDTO no muestra el token ni los códigos de respaldo")
    void sesionVerificada() {
        var texto = new SesionVerificadaDTO(TOKEN, "ADMIN", null, RESPALDO).toString();

        assertThat(texto).doesNotContain(TOKEN).doesNotContain("ABCDE").doesNotContain("KLMNO")
                .isEqualTo("SesionVerificadaDTO[token=***, rol=ADMIN, aviso=null, codigosRespaldo=***]");
    }

    @Test
    @DisplayName("HU-17 R57: ConfiguracionMfaDTO no muestra el secreto ni la URI")
    void configuracion() {
        assertThat(new ConfiguracionMfaDTO(SECRETO, URI).toString())
                .doesNotContain(SECRETO)
                .doesNotContain("otpauth")
                .isEqualTo("ConfiguracionMfaDTO[secreto=***, uri=***]");
    }

    @Test
    @DisplayName("HU-17 R57: CodigoMfaDTO no muestra el código")
    void codigo() {
        assertThat(new CodigoMfaDTO(CODIGO).toString()).doesNotContain(CODIGO).isEqualTo("CodigoMfaDTO[codigo=***]");
    }

    @Test
    @DisplayName("HU-17 R57: CodigosRespaldoDTO no muestra los códigos")
    void codigosRespaldo() {
        assertThat(new CodigosRespaldoDTO(RESPALDO).toString())
                .doesNotContain("ABCDE")
                .isEqualTo("CodigosRespaldoDTO[codigos=***]");
    }

    @Test
    @DisplayName("HU-17 R57: DesactivacionMfaDTO no muestra la contraseña ni el código")
    void desactivacion() {
        assertThat(new DesactivacionMfaDTO(CONTRASENA, CODIGO).toString())
                .doesNotContain(CONTRASENA)
                .doesNotContain(CODIGO)
                .isEqualTo("DesactivacionMfaDTO[contrasena=***, codigo=***]");
    }

    @Test
    @DisplayName("HU-17 R57: EstadoMfaDTO solo muestra el estado, sin datos sensibles")
    void estado() {
        assertThat(new EstadoMfaDTO(true, 4, false).toString())
                .isEqualTo("EstadoMfaDTO[activo=true, codigosRespaldoRestantes=4, desactivable=false]");
    }

    @Test
    @DisplayName("HU-17 R10: SesionVerificadaDTO admite codigosRespaldo nulo y copia la lista recibida")
    void sesionVerificadaCopiaLista() {
        assertThat(new SesionVerificadaDTO(TOKEN, "USUARIO", null, null).codigosRespaldo()).isNull();
        assertThat(new SesionVerificadaDTO(TOKEN, "ADMIN", null, RESPALDO).codigosRespaldo())
                .containsExactlyElementsOf(RESPALDO);
    }
}
