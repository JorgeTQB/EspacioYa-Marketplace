package pe.edu.ulima.espacioya.administracion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.administracion.domain.AccionSuspension;
import pe.edu.ulima.espacioya.administracion.domain.SuspensionCuenta;
import pe.edu.ulima.espacioya.administracion.dto.AccionCuentaDTO;
import pe.edu.ulima.espacioya.administracion.dto.SuspensionVigenteDTO;
import pe.edu.ulima.espacioya.administracion.repository.SuspensionCuentaRepository;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.dto.BusquedaCuentasDTO;
import pe.edu.ulima.espacioya.cuentas.dto.CambioEstadoCuentaDTO;
import pe.edu.ulima.espacioya.cuentas.dto.CuentaResumenDTO;
import pe.edu.ulima.espacioya.cuentas.dto.DetalleCuentaDTO;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;

@ExtendWith(MockitoExtension.class)
class CuentaAdminServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-29T15:00:00Z");
    private static final long CUENTA = 7L;
    private static final long ID_REGISTRO = 31L;
    private static final String IP = "127.0.0.1";
    private static final String CORREO = "ana.secreta@correo.pe";
    private static final String MOTIVO = "Publicó el teléfono 912345678 de otra persona.";
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(1L, 99L, "ADMIN");

    @Mock
    private CuentaService cuentas;
    @Mock
    private SuspensionCuentaRepository suspensiones;
    @Mock
    private AuditoriaService auditoria;

    private CuentaAdminService servicio;

    @BeforeEach
    void preparar() {
        servicio = new CuentaAdminService(cuentas, suspensiones, auditoria, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private void guardadoConId() {
        when(suspensiones.save(any(SuspensionCuenta.class))).thenAnswer(invocacion -> {
            SuspensionCuenta registro = invocacion.getArgument(0);
            ReflectionTestUtils.setField(registro, "id", ID_REGISTRO);
            return registro;
        });
    }

    private SuspensionCuenta registroGuardado() {
        var registro = ArgumentCaptor.forClass(SuspensionCuenta.class);
        verify(suspensiones).save(registro.capture());
        return registro.getValue();
    }

    private EventoAuditoria eventoAuditado() {
        var evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        return evento.getValue();
    }

    private static DetalleCuentaDTO detalle(String estado) {
        return new DetalleCuentaDTO(CUENTA, "Ana", CORREO, "987654321", "ARRENDADOR", estado, true, "VERIFICADA",
                "ACTIVA".equals(estado), "SUSPENDIDA".equals(estado));
    }

    @Test
    @DisplayName("HU-09 R41: con un rol distinto de ADMIN o sin sesión los 4 casos de uso lanzan 403 sin llamar a "
            + "CuentaService")
    void rolDistintoDeAdmin() {
        var usuarioRol = new UsuarioAutenticado(CUENTA, 31L, "USUARIO");
        var arrendadorRol = new UsuarioAutenticado(CUENTA, 31L, "ARRENDADOR");
        for (var usuario : new UsuarioAutenticado[] {usuarioRol, arrendadorRol, null}) {
            assertThatThrownBy(() -> servicio.buscar(usuario, "ana")).isInstanceOf(AccesoDenegadoException.class);
            assertThatThrownBy(() -> servicio.detalle(usuario, CUENTA)).isInstanceOf(AccesoDenegadoException.class);
            assertThatThrownBy(() -> servicio.suspender(usuario, CUENTA, MOTIVO, IP))
                    .isInstanceOf(AccesoDenegadoException.class);
            assertThatThrownBy(() -> servicio.reactivar(usuario, CUENTA, IP))
                    .isInstanceOf(AccesoDenegadoException.class);
        }
        verifyNoInteractions(cuentas, suspensiones, auditoria);
    }

    @Test
    @DisplayName("HU-09 R6: sin q, con menos de 3 o más de 100 caracteres tras recortar → 400 en el campo q sin "
            + "llamar a CuentaService")
    void busquedaInvalida() {
        for (var q : new String[] {null, "", "  ab ", "a".repeat(101)}) {
            assertThatThrownBy(() -> servicio.buscar(ADMIN, q))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                        assertThat(e.getCampo()).isEqualTo("q");
                        assertThat(e.getMessage()).isEqualTo("Escribe de 3 a 100 caracteres del correo o del nombre.");
                    });
        }
        verifyNoInteractions(cuentas);
    }

    @Test
    @DisplayName("HU-09 R1: una búsqueda válida pasa el texto recortado a CuentaService y devuelve su resultado")
    void busquedaValidaRecortada() {
        var resultado = new BusquedaCuentasDTO(List.of(new CuentaResumenDTO(CUENTA, "Ana", CORREO, "ARRENDADOR",
                "ACTIVA")), false);
        when(cuentas.buscarCuentas(ADMIN, "Ana")).thenReturn(resultado);
        when(cuentas.buscarCuentas(ADMIN, "a".repeat(100))).thenReturn(resultado);

        assertThat(servicio.buscar(ADMIN, "  Ana  ")).isEqualTo(resultado);
        assertThat(servicio.buscar(ADMIN, " " + "a".repeat(100) + " ")).isEqualTo(resultado);
    }

    @Test
    @DisplayName("HU-09 R10: el detalle de una cuenta SUSPENDIDA trae motivo y fecha de su última SUSPENSION")
    void detalleSuspendidaConSuspension() {
        var cuenta = detalle("SUSPENDIDA");
        when(cuentas.detalleCuenta(ADMIN, CUENTA)).thenReturn(cuenta);
        var registro = SuspensionCuenta.suspender(CUENTA, 1L, MOTIVO, AHORA.minusSeconds(60));
        when(suspensiones.findFirstByUsuarioIdAndAccionOrderByFechaDescIdDesc(CUENTA, AccionSuspension.SUSPENSION))
                .thenReturn(Optional.of(registro));

        var resultado = servicio.detalle(ADMIN, CUENTA);

        assertThat(resultado.cuenta()).isEqualTo(cuenta);
        assertThat(resultado.suspension()).isEqualTo(new SuspensionVigenteDTO(MOTIVO, AHORA.minusSeconds(60)));
    }

    @Test
    @DisplayName("HU-09 R11: el detalle de una cuenta no SUSPENDIDA trae suspension nula sin leer el historial")
    void detalleNoSuspendidaSinSuspension() {
        for (var estado : new String[] {"ACTIVA", "ELIMINADA"}) {
            when(cuentas.detalleCuenta(ADMIN, CUENTA)).thenReturn(detalle(estado));

            assertThat(servicio.detalle(ADMIN, CUENTA).suspension()).isNull();
        }
        verifyNoInteractions(suspensiones);
    }

    @Test
    @DisplayName("HU-09 R12: el 404 de CuentaService se propaga en el detalle")
    void detalleInexistente() {
        when(cuentas.detalleCuenta(ADMIN, CUENTA)).thenThrow(new RecursoNoEncontradoException());

        assertThatThrownBy(() -> servicio.detalle(ADMIN, CUENTA)).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(suspensiones);
    }

    @Test
    @DisplayName("HU-09 R14, R17, R44, R46: suspender guarda el registro con el admin de la sesión y audita el detalle "
            + "exacto sin motivo ni correo")
    void suspenderGuardaYAudita() {
        when(cuentas.suspenderCuenta(ADMIN, CUENTA, MOTIVO)).thenReturn(new CambioEstadoCuentaDTO("SUSPENDIDA", 2));
        guardadoConId();

        var resultado = servicio.suspender(ADMIN, CUENTA, MOTIVO, IP);

        assertThat(resultado).isEqualTo(new AccionCuentaDTO("SUSPENSION", "SUSPENDIDA", AHORA));
        var registro = registroGuardado();
        assertThat(registro.getUsuarioId()).isEqualTo(CUENTA);
        assertThat(registro.getAdminId()).isEqualTo(1L);
        assertThat(registro.getAccion()).isEqualTo(AccionSuspension.SUSPENSION);
        assertThat(registro.getMotivo()).isEqualTo(MOTIVO);
        assertThat(registro.getFecha()).isEqualTo(AHORA);
        var evento = eventoAuditado();
        assertThat(evento).isEqualTo(new EventoAuditoria(1L, "CUENTA_SUSPENDIDA", "usuario", CUENTA, IP,
                "accion=SUSPENSION;registro=31;sesionesRevocadas=2"));
        assertThat(evento.detalle()).doesNotContain("912345678").doesNotContain(CORREO);
    }

    @Test
    @DisplayName("HU-09 R29, R31, R45, R46: reactivar guarda el registro sin motivo y audita el detalle exacto")
    void reactivarGuardaYAudita() {
        when(cuentas.reactivarCuenta(ADMIN, CUENTA)).thenReturn(new CambioEstadoCuentaDTO("ACTIVA", 0));
        guardadoConId();

        var resultado = servicio.reactivar(ADMIN, CUENTA, IP);

        assertThat(resultado).isEqualTo(new AccionCuentaDTO("REACTIVACION", "ACTIVA", AHORA));
        var registro = registroGuardado();
        assertThat(registro.getUsuarioId()).isEqualTo(CUENTA);
        assertThat(registro.getAdminId()).isEqualTo(1L);
        assertThat(registro.getAccion()).isEqualTo(AccionSuspension.REACTIVACION);
        assertThat(registro.getMotivo()).isNull();
        assertThat(registro.getFecha()).isEqualTo(AHORA);
        var evento = eventoAuditado();
        assertThat(evento).isEqualTo(new EventoAuditoria(1L, "CUENTA_REACTIVADA", "usuario", CUENTA, IP,
                "accion=REACTIVACION;registro=31"));
        assertThat(evento.detalle()).doesNotContain(CORREO);
    }

    @Test
    @DisplayName("HU-09 R34, R35, R36, R37, R39: si CuentaService rechaza la acción no se guarda registro ni se audita")
    void rechazoDeCuentaServiceSinRegistroNiAuditoria() {
        when(cuentas.suspenderCuenta(ADMIN, CUENTA, MOTIVO)).thenThrow(new ReglaNegocioException("ya suspendida"));
        when(cuentas.reactivarCuenta(ADMIN, CUENTA)).thenThrow(new RecursoNoEncontradoException());

        assertThatThrownBy(() -> servicio.suspender(ADMIN, CUENTA, MOTIVO, IP))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> servicio.reactivar(ADMIN, CUENTA, IP))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(suspensiones, never()).save(any());
        verifyNoInteractions(auditoria);
    }
}
