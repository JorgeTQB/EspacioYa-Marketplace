package pe.edu.ulima.espacioya.comun.limite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReglaLimiteTest {

    private static final Duration HORA = Duration.ofHours(1);
    private static final Duration MINUTO = Duration.ofMinutes(1);

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

    @Test
    @DisplayName("HU-10 R42: una regla con variable de ruta aplica solo a las rutas que coinciden con el patrón")
    void patronConVariable() {
        var regla = new ReglaLimite("GET", "/api/espacios/{id}", 120, MINUTO, "catalogo");

        assertThat(regla.aplicaA("GET", "/api/espacios/7")).isTrue();
        assertThat(regla.aplicaA("GET", "/api/espacios")).isFalse();
        assertThat(regla.aplicaA("GET", "/api/espacios/7/fotos")).isFalse();
        assertThat(regla.aplicaA("POST", "/api/espacios/7")).isFalse();
    }

    @Test
    @DisplayName("HU-10 R42: una regla con comodín aplica a cualquier segmento en esa posición")
    void patronConComodin() {
        var regla = new ReglaLimite("GET", "/api/fotos/*", 10, MINUTO, "fotos");

        assertThat(regla.aplicaA("GET", "/api/fotos/3f0c-uuid")).isTrue();
        assertThat(regla.aplicaA("GET", "/api/fotos")).isFalse();
        assertThat(regla.aplicaA("GET", "/api/fotos/a/b")).isFalse();
    }

    @Test
    @DisplayName("HU-10 R43: una regla literal aplica solo a su ruta y compara el método sin mayúsculas")
    void literalSoloASuRuta() {
        var regla = new ReglaLimite("GET", "/api/espacios", 120, MINUTO, "catalogo");

        assertThat(regla.aplicaA("get", "/api/espacios")).isTrue();
        assertThat(regla.aplicaA("GET", "/api/espacios/7")).isFalse();
        assertThat(regla.aplicaA("GET", "/api/espaciosx")).isFalse();
        assertThat(regla.aplicaA("GET", "/api/zonas")).isFalse();
    }

    @Test
    @DisplayName("HU-10 R44: la ruta con codificación porcentual de caracteres no reservados recibe la misma regla")
    void rutaCodificadaAplica() {
        var regla = new ReglaLimite("GET", "/api/espacios", 120, MINUTO, "catalogo");
        var registro = new ReglaLimite("POST", "/api/usuarios", 5, HORA, "registro");

        assertThat(regla.aplicaA("GET", "/api/%65spacios")).isTrue();
        assertThat(regla.aplicaA("GET", "/%61pi/espacio%73")).isTrue();
        assertThat(registro.aplicaA("POST", "/api/usu%61rios")).isTrue();
    }

    @Test
    @DisplayName("HU-10 R44: una ruta con escape mal formado no aplica a la regla (Spring MVC tampoco la enruta)")
    void escapeMalFormadoNoAplica() {
        var regla = new ReglaLimite("GET", "/api/espacios", 120, MINUTO, "catalogo");

        assertThat(regla.aplicaA("GET", "/api/espacios%zz")).isFalse();
    }

    @Test
    @DisplayName("HU-10 R45: una ruta que no es un patrón válido lanza IllegalArgumentException al construir")
    void patronInvalidoLanza() {
        assertThatThrownBy(() -> new ReglaLimite("GET", "/api/{", 120, MINUTO, "catalogo"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReglaLimite("GET", "/api/{id}/{id}", 120, MINUTO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
