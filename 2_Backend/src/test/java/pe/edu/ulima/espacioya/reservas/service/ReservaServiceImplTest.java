package pe.edu.ulima.espacioya.reservas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.RelojControlable;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.espacios.service.DisponibilidadService;
import pe.edu.ulima.espacioya.espacios.service.EspacioRetenido;
import pe.edu.ulima.espacioya.reservas.domain.EstadoReserva;
import pe.edu.ulima.espacioya.reservas.domain.Reserva;
import pe.edu.ulima.espacioya.reservas.domain.ReservaBloque;
import pe.edu.ulima.espacioya.reservas.dto.CrearReservaDTO;
import pe.edu.ulima.espacioya.reservas.dto.ReservaDTO;
import pe.edu.ulima.espacioya.reservas.repository.ReservaBloqueRepository;
import pe.edu.ulima.espacioya.reservas.repository.ReservaRepository;

@ExtendWith(MockitoExtension.class)
class ReservaServiceImplTest {

    // 10:00 del miércoles 2026-10-28 en Lima.
    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 28);
    private static final LocalDate FECHA = LocalDate.of(2026, 11, 2);
    private static final long USUARIO_ID = 7L;
    private static final long ESPACIO_ID = 40L;
    private static final long RESERVA_ID = 90L;
    private static final String IP = "10.14.0.1";
    private static final String CONFLICTO = "ERROR: duplicate key value violates unique constraint "
            + "\"uq_reserva_bloque_activo\"";

    private static final UsuarioAutenticado USUARIO = new UsuarioAutenticado(USUARIO_ID, 31L, "USUARIO");
    private static final CrearReservaDTO SOLICITUD = new CrearReservaDTO(ESPACIO_ID, FECHA.toString(), List.of(15, 14));
    private static final EspacioRetenido RETENIDO = new EspacioRetenido("Cancha Los Olivos", new BigDecimal("80.50"));

    @Mock
    private ReservaRepository reservas;
    @Mock
    private ReservaBloqueRepository bloques;
    @Mock
    private DisponibilidadService disponibilidad;
    @Mock
    private CatalogoEspaciosService catalogo;
    @Mock
    private CuentaService cuentas;
    @Mock
    private ExpiracionReservas expiracion;
    @Mock
    private AuditoriaService auditoria;

    private ReservaServiceImpl servicio;

    @BeforeEach
    void preparar() {
        servicio = new ReservaServiceImpl(reservas, bloques, disponibilidad, catalogo, cuentas, expiracion, auditoria,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private void disponible() {
        when(reservas.contarVigentes(USUARIO_ID, AHORA)).thenReturn(2L);
        when(disponibilidad.retenerSiLibres(ESPACIO_ID, FECHA, List.of(14, 15))).thenReturn(RETENIDO);
        when(reservas.save(any(Reserva.class))).thenAnswer(llamada -> {
            Reserva reserva = llamada.getArgument(0);
            ReflectionTestUtils.setField(reserva, "id", RESERVA_ID);
            return reserva;
        });
    }

    private static Reserva guardada(Instant creadaEn) {
        var reserva = Reserva.pendiente(USUARIO_ID, ESPACIO_ID, new BigDecimal("80.50"), 2, creadaEn);
        ReflectionTestUtils.setField(reserva, "id", RESERVA_ID);
        return reserva;
    }

    private void sinEfectos() {
        verify(reservas, never()).save(any());
        verify(bloques, never()).saveAllAndFlush(anyList());
        verifyNoInteractions(auditoria);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ARRENDADOR", "ADMIN"})
    @DisplayName("HU-12 R36: un rol distinto de USUARIO es 403 sin tocar repositorios")
    void rolDistintoDeUsuario(String rol) {
        var cuenta = new UsuarioAutenticado(USUARIO_ID, 31L, rol);

        assertThatThrownBy(() -> servicio.crear(cuenta, SOLICITUD, IP)).isInstanceOf(AccesoDenegadoException.class);

        verifyNoInteractions(reservas, bloques, disponibilidad, cuentas, expiracion, auditoria);
    }

    @Test
    @DisplayName("HU-12 R1, R2: crea a nombre del usuario de la sesión y responde PENDIENTE_PAGO con 900 segundos")
    void creaLaReserva() {
        disponible();

        var dto = servicio.crear(USUARIO, SOLICITUD, IP);

        assertThat(dto).isEqualTo(new ReservaDTO(RESERVA_ID, ESPACIO_ID, "Cancha Los Olivos", "PENDIENTE_PAGO", FECHA,
                14, 16, new BigDecimal("80.50"), new BigDecimal("161.00"), AHORA.plus(Duration.ofMinutes(15)), 900));
        var reserva = ArgumentCaptor.forClass(Reserva.class);
        verify(reservas).save(reserva.capture());
        assertThat(reserva.getValue().getUsuarioId()).isEqualTo(USUARIO_ID);
    }

    @Test
    @DisplayName("HU-12 R3: el precio sale del espacio retenido y el monto del servidor")
    void elMontoSaleDelServidor() {
        disponible();

        servicio.crear(USUARIO, SOLICITUD, IP);

        var reserva = ArgumentCaptor.forClass(Reserva.class);
        verify(reservas).save(reserva.capture());
        assertThat(reserva.getValue().getPrecioHoraAplicado()).isEqualByComparingTo("80.50");
        assertThat(reserva.getValue().getMontoTotal()).isEqualByComparingTo("161.00");
    }

    @Test
    @DisplayName("HU-12 R4: guarda un bloque activo por hora con la reserva, el espacio y la fecha")
    void guardaUnBloquePorHora() {
        disponible();

        servicio.crear(USUARIO, SOLICITUD, IP);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReservaBloque>> filas = ArgumentCaptor.forClass(List.class);
        verify(bloques).saveAllAndFlush(filas.capture());
        assertThat(filas.getValue()).extracting(ReservaBloque::getHoraInicio).containsExactly(14, 15);
        assertThat(filas.getValue()).allSatisfy(fila -> {
            assertThat(fila.getReservaId()).isEqualTo(RESERVA_ID);
            assertThat(fila.getEspacioId()).isEqualTo(ESPACIO_ID);
            assertThat(fila.getFecha()).isEqualTo(FECHA);
            assertThat(fila.isActivo()).isTrue();
        });
    }

    @Test
    @DisplayName("HU-12 R17, R28: bloquea cuenta, luego espacio, libera vencidas y al final guarda")
    void ordenDeLaTransaccion() {
        disponible();

        servicio.crear(USUARIO, SOLICITUD, IP);

        var orden = inOrder(cuentas, reservas, disponibilidad, expiracion, bloques, auditoria);
        orden.verify(cuentas).bloquearCuenta(USUARIO_ID);
        orden.verify(reservas).contarVigentes(USUARIO_ID, AHORA);
        orden.verify(disponibilidad).retenerSiLibres(ESPACIO_ID, FECHA, List.of(14, 15));
        orden.verify(expiracion).liberarVencidas(ESPACIO_ID, FECHA, List.of(14, 15), AHORA);
        orden.verify(reservas).save(any(Reserva.class));
        orden.verify(bloques).saveAllAndFlush(anyList());
        orden.verify(auditoria).registrar(any());
    }

    @Test
    @DisplayName("HU-12 R2, R17: el instante se toma tras retener el espacio: libera lo que venció durante la espera y "
            + "la retención empieza ahí")
    void elInstanteSeTomaTrasRetenerElEspacio() {
        var reloj = new RelojControlable(AHORA);
        var conEspera = new ReservaServiceImpl(reservas, bloques, disponibilidad, catalogo, cuentas, expiracion,
                auditoria, new TransactionTemplate(mock(PlatformTransactionManager.class)), reloj);
        var trasLaEspera = AHORA.plusSeconds(5);
        when(reservas.contarVigentes(USUARIO_ID, AHORA)).thenReturn(0L);
        when(disponibilidad.retenerSiLibres(ESPACIO_ID, FECHA, List.of(14, 15))).thenAnswer(llamada -> {
            reloj.avanzar(Duration.ofSeconds(5));
            return RETENIDO;
        });
        when(reservas.save(any(Reserva.class))).thenAnswer(llamada -> llamada.getArgument(0));

        conEspera.crear(USUARIO, SOLICITUD, IP);

        verify(expiracion).liberarVencidas(ESPACIO_ID, FECHA, List.of(14, 15), trasLaEspera);
        var reserva = ArgumentCaptor.forClass(Reserva.class);
        verify(reservas).save(reserva.capture());
        assertThat(reserva.getValue().getCreadaEn()).isEqualTo(trasLaEspera);
        assertThat(reserva.getValue().getExpiraEn()).isEqualTo(trasLaEspera.plus(Duration.ofMinutes(15)));
    }

    @Test
    @DisplayName("HU-12 R42: audita RESERVA_CREADA con el usuario, la IP y el detalle exacto")
    void auditaLaCreacion() {
        disponible();

        servicio.crear(USUARIO, SOLICITUD, IP);

        verify(auditoria).registrar(new EventoAuditoria(USUARIO_ID, "RESERVA_CREADA", "reserva", RESERVA_ID, IP,
                "espacioId=40;fecha=2026-11-02;horas=14,15;monto=161.00"));
    }

    @Test
    @DisplayName("HU-12 R26, R27, R44: con 3 vigentes responde cupo sin llamar a retenerSiLibres ni auditar")
    void cupoSuperado() {
        when(reservas.contarVigentes(USUARIO_ID, AHORA)).thenReturn(3L);

        assertThatThrownBy(() -> servicio.crear(USUARIO, SOLICITUD, IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(ReservaServiceImpl.MENSAJE_CUPO)
                .extracting("campo").isNull();

        verifyNoInteractions(disponibilidad, expiracion);
        sinEfectos();
    }

    @Test
    @DisplayName("HU-12 R18, R44: si alguna hora no está libre propaga el conflicto sin guardar ni auditar")
    void conflictoDeHorario() {
        when(reservas.contarVigentes(USUARIO_ID, AHORA)).thenReturn(0L);
        when(disponibilidad.retenerSiLibres(ESPACIO_ID, FECHA, List.of(14, 15)))
                .thenThrow(new ReglaNegocioException(DisponibilidadService.MENSAJE_HORARIO_NO_DISPONIBLE));

        assertThatThrownBy(() -> servicio.crear(USUARIO, SOLICITUD, IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(DisponibilidadService.MENSAJE_HORARIO_NO_DISPONIBLE);

        verifyNoInteractions(expiracion);
        sinEfectos();
    }

    @Test
    @DisplayName("HU-12 R33: un espacio no visible es 404 sin guardar ni auditar")
    void espacioNoVisible() {
        when(reservas.contarVigentes(USUARIO_ID, AHORA)).thenReturn(0L);
        when(disponibilidad.retenerSiLibres(ESPACIO_ID, FECHA, List.of(14, 15)))
                .thenThrow(new RecursoNoEncontradoException());

        assertThatThrownBy(() -> servicio.crear(USUARIO, SOLICITUD, IP))
                .isInstanceOf(RecursoNoEncontradoException.class);

        sinEfectos();
    }

    @Test
    @DisplayName("HU-12 R22: si la inserción viola uq_reserva_bloque_activo responde horario no disponible")
    void violacionDelIndiceUnico() {
        disponible();
        when(bloques.saveAllAndFlush(anyList())).thenThrow(new DataIntegrityViolationException("no se pudo guardar",
                new SQLException(CONFLICTO)));

        assertThatThrownBy(() -> servicio.crear(USUARIO, SOLICITUD, IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(DisponibilidadService.MENSAJE_HORARIO_NO_DISPONIBLE)
                .extracting("campo").isNull();

        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-12 R23: otra violación de integridad no se disfraza de horario no disponible")
    void otraViolacionSeRelanza() {
        disponible();
        var otra = new DataIntegrityViolationException("no se pudo guardar",
                new SQLException("ERROR: violates foreign key constraint \"reserva_espacio_id_fkey\""));
        when(bloques.saveAllAndFlush(anyList())).thenThrow(otra);

        assertThatThrownBy(() -> servicio.crear(USUARIO, SOLICITUD, IP)).isSameAs(otra);
    }

    // ── Orden de R34 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-12 R34: el rol se comprueba antes que la fecha y la selección")
    void rolAntesQueFecha() {
        var arrendador = new UsuarioAutenticado(USUARIO_ID, 31L, "ARRENDADOR");
        var todoMal = new CrearReservaDTO(ESPACIO_ID, "2026-02-30", List.of(14, 16));

        assertThatThrownBy(() -> servicio.crear(arrendador, todoMal, IP)).isInstanceOf(AccesoDenegadoException.class);
    }

    @Test
    @DisplayName("HU-12 R30, R34: una fecha que no existe en el calendario se señala en fecha, antes que la selección")
    void fechaInexistenteAntesQueSeleccion() {
        var solicitud = new CrearReservaDTO(ESPACIO_ID, "2026-02-30", List.of(14, 16));

        assertThatThrownBy(() -> servicio.crear(USUARIO, solicitud, IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(CrearReservaDTO.M_FECHA)
                .extracting("campo").isEqualTo("fecha");

        verifyNoInteractions(cuentas, reservas);
    }

    @Test
    @DisplayName("HU-12 R31, R34: ayer y hoy + 56 están fuera de la ventana, antes que la selección")
    void fechaFueraDeVentana() {
        for (var fecha : List.of(HOY.minusDays(1), HOY.plusDays(56))) {
            var solicitud = new CrearReservaDTO(ESPACIO_ID, fecha.toString(), List.of(14, 16));

            assertThatThrownBy(() -> servicio.crear(USUARIO, solicitud, IP))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(ReservaServiceImpl.MENSAJE_FUERA_DE_VENTANA)
                    .extracting("campo").isEqualTo("fecha");
        }
        verifyNoInteractions(cuentas, reservas);
    }

    @Test
    @DisplayName("HU-12 R31: hoy y hoy + 55 están dentro de la ventana")
    void extremosDeLaVentana() {
        when(reservas.contarVigentes(USUARIO_ID, AHORA)).thenReturn(0L);
        when(disponibilidad.retenerSiLibres(eq(ESPACIO_ID), any(), anyList())).thenReturn(RETENIDO);
        when(reservas.save(any(Reserva.class))).thenAnswer(llamada -> llamada.getArgument(0));

        for (var fecha : List.of(HOY, HOY.plusDays(55))) {
            servicio.crear(USUARIO, new CrearReservaDTO(ESPACIO_ID, fecha.toString(), List.of(14)), IP);

            verify(disponibilidad).retenerSiLibres(ESPACIO_ID, fecha, List.of(14));
        }
    }

    @Test
    @DisplayName("HU-12 R32, R34: una selección inválida se señala en horas, antes del cupo y del bloqueo")
    void seleccionInvalidaAntesDelCupo() {
        for (var horas : List.of(List.of(14, 16), List.of(14, 14), List.of(24), Arrays.asList((Integer) null))) {
            var solicitud = new CrearReservaDTO(ESPACIO_ID, FECHA.toString(), horas);

            assertThatThrownBy(() -> servicio.crear(USUARIO, solicitud, IP))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(CrearReservaDTO.M_HORAS)
                    .extracting("campo").isEqualTo("horas");
        }
        verifyNoInteractions(cuentas, reservas, disponibilidad);
    }

    @Test
    @DisplayName("HU-12 R34: el cupo se comprueba antes que el espacio: con 3 vigentes no se mira si el espacio existe")
    void cupoAntesQueEspacio() {
        when(reservas.contarVigentes(USUARIO_ID, AHORA)).thenReturn(3L);
        var solicitud = new CrearReservaDTO(999_999L, FECHA.toString(), List.of(14));

        assertThatThrownBy(() -> servicio.crear(USUARIO, solicitud, IP)).hasMessage(ReservaServiceImpl.MENSAJE_CUPO);

        verify(disponibilidad, never()).retenerSiLibres(anyLong(), any(), anyList());
    }

    // ── Consulta ─────────────────────────────────────────────────────────

    private void conFilas(int... horas) {
        when(bloques.findByReservaIdOrderByHoraInicioAsc(RESERVA_ID)).thenReturn(Arrays.stream(horas)
                .mapToObj(hora -> new ReservaBloque(RESERVA_ID, ESPACIO_ID, FECHA, hora)).toList());
        when(catalogo.nombres(List.of(ESPACIO_ID))).thenReturn(Map.of(ESPACIO_ID, "Cancha Los Olivos"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "+5", "5.0", "99999999999999999999", ""})
    @DisplayName("HU-12 R38: un id mal escrito, cero o desbordado es 404 sin consultar el repositorio")
    void idMalEscrito(String id) {
        assertThatThrownBy(() -> servicio.consultar(USUARIO, id)).isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(reservas, bloques, catalogo);
    }

    @Test
    @DisplayName("HU-12 R38: una reserva de otro usuario o inexistente es 404, filtrando por el usuario de la sesión")
    void ajenaOInexistente() {
        when(reservas.findByIdAndUsuarioId(RESERVA_ID, USUARIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(USUARIO, String.valueOf(RESERVA_ID)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(reservas, never()).findById(any());
    }

    @Test
    @DisplayName("HU-12 R37, R40: responde el horario de sus filas, el nombre del catálogo y los segundos restantes")
    void consultaUnaVigente() {
        var reserva = guardada(AHORA.minusSeconds(60));
        when(reservas.findByIdAndUsuarioId(RESERVA_ID, USUARIO_ID)).thenReturn(Optional.of(reserva));
        conFilas(14, 15);

        var dto = servicio.consultar(USUARIO, String.valueOf(RESERVA_ID));

        assertThat(dto).isEqualTo(new ReservaDTO(RESERVA_ID, ESPACIO_ID, "Cancha Los Olivos", "PENDIENTE_PAGO", FECHA,
                14, 16, new BigDecimal("80.50"), new BigDecimal("161.00"), reserva.getExpiraEn(), 840));
    }

    @Test
    @DisplayName("HU-12 R39, R40: una vencida se responde EXPIRADA con 0 segundos sin escribir en la base")
    void consultaUnaVencida() {
        var reserva = guardada(AHORA.minus(Duration.ofMinutes(20)));
        when(reservas.findByIdAndUsuarioId(RESERVA_ID, USUARIO_ID)).thenReturn(Optional.of(reserva));
        conFilas(14);

        var dto = servicio.consultar(USUARIO, String.valueOf(RESERVA_ID));

        assertThat(dto.estado()).isEqualTo("EXPIRADA");
        assertThat(dto.segundosRestantes()).isZero();
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
        verify(reservas, never()).save(any());
        verifyNoInteractions(expiracion, auditoria);
    }

    @Test
    @DisplayName("HU-12 R41: el DTO no tiene campos de usuario ni de arrendador")
    void elDtoNoExponeDatosDePersonas() {
        assertThat(Arrays.stream(ReservaDTO.class.getRecordComponents()).map(campo -> campo.getName()))
                .containsExactly("id", "espacioId", "espacioNombre", "estado", "fecha", "horaInicio", "horaFin",
                        "precioHora", "montoTotal", "expiraEn", "segundosRestantes");
    }
}
