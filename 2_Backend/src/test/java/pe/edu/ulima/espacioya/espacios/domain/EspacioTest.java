package pe.edu.ulima.espacioya.espacios.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EspacioTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final Instant DESPUES = AHORA.plus(Duration.ofHours(2));
    private static final DatosEspacio DATOS = new DatosEspacio("Cancha Los Olivos", TipoEspacio.CANCHA,
            "Av. Universitaria 1234", "LOS_OLIVOS", 20, new BigDecimal("80"), "Cancha de grass sintético techada.");

    static Espacio enEstado(EstadoEspacio estado) {
        var espacio = Espacio.crear(7L, DATOS, AHORA);
        ReflectionTestUtils.setField(espacio, "estado", estado);
        return espacio;
    }

    @Test
    @DisplayName("HU-07 R2: crear deja el espacio en BORRADOR con los datos y ambas fechas del reloj")
    void crearEnBorrador() {
        var espacio = Espacio.crear(7L, DATOS, AHORA);

        assertThat(espacio.getArrendadorId()).isEqualTo(7L);
        assertThat(espacio.getEstado()).isEqualTo(EstadoEspacio.BORRADOR);
        assertThat(espacio.getNombre()).isEqualTo("Cancha Los Olivos");
        assertThat(espacio.getTipo()).isEqualTo(TipoEspacio.CANCHA);
        assertThat(espacio.getZona()).isEqualTo("LOS_OLIVOS");
        assertThat(espacio.getCapacidad()).isEqualTo(20);
        assertThat(espacio.getPrecioHora()).isEqualByComparingTo("80").hasToString("80.00");
        assertThat(espacio.getCreadoEn()).isEqualTo(AHORA);
        assertThat(espacio.getActualizadoEn()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("HU-07 R21, R46: editar devuelve los cambios en orden fijo, precio a 2 decimales y descripcion sola")
    void editarDevuelveCambios() {
        var espacio = Espacio.crear(7L, DATOS, AHORA);
        var nuevos = new DatosEspacio("Cancha Norte", TipoEspacio.AUDITORIO, "Av. Universitaria 1234",
                "COMAS", 30, new BigDecimal("90.5"), "Otra descripción más completa.");

        var cambios = espacio.editar(nuevos, DESPUES);

        assertThat(cambios).containsExactly("nombre=Cancha Los Olivos->Cancha Norte", "tipo=CANCHA->AUDITORIO",
                "zona=LOS_OLIVOS->COMAS", "capacidad=20->30", "precioHora=80.00->90.50", "descripcion");
        assertThat(espacio.getNombre()).isEqualTo("Cancha Norte");
        assertThat(espacio.getPrecioHora()).hasToString("90.50");
        assertThat(espacio.getActualizadoEn()).isEqualTo(DESPUES);
        assertThat(espacio.getCreadoEn()).isEqualTo(AHORA);
        assertThat(espacio.getEstado()).isEqualTo(EstadoEspacio.BORRADOR);
    }

    @Test
    @DisplayName("HU-07 R47: editar sin cambios devuelve una lista vacía y no toca actualizadoEn")
    void editarSinCambios() {
        var espacio = Espacio.crear(7L, DATOS, AHORA);
        var iguales = new DatosEspacio(DATOS.nombre(), DATOS.tipo(), DATOS.direccion(), DATOS.zona(),
                DATOS.capacidad(), new BigDecimal("80.00"), DATOS.descripcion());

        assertThat(espacio.editar(iguales, DESPUES)).isEmpty();
        assertThat(espacio.getActualizadoEn()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("HU-07 R21, R22: editar un PUBLICADO o un PAUSADO no cambia su estado")
    void editarConservaEstado() {
        for (var estado : new EstadoEspacio[] {EstadoEspacio.PUBLICADO, EstadoEspacio.PAUSADO}) {
            var espacio = enEstado(estado);
            espacio.editar(new DatosEspacio("Otro nombre", DATOS.tipo(), DATOS.direccion(), DATOS.zona(),
                    DATOS.capacidad(), DATOS.precioHora(), DATOS.descripcion()), DESPUES);
            assertThat(espacio.getEstado()).isEqualTo(estado);
        }
    }

    @Test
    @DisplayName("HU-07 R23: editar un espacio en BAJA u OCULTO lanza y no cambia nada")
    void editarNoEditableLanza() {
        for (var estado : new EstadoEspacio[] {EstadoEspacio.BAJA, EstadoEspacio.OCULTO}) {
            var espacio = enEstado(estado);
            var nuevos = new DatosEspacio("Otro nombre", DATOS.tipo(), DATOS.direccion(), DATOS.zona(),
                    DATOS.capacidad(), DATOS.precioHora(), DATOS.descripcion());
            assertThatThrownBy(() -> espacio.editar(nuevos, DESPUES)).isInstanceOf(IllegalStateException.class);
            assertThat(espacio.getNombre()).isEqualTo(DATOS.nombre());
            assertThat(espacio.getActualizadoEn()).isEqualTo(AHORA);
        }
    }

    @Test
    @DisplayName("HU-07 R12, R13: publicar un BORRADOR lo deja PUBLICADO con actualizadoEn del reloj")
    void publicarBorrador() {
        var espacio = Espacio.crear(7L, DATOS, AHORA);

        espacio.publicar(DESPUES);

        assertThat(espacio.getEstado()).isEqualTo(EstadoEspacio.PUBLICADO);
        assertThat(espacio.getActualizadoEn()).isEqualTo(DESPUES);
    }

    @Test
    @DisplayName("HU-07 R14, R19: publicar un espacio que no está en BORRADOR lanza y no cambia el estado")
    void publicarFueraDeBorradorLanza() {
        for (var estado : new EstadoEspacio[] {EstadoEspacio.PUBLICADO, EstadoEspacio.PAUSADO, EstadoEspacio.BAJA,
                EstadoEspacio.OCULTO}) {
            var espacio = enEstado(estado);
            assertThatThrownBy(() -> espacio.publicar(DESPUES)).isInstanceOf(IllegalStateException.class);
            assertThat(espacio.getEstado()).isEqualTo(estado);
            assertThat(espacio.getActualizadoEn()).isEqualTo(AHORA);
        }
    }

    @Test
    @DisplayName("HU-07 R23, R14: editable en BORRADOR, PUBLICADO y PAUSADO; publicable solo en BORRADOR")
    void editableYPublicablePorEstado() {
        for (var estado : EstadoEspacio.values()) {
            var espacio = enEstado(estado);
            assertThat(espacio.esEditable()).isEqualTo(estado == EstadoEspacio.BORRADOR
                    || estado == EstadoEspacio.PUBLICADO || estado == EstadoEspacio.PAUSADO);
            assertThat(espacio.esPublicable()).isEqualTo(estado == EstadoEspacio.BORRADOR);
        }
    }

    @Test
    @DisplayName("HU-07 R34: tocar actualiza solo actualizadoEn")
    void tocar() {
        var espacio = Espacio.crear(7L, DATOS, AHORA);

        espacio.tocar(DESPUES);

        assertThat(espacio.getActualizadoEn()).isEqualTo(DESPUES);
        assertThat(espacio.getEstado()).isEqualTo(EstadoEspacio.BORRADOR);
    }
}
