package pe.edu.ulima.espacioya.reservas.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.Order;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.cuentas.service.puertos.Impedimento;
import pe.edu.ulima.espacioya.cuentas.service.puertos.SeccionDatos;
import pe.edu.ulima.espacioya.cuentas.service.puertos.TitularDatos;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.espacios.service.DisponibilidadService;
import pe.edu.ulima.espacioya.espacios.service.puertos.BloqueReservado;
import pe.edu.ulima.espacioya.espacios.service.puertos.ReservasDelEspacio;
import pe.edu.ulima.espacioya.pagos.service.PagoDeReserva;
import pe.edu.ulima.espacioya.pagos.service.PagoService;
import pe.edu.ulima.espacioya.pagos.service.ReembolsoDePago;
import pe.edu.ulima.espacioya.reservas.domain.QuienCancela;
import pe.edu.ulima.espacioya.reservas.domain.Reserva;
import pe.edu.ulima.espacioya.reservas.domain.ReservaConHorario;
import pe.edu.ulima.espacioya.reservas.dto.PagoExportadoDTO;
import pe.edu.ulima.espacioya.reservas.dto.ReembolsoExportadoDTO;
import pe.edu.ulima.espacioya.reservas.dto.ReservaExportadaDTO;
import pe.edu.ulima.espacioya.reservas.repository.ReservaDAO;

@ExtendWith(MockitoExtension.class)
class DatosPersonalesReservasImplTest {

    // 10:00 del 2026-11-04 en Lima.
    private static final Instant AHORA = Instant.parse("2026-11-04T15:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 11, 4);
    private static final long ID = 41L;
    private static final long OTRO_USUARIO = 99L;
    private static final TitularDatos USUARIO = new TitularDatos(ID, "USUARIO");
    private static final TitularDatos ARRENDADOR = new TitularDatos(ID, "ARRENDADOR");
    private static final BigDecimal PRECIO = new BigDecimal("50.00");

    @Mock
    private ReservaDAO dao;
    @Mock
    private CatalogoEspaciosService catalogo;
    @Mock
    private DisponibilidadService disponibilidad;
    @Mock
    private ReservasDelEspacio reservasDelEspacio;
    @Mock
    private PagoService pagos;

    private DatosPersonalesReservasImpl participante;

    @BeforeEach
    void preparar() {
        participante = new DatosPersonalesReservasImpl(dao, catalogo, disponibilidad, reservasDelEspacio, pagos,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static Reserva pendiente(long id, long usuarioId, Instant creada) {
        var reserva = Reserva.pendiente(usuarioId, 5L, PRECIO, 2, creada);
        ReflectionTestUtils.setField(reserva, "id", id);
        return reserva;
    }

    private static Reserva confirmada(long id, long usuarioId) {
        var creada = AHORA.minus(Duration.ofDays(3));
        var reserva = pendiente(id, usuarioId, creada);
        reserva.confirmar(creada.plusSeconds(60));
        return reserva;
    }

    private static ReservaConHorario en(Reserva reserva, LocalDate fecha, int inicio, int fin) {
        return new ReservaConHorario(reserva, fecha, inicio, fin);
    }

    private static ReservaConHorario pendienteVigente() {
        return en(pendiente(1L, ID, AHORA.minus(Duration.ofMinutes(5))), HOY.plusDays(1), 10, 12);
    }

    private static ReservaConHorario pendienteVencida() {
        return en(pendiente(2L, ID, AHORA.minus(Duration.ofMinutes(20))), HOY.plusDays(1), 14, 16);
    }

    private static ReservaConHorario confirmadaFutura() {
        return en(confirmada(3L, ID), HOY.plusDays(2), 18, 20);
    }

    private static ReservaConHorario confirmadaEnCurso() {
        return en(confirmada(4L, ID), HOY, 9, 11);
    }

    private static ReservaConHorario confirmadaTerminada() {
        return en(confirmada(5L, ID), HOY, 7, 9);
    }

    private static ReservaConHorario expirada() {
        var creada = AHORA.minus(Duration.ofDays(2));
        var reserva = pendiente(6L, ID, creada);
        reserva.expirar(creada.plus(Duration.ofMinutes(20)));
        return en(reserva, HOY.plusDays(3), 8, 9);
    }

    private static ReservaConHorario cancelada() {
        var reserva = confirmada(7L, ID);
        var inicio = HOY.plusDays(5).atTime(8, 0).atZone(ZonaHoraria.LIMA).toInstant();
        reserva.cancelar(QuienCancela.USUARIO, inicio, new BigDecimal("100.00"), AHORA.minus(Duration.ofHours(1)));
        return en(reserva, HOY.plusDays(5), 8, 10);
    }

    // ---- Exportación ----

    @Test
    @DisplayName("HU-22 R5: el usuario exporta todas sus reservas, por inicio, con el estado leído y los datos de "
            + "cancelación")
    void exportaReservasDelUsuario() {
        when(dao.delUsuario(ID)).thenReturn(List.of(cancelada(), pendienteVencida(), confirmadaFutura()));
        when(catalogo.nombres(any())).thenReturn(Map.of(5L, "Cancha Norte"));
        when(pagos.pagosDeReservas(any())).thenReturn(List.of());

        var secciones = participante.exportar(USUARIO);

        assertThat(secciones).extracting(SeccionDatos::nombre).containsExactly("reservas", "pagos");
        @SuppressWarnings("unchecked")
        var reservas = (List<ReservaExportadaDTO>) secciones.get(0).contenido();
        assertThat(reservas).extracting(ReservaExportadaDTO::id).containsExactly(2L, 3L, 7L);
        assertThat(reservas).extracting(ReservaExportadaDTO::estado)
                .containsExactly("EXPIRADA", "CONFIRMADA", "CANCELADA");
        var vencida = reservas.get(0);
        assertThat(vencida.espacio()).isEqualTo("Cancha Norte");
        assertThat(vencida.fecha()).isEqualTo(HOY.plusDays(1));
        assertThat(vencida.horaInicio()).isEqualTo(14);
        assertThat(vencida.horaFin()).isEqualTo(16);
        assertThat(vencida.precioHora()).isEqualByComparingTo("50.00");
        assertThat(vencida.montoTotal()).isEqualByComparingTo("100.00");
        assertThat(vencida.creadaEn()).isEqualTo(AHORA.minus(Duration.ofMinutes(20)));
        assertThat(vencida.canceladaPor()).isNull();
        var anulada = reservas.get(2);
        assertThat(anulada.canceladaPor()).isEqualTo("USUARIO");
        assertThat(anulada.canceladaEn()).isEqualTo(AHORA.minus(Duration.ofHours(1)));
        assertThat(anulada.montoReembolso()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("HU-22 R6: los pagos del usuario salen de pagosDeReservas con los ids de sus reservas")
    void exportaPagosDelUsuario() {
        when(dao.delUsuario(ID)).thenReturn(List.of(confirmadaFutura(), pendienteVigente()));
        when(catalogo.nombres(any())).thenReturn(Map.of());
        var reembolso = new ReembolsoDePago(new BigDecimal("100.00"), "REEMBOLSADO", AHORA);
        when(pagos.pagosDeReservas(List.of(1L, 3L))).thenReturn(List.of(
                new PagoDeReserva(3L, new BigDecimal("100.00"), "REEMBOLSADO", AHORA, List.of(reembolso))));

        var secciones = participante.exportar(USUARIO);

        assertThat(secciones.get(1)).isEqualTo(new SeccionDatos("pagos", List.of(new PagoExportadoDTO(3L,
                new BigDecimal("100.00"), "REEMBOLSADO", AHORA,
                List.of(new ReembolsoExportadoDTO(new BigDecimal("100.00"), "REEMBOLSADO", AHORA))))));
    }

    @Test
    @DisplayName("HU-22 R7: el arrendador exporta solo las reservas CONFIRMADA de sus espacios")
    void exportaReservasDelArrendador() {
        when(catalogo.nombresDelArrendador(ID)).thenReturn(Map.of(5L, "Cancha Norte"));
        when(dao.confirmadasDeEspacios(Set.of(5L))).thenReturn(List.of(en(confirmada(8L, OTRO_USUARIO), HOY, 18, 20)));

        var secciones = participante.exportar(ARRENDADOR);

        assertThat(secciones).containsExactly(new SeccionDatos("reservas", List.of(new ReservaExportadaDTO(8L,
                "Cancha Norte", HOY, 18, 20, "CONFIRMADA", null, new BigDecimal("100.00"), null, null, null, null))));
        verifyNoInteractions(pagos);
    }

    @Test
    @DisplayName("HU-22 R12: solo se leen las reservas del titular y las de los espacios del arrendador")
    void soloDelTitular() {
        when(dao.delUsuario(ID)).thenReturn(List.of());
        when(catalogo.nombres(any())).thenReturn(Map.of());
        when(pagos.pagosDeReservas(List.of())).thenReturn(List.of());
        when(catalogo.nombresDelArrendador(ID)).thenReturn(Map.of(5L, "Cancha Norte", 6L, "Sala"));
        when(dao.confirmadasDeEspacios(Set.of(5L, 6L))).thenReturn(List.of());

        participante.exportar(USUARIO);
        participante.exportar(ARRENDADOR);

        verify(dao).delUsuario(ID);
        verify(dao).confirmadasDeEspacios(Set.of(5L, 6L));
        verify(catalogo).nombresDelArrendador(ID);
    }

    @Test
    @DisplayName("HU-22 R13: la reserva exportada no tiene campos del usuario que reservó")
    void sinDatosDeQuienReservo() {
        assertThat(ReservaExportadaDTO.class.getRecordComponents()).extracting(RecordComponent::getName)
                .doesNotContain("usuarioId", "usuarioNombre", "correo", "telefono");
        assertThat(PagoExportadoDTO.class.getRecordComponents()).extracting(RecordComponent::getName)
                .doesNotContain("ultimos4", "idTransaccionPasarela");
    }

    // ---- Impedimentos ----

    @Test
    @DisplayName("HU-22 R36: una reserva PENDIENTE_PAGO vigente del usuario es impedimento")
    void pendienteVigenteImpide() {
        when(dao.delUsuario(ID)).thenReturn(List.of(pendienteVigente()));

        assertThat(participante.impedimentos(USUARIO, AHORA)).containsExactly(
                new Impedimento("reservas", DatosPersonalesReservasImpl.MENSAJE_RESERVAS_USUARIO));
    }

    @Test
    @DisplayName("HU-22 R36: una reserva CONFIRMADA futura del usuario es impedimento")
    void confirmadaFuturaImpide() {
        when(dao.delUsuario(ID)).thenReturn(List.of(confirmadaTerminada(), confirmadaFutura()));

        assertThat(participante.impedimentos(USUARIO, AHORA)).extracting(Impedimento::campo)
                .containsExactly("reservas");
    }

    @Test
    @DisplayName("HU-22 R36: una reserva CONFIRMADA en curso del usuario es impedimento")
    void enCursoImpide() {
        when(dao.delUsuario(ID)).thenReturn(List.of(confirmadaEnCurso()));

        assertThat(participante.impedimentos(USUARIO, AHORA)).hasSize(1);
    }

    @Test
    @DisplayName("HU-22 R41: EXPIRADA, CANCELADA, pendiente vencida y CONFIRMADA terminada no son impedimento")
    void noImpiden() {
        when(dao.delUsuario(ID)).thenReturn(List.of(expirada(), cancelada(), pendienteVencida(),
                confirmadaTerminada()));

        assertThat(participante.impedimentos(USUARIO, AHORA)).isEmpty();
        verifyNoInteractions(disponibilidad, reservasDelEspacio);
    }

    @Test
    @DisplayName("HU-22 R37: un bloque que termina después de ahora en un espacio del arrendador es impedimento")
    void arrendadorConReservaPorTerminar() {
        when(catalogo.nombresDelArrendador(ID)).thenReturn(Map.of(5L, "Cancha Norte"));
        when(reservasDelEspacio.bloquesReservados(5L, HOY, HOY.plusDays(55)))
                .thenReturn(List.of(new BloqueReservado(HOY, 7, true), new BloqueReservado(HOY, 10, false)));

        assertThat(participante.impedimentos(ARRENDADOR, AHORA)).containsExactly(
                new Impedimento("reservas", DatosPersonalesReservasImpl.MENSAJE_RESERVAS_ARRENDADOR));
    }

    @Test
    @DisplayName("HU-22 R37: bloques ya terminados no son impedimento del arrendador")
    void arrendadorSoloConTerminadas() {
        when(catalogo.nombresDelArrendador(ID)).thenReturn(Map.of(5L, "Cancha Norte"));
        when(reservasDelEspacio.bloquesReservados(5L, HOY, HOY.plusDays(55)))
                .thenReturn(List.of(new BloqueReservado(HOY, 8, true)));

        assertThat(participante.impedimentos(ARRENDADOR, AHORA)).isEmpty();
    }

    @Test
    @DisplayName("HU-22 R43: en cada espacio, por id ascendente, el bloqueo va antes de leer sus reservas")
    void bloqueoAntesDeLeer() {
        when(catalogo.nombresDelArrendador(ID)).thenReturn(Map.of(9L, "Sala", 3L, "Cancha"));
        when(reservasDelEspacio.bloquesReservados(any(), any(), any())).thenReturn(List.of());

        assertThat(participante.impedimentos(ARRENDADOR, AHORA)).isEmpty();

        var orden = inOrder(disponibilidad, reservasDelEspacio);
        orden.verify(disponibilidad).bloquearEspacio(3L);
        orden.verify(reservasDelEspacio).bloquesReservados(3L, HOY, HOY.plusDays(55));
        orden.verify(disponibilidad).bloquearEspacio(9L);
        orden.verify(reservasDelEspacio).bloquesReservados(9L, HOY, HOY.plusDays(55));
    }

    @Test
    @DisplayName("HU-22 R37: un arrendador sin espacios no tiene impedimentos ni toma bloqueos")
    void arrendadorSinEspacios() {
        when(catalogo.nombresDelArrendador(ID)).thenReturn(Map.of());

        assertThat(participante.impedimentos(ARRENDADOR, AHORA)).isEmpty();
        verifyNoInteractions(disponibilidad, reservasDelEspacio, dao);
    }

    @Test
    @DisplayName("HU-22 R43: impedimentos y anonimizar exigen la transacción del llamador; anonimizar no toca nada")
    void contratoDelPuerto() throws Exception {
        var impedimentos = DatosPersonalesReservasImpl.class.getMethod("impedimentos", TitularDatos.class,
                Instant.class);
        var anonimizar = DatosPersonalesReservasImpl.class.getMethod("anonimizar", TitularDatos.class);

        participante.anonimizar(USUARIO);
        participante.anonimizar(ARRENDADOR);

        assertThat(impedimentos.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.MANDATORY);
        assertThat(anonimizar.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.MANDATORY);
        assertThat(DatosPersonalesReservasImpl.class.getAnnotation(Order.class).value()).isEqualTo(1);
        verifyNoInteractions(dao, catalogo, disponibilidad, reservasDelEspacio, pagos);
    }
}
