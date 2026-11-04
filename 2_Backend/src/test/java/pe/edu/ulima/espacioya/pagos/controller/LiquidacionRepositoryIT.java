package pe.edu.ulima.espacioya.pagos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.pagos.domain.EstadoLiquidacion;
import pe.edu.ulima.espacioya.pagos.domain.Liquidacion;
import pe.edu.ulima.espacioya.pagos.domain.LiquidacionDetalle;
import pe.edu.ulima.espacioya.pagos.domain.Pago;
import pe.edu.ulima.espacioya.pagos.repository.LiquidacionDetalleRepository;
import pe.edu.ulima.espacioya.pagos.repository.LiquidacionRepository;
import pe.edu.ulima.espacioya.pagos.repository.PagoRepository;

/** Las tablas {@code liquidacion} y {@code liquidacion_detalle} y sus consultas contra PostgreSQL real (T1, T8). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PagosSoporte.Dobles.class})
class LiquidacionRepositoryIT extends LiquidacionesSoporte {

    private static final String INSERTAR = "INSERT INTO liquidacion (arrendador_id, periodo, monto_bruto, comision, "
            + "monto_neto, estado, simulada, generada_en, generada_por, numero_operacion, pagada_en, pagada_por, "
            + "banco_destino, cuenta_destino) VALUES (?, ?, ?, ?, ?, ?, false, ?, ?, ?, ?, ?, ?, ?)";
    private static final String INSERTAR_LINEA = "INSERT INTO liquidacion_detalle (liquidacion_id, reserva_id, "
            + "pago_id, monto, simulado) VALUES (?, ?, ?, ?, false)";

    @Autowired
    private LiquidacionRepository liquidacionesRepo;
    @Autowired
    private LiquidacionDetalleRepository detalles;
    @Autowired
    private PagoRepository pagos;
    @Autowired
    private TransactionTemplate transacciones;

    private void insertar(String bruto, String comision, String neto, String estado, String operacion,
                          Instant pagadaEn) {
        var ahora = reloj.instant();
        var pagada = "PAGADA".equals(estado) || operacion != null;
        jdbc.update(INSERTAR, arrendadorId, Date.valueOf(LocalDate.ofInstant(ahora, ZonaHoraria.LIMA)),
                new BigDecimal(bruto),
                new BigDecimal(comision), new BigDecimal(neto), estado, Timestamp.from(ahora), usuarioId, operacion,
                pagadaEn == null ? null : Timestamp.from(pagadaEn), pagada ? usuarioId : null,
                pagada ? BANCO : null, pagada ? "****1234" : null);
    }

    private long liquidacionPersistida(EstadoLiquidacion estado, Instant generadaEn) {
        return liquidacionSembrada(arrendadorId, estado.name(), "241.50", generadaEn, usuarioId);
    }

    // ── T1: restricciones (R12, R23) ─────────────────────────────────────

    @Test
    @DisplayName("HU-19 R12, R23: una reserva o un pago no pueden figurar en dos líneas de liquidación")
    void unaReservaYUnPagoUnaSolaVez() {
        var liquidacion = liquidacionPersistida(EstadoLiquidacion.GENERADA, reloj.instant());
        var reserva = reservaSinBloques("CONFIRMADA");
        var otraReserva = reservaSinBloques("CONFIRMADA");
        var pago = pagoSembrado(reserva, transaccionReal(), "PAGADO");
        var rechazado = pagoSembrado(reserva, transaccionReal(), "RECHAZADO");
        jdbc.update(INSERTAR_LINEA, liquidacion, reserva, pago, new BigDecimal("241.50"));

        assertThatThrownBy(() -> jdbc.update(INSERTAR_LINEA, liquidacion, reserva, rechazado,
                new BigDecimal("241.50"))).hasMessageContaining("uq_liquidacion_detalle_reserva");
        assertThatThrownBy(() -> jdbc.update(INSERTAR_LINEA, liquidacion, otraReserva, pago,
                new BigDecimal("241.50"))).hasMessageContaining("uq_liquidacion_detalle_pago");
        assertThatThrownBy(() -> jdbc.update(INSERTAR_LINEA, liquidacion, otraReserva, rechazado,
                BigDecimal.ZERO)).hasMessageContaining("ck_liquidacion_detalle_monto");
    }

    @Test
    @DisplayName("HU-19 R23: los CHECK rechazan un estado fuera de los tres, montos que no cumplen 10 % HALF_UP y "
            + "neto, una PAGADA incompleta, un número de operación inválido y fechas invertidas")
    void restriccionesDeLaTabla() {
        var ahora = reloj.instant();

        insertar("10.05", "1.01", "9.04", "GENERADA", null, null);
        assertThat(filas("liquidacion")).isEqualTo(1);

        assertThatThrownBy(() -> insertar("100.00", "10.00", "90.00", "RETENIDA", null, null))
                .hasMessageContaining("ck_liquidacion_estado");
        assertThatThrownBy(() -> insertar("10.05", "1.00", "9.05", "GENERADA", null, null))
                .hasMessageContaining("ck_liquidacion_montos");
        assertThatThrownBy(() -> insertar("100.00", "10.00", "91.00", "GENERADA", null, null))
                .hasMessageContaining("ck_liquidacion_montos");
        assertThatThrownBy(() -> insertar("0.00", "0.00", "0.00", "GENERADA", null, null))
                .hasMessageContaining("ck_liquidacion_montos");
        assertThatThrownBy(() -> insertar("100.00", "10.00", "90.00", "PAGADA", null, ahora))
                .hasMessageContaining("ck_liquidacion_pagada");
        assertThatThrownBy(() -> insertar("100.00", "10.00", "90.00", "GENERADA", "OP-1234", ahora))
                .hasMessageContaining("ck_liquidacion_pagada");
        assertThatThrownBy(() -> insertar("100.00", "10.00", "90.00", "PAGADA", "OP 1234", ahora))
                .hasMessageContaining("ck_liquidacion_numero_operacion");
        assertThatThrownBy(() -> insertar("100.00", "10.00", "90.00", "PAGADA", "OP-1234",
                ahora.minus(Duration.ofSeconds(1)))).hasMessageContaining("ck_liquidacion_fechas");
        assertThat(filas("liquidacion")).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-19 R23: una liquidación debe pertenecer a un perfil de arrendador existente")
    void exigeElPerfil() {
        var liquidacion = Liquidacion.generar(usuarioId, List.of(new BigDecimal("241.50")), true, false,
                reloj.instant(), usuarioId);

        assertThatThrownBy(() -> liquidacionesRepo.saveAndFlush(liquidacion))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("HU-19 R13, R23: la entidad guarda montos que cumplen los CHECK y se relee igual")
    void guardaYReleeLaEntidad() {
        var guardada = liquidacionesRepo.saveAndFlush(Liquidacion.generar(arrendadorId,
                List.of(new BigDecimal("241.50"), new BigDecimal("80.50")), false, true, reloj.instant(), usuarioId));

        var fila = liquidacion(guardada.getId());

        assertThat(fila.get("estado")).isEqualTo("PENDIENTE_CUENTA");
        assertThat(new BigDecimal(fila.get("monto_bruto").toString())).isEqualByComparingTo("322.00");
        assertThat(new BigDecimal(fila.get("comision").toString())).isEqualByComparingTo("32.20");
        assertThat(new BigDecimal(fila.get("monto_neto").toString())).isEqualByComparingTo("289.80");
        assertThat(fila.get("simulada")).isEqualTo(true);
        Long bloqueada = transacciones.execute(estado -> liquidacionesRepo.findParaActualizar(guardada.getId())
                .map(Liquidacion::getArrendadorId).orElse(null));
        assertThat(bloqueada).isEqualTo(arrendadorId);
    }

    // ── T8: consultas (R9, R12, R21, R22, R33, R34, R42, R47) ────────────

    @Test
    @DisplayName("HU-19 R9, R12, R20, R21: pagadosSinLiquidar solo da PAGADO sin reembolso ni liquidar, por reserva, y "
            + "los sim- solo si se piden")
    void pagadosSinLiquidar() {
        var simulada = reservaSinBloques("CONFIRMADA");
        pagoSembrado(simulada, transaccionSimulada(), "PAGADO");
        var real = reservaSinBloques("CONFIRMADA");
        pagoSembrado(real, transaccionReal(), "PAGADO");
        var conReembolso = reservaSinBloques("CANCELADA");
        var pagoConReembolso = pagoSembrado(conReembolso, transaccionReal(), "PAGADO");
        jdbc.update("INSERT INTO reembolso (pago_id, monto, origen, estado, intentos, creado_en) "
                + "VALUES (?, 241.50, 'CANCELACION', 'PENDIENTE', 0, now())", pagoConReembolso);
        var liquidada = reservaSinBloques("CONFIRMADA");
        var pagoLiquidado = pagoSembrado(liquidada, transaccionReal(), "PAGADO");
        jdbc.update(INSERTAR_LINEA, liquidacionPersistida(EstadoLiquidacion.GENERADA, reloj.instant()), liquidada,
                pagoLiquidado, new BigDecimal("241.50"));
        for (var estado : List.of("RECHAZADO", "PAGADO_TARDIO", "MONTO_DISTINTO", "REEMBOLSADO",
                "REEMBOLSO_FALLIDO")) {
            pagoSembrado(reservaSinBloques("CONFIRMADA"), transaccionReal(), estado);
        }

        assertThat(pagos.pagadosSinLiquidar(true)).extracting(Pago::getReservaId).containsExactly(simulada, real);
        assertThat(pagos.pagadosSinLiquidar(false)).extracting(Pago::getReservaId).containsExactly(real);
    }

    @Test
    @DisplayName("HU-19 R22: bloquearGeneracion exige una transacción y dentro de ella toma el bloqueo consultivo")
    void bloqueoConsultivo() {
        assertThatThrownBy(() -> liquidacionesRepo.bloquearGeneracion(LiquidacionRepository.CLAVE_GENERACION))
                .isInstanceOf(IllegalTransactionStateException.class);

        var bloqueado = transacciones.execute(estado -> {
            liquidacionesRepo.bloquearGeneracion(LiquidacionRepository.CLAVE_GENERACION);
            return jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' "
                    + "AND objid::bigint = ? AND pid = pg_backend_pid()", Long.class,
                    LiquidacionRepository.CLAVE_GENERACION);
        });

        assertThat(bloqueado).isEqualTo(1L);
    }

    @Test
    @DisplayName("HU-19 R33, R34: findByEstadoIn pagina de 20 en 20 en el orden pedido con hasNext")
    void paginaPorEstado() {
        var base = reloj.instant();
        for (var i = 0; i < 21; i++) {
            liquidacionPersistida(i % 2 == 0 ? EstadoLiquidacion.GENERADA : EstadoLiquidacion.PENDIENTE_CUENTA,
                    base.minus(Duration.ofMinutes(30 - i)));
        }
        var orden = Sort.by("generadaEn", "id");

        var primera = liquidacionesRepo.findByEstadoIn(EstadoLiquidacion.POR_PAGAR, PageRequest.of(0, 20, orden));
        var segunda = liquidacionesRepo.findByEstadoIn(EstadoLiquidacion.POR_PAGAR, PageRequest.of(1, 20, orden));

        assertThat(primera.getContent()).hasSize(20);
        assertThat(primera.hasNext()).isTrue();
        assertThat(primera.getContent()).extracting(Liquidacion::getGeneradaEn).isSorted();
        assertThat(segunda.getContent()).hasSize(1);
        assertThat(segunda.hasNext()).isFalse();
        assertThat(liquidacionesRepo.findByEstadoIn(List.of(EstadoLiquidacion.PAGADA), PageRequest.of(0, 20, orden))
                .getContent()).isEmpty();
    }

    @Test
    @DisplayName("HU-19 R42, R47: findByArrendadorId solo da las del arrendador y existsByArrendadorIdAndEstadoIn "
            + "detecta las por pagar")
    void delArrendador() {
        var otro = otroArrendador();
        var mia = liquidacionPersistida(EstadoLiquidacion.GENERADA, reloj.instant());
        liquidacionSembrada(otro, "PENDIENTE_CUENTA", "100.00", reloj.instant(), usuarioId);

        assertThat(liquidacionesRepo.findByArrendadorId(arrendadorId, PageRequest.of(0, 20)).getContent())
                .extracting(Liquidacion::getId).containsExactly(mia);
        assertThat(liquidacionesRepo.existsByArrendadorIdAndEstadoIn(arrendadorId, EstadoLiquidacion.POR_PAGAR))
                .isTrue();
        assertThat(liquidacionesRepo.existsByArrendadorIdAndEstadoIn(usuarioId, EstadoLiquidacion.POR_PAGAR))
                .isFalse();
    }

    @Test
    @DisplayName("HU-19 R12: las líneas de varias liquidaciones salen en una consulta, por liquidación y reserva")
    void lineasPorLote() {
        var primera = liquidacionPersistida(EstadoLiquidacion.GENERADA, reloj.instant());
        var segunda = liquidacionPersistida(EstadoLiquidacion.GENERADA, reloj.instant());
        var a = reservaSinBloques("CONFIRMADA");
        var b = reservaSinBloques("CONFIRMADA");
        var c = reservaSinBloques("CONFIRMADA");
        detalles.saveAllAndFlush(List.of(
                LiquidacionDetalle.de(segunda, a, pagoSembrado(a, transaccionReal(), "PAGADO"),
                        new BigDecimal("241.50"), false),
                LiquidacionDetalle.de(primera, c, pagoSembrado(c, transaccionReal(), "PAGADO"),
                        new BigDecimal("241.50"), false),
                LiquidacionDetalle.de(primera, b, pagoSembrado(b, transaccionSimulada(), "PAGADO"),
                        new BigDecimal("241.50"), true)));

        assertThat(detalles.findByLiquidacionIdInOrderByLiquidacionIdAscReservaIdAsc(List.of(primera, segunda)))
                .extracting(LiquidacionDetalle::getReservaId).containsExactly(b, c, a);
    }
}
