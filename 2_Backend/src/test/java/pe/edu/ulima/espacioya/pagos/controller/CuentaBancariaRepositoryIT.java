package pe.edu.ulima.espacioya.pagos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.pagos.domain.CuentaBancaria;
import pe.edu.ulima.espacioya.pagos.repository.CuentaBancariaRepository;

/** La tabla {@code cuenta_bancaria} contra PostgreSQL real: cifrado en reposo y restricciones (R17, R20, R25). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PagosSoporte.Dobles.class})
class CuentaBancariaRepositoryIT extends PagosSoporte {

    private static final String NUMERO = "00112233445566778899";

    @Autowired
    private CuentaBancariaRepository repositorio;

    private static Instant instante(Object valor) {
        return valor instanceof Timestamp marca ? marca.toInstant() : ((java.time.OffsetDateTime) valor).toInstant();
    }

    private CuentaBancaria nueva(long arrendador) {
        return CuentaBancaria.registrar(arrendador, "Banco Andino", NUMERO, "Inversiones Ocultas SAC", reloj.instant());
    }

    @Test
    @DisplayName("HU-18 R20: la columna numero guarda v1:… y no el número; al leer se descifra")
    void numeroCifradoEnReposo() {
        var guardada = repositorio.saveAndFlush(nueva(arrendadorId));

        var enBase = jdbc.queryForObject("SELECT numero FROM cuenta_bancaria WHERE id = ?", String.class,
                guardada.getId());

        assertThat(enBase).startsWith("v1:").doesNotContain(NUMERO).doesNotContain("8899");
        assertThat(repositorio.findByArrendadorId(arrendadorId)).get()
                .satisfies(cuenta -> assertThat(cuenta.enmascarado()).isEqualTo("****8899"));
        assertThat(cifrador.descifrar(enBase)).isEqualTo(NUMERO);
    }

    @Test
    @DisplayName("HU-18 R17: la primera cuenta guarda retener_hasta nulo y las fechas de creación y actualización")
    void primeraCuentaSinRetencion() {
        var guardada = repositorio.saveAndFlush(nueva(arrendadorId));

        var fila = jdbc.queryForMap("SELECT * FROM cuenta_bancaria WHERE id = ?", guardada.getId());

        assertThat(fila.get("retener_hasta")).isNull();
        assertThat(instante(fila.get("creada_en"))).isEqualTo(reloj.instant());
        assertThat(fila.get("titular")).isEqualTo("Inversiones Ocultas SAC");
    }

    @Test
    @DisplayName("HU-18 R25: una segunda fila del mismo arrendador viola uq_cuenta_bancaria_arrendador")
    void unaCuentaPorArrendador() {
        repositorio.saveAndFlush(nueva(arrendadorId));

        assertThatThrownBy(() -> repositorio.saveAndFlush(nueva(arrendadorId)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_cuenta_bancaria_arrendador");
    }

    @Test
    @DisplayName("HU-18 R25: un banco o un titular en blanco, o fechas invertidas, violan los CHECK")
    void restriccionesDeLaTabla() {
        var sql = "INSERT INTO cuenta_bancaria (arrendador_id, banco, numero, titular, creada_en, actualizada_en) "
                + "VALUES (?, ?, 'v1:x', ?, ?, ?)";
        var ahora = Timestamp.from(reloj.instant());
        var antes = Timestamp.from(reloj.instant().minus(Duration.ofSeconds(1)));

        assertThatThrownBy(() -> jdbc.update(sql, arrendadorId, "  ", "Titular", ahora, ahora))
                .hasMessageContaining("ck_cuenta_bancaria_banco");
        assertThatThrownBy(() -> jdbc.update(sql, arrendadorId, "Banco", " ", ahora, ahora))
                .hasMessageContaining("ck_cuenta_bancaria_titular");
        assertThatThrownBy(() -> jdbc.update(sql, arrendadorId, "Banco", "Titular", ahora, antes))
                .hasMessageContaining("ck_cuenta_bancaria_fechas");
    }

    @Test
    @DisplayName("HU-18 R25: la fila debe pertenecer a un perfil de arrendador existente")
    void exigeElPerfil() {
        assertThatThrownBy(() -> repositorio.saveAndFlush(nueva(usuarioId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
