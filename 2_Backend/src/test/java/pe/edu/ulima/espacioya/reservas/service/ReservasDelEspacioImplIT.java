package pe.edu.ulima.espacioya.reservas.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;
import pe.edu.ulima.espacioya.espacios.service.puertos.BloqueReservado;
import pe.edu.ulima.espacioya.espacios.service.puertos.ReservasDelEspacio;

/** El puerto real contra PostgreSQL. Comparte contexto con otras IT: las filas de reserva se borran al terminar. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReservasDelEspacioImplIT {

    @Autowired
    private ReservasDelEspacio puerto;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private PerfilArrendadorRepository perfiles;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Clock reloj;

    private long usuarioId;
    private long espacioId;
    private LocalDate fecha;

    @BeforeEach
    void prepararBase() {
        limpiar();
        var arrendador = usuarios.save(Usuario.registrarArrendador("ana@correo.pe", "x", "Ana Pérez", "987654321",
                "2026-10", reloj.instant()));
        perfiles.save(PerfilArrendador.crear(arrendador.getId(), TipoDocumento.RUC, "20100070970", "0".repeat(64),
                "Canchas del Sur SAC", "Av. Javier Prado 4600"));
        usuarioId = usuarios.save(Usuario.registrar("carla@correo.pe", "x", "2026-10", reloj.instant())).getId();
        espacioId = espacio(arrendador.getId());
        fecha = ZonaHoraria.hoy(reloj).plusDays(7);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM reserva");
        jdbc.update("DELETE FROM foto_espacio");
        jdbc.update("DELETE FROM espacio");
        jdbc.update("DELETE FROM documento_identidad");
        jdbc.update("DELETE FROM sesion");
        jdbc.update("DELETE FROM auditoria");
        jdbc.update("DELETE FROM perfil_arrendador");
        jdbc.update("DELETE FROM usuario");
    }

    private long espacio(long arrendadorId) {
        return jdbc.queryForObject("INSERT INTO espacio (arrendador_id, nombre, tipo, direccion, zona, capacidad, "
                        + "precio_hora, descripcion, estado, creado_en, actualizado_en) VALUES (?, ?, ?, ?, ?, ?, ?, "
                        + "?, ?, now(), now()) RETURNING id", Long.class, arrendadorId, "Cancha Los Olivos", "CANCHA",
                "Av. Universitaria 1234, Los Olivos", "LOS_OLIVOS", 22, new BigDecimal("80.00"),
                "Cancha de grass sintético con iluminación.", "PUBLICADO");
    }

    private long reserva(String estado, Instant expiraEn) {
        var creadaEn = expiraEn.minus(Duration.ofMinutes(15));
        return jdbc.queryForObject("INSERT INTO reserva (usuario_id, espacio_id, estado, precio_hora_aplicado, "
                        + "monto_total, creada_en, expira_en) VALUES (?, ?, ?, 80.00, 80.00, ?, ?) RETURNING id",
                Long.class, usuarioId, espacioId, estado, Timestamp.from(creadaEn), Timestamp.from(expiraEn));
    }

    private void bloque(long reservaId, long espacio, LocalDate dia, int hora, boolean activo) {
        jdbc.update("INSERT INTO reserva_bloque (reserva_id, espacio_id, fecha, hora_inicio, activo) "
                + "VALUES (?, ?, ?, ?, ?)", reservaId, espacio, Date.valueOf(dia), hora, activo);
    }

    private Instant enElFuturo() {
        return reloj.instant().plus(Duration.ofMinutes(10));
    }

    private Instant enElPasado() {
        return reloj.instant().minus(Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("HU-12 R6: una vigente retiene con pagado = false y una CONFIRMADA con pagado = true")
    void vigenteYConfirmada() {
        bloque(reserva("PENDIENTE_PAGO", enElFuturo()), espacioId, fecha, 8, true);
        bloque(reserva("CONFIRMADA", enElPasado()), espacioId, fecha, 9, true);

        assertThat(puerto.bloquesReservados(espacioId, fecha, fecha)).containsExactlyInAnyOrder(
                new BloqueReservado(fecha, 8, false), new BloqueReservado(fecha, 9, true));
    }

    @Test
    @DisplayName("HU-12 R6: devuelve todas las filas activas de cada reserva")
    void todasLasFilasActivas() {
        var reserva = reserva("PENDIENTE_PAGO", enElFuturo());
        bloque(reserva, espacioId, fecha, 8, true);
        bloque(reserva, espacioId, fecha, 9, true);
        bloque(reserva, espacioId, fecha, 10, false);

        assertThat(puerto.bloquesReservados(espacioId, fecha, fecha)).containsExactlyInAnyOrder(
                new BloqueReservado(fecha, 8, false), new BloqueReservado(fecha, 9, false));
    }

    @Test
    @DisplayName("HU-12 R12: una vencida con activo = true no aparece aunque nadie la haya expirado")
    void vencidaSinProcesar() {
        bloque(reserva("PENDIENTE_PAGO", enElPasado()), espacioId, fecha, 8, true);

        assertThat(puerto.bloquesReservados(espacioId, fecha, fecha)).isEmpty();
        assertThat(puerto.bloquesReservadosDelDia(List.of(espacioId), fecha)).isEmpty();
    }

    @Test
    @DisplayName("HU-12 R6: una EXPIRADA o una CANCELADA no aparecen")
    void reservasNoActivas() {
        bloque(reserva("EXPIRADA", enElPasado()), espacioId, fecha, 8, false);
        bloque(reserva("CANCELADA", enElPasado()), espacioId, fecha, 9, false);

        assertThat(puerto.bloquesReservados(espacioId, fecha, fecha)).isEmpty();
    }

    @Test
    @DisplayName("HU-12 R6: los bloques de otro espacio no aparecen")
    void otroEspacio() {
        var otro = espacio(jdbc.queryForObject("SELECT arrendador_id FROM espacio WHERE id = ?", Long.class,
                espacioId));
        bloque(reserva("PENDIENTE_PAGO", enElFuturo()), otro, fecha, 8, true);

        assertThat(puerto.bloquesReservados(espacioId, fecha, fecha)).isEmpty();
    }

    @Test
    @DisplayName("HU-12 R6: el rango incluye sus dos extremos y nada fuera de ellos")
    void extremosDelRango() {
        var reserva = reserva("PENDIENTE_PAGO", enElFuturo());
        bloque(reserva, espacioId, fecha.minusDays(1), 8, true);
        bloque(reserva, espacioId, fecha, 8, true);
        bloque(reserva, espacioId, fecha.plusDays(2), 8, true);
        bloque(reserva, espacioId, fecha.plusDays(3), 8, true);

        assertThat(puerto.bloquesReservados(espacioId, fecha, fecha.plusDays(2))).containsExactlyInAnyOrder(
                new BloqueReservado(fecha, 8, false), new BloqueReservado(fecha.plusDays(2), 8, false));
    }

    @Test
    @DisplayName("HU-12 R7, R12: el lote da los mismos bloques por espacio; los ids sin reservas faltan en el mapa")
    void loteContraLaBase() {
        var conReservas = espacioId;
        var sinReservas = espacio(jdbc.queryForObject("SELECT arrendador_id FROM espacio WHERE id = ?", Long.class,
                espacioId));
        bloque(reserva("CONFIRMADA", enElPasado()), conReservas, fecha, 8, true);
        bloque(reserva("PENDIENTE_PAGO", enElFuturo()), conReservas, fecha, 9, true);
        bloque(reserva("PENDIENTE_PAGO", enElFuturo()), conReservas, fecha.plusDays(1), 9, true);

        var porEspacio = puerto.bloquesReservadosDelDia(List.of(conReservas, sinReservas), fecha);

        assertThat(porEspacio).containsOnlyKeys(conReservas);
        assertThat(porEspacio.get(conReservas)).containsExactlyInAnyOrder(new BloqueReservado(fecha, 8, true),
                new BloqueReservado(fecha, 9, false));
    }
}
