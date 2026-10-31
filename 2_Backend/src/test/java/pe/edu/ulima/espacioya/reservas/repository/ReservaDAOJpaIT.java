package pe.edu.ulima.espacioya.reservas.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

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
import pe.edu.ulima.espacioya.reservas.domain.ReservaConHorario;

/** El DAO real contra PostgreSQL (★ HU-14). Comparte contexto con otras IT: las filas se borran al terminar. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReservaDAOJpaIT {

    @Autowired
    private ReservaDAO dao;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private PerfilArrendadorRepository perfiles;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Clock reloj;

    private long usuarioId;
    private long otroUsuarioId;
    private long espacioId;
    private long otroEspacioId;
    private long espacioAjenoId;
    private LocalDate fecha;

    @BeforeEach
    void prepararBase() {
        limpiar();
        var arrendador = arrendador("ana.dao@correo.pe", "20100070970", "1");
        var otroArrendador = arrendador("luis.dao@correo.pe", "20100070971", "2");
        usuarioId = usuarios.save(Usuario.registrar("carla.dao@correo.pe", "x", "2026-10", reloj.instant())).getId();
        otroUsuarioId = usuarios.save(Usuario.registrar("beto.dao@correo.pe", "x", "2026-10", reloj.instant()))
                .getId();
        espacioId = espacio(arrendador);
        otroEspacioId = espacio(arrendador);
        espacioAjenoId = espacio(otroArrendador);
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

    private long arrendador(String correo, String documento, String huella) {
        var cuenta = usuarios.save(Usuario.registrarArrendador(correo, "x", "Ana Pérez", "987654321", "2026-10",
                reloj.instant()));
        perfiles.save(PerfilArrendador.crear(cuenta.getId(), TipoDocumento.RUC, documento, huella.repeat(64),
                "Canchas del Sur SAC", "Av. Javier Prado 4600"));
        return cuenta.getId();
    }

    private long espacio(long arrendadorId) {
        return jdbc.queryForObject("INSERT INTO espacio (arrendador_id, nombre, tipo, direccion, zona, capacidad, "
                        + "precio_hora, descripcion, estado, creado_en, actualizado_en) VALUES (?, ?, ?, ?, ?, ?, ?, "
                        + "?, ?, now(), now()) RETURNING id", Long.class, arrendadorId, "Cancha Los Olivos", "CANCHA",
                "Av. Universitaria 1234, Los Olivos", "LOS_OLIVOS", 22, new BigDecimal("80.00"),
                "Cancha de grass sintético con iluminación.", "PUBLICADO");
    }

    private long reserva(long usuario, long espacio, String estado) {
        var expiraEn = reloj.instant().plus(Duration.ofMinutes(10));
        return jdbc.queryForObject("INSERT INTO reserva (usuario_id, espacio_id, estado, precio_hora_aplicado, "
                        + "monto_total, creada_en, expira_en) VALUES (?, ?, ?, 80.00, 80.00, ?, ?) RETURNING id",
                Long.class, usuario, espacio, estado, Timestamp.from(expiraEn.minus(Duration.ofMinutes(15))),
                Timestamp.from(expiraEn));
    }

    private void bloques(long reservaId, long espacio, LocalDate dia, boolean activo, int... horas) {
        for (var hora : horas) {
            jdbc.update("INSERT INTO reserva_bloque (reserva_id, espacio_id, fecha, hora_inicio, activo) "
                    + "VALUES (?, ?, ?, ?, ?)", reservaId, espacio, Date.valueOf(dia), hora, activo);
        }
    }

    private static List<Long> ids(List<ReservaConHorario> reservas) {
        return reservas.stream().map(reserva -> reserva.reserva().getId()).toList();
    }

    private static ReservaConHorario de(List<ReservaConHorario> reservas, long id) {
        return reservas.stream().filter(reserva -> reserva.reserva().getId() == id).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("HU-14 R33: delUsuario devuelve todas las reservas del usuario, en cualquier estado, y nunca las de "
            + "otro usuario")
    void delUsuarioSoloLasSuyas() {
        var pendiente = reserva(usuarioId, espacioId, "PENDIENTE_PAGO");
        bloques(pendiente, espacioId, fecha, true, 8);
        var confirmada = reserva(usuarioId, espacioAjenoId, "CONFIRMADA");
        bloques(confirmada, espacioAjenoId, fecha, true, 9);
        var expirada = reserva(usuarioId, espacioId, "EXPIRADA");
        bloques(expirada, espacioId, fecha, false, 10);
        var ajena = reserva(otroUsuarioId, espacioId, "CONFIRMADA");
        bloques(ajena, espacioId, fecha, true, 11);

        assertThat(ids(dao.delUsuario(usuarioId))).containsExactlyInAnyOrder(pendiente, confirmada, expirada);
        assertThat(ids(dao.delUsuario(otroUsuarioId))).containsExactly(ajena);
    }

    @Test
    @DisplayName("HU-14 R8, R33: el horario sale de todas las filas, también las inactivas, y 23 da horaFin = 24")
    void horarioConFilasInactivasYMedianoche() {
        var expirada = reserva(usuarioId, espacioId, "EXPIRADA");
        bloques(expirada, espacioId, fecha.minusDays(14), false, 14, 15, 16);
        var ultima = reserva(usuarioId, espacioId, "CONFIRMADA");
        bloques(ultima, espacioId, fecha, true, 22, 23);

        var encontradas = dao.delUsuario(usuarioId);

        assertThat(de(encontradas, expirada)).satisfies(reserva -> {
            assertThat(reserva.fecha()).isEqualTo(fecha.minusDays(14));
            assertThat(reserva.horaInicio()).isEqualTo(14);
            assertThat(reserva.horaFin()).isEqualTo(17);
        });
        assertThat(de(encontradas, ultima)).satisfies(reserva -> {
            assertThat(reserva.fecha()).isEqualTo(fecha);
            assertThat(reserva.horaInicio()).isEqualTo(22);
            assertThat(reserva.horaFin()).isEqualTo(24);
        });
    }

    @Test
    @DisplayName("HU-14 R34: confirmadasDeEspacios devuelve solo CONFIRMADA de esos espacios, nunca de otros")
    void confirmadasSoloDeEsosEspacios() {
        var deUno = reserva(usuarioId, espacioId, "CONFIRMADA");
        bloques(deUno, espacioId, fecha, true, 8);
        var deOtro = reserva(otroUsuarioId, otroEspacioId, "CONFIRMADA");
        bloques(deOtro, otroEspacioId, fecha, true, 8);
        var ajena = reserva(usuarioId, espacioAjenoId, "CONFIRMADA");
        bloques(ajena, espacioAjenoId, fecha, true, 8);
        for (var estado : List.of("PENDIENTE_PAGO", "EXPIRADA", "CANCELADA")) {
            bloques(reserva(usuarioId, espacioId, estado), espacioId, fecha, false, 12);
        }

        var encontradas = dao.confirmadasDeEspacios(Set.of(espacioId, otroEspacioId));

        assertThat(ids(encontradas)).containsExactlyInAnyOrder(deUno, deOtro);
        assertThat(de(encontradas, deUno).horaFin()).isEqualTo(9);
        assertThat(ids(dao.confirmadasDeEspacios(Set.of(espacioAjenoId)))).containsExactly(ajena);
    }

    @Test
    @DisplayName("HU-14 R34: con la colección vacía devuelve vacío")
    void coleccionVacia() {
        bloques(reserva(usuarioId, espacioId, "CONFIRMADA"), espacioId, fecha, true, 8);

        assertThat(dao.confirmadasDeEspacios(Set.of())).isEmpty();
    }

    @Test
    @DisplayName("HU-14 R33: un usuario sin reservas recibe una lista vacía")
    void usuarioSinReservas() {
        assertThat(dao.delUsuario(usuarioId)).isEmpty();
    }
}
