package pe.edu.ulima.espacioya.administracion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.administracion.controller.ReclamosSoporte;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReclamosAbiertos;

/** El puerto de {@code pagos} contra PostgreSQL real (HU-26 R20, R50). */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({TestcontainersConfiguration.class, ReclamosSoporte.Dobles.class})
class ReclamosAbiertosImplIT extends ReclamosSoporte {

    @Autowired
    private ReclamosAbiertos puerto;

    @Test
    @DisplayName("HU-26 R20, R50: de los ids pedidos devuelve solo los que tienen un reclamo ABIERTO")
    void soloAbiertos() {
        var admin = crearAdmin();
        var abierta = confirmada(hoy.minusDays(1), 8);
        var resueltaArrendador = confirmada(hoy.minusDays(1), 9);
        var resueltaUsuario = confirmada(hoy.minusDays(1), 10);
        var sinReclamo = confirmada(hoy.minusDays(1), 11);
        var abiertaNoPedida = confirmada(hoy.minusDays(1), 12);
        sembrarReclamo(abierta);
        resolverPorJdbc(sembrarReclamo(resueltaArrendador), "RESUELTO_ARRENDADOR", null, admin,
                reloj.instant().plusSeconds(60));
        resolverPorJdbc(sembrarReclamo(resueltaUsuario), "RESUELTO_USUARIO", "10.00", admin,
                reloj.instant().plusSeconds(60));
        sembrarReclamo(abiertaNoPedida);

        assertThat(puerto.conReclamoAbierto(List.of(abierta, resueltaArrendador, resueltaUsuario, sinReclamo)))
                .containsExactly(abierta);
    }

    @Test
    @DisplayName("HU-26 R20: una colección vacía devuelve un conjunto vacío")
    void vacia() {
        sembrarReclamo(confirmada(hoy.minusDays(1), 8));

        assertThat(puerto.conReclamoAbierto(List.of())).isEmpty();
    }

    @Test
    @DisplayName("HU-26 DT-4: el bean del puerto es la implementación de administracion")
    void implementacionReal() {
        assertThat(puerto).isInstanceOf(ReclamosAbiertosImpl.class);
    }
}
