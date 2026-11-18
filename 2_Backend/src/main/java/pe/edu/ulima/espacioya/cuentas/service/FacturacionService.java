package pe.edu.ulima.espacioya.cuentas.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.cifrado.HuellaCampos;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.domain.CampoFacturacion;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.Rol;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.ValidadorDocumento;
import pe.edu.ulima.espacioya.cuentas.dto.ActualizacionFacturacionDTO;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;
import pe.edu.ulima.espacioya.cuentas.service.ReautenticacionPerfil.Operacion;

/**
 * Datos de facturación del arrendador (HU-20 CA3). El orden evita gastar intentos en errores de forma: rol, documento,
 * contraseña y, ya en transacción, el bloqueo {@code usuario → perfil_arrendador} (R52). Una identidad verificada con
 * otro documento u otra razón social vuelve a revisión (RN-02, D9) y reutiliza los documentos vigentes (D11).
 */
@Service
public class FacturacionService {

    public static final String ACCION_ACTUALIZADA = "FACTURACION_ACTUALIZADA";
    public static final String ACCION_DOCUMENTO_RECHAZADO = "FACTURACION_DOCUMENTO_RECHAZADO";

    public static final String MENSAJE_IDENTIDAD_EN_REVISION =
            "Tu identidad está en revisión. Podrás cambiar tu documento o razón social cuando termine.";
    public static final String MENSAJE_DOCUMENTO_NO_DISPONIBLE =
            "No pudimos actualizar tu documento. Revisa el número o escríbenos a soporte.";

    static final String CAMPO_NUMERO = "numeroDocumento";
    static final String MOTIVO_DOCUMENTO_EN_USO = "DOCUMENTO_EN_USO";
    private static final String ENTIDAD_PERFIL = "perfil_arrendador";

    private final UsuarioRepository usuarios;
    private final PerfilArrendadorRepository perfiles;
    private final ReautenticacionPerfil reautenticacion;
    private final HuellaCampos huella;
    private final AuditoriaService auditoria;
    private final TransactionTemplate transacciones;

    public FacturacionService(UsuarioRepository usuarios, PerfilArrendadorRepository perfiles,
                              ReautenticacionPerfil reautenticacion, HuellaCampos huella, AuditoriaService auditoria,
                              TransactionTemplate transacciones) {
        this.usuarios = usuarios;
        this.perfiles = perfiles;
        this.reautenticacion = reautenticacion;
        this.huella = huella;
        this.auditoria = auditoria;
        this.transacciones = transacciones;
    }

    public void actualizar(UsuarioAutenticado usuario, ActualizacionFacturacionDTO solicitud, String ip) {
        if (usuario == null || !Rol.ARRENDADOR.name().equals(usuario.rol())) {
            throw new AccesoDenegadoException();
        }
        var id = usuario.usuarioId();
        var tipo = TipoDocumento.valueOf(solicitud.tipoDocumento());
        var numero = numeroNormalizado(solicitud.numeroDocumento());
        if (numero != null) {
            ValidadorDocumento.error(tipo, numero).ifPresent(mensaje -> {
                throw new ReglaNegocioException(CAMPO_NUMERO, mensaje);
            });
        } else if (tipo != perfil(id).getTipoDocumento()) {
            throw new ReglaNegocioException(CAMPO_NUMERO, ValidadorDocumento.MENSAJE_NUMERO_FALTA);
        }

        reautenticacion.exigir(usuario, solicitud.contrasena(), Operacion.FACTURACION, ip);
        var huellaNueva = numero == null ? null : huella.calcular(numero);

        boolean documentoEnUso;
        try {
            documentoEnUso = Boolean.TRUE.equals(transacciones.execute(
                    estado -> escribir(id, tipo, numero, huellaNueva, solicitud, ip)));
        } catch (DataIntegrityViolationException e) {
            // Otro arrendador guardó ese documento entre la búsqueda y el UPDATE (uq_perfil_arrendador_hash_documento).
            documentoEnUso = true;
        }
        if (documentoEnUso) {
            // D13: cuenta como un fallo para que enumerar documentos gaste el mismo cupo que adivinar la contraseña.
            reautenticacion.registrarFallo(usuario);
            auditoria.registrar(new EventoAuditoria(id, ACCION_DOCUMENTO_RECHAZADO, ENTIDAD_PERFIL, id, ip,
                    "motivo=" + MOTIVO_DOCUMENTO_EN_USO));
            throw new ReglaNegocioException(MENSAJE_DOCUMENTO_NO_DISPONIBLE);
        }
    }

    /** @return {@code true} si el documento nuevo ya es de otro arrendador (sin escribir nada). */
    private boolean escribir(Long id, TipoDocumento tipo, String numero, String huellaNueva,
                             ActualizacionFacturacionDTO solicitud, String ip) {
        usuarios.findParaActualizar(id)
                .orElseThrow(() -> new IllegalStateException("La cuenta de una sesión vigente no existe"));
        var perfil = perfiles.findParaActualizar(id)
                .orElseThrow(() -> new IllegalStateException("El arrendador no tiene perfil"));
        var propuesto = perfil.cambiosPropuestos(tipo, numero, solicitud.razonSocial(), solicitud.direccionFiscal());
        if (!propuesto.hayCambios()) {
            return false;
        }
        if (propuesto.cambiaIdentidad() && !perfil.admiteCambioDeIdentidad()) {
            throw new ReglaNegocioException(MENSAJE_IDENTIDAD_EN_REVISION);
        }
        var campos = propuesto.campos();
        if (campos.contains(CampoFacturacion.TIPO_DOCUMENTO) && !campos.contains(CampoFacturacion.NUMERO_DOCUMENTO)) {
            throw new ReglaNegocioException(CAMPO_NUMERO, ValidadorDocumento.MENSAJE_NUMERO_FALTA);
        }
        if (campos.contains(CampoFacturacion.NUMERO_DOCUMENTO) && perfiles.findByHashDocumento(huellaNueva)
                .filter(otro -> !otro.getUsuarioId().equals(id)).isPresent()) {
            return true;
        }
        var cambio = perfil.actualizarFacturacion(tipo, numero, huellaNueva, solicitud.razonSocial(),
                solicitud.direccionFiscal());
        perfiles.saveAndFlush(perfil);
        // Solo nombres de campo y estados, nunca los valores (R46, R51).
        auditoria.registrar(new EventoAuditoria(id, ACCION_ACTUALIZADA, ENTIDAD_PERFIL, id, ip,
                "campos=" + cambio.nombresDeCampos() + ";identidad=" + cambio.identidadAnterior() + "->"
                        + cambio.identidadNueva()));
        return false;
    }

    private PerfilArrendador perfil(Long id) {
        return perfiles.findById(id).orElseThrow(() -> new IllegalStateException("El arrendador no tiene perfil"));
    }

    private static String numeroNormalizado(String numeroDocumento) {
        var numero = ValidadorDocumento.normalizar(numeroDocumento);
        return numero == null || numero.isEmpty() ? null : numero;
    }
}
