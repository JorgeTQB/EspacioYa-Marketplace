package pe.edu.ulima.espacioya.espacios.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

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

    @Test
    @DisplayName("HU-21 R6, R8, R16: pausable solo PUBLICADO, reanudable solo PAUSADO, baja desde PUBLICADO o PAUSADO")
    void predicadosDeEstado() {
        for (var estado : EstadoEspacio.values()) {
            var espacio = enEstado(estado);
            assertThat(espacio.esPausable()).isEqualTo(estado == EstadoEspacio.PUBLICADO);
            assertThat(espacio.esReanudable()).isEqualTo(estado == EstadoEspacio.PAUSADO);
            assertThat(espacio.admiteBaja())
                    .isEqualTo(estado == EstadoEspacio.PUBLICADO || estado == EstadoEspacio.PAUSADO);
        }
    }

    @Test
    @DisplayName("HU-21 R39: pausar un PUBLICADO lo deja PAUSADO con actualizadoEn del reloj")
    void pausarPublicado() {
        var espacio = enEstado(EstadoEspacio.PUBLICADO);

        espacio.pausar(DESPUES);

        assertThat(espacio.getEstado()).isEqualTo(EstadoEspacio.PAUSADO);
        assertThat(espacio.getActualizadoEn()).isEqualTo(DESPUES);
    }

    @Test
    @DisplayName("HU-21 R6: pausar fuera de PUBLICADO lanza y no cambia estado ni actualizadoEn")
    void pausarFueraDePublicadoLanza() {
        for (var estado : new EstadoEspacio[] {EstadoEspacio.BORRADOR, EstadoEspacio.PAUSADO, EstadoEspacio.BAJA,
                EstadoEspacio.OCULTO}) {
            var espacio = enEstado(estado);
            assertThatThrownBy(() -> espacio.pausar(DESPUES)).isInstanceOf(IllegalStateException.class);
            assertThat(espacio.getEstado()).isEqualTo(estado);
            assertThat(espacio.getActualizadoEn()).isEqualTo(AHORA);
        }
    }

    @Test
    @DisplayName("HU-21 R39: reanudar un PAUSADO lo deja PUBLICADO con actualizadoEn del reloj")
    void reanudarPausado() {
        var espacio = enEstado(EstadoEspacio.PAUSADO);

        espacio.reanudar(DESPUES);

        assertThat(espacio.getEstado()).isEqualTo(EstadoEspacio.PUBLICADO);
        assertThat(espacio.getActualizadoEn()).isEqualTo(DESPUES);
    }

    @Test
    @DisplayName("HU-21 R8: reanudar fuera de PAUSADO lanza y no cambia estado ni actualizadoEn")
    void reanudarFueraDePausadoLanza() {
        for (var estado : new EstadoEspacio[] {EstadoEspacio.BORRADOR, EstadoEspacio.PUBLICADO, EstadoEspacio.BAJA,
                EstadoEspacio.OCULTO}) {
            var espacio = enEstado(estado);
            assertThatThrownBy(() -> espacio.reanudar(DESPUES)).isInstanceOf(IllegalStateException.class);
            assertThat(espacio.getEstado()).isEqualTo(estado);
            assertThat(espacio.getActualizadoEn()).isEqualTo(AHORA);
        }
    }

    @Test
    @DisplayName("HU-21 R39: dar de baja un PUBLICADO o un PAUSADO lo deja en BAJA con actualizadoEn del reloj")
    void darDeBajaPublicadoOPausado() {
        for (var estado : new EstadoEspacio[] {EstadoEspacio.PUBLICADO, EstadoEspacio.PAUSADO}) {
            var espacio = enEstado(estado);

            espacio.darDeBaja(DESPUES);

            assertThat(espacio.getEstado()).isEqualTo(EstadoEspacio.BAJA);
            assertThat(espacio.getActualizadoEn()).isEqualTo(DESPUES);
            assertThat(espacio.esEditable()).isFalse();
        }
    }

    @Test
    @DisplayName("HU-21 R16: dar de baja un BORRADOR, BAJA u OCULTO lanza y no cambia estado ni actualizadoEn")
    void darDeBajaNoAdmitidaLanza() {
        for (var estado : new EstadoEspacio[] {EstadoEspacio.BORRADOR, EstadoEspacio.BAJA, EstadoEspacio.OCULTO}) {
            var espacio = enEstado(estado);
            assertThatThrownBy(() -> espacio.darDeBaja(DESPUES)).isInstanceOf(IllegalStateException.class);
            assertThat(espacio.getEstado()).isEqualTo(estado);
            assertThat(espacio.getActualizadoEn()).isEqualTo(AHORA);
        }
    }

    private static DatosEspacio conCaracteristicas(Caracteristica... caracteristicas) {
        return new DatosEspacio(DATOS.nombre(), DATOS.tipo(), DATOS.direccion(), DATOS.zona(), DATOS.capacidad(),
                DATOS.precioHora(), DATOS.descripcion(), Set.of(caracteristicas));
    }

    @Test
    @DisplayName("HU-23 R1: crear guarda exactamente las características de la ficha, en el orden fijo")
    void crearGuardaCaracteristicas() {
        var espacio = Espacio.crear(7L, conCaracteristicas(Caracteristica.VESTUARIOS, Caracteristica.TECHADO), AHORA);

        assertThat(espacio.getCaracteristicas())
                .containsExactly(Caracteristica.TECHADO, Caracteristica.VESTUARIOS);
    }

    @Test
    @DisplayName("HU-23 R3: la ficha de HU-07 sin características deja el espacio sin ninguna")
    void fichaSinCaracteristicas() {
        assertThat(Espacio.crear(7L, DATOS, AHORA).getCaracteristicas()).isEmpty();
        assertThat(new DatosEspacio("N", TipoEspacio.CANCHA, "D", "Z", 1, BigDecimal.ONE, "X", null)
                .caracteristicas()).isEmpty();
    }

    @Test
    @DisplayName("HU-23 R2, R7: editar reemplaza las características y anota caracteristicas=[antes]->[nuevas]")
    void editarAnotaCambioDeCaracteristicas() {
        var espacio = Espacio.crear(7L, conCaracteristicas(Caracteristica.ILUMINACION), AHORA);

        var cambios = espacio.editar(
                conCaracteristicas(Caracteristica.VESTUARIOS, Caracteristica.TECHADO), DESPUES);

        assertThat(cambios).containsExactly("caracteristicas=[ILUMINACION]->[TECHADO, VESTUARIOS]");
        assertThat(espacio.getCaracteristicas())
                .containsExactly(Caracteristica.TECHADO, Caracteristica.VESTUARIOS);
        assertThat(espacio.getActualizadoEn()).isEqualTo(DESPUES);
    }

    @Test
    @DisplayName("HU-23 R7: el cambio de características va después de la descripción y desde [] si no tenía")
    void editarCaracteristicasDespuesDeDescripcion() {
        var espacio = Espacio.crear(7L, DATOS, AHORA);
        var nuevos = new DatosEspacio(DATOS.nombre(), DATOS.tipo(), DATOS.direccion(), DATOS.zona(),
                DATOS.capacidad(), DATOS.precioHora(), "Otra descripción más completa.",
                Set.of(Caracteristica.ESTACIONAMIENTO, Caracteristica.TECHADO));

        assertThat(espacio.editar(nuevos, DESPUES))
                .containsExactly("descripcion", "caracteristicas=[]->[TECHADO, ESTACIONAMIENTO]");
    }

    @Test
    @DisplayName("HU-23 R8: el mismo conjunto en otro orden y sin otros cambios no anota nada ni toca actualizadoEn")
    void editarMismasCaracteristicasSinCambios() {
        var espacio = Espacio.crear(7L, conCaracteristicas(Caracteristica.TECHADO, Caracteristica.VESTUARIOS), AHORA);

        var cambios = espacio.editar(conCaracteristicas(Caracteristica.VESTUARIOS, Caracteristica.TECHADO), DESPUES);

        assertThat(cambios).isEmpty();
        assertThat(espacio.getActualizadoEn()).isEqualTo(AHORA);
    }
}
