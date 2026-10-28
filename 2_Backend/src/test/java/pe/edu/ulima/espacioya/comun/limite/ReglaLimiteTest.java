package pe.edu.ulima.espacioya.comun.limite;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReglaLimiteTest {

    private static final Duration HORA = Duration.ofHours(1);

    @Test
    @DisplayName("HU-02 R42: el constructor de 4 argumentos conserva la clave por método y ruta")
    void constructorDeCuatroConservaLaClave() {
        var regla = new ReglaLimite("POST", "/api/usuarios", 5, HORA);

        assertThat(regla.grupo()).isEqualTo("POST /api/usuarios");
        assertThat(regla.clave("1.2.3.4")).isEqualTo("POST /api/usuarios|1.2.3.4");
    }

    @Test
    @DisplayName("HU-02 R42, R43: dos reglas del mismo grupo comparten la clave para la misma IP")
    void mismoGrupoMismaClave() {
        var usuarios = new ReglaLimite("POST", "/api/usuarios", 5, HORA, "registro");
        var arrendadores = new ReglaLimite("POST", "/api/arrendadores", 5, HORA, "registro");

        assertThat(usuarios.clave("1.2.3.4")).isEqualTo(arrendadores.clave("1.2.3.4")).isEqualTo("registro|1.2.3.4");
        assertThat(usuarios.clave("1.2.3.5")).isNotEqualTo(usuarios.clave("1.2.3.4"));
    }

    @Test
    @DisplayName("HU-02 R42: aplicaA sigue comparando método sin mayúsculas y ruta exacta")
    void aplicaASinCambios() {
        var regla = new ReglaLimite("POST", "/api/arrendadores", 5, HORA, "registro");

        assertThat(regla.aplicaA("post", "/api/arrendadores")).isTrue();
        assertThat(regla.aplicaA("GET", "/api/arrendadores")).isFalse();
        assertThat(regla.aplicaA("POST", "/api/usuarios")).isFalse();
        assertThat(regla.aplicaA("POST", "/api/arrendadores/1")).isFalse();
    }
}
