package pe.edu.ulima.espacioya.pagos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;

/**
 * HU-19 con cancelaciones (HU-15) y reservas nuevas, contra PostgreSQL real: la cancelada por el usuario sin reembolso
 * se liquida (D3 ⚠), la reembolsada y la cancelada por el arrendador no, y una segunda generación crea una liquidación
 * nueva sin tocar la anterior.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PagosSoporte.Dobles.class})
class LiquidacionCancelacionIT extends LiquidacionesSoporte {

    @Test
    @DisplayName("HU-19 R10, R11: la cancelada por el usuario con 0 % se liquida marcada cancelada al pasar fin + 48 "
            + "h; la cancelada con 100 % y la cancelada por el arrendador quedan fuera")
    void canceladas() throws Exception {
        var usuario = token(CORREO_USUARIO);
        var manana = ZonaHoraria.hoy(reloj).plusDays(1);
        var enUnaSemana = ZonaHoraria.hoy(reloj).plusDays(7);
        var sinReembolso = pagadaEl(usuario, manana, 20);
        var conReembolso = pagadaEl(usuario, enUnaSemana, 8);
        var porElArrendador = pagadaEl(usuario, enUnaSemana, 14);

        cancelar(usuario, sinReembolso, "0").andExpect(status().isOk())
                .andExpect(jsonPath("$.porcentaje").value(0));
        cancelar(usuario, conReembolso, "241.50").andExpect(status().isOk());
        mvc.perform(con(token(CORREO_ARRENDADOR), post("/api/arrendadores/actual/reservas/" + porElArrendador
                        + "/cancelacion")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montoReembolsoVisto\":241.50}"))
                .andExpect(status().isOk());
        assertThat(List.of(estadoDeReserva(sinReembolso), estadoDeReserva(conReembolso),
                estadoDeReserva(porElArrendador))).containsOnly("CANCELADA");
        assertThat(pagoDe(sinReembolso).get("estado")).isEqualTo("PAGADO");

        llevarRelojA(finDe(enUnaSemana, 14).plus(ESPERA));
        var admin = tokenAdmin();
        var respuesta = generar(admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.liquidaciones.length()").value(1))
                .andExpect(jsonPath("$.liquidaciones[0].reservas").value(1))
                .andExpect(jsonPath("$.liquidaciones[0].montoBruto").value(241.5));
        var id = numero(lista(cuerpo(respuesta), "liquidaciones").getFirst().get("id"));

        assertThat(lineasDe(id)).extracting(linea -> numero(linea.get("reserva_id"))).containsExactly(sinReembolso);
        detalle(admin, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.reservas.length()").value(1))
                .andExpect(jsonPath("$.reservas[0].reservaId").value(sinReembolso))
                .andExpect(jsonPath("$.reservas[0].cancelada").value(true))
                .andExpect(jsonPath("$.reservas[0].horaInicio").value(20))
                .andExpect(jsonPath("$.reservas[0].horaFin").value(23));
    }

    @Test
    @DisplayName("HU-19 R10: una PENDIENTE_PAGO o EXPIRADA con un pago PAGADO no se liquida")
    void pendientesYExpiradasFuera() throws Exception {
        var pasado = ZonaHoraria.hoy(reloj).minusDays(5);
        for (var estado : List.of("PENDIENTE_PAGO", "EXPIRADA")) {
            var reserva = usadaSembrada(espacioId, pasado, "PENDIENTE_PAGO".equals(estado) ? 8 : 12,
                    transaccionSimulada());
            jdbc.update("UPDATE reserva SET estado = ? WHERE id = ?", estado, reserva);
        }

        generar(tokenAdmin()).andExpect(status().isOk()).andExpect(jsonPath("$.liquidaciones.length()").value(0));

        assertThat(filas("liquidacion")).isZero();
    }

    @Test
    @DisplayName("HU-19 R12, R17: con una PENDIENTE_CUENTA previa, una reserva nueva va a una liquidación nueva y la "
            + "anterior no cambia")
    void reservasNuevasEnOtraLiquidacion() throws Exception {
        var pasado = ZonaHoraria.hoy(reloj).minusDays(5);
        var primera = usadaSembrada(espacioId, pasado, 8, transaccionSimulada());
        var admin = tokenAdmin();
        var vieja = numero(lista(cuerpo(generar(admin).andExpect(status().isOk())), "liquidaciones")
                .getFirst().get("id"));
        var filaVieja = liquidacion(vieja);
        var lineasViejas = lineasDe(vieja);
        assertThat(filaVieja.get("estado")).isEqualTo("PENDIENTE_CUENTA");

        var segunda = usadaSembrada(espacioId, pasado, 12, transaccionSimulada());
        var respuesta = generar(admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.liquidaciones.length()").value(1));
        var nueva = numero(lista(cuerpo(respuesta), "liquidaciones").getFirst().get("id"));

        assertThat(nueva).isNotEqualTo(vieja);
        assertThat(lineasDe(nueva)).extracting(linea -> numero(linea.get("reserva_id"))).containsExactly(segunda);
        assertThat(liquidacion(vieja)).isEqualTo(filaVieja);
        assertThat(lineasDe(vieja)).isEqualTo(lineasViejas);
        assertThat(lineasViejas).extracting(linea -> numero(linea.get("reserva_id"))).containsExactly(primera);
        assertThat(filas("liquidacion")).isEqualTo(2);
    }
}
