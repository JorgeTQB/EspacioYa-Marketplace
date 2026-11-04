package pe.edu.ulima.espacioya.pagos.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.DemasiadosIntentosException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.limite.LimitadorIntentos;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.transaccion.DespuesDelCommit;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCambioCuentaBancaria;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.cuentas.service.NotificadorCorreo;
import pe.edu.ulima.espacioya.pagos.domain.CuentaBancaria;
import pe.edu.ulima.espacioya.pagos.dto.CuentaBancariaDTO;
import pe.edu.ulima.espacioya.pagos.dto.GuardarCuentaBancariaDTO;
import pe.edu.ulima.espacioya.pagos.dto.MiCuentaBancariaDTO;
import pe.edu.ulima.espacioya.pagos.repository.CuentaBancariaRepository;

/**
 * Registro, cambio y consulta de la cuenta bancaria del arrendador (HU-18). El orden de las comprobaciones de
 * {@link #guardar} es el de DT-7: rol, identidad, límite, contraseña y, ya en transacción, bloqueo de la cuenta.
 */
@Service
public class CuentaBancariaService {

    public static final String ACCION_REGISTRADA = "CUENTA_BANCARIA_REGISTRADA";
    public static final String ACCION_CAMBIADA = "CUENTA_BANCARIA_CAMBIADA";
    public static final String ACCION_CONTRASENA_FALLIDA = "CUENTA_BANCARIA_CONTRASENA_FALLIDA";
    public static final int MAXIMO_FALLOS = 5;
    public static final Duration VENTANA_FALLOS = Duration.ofMinutes(15);

    public static final String MENSAJE_IDENTIDAD_NO_VERIFICADA =
            "Para registrar una cuenta bancaria, primero debes verificar tu identidad.";
    public static final String MENSAJE_CONTRASENA_INCORRECTA = "La contraseña no es correcta.";
    public static final String MENSAJE_YA_REGISTRADA = "Esa cuenta ya es la que tienes registrada.";

    static final String PREFIJO_CLAVE = "cuenta-bancaria|";
    private static final String ROL_ARRENDADOR = "ARRENDADOR";
    private static final String ENTIDAD_CUENTA = "cuenta_bancaria";
    private static final String ENTIDAD_USUARIO = "usuario";

    private final CuentaBancariaRepository cuentas;
    private final CuentaService cuentasUsuario;
    private final NotificadorCorreo notificador;
    private final LimitadorIntentos limitador;
    private final AuditoriaService auditoria;
    private final TransactionTemplate transacciones;
    private final Clock reloj;

    public CuentaBancariaService(CuentaBancariaRepository cuentas, CuentaService cuentasUsuario,
                                 NotificadorCorreo notificador, LimitadorIntentos limitador,
                                 AuditoriaService auditoria, TransactionTemplate transacciones, Clock reloj) {
        this.cuentas = cuentas;
        this.cuentasUsuario = cuentasUsuario;
        this.notificador = notificador;
        this.limitador = limitador;
        this.auditoria = auditoria;
        this.transacciones = transacciones;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public MiCuentaBancariaDTO consultar(UsuarioAutenticado usuario) {
        exigirArrendador(usuario);
        var verificada = cuentasUsuario.habilitacionArrendador(usuario).identidadVerificada();
        var ahora = Instant.now(reloj);
        var cuenta = cuentas.findByArrendadorId(usuario.usuarioId()).map(c -> CuentaBancariaDTO.de(c, ahora))
                .orElse(null);
        return new MiCuentaBancariaDTO(verificada, cuenta);
    }

    public CuentaBancariaDTO guardar(UsuarioAutenticado usuario, GuardarCuentaBancariaDTO solicitud, String ip) {
        exigirArrendador(usuario);
        if (!cuentasUsuario.habilitacionArrendador(usuario).identidadVerificada()) {
            throw new ReglaNegocioException(MENSAJE_IDENTIDAD_NO_VERIFICADA);
        }
        var id = usuario.usuarioId();
        var clave = PREFIJO_CLAVE + id;
        if (limitador.superaLimite(clave, MAXIMO_FALLOS, VENTANA_FALLOS)) {
            throw new DemasiadosIntentosException();
        }
        if (!cuentasUsuario.confirmarContrasena(usuario, solicitud.contrasena())) {
            limitador.registrar(clave);
            // No hay transacción abierta: la auditoría se confirma sola y no se pierde con el rechazo (R7).
            auditoria.registrar(new EventoAuditoria(id, ACCION_CONTRASENA_FALLIDA, ENTIDAD_USUARIO, id, ip, null));
            throw new ReglaNegocioException("contrasena", MENSAJE_CONTRASENA_INCORRECTA);
        }
        limitador.limpiar(clave);
        return transacciones.execute(estado -> escribir(usuario, solicitud, ip));
    }

    // El bloqueo de la fila de usuario va después del bcrypt (arriba) para no retenerla durante el hash (DT-8).
    private CuentaBancariaDTO escribir(UsuarioAutenticado usuario, GuardarCuentaBancariaDTO solicitud, String ip) {
        var id = usuario.usuarioId();
        cuentasUsuario.bloquearCuenta(id);
        if (!cuentasUsuario.habilitacionArrendador(usuario).identidadVerificada()) {
            throw new ReglaNegocioException(MENSAJE_IDENTIDAD_NO_VERIFICADA);
        }
        var ahora = Instant.now(reloj);
        var vigente = cuentas.findParaActualizar(id).orElse(null);
        if (vigente == null) {
            return registrar(id, solicitud, ip, ahora);
        }
        if (vigente.esLaMisma(solicitud.banco(), solicitud.numero())) {
            throw new ReglaNegocioException(MENSAJE_YA_REGISTRADA);
        }
        return cambiar(vigente, solicitud, ip, ahora);
    }

    private CuentaBancariaDTO registrar(Long id, GuardarCuentaBancariaDTO solicitud, String ip, Instant ahora) {
        var titular = cuentasUsuario.razonSocialDe(id);
        var cuenta = cuentas.saveAndFlush(
                CuentaBancaria.registrar(id, solicitud.banco(), solicitud.numero(), titular, ahora));
        auditoria.registrar(new EventoAuditoria(id, ACCION_REGISTRADA, ENTIDAD_CUENTA, cuenta.getId(), ip,
                "banco=" + cuenta.getBanco() + ";cuenta=" + cuenta.enmascarado()));
        return CuentaBancariaDTO.de(cuenta, ahora);
    }

    private CuentaBancariaDTO cambiar(CuentaBancaria cuenta, GuardarCuentaBancariaDTO solicitud, String ip,
                                      Instant ahora) {
        var id = cuenta.getArrendadorId();
        var anterior = cuenta.getBanco() + "/" + cuenta.enmascarado();
        cuenta.cambiar(solicitud.banco(), solicitud.numero(), cuenta.getTitular(), ahora);
        cuentas.saveAndFlush(cuenta);
        auditoria.registrar(new EventoAuditoria(id, ACCION_CAMBIADA, ENTIDAD_CUENTA, cuenta.getId(), ip,
                "anterior=" + anterior + ";nueva=" + cuenta.getBanco() + "/" + cuenta.enmascarado()
                        + ";retenerHasta=" + cuenta.getRetenerHasta()));
        var aviso = new AvisoCambioCuentaBancaria(cuenta.getBanco(), cuenta.enmascarado(), cuenta.getRetenerHasta());
        var correo = cuentasUsuario.correoDe(id);
        DespuesDelCommit.ejecutar(() -> notificador.notificarCambioCuentaBancaria(correo, aviso));
        return CuentaBancariaDTO.de(cuenta, ahora);
    }

    private static void exigirArrendador(UsuarioAutenticado usuario) {
        if (usuario == null || !ROL_ARRENDADOR.equals(usuario.rol())) {
            throw new AccesoDenegadoException();
        }
    }
}
