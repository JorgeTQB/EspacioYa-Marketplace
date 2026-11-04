package pe.edu.ulima.espacioya.pagos.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.pagos.service.pasarela.ReembolsoRealizado;
import pe.edu.ulima.espacioya.pagos.service.reembolso.Disparo;
import pe.edu.ulima.espacioya.pagos.service.reembolso.IntentoReembolso;
import pe.edu.ulima.espacioya.pagos.service.reembolso.RegistroReembolsos;
import pe.edu.ulima.espacioya.pagos.service.reembolso.SolicitudReembolso;
import pe.edu.ulima.espacioya.pagos.service.reembolso.SolicitudesReembolso;

/**
 * Las transacciones {@code REQUIRES_NEW} del comando (HU-16 DT-5) contra PostgreSQL real: lo que confirman, aunque el
 * llamador revierta, y lo que dejan en reembolso, pago y auditoría.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PagosSoporte.Dobles.class})
class RegistroReembolsosIT extends ReembolsosSoporte {

    @Autowired
    private RegistroReembolsos registro;
    @Autowired
    private SolicitudesReembolso solicitudes;
    @Autowired
    private TransactionTemplate transacciones;

    private SolicitudReembolso comando(long reembolso, Disparo disparo) {
        return solicitudes.crear(reembolso, disparo, usuarioId, ip);
    }

    private long reservaDelReembolso(long reembolso) {
        return jdbc.queryForObject("SELECT p.reserva_id FROM pago p JOIN reembolso r ON r.pago_id = p.id "
                + "WHERE r.id = ?", Long.class, reembolso);
    }

    private long pagoDelReembolso(long reembolso) {
        return jdbc.queryForObject("SELECT pago_id FROM reembolso WHERE id = ?", Long.class, reembolso);
    }

    @Test
    @DisplayName("HU-16 R18: iniciar confirma intentos + 1 y ultimo_intento_en aunque el llamador revierta su "
            + "transacción, y devuelve los datos del pago original")
    void iniciarConfirmaElIntento() {
        var id = fallidoSembrado();
        var ahora = reloj.instant();
        var pago = jdbc.queryForMap("SELECT * FROM pago WHERE id = ?", pagoDelReembolso(id));

        var intento = transacciones.execute(estado -> {
            var reclamado = registro.iniciar(id, ahora);
            estado.setRollbackOnly();
            return reclamado;
        });

        assertThat(intento).contains(new IntentoReembolso(id, numero(pago.get("id")), reservaDelReembolso(id),
                (String) pago.get("id_transaccion_pasarela"), intento.orElseThrow().monto(), 2,
                "espacioya-reembolso-" + id));
        assertThat(intento.orElseThrow().monto()).isEqualByComparingTo(MONTO_PAGADO);
        var fila = reembolso(id);
        assertThat(fila.get("estado")).isEqualTo("PENDIENTE");
        assertThat(fila.get("intentos")).isEqualTo(2);
        assertThat(instante(fila.get("ultimo_intento_en"))).isEqualTo(ahora);
        assertThat(estadoDelPagoDelReembolso(id)).isEqualTo("REEMBOLSO_FALLIDO");
        assertThat(filas("auditoria")).isZero();
    }

    @Test
    @DisplayName("HU-16 R5, R18: el primer intento de un PENDIENTE recién creado se admite enseguida")
    void iniciarElPrimero() {
        var id = reembolsoSembrado("PENDIENTE", 0, reloj.instant(), null, "PAGADO");

        var intento = registro.iniciar(id, reloj.instant());

        assertThat(intento).isPresent();
        assertThat(intento.get().numero()).isEqualTo(1);
        assertThat(reembolso(id).get("intentos")).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-16 R21, R22: iniciar sobre un REEMBOLSADO, uno en proceso o uno inexistente devuelve vacío sin "
            + "cambiar nada")
    void iniciarSinMotivo() {
        var reembolsado = reembolsoSembrado("REEMBOLSADO", 1, haceMinutos(60), haceMinutos(60), "REEMBOLSADO");
        var enProceso = reembolsoSembrado("PENDIENTE", 1, haceMinutos(30), haceMinutos(1), "PAGADO");
        var antes = List.of(reembolso(reembolsado), reembolso(enProceso));

        assertThat(registro.iniciar(reembolsado, reloj.instant())).isEmpty();
        assertThat(registro.iniciar(enProceso, reloj.instant())).isEmpty();
        assertThat(registro.iniciar(enProceso + 100000, reloj.instant())).isEmpty();

        assertThat(List.of(reembolso(reembolsado), reembolso(enProceso))).isEqualTo(antes);
        assertThat(filas("auditoria")).isZero();
    }

    @Test
    @DisplayName("HU-16 R10, R11, R36, R38: registrarExito deja reembolso y pago REEMBOLSADOS y audita "
            + "REEMBOLSO_REALIZADO con el detalle exacto")
    void registrarExito() {
        var id = reembolsoSembrado("PENDIENTE", 0, reloj.instant(), null, "PAGADO");
        var intento = registro.iniciar(id, reloj.instant()).orElseThrow();
        reloj.avanzar(Duration.ofSeconds(3));

        registro.registrarExito(intento, new ReembolsoRealizado("sim-reembolso-espacioya-reembolso-" + id),
                comando(id, Disparo.AUTOMATICO));

        var fila = reembolso(id);
        assertThat(fila.get("estado")).isEqualTo("REEMBOLSADO");
        assertThat(fila.get("id_reembolso_pasarela")).isEqualTo("sim-reembolso-espacioya-reembolso-" + id);
        assertThat(fila.get("intentos")).isEqualTo(1);
        assertThat(estadoDelPagoDelReembolso(id)).isEqualTo("REEMBOLSADO");
        var registros = auditorias(RegistroReembolsos.ACCION_REEMBOLSO_REALIZADO);
        assertThat(registros).hasSize(1);
        var auditado = registros.getFirst();
        assertThat(auditado.get("usuario_id")).isEqualTo(usuarioId);
        assertThat(auditado.get("ip")).isEqualTo(ip);
        assertThat(auditado.get("entidad")).isEqualTo("reembolso");
        assertThat(numero(auditado.get("entidad_id"))).isEqualTo(id);
        assertThat(instante(auditado.get("fecha"))).isEqualTo(reloj.instant());
        assertThat(auditado.get("detalle")).isEqualTo("pagoId=" + pagoDelReembolso(id) + ";reservaId="
                + reservaDelReembolso(id) + ";intento=1;disparo=AUTOMATICO;monto=241.50;idReembolso="
                + "sim-reembolso-espacioya-reembolso-" + id);
    }

    @Test
    @DisplayName("HU-16 R17, R37, R38: registrarFallo deja el reembolso FALLIDO, el pago REEMBOLSO_FALLIDO y audita "
            + "REEMBOLSO_FALLIDO sin datos del proveedor")
    void registrarFallo() {
        var id = fallidoSembrado();
        var intento = registro.iniciar(id, reloj.instant()).orElseThrow();
        var admin = crearAdmin(CORREO_ADMIN);

        registro.registrarFallo(intento, solicitudes.crear(id, Disparo.ADMIN, admin, IP_ADMIN));

        var fila = reembolso(id);
        assertThat(fila.get("estado")).isEqualTo("FALLIDO");
        assertThat(fila.get("id_reembolso_pasarela")).isNull();
        assertThat(fila.get("intentos")).isEqualTo(2);
        assertThat(estadoDelPagoDelReembolso(id)).isEqualTo("REEMBOLSO_FALLIDO");
        var registros = auditorias(RegistroReembolsos.ACCION_REEMBOLSO_FALLIDO);
        assertThat(registros).hasSize(1);
        assertThat(registros.getFirst().get("usuario_id")).isEqualTo(admin);
        assertThat(registros.getFirst().get("ip")).isEqualTo(IP_ADMIN);
        assertThat(registros.getFirst().get("detalle")).isEqualTo("pagoId=" + pagoDelReembolso(id) + ";reservaId="
                + reservaDelReembolso(id) + ";intento=2;disparo=ADMIN;monto=241.50");
    }

    @Test
    @DisplayName("HU-16 R17: el fallo del primer intento lleva el pago de PAGADO a REEMBOLSO_FALLIDO")
    void falloDesdePagado() {
        var id = reembolsoSembrado("PENDIENTE", 0, reloj.instant(), null, "PAGADO");
        var intento = registro.iniciar(id, reloj.instant()).orElseThrow();

        registro.registrarFallo(intento, comando(id, Disparo.AUTOMATICO));

        assertThat(reembolso(id).get("estado")).isEqualTo("FALLIDO");
        assertThat(estadoDelPagoDelReembolso(id)).isEqualTo("REEMBOLSO_FALLIDO");
    }
}
