package pe.edu.ulima.espacioya.reservas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CalculoReembolsoTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final BigDecimal PAGADO = new BigDecimal("161.00");
    private static final List<PoliticaReembolso> ESTRATEGIAS = List.of(new ReembolsoUsuarioConAnticipacion(),
            new ReembolsoUsuarioSinAnticipacion(), new ReembolsoCancelaArrendador());

    private final CalculoReembolso calculo = CalculoReembolso.politicaFija();

    private static SolicitudCancelacion solicitud(QuienCancela quien, Duration anticipacion, BigDecimal monto) {
        return new SolicitudCancelacion(quien, AHORA.plus(anticipacion), AHORA, monto);
    }

    private static SolicitudCancelacion solicitud(QuienCancela quien, Duration anticipacion) {
        return solicitud(quien, anticipacion, PAGADO);
    }

    @Test
    @DisplayName("HU-15 R11: el usuario con 48 horas exactas recibe USUARIO_CON_ANTICIPACION al 100 %")
    void usuarioConCuarentaYOchoHorasExactas() {
        var reembolso = calculo.calcular(solicitud(QuienCancela.USUARIO, Duration.ofHours(48)));

        assertThat(reembolso).isEqualTo(new ReembolsoCalculado("USUARIO_CON_ANTICIPACION", 100,
                new BigDecimal("161.00")));
    }

    @Test
    @DisplayName("HU-15 R11: el usuario con 10 días de anticipación recibe el 100 %")
    void usuarioConMuchaAnticipacion() {
        var reembolso = calculo.calcular(solicitud(QuienCancela.USUARIO, Duration.ofDays(10)));

        assertThat(reembolso.politica()).isEqualTo("USUARIO_CON_ANTICIPACION");
        assertThat(reembolso.porcentaje()).isEqualTo(100);
    }

    @Test
    @DisplayName("HU-15 R12: el usuario con 48 horas menos un segundo recibe USUARIO_SIN_ANTICIPACION al 0 %")
    void usuarioUnSegundoAntesDelLimite() {
        var reembolso = calculo.calcular(solicitud(QuienCancela.USUARIO, Duration.ofHours(48).minusSeconds(1)));

        assertThat(reembolso).isEqualTo(new ReembolsoCalculado("USUARIO_SIN_ANTICIPACION", 0,
                new BigDecimal("0.00")));
    }

    @Test
    @DisplayName("HU-15 R12: el usuario con 1 minuto de anticipación recibe el 0 %")
    void usuarioConUnMinuto() {
        var reembolso = calculo.calcular(solicitud(QuienCancela.USUARIO, Duration.ofMinutes(1)));

        assertThat(reembolso.politica()).isEqualTo("USUARIO_SIN_ANTICIPACION");
        assertThat(reembolso.monto()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("HU-15 R13: el arrendador recibe ARRENDADOR al 100 % con 1 hora y con 10 días de anticipación")
    void arrendadorSiempreCien() {
        var conUnaHora = calculo.calcular(solicitud(QuienCancela.ARRENDADOR, Duration.ofHours(1)));
        var conDiezDias = calculo.calcular(solicitud(QuienCancela.ARRENDADOR, Duration.ofDays(10)));

        assertThat(conUnaHora).isEqualTo(new ReembolsoCalculado("ARRENDADOR", 100, new BigDecimal("161.00")));
        assertThat(conDiezDias).isEqualTo(conUnaHora);
    }

    @Test
    @DisplayName("HU-15 R14: la anticipación es inicio − ahora")
    void anticipacionEsInicioMenosAhora() {
        var solicitud = new SolicitudCancelacion(QuienCancela.USUARIO, AHORA.plus(Duration.ofHours(50)), AHORA,
                PAGADO);

        assertThat(solicitud.anticipacion()).isEqualTo(Duration.ofHours(50));
    }

    @Test
    @DisplayName("HU-15 R16: el monto es monto_total × porcentaje / 100 con escala 2")
    void montoConEscalaDos() {
        var entero = calculo.calcular(solicitud(QuienCancela.ARRENDADOR, Duration.ofDays(3), new BigDecimal("80")));
        var conDecimales = calculo.calcular(solicitud(QuienCancela.USUARIO, Duration.ofDays(3),
                new BigDecimal("241.50")));

        assertThat(entero.monto()).isEqualTo(new BigDecimal("80.00"));
        assertThat(conDecimales.monto()).isEqualTo(new BigDecimal("241.50"));
        assertThat(new ReembolsoUsuarioSinAnticipacion().monto(new BigDecimal("2400000.00")))
                .isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("HU-15 R15: para cada anticipación y cada quien cancela, exactamente una estrategia aplica")
    void exactamenteUnaAplica() {
        var anticipaciones = List.of(Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofHours(1),
                Duration.ofHours(47), Duration.ofHours(48).minusSeconds(1), Duration.ofHours(48),
                Duration.ofHours(48).plusSeconds(1), Duration.ofDays(7), Duration.ofDays(56));

        for (var quien : QuienCancela.values()) {
            for (var anticipacion : anticipaciones) {
                var solicitud = solicitud(quien, anticipacion);
                assertThat(ESTRATEGIAS.stream().filter(estrategia -> estrategia.aplicaA(solicitud)).count())
                        .as("%s con %s", quien, anticipacion)
                        .isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("HU-15 R15: si no aplica ninguna estrategia, lanza IllegalStateException")
    void ningunaAplica() {
        var sinEstrategias = new CalculoReembolso(List.of());

        assertThatThrownBy(() -> sinEstrategias.calcular(solicitud(QuienCancela.USUARIO, Duration.ofDays(3))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("HU-15 R15: si aplican dos estrategias, lanza IllegalStateException")
    void dosAplican() {
        var duplicadas = new CalculoReembolso(List.of(new ReembolsoCancelaArrendador(),
                new ReembolsoCancelaArrendador()));

        assertThatThrownBy(() -> duplicadas.calcular(solicitud(QuienCancela.ARRENDADOR, Duration.ofDays(3))))
                .isInstanceOf(IllegalStateException.class);
    }
}
