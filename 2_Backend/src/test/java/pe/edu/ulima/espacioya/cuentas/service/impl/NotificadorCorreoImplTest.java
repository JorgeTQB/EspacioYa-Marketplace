package pe.edu.ulima.espacioya.cuentas.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;
import pe.edu.ulima.espacioya.comun.correo.MensajeCorreo;
import pe.edu.ulima.espacioya.comun.error.ServicioExternoException;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCambioCuentaBancaria;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCancelacionArrendador;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCancelacionUsuario;
import pe.edu.ulima.espacioya.cuentas.service.AvisoReservaPagada;
import pe.edu.ulima.espacioya.cuentas.service.ComprobantePago;
import pe.edu.ulima.espacioya.cuentas.service.NotificadorCorreo;

/** El adaptador real de NotificadorCorreo con un Target que guarda los mensajes (HU-25 R36, tabla C1). */
class NotificadorCorreoImplTest {

    private static final String PARA = "ana@correo.pe";
    private static final LocalDate FECHA = LocalDate.of(2026, 11, 20);
    private static final String ENLACE = "https://espacioya.pe/recuperar?token=abc123";
    private static final String NUMERO_COMPLETO = "4111111111111111";

    private final List<MensajeCorreo> enviados = new ArrayList<>();
    private final NotificadorCorreo notificador = new NotificadorCorreoImpl(enviados::add);

    static Stream<Arguments> eventos() {
        return Stream.of(
                caso("notificarVerificacion", n -> n.notificarVerificacion(PARA), "Bienvenido a EspacioYa",
                        List.of("ya está lista", "iniciar sesión"), List.of("http", "contraseña:")),
                caso("notificarRecuperacion", n -> n.notificarRecuperacion(PARA, ENLACE),
                        "Recupera tu contraseña de EspacioYa", List.of(ENLACE, "30 minutos"), List.of()),
                caso("notificarIdentidadAprobada", n -> n.notificarIdentidadAprobada(PARA),
                        "Tu identidad fue verificada", List.of("publicar espacios"), List.of()),
                caso("notificarIdentidadRechazada", n -> n.notificarIdentidadRechazada(PARA, "La foto está borrosa"),
                        "Tu identidad no fue verificada", List.of("La foto está borrosa", "volver a subir"),
                        List.of()),
                caso("notificarCuentaSuspendida", n -> n.notificarCuentaSuspendida(PARA, "Reclamos repetidos"),
                        "Tu cuenta fue suspendida", List.of("Reclamos repetidos"), List.of()),
                caso("notificarCuentaReactivada", n -> n.notificarCuentaReactivada(PARA),
                        "Tu cuenta fue reactivada", List.of("iniciar sesión"), List.of()),
                caso("notificarPagoConfirmado", n -> n.notificarPagoConfirmado(PARA, new ComprobantePago("Sala Azul",
                                FECHA, 10, 12, new BigDecimal("120.5"), "1111", "PAGO-77")),
                        "Comprobante de tu reserva",
                        List.of("Sala Azul", "20/11/2026", "10:00 a 12:00", "S/ 120.50", "PAGO-77", "1111"),
                        List.of(NUMERO_COMPLETO)),
                caso("notificarReservaPagada", n -> n.notificarReservaPagada(PARA,
                                new AvisoReservaPagada("Sala Azul", FECHA, 23, 24)),
                        "Tienes una reserva pagada", List.of("Sala Azul", "20/11/2026", "23:00 a 24:00"),
                        List.of("S/", "Referencia", "Tarjeta")),
                caso("notificarCancelacionAlUsuario", n -> n.notificarCancelacionAlUsuario(PARA,
                                new AvisoCancelacionUsuario("Sala Azul", FECHA, 10, 12, "ARRENDADOR", 100,
                                        new BigDecimal("120"))),
                        "Tu reserva fue cancelada",
                        List.of("Sala Azul", "20/11/2026", "10:00 a 12:00", "el arrendador", "100 %", "S/ 120.00"),
                        List.of("Tarjeta")),
                caso("notificarCancelacionAlArrendador", n -> n.notificarCancelacionAlArrendador(PARA,
                                new AvisoCancelacionArrendador("Sala Azul", FECHA, 10, 12, "USUARIO")),
                        "Se canceló una reserva de tu espacio",
                        List.of("Sala Azul", "20/11/2026", "10:00 a 12:00", "quien reservó"),
                        List.of("S/", "Reembolso")),
                caso("notificarCambioCuentaBancaria", n -> n.notificarCambioCuentaBancaria(PARA,
                                new AvisoCambioCuentaBancaria("BCP", "****4321",
                                        Instant.parse("2026-11-06T15:00:00Z"))),
                        "Cambió tu cuenta bancaria", List.of("BCP", "****4321", "06/11/2026 10:00"),
                        List.of("1912345678904321")),
                caso("notificarCambioCorreo", n -> n.notificarCambioCorreo(PARA, ENLACE),
                        "Confirma tu nuevo correo", List.of(ENLACE, "30 minutos"), List.of()),
                caso("notificarAvisoCambioCorreo", n -> n.notificarAvisoCambioCorreo(PARA, "n***@correo.pe"),
                        "Se pidió cambiar el correo de tu cuenta", List.of("n***@correo.pe"),
                        List.of("nuevo@correo.pe")));
    }

    private static Arguments caso(String metodo, Consumer<NotificadorCorreo> llamada, String asunto,
                                  List<String> incluye, List<String> excluye) {
        return Arguments.of(metodo, llamada, asunto, incluye, excluye);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("eventos")
    @DisplayName("HU-25 R36, R30, R31: cada método envía un único mensaje al correo recibido con el asunto fijo y los "
            + "datos de C1")
    void cadaMetodoEnviaSuCorreo(String metodo, Consumer<NotificadorCorreo> llamada, String asunto,
                                 List<String> incluye, List<String> excluye) {
        llamada.accept(notificador);

        assertThat(enviados).hasSize(1);
        var mensaje = enviados.getFirst();
        assertThat(mensaje.para()).isEqualTo(PARA);
        assertThat(mensaje.asunto()).isEqualTo(asunto);
        assertThat(mensaje.cuerpo()).contains(incluye);
        excluye.forEach(texto -> assertThat(mensaje.cuerpo()).doesNotContain(texto));
        assertThat(mensaje.cuerpo()).doesNotContain("<");
    }

    @Test
    @DisplayName("HU-25 R36: el comprobante sin últimos 4 no muestra ninguna línea de tarjeta")
    void comprobanteSinTarjeta() {
        notificador.notificarPagoConfirmado(PARA, new ComprobantePago("Sala Azul", FECHA, 10, 12,
                new BigDecimal("80"), null, "PAGO-78"));

        assertThat(enviados.getFirst().cuerpo()).doesNotContain("Tarjeta").contains("PAGO-78", "S/ 80.00");
    }

    @Test
    @DisplayName("HU-25 R31: el asunto no cambia con los datos que ingresan los usuarios")
    void asuntoSinDatosDeUsuarios() {
        notificador.notificarIdentidadRechazada(PARA, "Motivo con Asunto: falso");
        notificador.notificarReservaPagada(PARA, new AvisoReservaPagada("Espacio <b>raro</b>", FECHA, 9, 10));

        assertThat(enviados).extracting(MensajeCorreo::asunto)
                .containsExactly("Tu identidad no fue verificada", "Tienes una reserva pagada");
    }

    @Test
    @DisplayName("HU-25 R33: si el Target falla, la ServicioExternoException llega a quien notifica")
    void propagaLaFalla() {
        EnvioCorreo falla = mensaje -> {
            throw new ServicioExternoException();
        };

        assertThatThrownBy(() -> new NotificadorCorreoImpl(falla).notificarCuentaReactivada(PARA))
                .isInstanceOf(ServicioExternoException.class);
    }
}
