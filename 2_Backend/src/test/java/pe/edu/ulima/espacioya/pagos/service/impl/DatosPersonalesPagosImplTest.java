package pe.edu.ulima.espacioya.pagos.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.cuentas.service.puertos.Impedimento;
import pe.edu.ulima.espacioya.cuentas.service.puertos.SeccionDatos;
import pe.edu.ulima.espacioya.cuentas.service.puertos.TitularDatos;
import pe.edu.ulima.espacioya.pagos.domain.CuentaBancaria;
import pe.edu.ulima.espacioya.pagos.domain.EstadoLiquidacion;
import pe.edu.ulima.espacioya.pagos.domain.EstadoPago;
import pe.edu.ulima.espacioya.pagos.domain.Liquidacion;
import pe.edu.ulima.espacioya.pagos.domain.Pago;
import pe.edu.ulima.espacioya.pagos.dto.CuentaBancariaExportadaDTO;
import pe.edu.ulima.espacioya.pagos.dto.MiLiquidacionDTO;
import pe.edu.ulima.espacioya.pagos.repository.CuentaBancariaRepository;
import pe.edu.ulima.espacioya.pagos.repository.LiquidacionRepository;
import pe.edu.ulima.espacioya.pagos.repository.PagoRepository;
import pe.edu.ulima.espacioya.pagos.service.MisLiquidacionesService;
import pe.edu.ulima.espacioya.pagos.service.VistaLiquidaciones;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservaLiquidable;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservasLiquidables;

@ExtendWith(MockitoExtension.class)
class DatosPersonalesPagosImplTest {

    private static final Instant AHORA = Instant.parse("2026-11-04T15:00:00Z");
    private static final long ID = 41L;
    private static final long OTRO = 52L;
    private static final TitularDatos ARRENDADOR = new TitularDatos(ID, "ARRENDADOR");
    private static final TitularDatos USUARIO = new TitularDatos(ID, "USUARIO");

    @Mock
    private LiquidacionRepository liquidaciones;
    @Mock
    private VistaLiquidaciones vista;
    @Mock
    private CuentaBancariaRepository cuentas;
    @Mock
    private PagoRepository pagos;
    @Mock
    private ReservasLiquidables reservas;

    private DatosPersonalesPagosImpl participante;

    @BeforeEach
    void preparar() {
        participante = crear("simulada");
    }

    private DatosPersonalesPagosImpl crear(String pasarela) {
        return new DatosPersonalesPagosImpl(liquidaciones, vista, new MisLiquidacionesService(liquidaciones, vista),
                cuentas, pagos, reservas, Clock.fixed(AHORA, ZoneOffset.UTC), pasarela);
    }

    private static Liquidacion liquidacion() {
        return Liquidacion.generar(ID, List.of(new BigDecimal("100.00")), true, false, AHORA, 1L);
    }

    private static MiLiquidacionDTO dto(long id) {
        return new MiLiquidacionDTO(id, LocalDate.of(2026, 11, 2), AHORA, "PAGADA", new BigDecimal("100.00"),
                new BigDecimal("10.00"), new BigDecimal("90.00"), false, null, List.of());
    }

    private static Pago pagado(long reservaId) {
        return Pago.de(reservaId, "mp-" + reservaId, new BigDecimal("100.00"), EstadoPago.PAGADO, "4321", AHORA);
    }

    private static ReservaLiquidable reserva(long reservaId, long arrendadorId, boolean admitida) {
        return new ReservaLiquidable(reservaId, arrendadorId, 5L, "Cancha Norte", LocalDate.of(2026, 11, 1), 18, 20,
                AHORA, admitida, !admitida);
    }

    private void sinPendientes() {
        when(liquidaciones.existsByArrendadorIdAndEstadoIn(ID, EstadoLiquidacion.POR_PAGAR)).thenReturn(false);
    }

    @Test
    @DisplayName("HU-22 R8: el usuario no tiene secciones, impedimentos ni nada que anonimizar en pagos")
    void usuarioSinNada() {
        assertThat(participante.exportar(USUARIO)).isEmpty();
        assertThat(participante.impedimentos(USUARIO, AHORA)).isEmpty();
        participante.anonimizar(USUARIO);

        verifyNoInteractions(liquidaciones, vista, cuentas, pagos, reservas);
    }

    @Test
    @DisplayName("HU-22 R8: el arrendador exporta todas sus liquidaciones como MiLiquidacionDTO, de la más reciente "
            + "a la más antigua")
    void exportaLiquidaciones() {
        var recientes = List.of(liquidacion(), liquidacion());
        when(liquidaciones.findByArrendadorIdOrderByGeneradaEnDescIdDesc(ID)).thenReturn(recientes);
        when(vista.misLiquidaciones(recientes)).thenReturn(List.of(dto(9L), dto(8L)));

        var secciones = participante.exportar(ARRENDADOR);

        assertThat(secciones).first().isEqualTo(new SeccionDatos("liquidaciones", List.of(dto(9L), dto(8L))));
    }

    @Test
    @DisplayName("HU-22 R9: la cuenta bancaria se exporta con banco, número enmascarado, titular y estado")
    void exportaCuentaEnmascarada() {
        when(liquidaciones.findByArrendadorIdOrderByGeneradaEnDescIdDesc(ID)).thenReturn(List.of());
        when(vista.misLiquidaciones(List.of())).thenReturn(List.of());
        when(cuentas.findByArrendadorId(ID)).thenReturn(Optional.of(
                CuentaBancaria.registrar(ID, "BCP", "19312345671234", "Ana Ríos EIRL", AHORA.minusSeconds(3600))));

        var secciones = participante.exportar(ARRENDADOR);

        assertThat(secciones).extracting(SeccionDatos::nombre).containsExactly("liquidaciones", "cuentaBancaria");
        assertThat(secciones.get(1).contenido())
                .isEqualTo(new CuentaBancariaExportadaDTO("BCP", "****1234", "Ana Ríos EIRL", "DISPONIBLE"));
    }

    @Test
    @DisplayName("HU-22 R9: sin cuenta bancaria, la sección cuentaBancaria es null")
    void cuentaNullSinRegistro() {
        when(liquidaciones.findByArrendadorIdOrderByGeneradaEnDescIdDesc(ID)).thenReturn(List.of());
        when(vista.misLiquidaciones(List.of())).thenReturn(List.of());
        when(cuentas.findByArrendadorId(ID)).thenReturn(Optional.empty());

        var secciones = participante.exportar(ARRENDADOR);

        assertThat(secciones).contains(new SeccionDatos("cuentaBancaria", null));
    }

    @Test
    @DisplayName("HU-22 R13: la cuenta exportada no tiene el número completo")
    void sinNumeroCompleto() {
        var dto = CuentaBancariaExportadaDTO.de(
                CuentaBancaria.registrar(ID, "BCP", "19312345671234", "Ana Ríos EIRL", AHORA), AHORA);

        assertThat(dto.toString()).doesNotContain("19312345671234");
        assertThat(dto.numero()).isEqualTo("****1234");
    }

    @Test
    @DisplayName("HU-22 R38: una liquidación GENERADA o PENDIENTE_CUENTA es impedimento; PAGADA no cuenta")
    void liquidacionPendiente() {
        when(liquidaciones.existsByArrendadorIdAndEstadoIn(ID, EstadoLiquidacion.POR_PAGAR)).thenReturn(true);
        when(pagos.pagadosSinLiquidar(true)).thenReturn(List.of());

        assertThat(participante.impedimentos(ARRENDADOR, AHORA))
                .containsExactly(new Impedimento("liquidaciones", DatosPersonalesPagosImpl.MENSAJE_LIQUIDACIONES));
        assertThat(EstadoLiquidacion.POR_PAGAR)
                .containsExactlyInAnyOrder(EstadoLiquidacion.GENERADA, EstadoLiquidacion.PENDIENTE_CUENTA)
                .doesNotContain(EstadoLiquidacion.PAGADA);
    }

    @Test
    @DisplayName("HU-22 R38: sin liquidaciones por pagar ni cobros, no hay impedimentos")
    void sinImpedimentos() {
        sinPendientes();
        when(pagos.pagadosSinLiquidar(true)).thenReturn(List.of());

        assertThat(participante.impedimentos(ARRENDADOR, AHORA)).isEmpty();
        verifyNoInteractions(reservas);
    }

    @Test
    @DisplayName("HU-22 R39: un cobro admitido de una reserva de sus espacios es impedimento en pagos")
    void cobroPorLiquidar() {
        sinPendientes();
        when(pagos.pagadosSinLiquidar(true)).thenReturn(List.of(pagado(7L), pagado(8L)));
        when(reservas.describir(List.of(7L, 8L))).thenReturn(List.of(reserva(7L, OTRO, true), reserva(8L, ID, true)));

        assertThat(participante.impedimentos(ARRENDADOR, AHORA))
                .containsExactly(new Impedimento("pagos", DatosPersonalesPagosImpl.MENSAJE_COBROS_POR_LIQUIDAR));
    }

    @Test
    @DisplayName("HU-22 R39: el cobro de otro arrendador o de una reserva no admitida no cuenta")
    void cobrosQueNoCuentan() {
        sinPendientes();
        when(pagos.pagadosSinLiquidar(true)).thenReturn(List.of(pagado(7L), pagado(8L)));
        when(reservas.describir(List.of(7L, 8L))).thenReturn(List.of(reserva(7L, OTRO, true), reserva(8L, ID, false)));

        assertThat(participante.impedimentos(ARRENDADOR, AHORA)).isEmpty();
    }

    @Test
    @DisplayName("HU-22 R38, R39: liquidaciones y cobros juntos salen en ese orden")
    void dosImpedimentos() {
        when(liquidaciones.existsByArrendadorIdAndEstadoIn(ID, EstadoLiquidacion.POR_PAGAR)).thenReturn(true);
        when(pagos.pagadosSinLiquidar(true)).thenReturn(List.of(pagado(8L)));
        when(reservas.describir(List.of(8L))).thenReturn(List.of(reserva(8L, ID, true)));

        assertThat(participante.impedimentos(ARRENDADOR, AHORA)).extracting(Impedimento::campo)
                .containsExactly("liquidaciones", "pagos");
    }

    @Test
    @DisplayName("HU-22 R39: los pagos simulados solo cuentan con la pasarela simulada, como en la generación")
    void incluirSimuladosSegunPropiedad() {
        participante = crear("mercadopago");
        sinPendientes();
        when(pagos.pagadosSinLiquidar(false)).thenReturn(List.of());

        participante.impedimentos(ARRENDADOR, AHORA);

        verify(pagos).pagadosSinLiquidar(false);
        verify(pagos, never()).pagadosSinLiquidar(true);
    }

    @Test
    @DisplayName("HU-22 R44: el bloqueo de generación se toma antes de leer liquidaciones y cobros")
    void bloqueoPrimero() {
        sinPendientes();
        when(pagos.pagadosSinLiquidar(anyBoolean())).thenReturn(List.of());

        participante.impedimentos(ARRENDADOR, AHORA);

        var orden = inOrder(liquidaciones, pagos);
        orden.verify(liquidaciones).bloquearGeneracion(LiquidacionRepository.CLAVE_GENERACION);
        orden.verify(liquidaciones).existsByArrendadorIdAndEstadoIn(ID, EstadoLiquidacion.POR_PAGAR);
        orden.verify(pagos).pagadosSinLiquidar(true);
    }

    @Test
    @DisplayName("HU-22 R44: impedimentos y anonimizar exigen la transacción del llamador")
    void exigenTransaccion() throws Exception {
        var impedimentos = DatosPersonalesPagosImpl.class.getMethod("impedimentos", TitularDatos.class, Instant.class);
        var anonimizar = DatosPersonalesPagosImpl.class.getMethod("anonimizar", TitularDatos.class);

        assertThat(impedimentos.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.MANDATORY);
        assertThat(anonimizar.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.MANDATORY);
        assertThat(DatosPersonalesPagosImpl.class.getAnnotation(Order.class).value()).isEqualTo(2);
    }

    @Test
    @DisplayName("HU-22 R27: anonimizar borra la cuenta bancaria del arrendador bajo su bloqueo")
    void borraCuentaBancaria() {
        var cuenta = CuentaBancaria.registrar(ID, "BCP", "19312345671234", "Ana Ríos EIRL", AHORA);
        when(cuentas.findParaActualizar(ID)).thenReturn(Optional.of(cuenta));

        participante.anonimizar(ARRENDADOR);

        verify(cuentas).delete(cuenta);
    }

    @Test
    @DisplayName("HU-22 R27: anonimizar sin cuenta bancaria no falla")
    void sinCuentaNoFalla() {
        when(cuentas.findParaActualizar(ID)).thenReturn(Optional.empty());

        participante.anonimizar(ARRENDADOR);

        verify(cuentas, never()).delete(any());
    }
}
