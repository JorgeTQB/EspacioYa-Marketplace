package pe.edu.ulima.espacioya.cuentas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.edu.ulima.espacioya.comun.cifrado.HuellaCampos;
import pe.edu.ulima.espacioya.cuentas.domain.Base32;
import pe.edu.ulima.espacioya.cuentas.domain.CodigoRespaldo;
import pe.edu.ulima.espacioya.cuentas.repository.CodigoRespaldoRepository;

@ExtendWith(MockitoExtension.class)
class GeneradorMfaTest {

    static final String LLAVE_HUELLA = "ZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXp7fH1+f4CBgoM=";
    private static final Instant AHORA = Instant.parse("2026-11-03T15:00:00Z");
    private static final long ID_USUARIO = 7L;

    @Mock
    private CodigoRespaldoRepository codigos;

    private final HuellaCampos huella = new HuellaCampos(LLAVE_HUELLA);
    private GeneradorMfa generador;

    @BeforeEach
    void preparar() {
        generador = new GeneradorMfa(huella, codigos);
    }

    @Test
    @DisplayName("HU-17 R54: nuevoSecreto son 20 bytes aleatorios en Base32 (32 caracteres) y dos llamadas difieren")
    void nuevoSecreto() {
        var primero = generador.nuevoSecreto();
        var segundo = generador.nuevoSecreto();

        assertThat(primero).hasSize(32).matches("^[A-Z2-7]{32}$");
        assertThat(Base32.decodificar(primero)).hasSize(20);
        assertThat(segundo).hasSize(32).isNotEqualTo(primero);
    }

    @Test
    @DisplayName("HU-17 R23, R55: configuracion devuelve el secreto y la uri otpauth exacta con el correo codificado")
    void configuracionArmaUri() {
        var configuracion = generador.configuracion("ana+mfa@correo.pe", "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");

        assertThat(configuracion.secreto()).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(configuracion.uri()).isEqualTo("otpauth://totp/EspacioYa:ana%2Bmfa%40correo.pe"
                + "?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=EspacioYa&algorithm=SHA1&digits=6&period=30");
    }

    @Test
    @DisplayName("HU-17 R36, R37: reemplazarCodigos borra los anteriores, guarda 5 huellas y devuelve 5 códigos "
            + "XXXXX-XXXXX distintos")
    void reemplazarCodigos() {
        when(codigos.save(any(CodigoRespaldo.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        var devueltos = generador.reemplazarCodigos(ID_USUARIO, AHORA);

        assertThat(devueltos).hasSize(5).doesNotHaveDuplicates()
                .allSatisfy(codigo -> assertThat(codigo).matches("^[A-Z2-7]{5}-[A-Z2-7]{5}$"));
        var orden = inOrder(codigos);
        orden.verify(codigos).borrarDeUsuario(ID_USUARIO);
        var guardados = ArgumentCaptor.forClass(CodigoRespaldo.class);
        orden.verify(codigos, times(5)).save(guardados.capture());
        var huellasEsperadas = devueltos.stream().map(codigo -> huella.calcular(codigo.replace("-", ""))).toList();
        assertThat(guardados.getAllValues()).extracting(CodigoRespaldo::getHashCodigo)
                .containsExactlyElementsOf(huellasEsperadas)
                .allSatisfy(hash -> assertThat(hash).matches("^[0-9a-f]{64}$"));
        assertThat(guardados.getAllValues()).allSatisfy(guardado -> {
            assertThat(guardado.getUsuarioId()).isEqualTo(ID_USUARIO);
            assertThat(guardado.getCreadoEn()).isEqualTo(AHORA);
            assertThat(guardado.estaUsado()).isFalse();
        });
        for (var codigo : devueltos) {
            assertThat(guardados.getAllValues()).extracting(CodigoRespaldo::getHashCodigo)
                    .doesNotContain(codigo, codigo.replace("-", ""));
        }
        verify(codigos, times(1)).borrarDeUsuario(ID_USUARIO);
    }

    @Test
    @DisplayName("HU-17 R17: normalizar quita espacios y guiones y pasa a mayúsculas")
    void normalizar() {
        assertThat(GeneradorMfa.normalizar("abcde-fghij")).isEqualTo("ABCDEFGHIJ");
        assertThat(GeneradorMfa.normalizar("ABCDEFGHIJ")).isEqualTo("ABCDEFGHIJ");
        assertThat(GeneradorMfa.normalizar(" ABCDE FGHIJ ")).isEqualTo("ABCDEFGHIJ");
        assertThat(GeneradorMfa.normalizar(null)).isEmpty();
    }

    @Test
    @DisplayName("HU-17 R17, R37: la huella de las tres formas de un mismo código es la misma")
    void huellaDeFormasEquivalentes() {
        var esperada = generador.huella("ABCDEFGHIJ");

        assertThat(generador.huella(GeneradorMfa.normalizar("abcde-fghij"))).isEqualTo(esperada);
        assertThat(generador.huella(GeneradorMfa.normalizar("ABCDE FGHIJ"))).isEqualTo(esperada);
        assertThat(esperada).isEqualTo(huella.calcular("ABCDEFGHIJ")).hasSize(64);
    }
}
