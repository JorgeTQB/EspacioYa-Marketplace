package pe.edu.ulima.espacioya.administracion.service.correo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import pe.edu.ulima.espacioya.administracion.domain.MotivoReclamo;
import pe.edu.ulima.espacioya.administracion.domain.PapelReclamo;
import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;
import pe.edu.ulima.espacioya.comun.correo.MensajeCorreo;

class CorreosReclamoTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 11, 3);

    private static DatosResolucion resolucion(PapelReclamo aFavorDe, String monto) {
        return new DatosResolucion("Cancha Olivos", FECHA, 23, 24, aFavorDe, "Se comprobó el cierre",
                monto == null ? null : new BigDecimal(monto));
    }

    @Test
    @DisplayName("HU-26 R22: al abrir, el arrendador recibe espacio, fecha, horario y motivo con asunto fijo")
    void reclamoAbierto() {
        var correo = new CorreoReclamoAbierto(new DatosReclamoAbierto("Cancha Olivos", FECHA, 14, 16,
                MotivoReclamo.DISTINTO_A_LO_PUBLICADO));

        assertThat(correo.asunto()).isEqualTo("Recibiste un reclamo sobre una reserva");
        assertThat(correo.cuerpo()).contains("Espacio: Cancha Olivos", "Fecha: 03/11/2026",
                "Horario: 14:00 a 16:00", "Motivo: Distinto a lo publicado", "en Reclamos, para responder",
                "queda retenido hasta que el reclamo se resuelva");
    }

    @Test
    @DisplayName("HU-26 R22: el aviso al arrendador no lleva la descripción ni datos del usuario")
    void reclamoAbiertoSinDatosDelUsuario() {
        var datos = DatosReclamoAbierto.class.getRecordComponents();

        assertThat(datos).extracting(RecordComponent::getName)
                .containsExactly("espacio", "fecha", "horaInicio", "horaFin", "motivo");
    }

    @Test
    @DisplayName("HU-26 R22: la fábrica entrega el correo al destinatario con su asunto y cuerpo")
    void fabricaReclamoAbierto() {
        var envio = mock(EnvioCorreo.class);

        new FabricaReclamoAbierto(envio).enviar("ana@correo.pe", new DatosReclamoAbierto("Cancha Olivos", FECHA, 8,
                9, MotivoReclamo.ESPACIO_CERRADO));

        var mensaje = ArgumentCaptor.forClass(MensajeCorreo.class);
        verify(envio).enviar(mensaje.capture());
        assertThat(mensaje.getValue().para()).isEqualTo("ana@correo.pe");
        assertThat(mensaje.getValue().asunto()).isEqualTo(CorreoReclamoAbierto.ASUNTO);
        assertThat(mensaje.getValue().cuerpo()).contains("Motivo: Espacio cerrado");
    }

    @Test
    @DisplayName("HU-26 R57: a favor del usuario, el usuario recibe el resultado, el motivo y el monto del reembolso")
    void usuarioAFavor() {
        var correo = new CorreoResolucionUsuario(resolucion(PapelReclamo.USUARIO, "80.5"));

        assertThat(correo.asunto()).isEqualTo("Resolvimos tu reclamo");
        assertThat(correo.cuerpo()).contains("Espacio: Cancha Olivos", "Fecha: 03/11/2026",
                "Horario: 23:00 a 24:00", "Resultado: a tu favor", "Motivo: Se comprobó el cierre",
                "Solicitamos un reembolso de S/ 80.50 a tu medio de pago.");
    }

    @Test
    @DisplayName("HU-26 R57: a favor del arrendador, el usuario recibe el resultado y el motivo, sin reembolso")
    void usuarioEnContra() {
        var cuerpo = new CorreoResolucionUsuario(resolucion(PapelReclamo.ARRENDADOR, null)).cuerpo();

        assertThat(cuerpo).contains("Resultado: a favor del arrendador", "Motivo: Se comprobó el cierre")
                .doesNotContain("reembolso", "S/");
    }

    @Test
    @DisplayName("HU-26 R58: a favor del usuario, el arrendador sabe que no se liquidará, sin montos")
    void arrendadorEnContra() {
        var correo = new CorreoResolucionArrendador(resolucion(PapelReclamo.USUARIO, "80.50"));

        assertThat(correo.asunto()).isEqualTo("Resolvimos un reclamo sobre tu espacio");
        assertThat(correo.cuerpo()).contains("Espacio: Cancha Olivos", "Fecha: 03/11/2026",
                "Horario: 23:00 a 24:00", "Resultado: a favor del usuario. Esta reserva no se liquidará.",
                "Motivo: Se comprobó el cierre").doesNotContain("S/", "80.50", "reembolso");
    }

    @Test
    @DisplayName("HU-26 R58: a favor del arrendador, sabe que la reserva entra en la siguiente liquidación")
    void arrendadorAFavor() {
        var cuerpo = new CorreoResolucionArrendador(resolucion(PapelReclamo.ARRENDADOR, null)).cuerpo();

        assertThat(cuerpo).contains("Resultado: a tu favor. La reserva entra en la siguiente liquidación.",
                "Motivo: Se comprobó el cierre").doesNotContain("S/");
    }

    @Test
    @DisplayName("HU-26 R57, R58: cada fábrica de la resolución crea su correo")
    void fabricasDeResolucion() {
        var envio = mock(EnvioCorreo.class);
        var datos = resolucion(PapelReclamo.USUARIO, "10.00");

        new FabricaResolucionUsuario(envio).enviar("carla@correo.pe", datos);
        new FabricaResolucionArrendador(envio).enviar("ana@correo.pe", datos);

        var mensajes = ArgumentCaptor.forClass(MensajeCorreo.class);
        verify(envio, times(2)).enviar(mensajes.capture());
        assertThat(mensajes.getAllValues()).extracting(MensajeCorreo::para, MensajeCorreo::asunto).containsExactly(
                tuple("carla@correo.pe", CorreoResolucionUsuario.ASUNTO),
                tuple("ana@correo.pe", CorreoResolucionArrendador.ASUNTO));
    }
}
