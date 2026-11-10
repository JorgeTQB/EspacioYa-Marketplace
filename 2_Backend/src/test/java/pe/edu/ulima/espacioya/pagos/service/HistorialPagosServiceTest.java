package pe.edu.ulima.espacioya.pagos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.ErrorCampoDTO;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ValidacionException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.pagos.domain.EstadoPago;
import pe.edu.ulima.espacioya.pagos.domain.Pago;
import pe.edu.ulima.espacioya.pagos.domain.Reembolso;
import pe.edu.ulima.espacioya.pagos.dto.PaginaTransaccionesDTO;
import pe.edu.ulima.espacioya.pagos.dto.TransaccionDTO;
import pe.edu.ulima.espacioya.pagos.repository.PagoRepository;
import pe.edu.ulima.espacioya.pagos.repository.ReembolsoRepository;
import pe.edu.ulima.espacioya.pagos.service.comprobante.DatosComprobante;
import pe.edu.ulima.espacioya.pagos.service.comprobante.GeneradorComprobantePdf;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservaDelUsuario;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservasDelUsuario;

@ExtendWith(MockitoExtension.class)
class HistorialPagosServiceTest {

    // 2026-11-05 02:30 UTC = 2026-11-04 21:30 en Lima: el día del pago es el 4, no el 5.
    private static final Instant PAGADO_EN = Instant.parse("2026-11-05T02:30:00Z");
    private static final UsuarioAutenticado CARLA = new UsuarioAutenticado(9L, 41L, "USUARIO");
    private static final ReservaDelUsuario RESERVA = new ReservaDelUsuario(5L, 40L, "Cancha Los Olivos",
            LocalDate.parse("2026-11-03"), 14, 17);
    private static final byte[] PDF = "%PDF-1.4 doble".getBytes();

    @Mock
    private PagoRepository pagos;
    @Mock
    private ReembolsoRepository reembolsos;
    @Mock
    private ReservasDelUsuario reservas;
    @Mock
    private GeneradorComprobantePdf generador;

    private HistorialPagosService servicio;

    @BeforeEach
    void preparar() {
        servicio = servicioCon("simulada");
    }

    private HistorialPagosService servicioCon(String pasarela) {
        return new HistorialPagosService(pagos, reembolsos, reservas, generador, pasarela);
    }

    private static Pago pago(long id, EstadoPago estado, String transaccion, String ultimos4) {
        var pago = Pago.de(5L, transaccion, new BigDecimal("241.50"), estado, ultimos4, PAGADO_EN);
        ReflectionTestUtils.setField(pago, "id", id);
        return pago;
    }

    private static Reembolso reembolso(long id, long pagoId, String monto, boolean hecho) {
        var reembolso = Reembolso.porCancelacion(pagoId, new BigDecimal(monto), PAGADO_EN);
        ReflectionTestUtils.setField(reembolso, "id", id);
        reembolso.iniciarIntento(PAGADO_EN);
        if (hecho) {
            reembolso.marcarReembolsado("mp-" + id);
        } else {
            reembolso.marcarFallido();
        }
        return reembolso;
    }

    private void pagina(Pageable pedida, boolean hayMas, Pago... filas) {
        when(reservas.todas(9L)).thenReturn(List.of(RESERVA));
        when(pagos.deReservas(eq(Set.of(5L)), eq(EstadoPago.EN_HISTORIAL), anyBoolean(), eq(pedida)))
                .thenReturn(new SliceImpl<>(List.of(filas), pedida, hayMas));
    }

    @Test
    @DisplayName("HU-28 R9: otro rol o sin sesión recibe 403 antes de validar página o id y sin leer nada")
    void soloElUsuario() {
        for (var otro : Arrays.asList(new UsuarioAutenticado(1L, 2L, "ARRENDADOR"),
                new UsuarioAutenticado(1L, 2L, "ADMIN"), null)) {
            assertThatThrownBy(() -> servicio.listar(otro, "abc")).isInstanceOf(AccesoDenegadoException.class);
            assertThatThrownBy(() -> servicio.comprobante(otro, "abc")).isInstanceOf(AccesoDenegadoException.class);
        }
        verifyNoInteractions(pagos, reembolsos, reservas, generador);
    }

    @Test
    @DisplayName("HU-28 R2, R5, R8: pide solo PAGADO y REEMBOLSADO de las reservas de la sesión, de 10 en 10 y con hayMas")
    void consultaPaginadaDeLasSuyas() {
        var pedida = PageRequest.of(1, 10);
        pagina(pedida, true, pago(100L, EstadoPago.PAGADO, "MP-1", "4242"));
        when(reembolsos.findByPagoIdInOrderByCreadoEnAscIdAsc(List.of(100L))).thenReturn(List.of());

        var resultado = servicio.listar(CARLA, "2");

        assertThat(resultado.pagina()).isEqualTo(2);
        assertThat(resultado.hayMas()).isTrue();
        assertThat(EstadoPago.EN_HISTORIAL).containsExactlyInAnyOrder(EstadoPago.PAGADO, EstadoPago.REEMBOLSADO);
        verify(reservas).todas(9L);
        verify(pagos).deReservas(Set.of(5L), EstadoPago.EN_HISTORIAL, true, pedida);
    }

    @Test
    @DisplayName("HU-28 R3: ningún estado fuera del historial está en EN_HISTORIAL")
    void estadosFueraDelHistorial() {
        assertThat(EstadoPago.EN_HISTORIAL).doesNotContain(EstadoPago.RECHAZADO, EstadoPago.PAGADO_TARDIO,
                EstadoPago.MONTO_DISTINTO, EstadoPago.REEMBOLSO_FALLIDO);
    }

    @Test
    @DisplayName("HU-28 R1, R4: la fila trae fecha en Lima, espacio, monto, estado y, si REEMBOLSADO, la suma de sus "
            + "reembolsos REEMBOLSADO; PAGADO la trae nula")
    void contenidoDeLaFila() {
        var pedida = PageRequest.of(0, 10);
        pagina(pedida, false, pago(101L, EstadoPago.REEMBOLSADO, "MP-2", "1111"),
                pago(100L, EstadoPago.PAGADO, "MP-1", "4242"));
        when(reembolsos.findByPagoIdInOrderByCreadoEnAscIdAsc(List.of(101L, 100L))).thenReturn(List.of(
                reembolso(1L, 101L, "100.00", true), reembolso(2L, 101L, "41.50", true),
                reembolso(3L, 101L, "50.00", false)));

        var resultado = servicio.listar(CARLA, null);

        assertThat(resultado.pagina()).isEqualTo(1);
        assertThat(resultado.hayMas()).isFalse();
        var dia = LocalDate.parse("2026-11-04");
        assertThat(resultado.transacciones()).containsExactly(
                new TransaccionDTO(101L, 5L, dia, "Cancha Los Olivos", new BigDecimal("241.50"), "REEMBOLSADO",
                        new BigDecimal("141.50"), false),
                new TransaccionDTO(100L, 5L, dia, "Cancha Los Olivos", new BigDecimal("241.50"), "PAGADO", null,
                        false));
        // Un solo lote de reembolsos por página, sin N+1.
        verify(reembolsos).findByPagoIdInOrderByCreadoEnAscIdAsc(List.of(101L, 100L));
        verifyNoMoreInteractions(reembolsos);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "", "2147483648"})
    @DisplayName("HU-28 R6: una página que no es un entero de 1 en adelante da 400 en pagina sin consultar")
    void paginaInvalida(String pagina) {
        assertThatThrownBy(() -> servicio.listar(CARLA, pagina)).isInstanceOf(ValidacionException.class)
                .satisfies(e -> assertThat(((ValidacionException) e).getErrores())
                        .containsExactly(new ErrorCampoDTO("pagina", "La página no es válida.")));
        verifyNoInteractions(pagos, reservas, reembolsos);
    }

    @Test
    @DisplayName("HU-28 R7: sin reservas o sin pagos elegibles responde la lista vacía y hayMas falso")
    void listaVacia() {
        when(reservas.todas(9L)).thenReturn(List.of());

        var sinReservas = servicio.listar(CARLA, null);

        assertThat(sinReservas).isEqualTo(new PaginaTransaccionesDTO(1, List.of(), false));
        verifyNoInteractions(pagos, reembolsos);

        var pedida = PageRequest.of(0, 10);
        pagina(pedida, false);
        var sinPagos = servicio.listar(CARLA, null);

        assertThat(sinPagos).isEqualTo(new PaginaTransaccionesDTO(1, List.of(), false));
        verify(reembolsos, never()).findByPagoIdInOrderByCreadoEnAscIdAsc(anyCollection());
    }

    @Test
    @DisplayName("HU-28 R18: el DTO no declara últimos 4 dígitos, id de la pasarela ni usuario")
    void sinDatosSensibles() {
        var campos = Arrays.stream(TransaccionDTO.class.getRecordComponents())
                .map(componente -> componente.getName().toLowerCase()).toList();

        assertThat(campos).containsExactly("id", "reservaid", "fecha", "espacio", "monto", "estado",
                "montoreembolsado", "simulada");
        assertThat(campos).noneMatch(nombre -> nombre.contains("ultimos") || nombre.contains("transaccion")
                || nombre.contains("pasarela") || nombre.contains("usuario"));
    }

    @Test
    @DisplayName("HU-28 R19: con la pasarela simulada los pagos sim- se piden y salen con simulada = true")
    void simuladosConPasarelaSimulada() {
        var pedida = PageRequest.of(0, 10);
        pagina(pedida, false, pago(100L, EstadoPago.PAGADO, "sim-1-APROBADO-1", "4242"));
        when(reembolsos.findByPagoIdInOrderByCreadoEnAscIdAsc(List.of(100L))).thenReturn(List.of());

        var resultado = servicioCon(" SIMULADA ").listar(CARLA, null);

        assertThat(resultado.transacciones()).extracting(TransaccionDTO::simulada).containsExactly(true);
        verify(pagos).deReservas(Set.of(5L), EstadoPago.EN_HISTORIAL, true, pedida);
    }

    @ParameterizedTest
    @ValueSource(strings = {"mercadopago", "", "otra"})
    @DisplayName("HU-28 R20: con una pasarela que no es simulada el listado no incluye los pagos sim-")
    void simuladosConPasarelaReal(String pasarela) {
        var pedida = PageRequest.of(0, 10);
        when(reservas.todas(9L)).thenReturn(List.of(RESERVA));
        when(pagos.deReservas(any(), any(), eq(false), eq(pedida))).thenReturn(new SliceImpl<>(List.of(), pedida, false));

        servicioCon(pasarela).listar(CARLA, null);

        verify(pagos).deReservas(Set.of(5L), EstadoPago.EN_HISTORIAL, false, pedida);
    }

    @Test
    @DisplayName("HU-28 R11, R12, R13, R14: el comprobante de un pago propio arma los datos y llama al generador")
    void comprobanteDeUnPagoPropio() {
        when(pagos.findById(101L)).thenReturn(Optional.of(pago(101L, EstadoPago.REEMBOLSADO, "MP-2", "1111")));
        when(reservas.una(9L, 5L)).thenReturn(Optional.of(RESERVA));
        when(reembolsos.findByPagoIdInOrderByCreadoEnAscIdAsc(List.of(101L)))
                .thenReturn(List.of(reembolso(1L, 101L, "241.50", true)));
        when(generador.generar(any())).thenReturn(PDF);

        var comprobante = servicio.comprobante(CARLA, "101");

        assertThat(comprobante.nombreArchivo()).isEqualTo("comprobante-101.pdf");
        assertThat(comprobante.contenido()).isEqualTo(PDF);
        var datos = ArgumentCaptor.forClass(DatosComprobante.class);
        verify(generador).generar(datos.capture());
        assertThat(datos.getValue()).isEqualTo(new DatosComprobante(101L, LocalDate.parse("2026-11-04"),
                "Cancha Los Olivos", LocalDate.parse("2026-11-03"), 14, 17, new BigDecimal("241.50"),
                "REEMBOLSADO", new BigDecimal("241.50"), "1111", false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "1.5", "", " 1", "1 ", "9999999999999999999", "1234567890123456789"})
    @DisplayName("HU-28 R17: un id mal escrito da el 404 genérico sin tocar el repositorio ni el generador")
    void idMalEscrito(String id) {
        assertThatThrownBy(() -> servicio.comprobante(CARLA, id)).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(pagos, reservas, reembolsos, generador);
    }

    @Test
    @DisplayName("HU-28 R17: un pago inexistente da el 404 genérico sin generar el PDF")
    void inexistente() {
        when(pagos.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.comprobante(CARLA, "77")).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(generador, reservas);
    }

    @Test
    @DisplayName("HU-28 R8, R17: un pago de otro usuario (la reserva no es suya) da el mismo 404 sin generar el PDF")
    void deOtroUsuario() {
        when(pagos.findById(100L)).thenReturn(Optional.of(pago(100L, EstadoPago.PAGADO, "MP-1", "4242")));
        when(reservas.una(9L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.comprobante(CARLA, "100")).isInstanceOf(RecursoNoEncontradoException.class);
        verify(reservas).una(9L, 5L);
        verifyNoInteractions(generador);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECHAZADO", "PAGADO_TARDIO", "MONTO_DISTINTO", "REEMBOLSO_FALLIDO"})
    @DisplayName("HU-28 R3, R17: un pago fuera del historial da el 404 genérico sin consultar la reserva ni generar")
    void noElegible(String estado) {
        when(pagos.findById(100L))
                .thenReturn(Optional.of(pago(100L, EstadoPago.valueOf(estado), "MP-1", "4242")));

        assertThatThrownBy(() -> servicio.comprobante(CARLA, "100")).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(reservas, generador);
    }

    @Test
    @DisplayName("HU-28 R19, R20: el comprobante de un pago sim- sale con la pasarela simulada y da 404 con una real")
    void comprobanteSimulado() {
        when(pagos.findById(100L)).thenReturn(Optional.of(pago(100L, EstadoPago.PAGADO, "sim-1-APROBADO-1", "4242")));
        when(reservas.una(9L, 5L)).thenReturn(Optional.of(RESERVA));
        when(reembolsos.findByPagoIdInOrderByCreadoEnAscIdAsc(List.of(100L))).thenReturn(List.of());
        when(generador.generar(any())).thenReturn(PDF);

        servicio.comprobante(CARLA, "100");

        var datos = ArgumentCaptor.forClass(DatosComprobante.class);
        verify(generador).generar(datos.capture());
        assertThat(datos.getValue().simulado()).isTrue();
        assertThat(datos.getValue().montoReembolsado()).isNull();

        assertThatThrownBy(() -> servicioCon("mercadopago").comprobante(CARLA, "100"))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoMoreInteractions(generador);
    }

    @Test
    @DisplayName("HU-28 R22: los métodos solo leen; el servicio no usa ninguna operación de escritura de los repositorios")
    void soloLee() {
        when(pagos.findById(100L)).thenReturn(Optional.of(pago(100L, EstadoPago.PAGADO, "MP-1", "4242")));
        when(reservas.una(9L, 5L)).thenReturn(Optional.of(RESERVA));
        when(reembolsos.findByPagoIdInOrderByCreadoEnAscIdAsc(List.of(100L))).thenReturn(List.of());
        when(generador.generar(any())).thenReturn(PDF);

        servicio.comprobante(CARLA, "100");

        verify(pagos).findById(100L);
        verifyNoMoreInteractions(pagos);
        verify(reembolsos).findByPagoIdInOrderByCreadoEnAscIdAsc(List.of(100L));
        verifyNoMoreInteractions(reembolsos);
        assertThat(HistorialPagosService.class.getMethods())
                .filteredOn(m -> m.getName().equals("listar") || m.getName().equals("comprobante"))
                .allSatisfy(m -> assertThat(m.getAnnotation(org.springframework.transaction.annotation.Transactional.class)
                        .readOnly()).isTrue());
    }
}
