package pe.edu.ulima.espacioya.espacios.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
import pe.edu.ulima.espacioya.espacios.domain.DatosEspacio;
import pe.edu.ulima.espacioya.espacios.domain.Espacio;
import pe.edu.ulima.espacioya.espacios.domain.TipoEspacio;
import pe.edu.ulima.espacioya.espacios.repository.EspacioRepository;

/** Contexto real sin implementación del puerto de reservas (como en producción hasta HU-12). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DisponibilidadServiceImplIT {

    @Autowired
    private DisponibilidadService disponibilidad;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private PerfilArrendadorRepository perfiles;
    @Autowired
    private EspacioRepository espacios;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Clock reloj;

    private long espacioId;
    private long arrendadorId;

    @BeforeEach
    void prepararBase() {
        jdbc.update("DELETE FROM foto_espacio");
        jdbc.update("DELETE FROM espacio");
        jdbc.update("DELETE FROM documento_identidad");
        jdbc.update("DELETE FROM sesion");
        jdbc.update("DELETE FROM auditoria");
        jdbc.update("DELETE FROM perfil_arrendador");
        jdbc.update("DELETE FROM usuario");
        var usuario = usuarios.save(Usuario.registrarArrendador("ana@correo.pe", "x", "Ana Pérez", "987654321",
                "2026-10", reloj.instant()));
        perfiles.save(PerfilArrendador.crear(usuario.getId(), TipoDocumento.RUC, "20100070970", "0".repeat(64),
                "Canchas del Sur SAC", "Av. Javier Prado 4600"));
        var datos = new DatosEspacio("Cancha Los Olivos", TipoEspacio.CANCHA, "Av. Universitaria 1234", "LOS_OLIVOS",
                20, new BigDecimal("80"), "Cancha de grass sintético techada.");
        espacioId = espacios.save(Espacio.crear(usuario.getId(), datos, reloj.instant())).getId();
        arrendadorId = usuario.getId();
    }

    private void franja(LocalDate fecha, int inicio, int fin) {
        jdbc.update("INSERT INTO horario_semanal (espacio_id, dia_semana, hora_inicio, hora_fin) VALUES (?, ?, ?, ?)",
                espacioId, fecha.getDayOfWeek().getValue(), inicio, fin);
    }

    @Test
    @DisplayName("HU-08 R21, R30: contra la base, un espacio publicado da sus horas libres sin el puerto de reservas")
    void horasLibresContraLaBase() {
        var fecha = ZonaHoraria.hoy(reloj).plusDays(7);
        franja(fecha, 14, 16);
        franja(fecha, 8, 10);
        jdbc.update("INSERT INTO bloqueo_fecha (espacio_id, fecha) VALUES (?, ?)", espacioId,
                Date.valueOf(fecha.plusDays(1)));

        assertThat(disponibilidad.horasLibres(espacioId, fecha)).isEmpty();

        jdbc.update("UPDATE espacio SET estado = 'PUBLICADO' WHERE id = ?", espacioId);

        assertThat(disponibilidad.horasLibres(espacioId, fecha)).containsExactly(8, 9, 14, 15);
        assertThat(disponibilidad.horasLibres(espacioId, fecha.plusDays(1))).isEmpty();
        assertThat(disponibilidad.horasLibres(espacioId, fecha.plusDays(56))).isEmpty();
    }

    @Test
    @DisplayName("HU-09 R23, R25: con el arrendador SUSPENDIDA no hay horas libres; al volver a ACTIVA, las mismas que "
            + "antes")
    void horasLibresSegunElEstadoDelArrendador() {
        var fecha = ZonaHoraria.hoy(reloj).plusDays(7);
        franja(fecha, 8, 10);
        jdbc.update("UPDATE espacio SET estado = 'PUBLICADO' WHERE id = ?", espacioId);
        var antes = disponibilidad.horasLibres(espacioId, fecha);
        assertThat(antes).containsExactly(8, 9);

        jdbc.update("UPDATE usuario SET estado = 'SUSPENDIDA' WHERE id = ?", arrendadorId);
        assertThat(disponibilidad.horasLibres(espacioId, fecha)).isEmpty();
        jdbc.update("UPDATE usuario SET estado = 'ELIMINADA' WHERE id = ?", arrendadorId);
        assertThat(disponibilidad.horasLibres(espacioId, fecha)).isEmpty();

        jdbc.update("UPDATE usuario SET estado = 'ACTIVA' WHERE id = ?", arrendadorId);
        assertThat(disponibilidad.horasLibres(espacioId, fecha)).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT estado FROM espacio WHERE id = ?", String.class, espacioId))
                .isEqualTo("PUBLICADO");
    }

    private long otroEspacio(long arrendador, String estado) {
        var datos = new DatosEspacio("Otro espacio", TipoEspacio.CANCHA, "Av. Universitaria 1234", "LOS_OLIVOS",
                20, new BigDecimal("80"), "Cancha de grass sintético techada.");
        var id = espacios.save(Espacio.crear(arrendador, datos, reloj.instant())).getId();
        jdbc.update("UPDATE espacio SET estado = ? WHERE id = ?", estado, id);
        return id;
    }

    private void franja(long espacio, LocalDate fecha, int inicio, int fin) {
        jdbc.update("INSERT INTO horario_semanal (espacio_id, dia_semana, hora_inicio, hora_fin) VALUES (?, ?, ?, ?)",
                espacio, fecha.getDayOfWeek().getValue(), inicio, fin);
    }

    private Set<Long> conHorasLibres(List<Long> ids, LocalDate fecha) {
        return ids.stream().filter(id -> !disponibilidad.horasLibres(id, fecha).isEmpty())
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("HU-10 R30: contra la base, conBloquesLibres da exactamente los ids con horasLibres no vacía")
    void conBloquesLibresEquivaleAHorasLibres() {
        var hoy = ZonaHoraria.hoy(reloj);
        var fecha = hoy.plusDays(8);
        var suspendido = usuarios.save(Usuario.registrarArrendador("beto@correo.pe", "x", "Beto Ruiz", "987654322",
                "2026-10", reloj.instant()));
        perfiles.save(PerfilArrendador.crear(suspendido.getId(), TipoDocumento.RUC, "20100070971", "1".repeat(64),
                "Estadios del Norte SAC", "Av. Túpac Amaru 100"));
        jdbc.update("UPDATE usuario SET estado = 'SUSPENDIDA' WHERE id = ?", suspendido.getId());

        jdbc.update("UPDATE espacio SET estado = 'PUBLICADO' WHERE id = ?", espacioId);
        franja(espacioId, fecha, 8, 10);
        var sinHorario = otroEspacio(arrendadorId, "PUBLICADO");
        var bloqueado = otroEspacio(arrendadorId, "PUBLICADO");
        franja(bloqueado, fecha, 8, 10);
        jdbc.update("INSERT INTO bloqueo_fecha (espacio_id, fecha) VALUES (?, ?)", bloqueado, Date.valueOf(fecha));
        var borrador = otroEspacio(arrendadorId, "BORRADOR");
        franja(borrador, fecha, 8, 10);
        var deSuspendido = otroEspacio(suspendido.getId(), "PUBLICADO");
        franja(deSuspendido, fecha, 8, 10);
        // Hoy, la franja 0–1 ya empezó siempre: sin horas libres. (fecha = hoy + 8 cae otro día de la semana.)
        var horasPasadas = otroEspacio(arrendadorId, "PUBLICADO");
        franja(horasPasadas, hoy, 0, 1);
        var ids = List.of(espacioId, sinHorario, bloqueado, borrador, deSuspendido, horasPasadas, 999_999L);

        assertThat(disponibilidad.conBloquesLibres(ids, fecha)).isEqualTo(conHorasLibres(ids, fecha))
                .containsExactly(espacioId);
        assertThat(disponibilidad.conBloquesLibres(ids, hoy)).isEqualTo(conHorasLibres(ids, hoy)).isEmpty();
    }
}
