package pe.edu.ulima.espacioya.administracion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.administracion.service.ReporteAdminService;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCancelacionUsuario;
import pe.edu.ulima.espacioya.cuentas.service.NotificadorCorreo;

/**
 * HU-27: ocultar un espacio por fraude cancela y reembolsa sus reservas por iniciar (decisión del dueño, P9 y P17),
 * contra PostgreSQL real y la pasarela simulada, que aprueba los reembolsos de los pagos {@code sim-}. El reloj está
 * fijo en el miércoles 2026-11-04 a las 10:00 de Lima.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({TestcontainersConfiguration.class, ReclamosSoporte.Dobles.class})
class OcultarPorFraudeIT extends ReportesSoporte {

    private static final String MOTIVO = "Cobra reservas de un local que no existe.";

    @MockitoSpyBean
    NotificadorCorreo notificador;

    private long admin;

    private String tokenAdmin() throws Exception {
        admin = crearAdmin();
        return token(CORREO_ADMIN);
    }

    private String estadoReserva(long reserva) {
        return estadoDe("reserva", reserva);
    }

    private void vigente(long reserva) {
        jdbc.update("UPDATE reserva SET expira_en = ? WHERE id = ?",
                Timestamp.from(reloj.instant().plus(Duration.ofMinutes(10))), reserva);
    }

    private void cancelada(long reserva) {
        jdbc.update("UPDATE reserva SET cancelada_por = 'USUARIO', cancelada_en = ?, monto_reembolso = 0 WHERE id = ?",
                Timestamp.from(reloj.instant().minus(Duration.ofDays(1))), reserva);
        jdbc.update("UPDATE reserva_bloque SET activo = false WHERE reserva_id = ?", reserva);
    }

    private int bloquesActivos(long reserva) {
        return jdbc.queryForObject("SELECT count(*) FROM reserva_bloque WHERE reserva_id = ? AND activo", Integer.class,
                reserva);
    }

    @Test
    @DisplayName("HU-27 R45, R64, R65, R66, R69, R70, R71, R74, R78: por fraude cancela solo la reserva por iniciar, la "
            + "reembolsa al 100 % y avisa al usuario y una vez al arrendador")
    void ocultaPorFraude() throws Exception {
        var sesion = tokenAdmin();
        var id = sembrarReporte(usuario, "ESPACIO", espacio, "SPAM", reloj.instant());
        var manana = confirmada(hoy.plusDays(1), 10, 11, 12);
        pago(manana, "PAGADO");
        var enCurso = confirmada(hoy, 9, 10);
        var terminada = confirmada(hoy.minusDays(1), 8);
        var pendiente = reserva(usuario, espacio, "PENDIENTE_PAGO", hoy.plusDays(2), 8);
        vigente(pendiente);
        var yaCancelada = reserva(usuario, espacio, "CANCELADA", hoy.plusDays(2), 15);
        cancelada(yaCancelada);

        decidir(sesion, id, "OCULTAR_POR_FRAUDE", MOTIVO).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("OCULTADO"))
                .andExpect(jsonPath("$.decision").value("OCULTAR_POR_FRAUDE"))
                .andExpect(jsonPath("$.reservasCanceladas").value(1))
                .andExpect(jsonPath("$.espacio.estado").value("OCULTO"));

        assertThat(estadoDe("espacio", espacio)).isEqualTo("OCULTO");
        var reporte = reporteGuardado(id);
        assertThat(reporte.get("estado")).isEqualTo("OCULTADO");
        assertThat(reporte.get("por_fraude")).isEqualTo(true);

        var cancelada = jdbc.queryForMap("SELECT * FROM reserva WHERE id = ?", manana);
        assertThat(cancelada.get("estado")).isEqualTo("CANCELADA");
        assertThat(cancelada.get("cancelada_por")).isEqualTo("ADMIN");
        assertThat(cancelada.get("cancelada_en")).isNotNull();
        assertThat(decimal(cancelada.get("monto_reembolso"))).isEqualByComparingTo(MONTO);
        assertThat(bloquesActivos(manana)).isZero();

        assertThat(estadoReserva(enCurso)).isEqualTo("CONFIRMADA");
        assertThat(estadoReserva(terminada)).isEqualTo("CONFIRMADA");
        assertThat(estadoReserva(pendiente)).isEqualTo("PENDIENTE_PAGO");
        assertThat(jdbc.queryForObject("SELECT cancelada_por FROM reserva WHERE id = ?", String.class, yaCancelada))
                .isEqualTo("USUARIO");
        assertThat(bloquesActivos(enCurso)).isEqualTo(2);

        var reembolso = jdbc.queryForMap("SELECT r.* FROM reembolso r JOIN pago p ON p.id = r.pago_id "
                + "WHERE p.reserva_id = ?", manana);
        assertThat(reembolso.get("origen")).isEqualTo("CANCELACION");
        assertThat(reembolso.get("estado")).isEqualTo("REEMBOLSADO");
        assertThat(decimal(reembolso.get("monto"))).isEqualByComparingTo(MONTO);
        assertThat(filas("reembolso")).isEqualTo(1);

        assertThat(auditorias("RESERVA_CANCELADA")).singleElement().satisfies(fila -> {
            assertThat(numero(fila.get("entidad_id"))).isEqualTo(manana);
            assertThat(numero(fila.get("usuario_id"))).isEqualTo(admin);
            assertThat(fila.get("detalle")).isEqualTo("canceladaPor=ADMIN;espacioId=" + espacio
                    + ";politica=MODERACION;porcentaje=100;monto=241.50");
        });
        assertThat(auditorias("REEMBOLSO_SOLICITADO")).hasSize(1);
        assertThat(auditorias("REPORTE_RESUELTO").getFirst().get("detalle")).isEqualTo("tipo=ESPACIO;objetivoId="
                + espacio + ";decision=OCULTAR_POR_FRAUDE;reportes=1;reservasCanceladas=1");

        verify(notificador).notificarCancelacionAlUsuario(eq(CORREO_USUARIO), eq(new AvisoCancelacionUsuario(
                NOMBRE_ESPACIO, hoy.plusDays(1), 10, 13, "ADMIN", 100, new BigDecimal("241.50"))));
        verify(notificador, never()).notificarCancelacionAlArrendador(any(), any());
        assertThat(correos.para(CORREO_ARRENDADOR)).singleElement().satisfies(correo -> assertThat(correo.cuerpo())
                .contains("cancelamos sus 1 reservas confirmadas", "100 %", "no se incluirán en tus liquidaciones",
                        "Motivo: " + MOTIVO));
    }

    @Test
    @DisplayName("HU-27 R68: una reserva cancelada por ADMIN no entra en ninguna liquidación posterior")
    void canceladaNoSeLiquida() throws Exception {
        var sesion = tokenAdmin();
        var id = sembrarReporte(usuario, "ESPACIO", espacio);
        var manana = confirmada(hoy.plusDays(1), 10);
        pago(manana, "PAGADO");
        var terminada = confirmada(hoy.minusDays(3), 8);
        pago(terminada, "PAGADO");
        decidir(sesion, id, "OCULTAR_POR_FRAUDE", MOTIVO).andExpect(status().isOk());

        reloj.avanzar(Duration.ofDays(6));
        try {
            mvc.perform(comoAdmin(token(CORREO_ADMIN), post("/api/admin/liquidaciones")))
                    .andExpect(status().is2xxSuccessful());
        } finally {
            reloj.avanzar(Duration.ofDays(-6));
        }

        assertThat(jdbc.queryForList("SELECT reserva_id FROM liquidacion_detalle", Long.class))
                .containsExactly(terminada);
    }

    @Test
    @DisplayName("HU-27 R63: OCULTAR_POR_FRAUDE sobre un reporte de reseña da 400 en decision sin cambiar nada")
    void fraudeEnResena() throws Exception {
        var sesion = tokenAdmin();
        var resena = resena(crearUsuario(CORREO_OTRO), espacio, "VISIBLE");
        var id = sembrarReporte(usuario, "RESENA", resena);

        decidir(sesion, id, "OCULTAR_POR_FRAUDE", MOTIVO).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("decision"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(ReporteAdminService.MENSAJE_FRAUDE_SOLO_ESPACIOS));

        assertThat(reporteGuardado(id).get("estado")).isEqualTo("POR_REVISAR");
        assertThat(estadoDe("resena", resena)).isEqualTo("VISIBLE");
        assertThat(auditorias("REPORTE_RESUELTO")).isEmpty();
    }

    @Test
    @DisplayName("HU-27 R72: si un reembolso falla (reserva sin pago PAGADO) se revierte toda la decisión")
    void rollbackTotal() throws Exception {
        var sesion = tokenAdmin();
        var id = sembrarReporte(usuario, "ESPACIO", espacio);
        var conPago = confirmada(hoy.plusDays(1), 10);
        pago(conPago, "PAGADO");
        var sinPago = confirmada(hoy.plusDays(2), 10);

        decidir(sesion, id, "OCULTAR_POR_FRAUDE", MOTIVO).andExpect(status().isInternalServerError());

        assertThat(estadoDe("espacio", espacio)).isEqualTo("PUBLICADO");
        assertThat(reporteGuardado(id).get("estado")).isEqualTo("POR_REVISAR");
        assertThat(estadoReserva(conPago)).isEqualTo("CONFIRMADA");
        assertThat(estadoReserva(sinPago)).isEqualTo("CONFIRMADA");
        assertThat(bloquesActivos(conPago)).isEqualTo(1);
        assertThat(filas("reembolso")).isZero();
        assertThat(jdbc.queryForObject("SELECT estado FROM pago WHERE reserva_id = ?", String.class, conPago))
                .isEqualTo("PAGADO");
        for (var accion : List.of("REPORTE_RESUELTO", "OCULTACION_ESPACIO", "RESERVA_CANCELADA",
                "REEMBOLSO_SOLICITADO")) {
            assertThat(auditorias(accion)).as(accion).isEmpty();
        }
        assertThat(correos.enviados()).isEmpty();
        verify(notificador, never()).notificarCancelacionAlUsuario(any(), any());
    }

    @Test
    @DisplayName("HU-27 R73: la cancelación del usuario y el ocultamiento por fraude simultáneos reembolsan una vez")
    void concurrenciaConHu15() throws Exception {
        var sesion = tokenAdmin();
        var id = sembrarReporte(crearUsuario(CORREO_OTRO), "ESPACIO", espacio);
        var reserva = confirmada(hoy.plusDays(3), 10, 11, 12);
        pago(reserva, "PAGADO");
        var sesionUsuario = token(CORREO_USUARIO);
        List<Callable<MvcResult>> solicitudes = List.of(
                () -> decidir(sesion, id, "OCULTAR_POR_FRAUDE", MOTIVO).andReturn(),
                () -> mvc.perform(con(sesionUsuario, post(URI.create("/api/reservas/" + reserva + "/cancelacion")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"montoReembolsoVisto\":241.50}"))
                        .andReturn());

        var respuestas = aLaVez(solicitudes);

        assertThat(respuestas.getFirst().getResponse().getStatus()).isEqualTo(200);
        assertThat(respuestas.get(1).getResponse().getStatus()).isIn(200, 400);
        assertThat(estadoReserva(reserva)).isEqualTo("CANCELADA");
        assertThat(filas("reembolso")).isEqualTo(1);
        assertThat(auditorias("RESERVA_CANCELADA")).hasSize(1);
        assertThat(estadoDe("espacio", espacio)).isEqualTo("OCULTO");
    }

    @Test
    @DisplayName("HU-27 R77: un admin no puede usar las rutas de cancelación del usuario ni del arrendador (403)")
    void adminNoCancelaPorHu15() throws Exception {
        var sesion = tokenAdmin();
        var reserva = confirmada(hoy.plusDays(3), 10);
        pago(reserva, "PAGADO");

        for (var ruta : List.of("/api/reservas/", "/api/arrendadores/actual/reservas/")) {
            mvc.perform(comoAdmin(sesion, get(URI.create(ruta + reserva + "/cancelacion"))))
                    .andExpect(status().isForbidden());
            mvc.perform(comoAdmin(sesion, post(URI.create(ruta + reserva + "/cancelacion")))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"montoReembolsoVisto\":241.50}"))
                    .andExpect(status().isForbidden());
        }
        assertThat(estadoReserva(reserva)).isEqualTo("CONFIRMADA");
        assertThat(filas("reembolso")).isZero();
    }
}
