package pe.edu.ulima.espacioya.cuentas.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivosPrivados;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.ErrorCampoDTO;
import pe.edu.ulima.espacioya.comun.error.ValidacionException;
import pe.edu.ulima.espacioya.comun.seguridad.TokenAleatorio;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.transaccion.DespuesDelCommit;
import pe.edu.ulima.espacioya.cuentas.domain.DocumentoIdentidad;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.Rol;
import pe.edu.ulima.espacioya.cuentas.domain.TipoToken;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.dto.ExportacionDatosDTO;
import pe.edu.ulima.espacioya.cuentas.dto.PerfilExportadoDTO;
import pe.edu.ulima.espacioya.cuentas.repository.CodigoRespaldoRepository;
import pe.edu.ulima.espacioya.cuentas.repository.DesafioMfaRepository;
import pe.edu.ulima.espacioya.cuentas.repository.DocumentoIdentidadRepository;
import pe.edu.ulima.espacioya.cuentas.repository.MfaRepository;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.SesionRepository;
import pe.edu.ulima.espacioya.cuentas.repository.TokenRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;
import pe.edu.ulima.espacioya.cuentas.service.ReautenticacionPerfil.Operacion;
import pe.edu.ulima.espacioya.cuentas.service.puertos.Impedimento;
import pe.edu.ulima.espacioya.cuentas.service.puertos.ParticipanteDatosPersonales;
import pe.edu.ulima.espacioya.cuentas.service.puertos.TitularDatos;

/**
 * Derechos de acceso y supresión del titular de la sesión (HU-22). Orden (DT-5): rol (403) → contraseña fuera de
 * transacción (429/400) → una transacción. Los demás módulos participan solo por {@link ParticipanteDatosPersonales}.
 */
@Service
public class DatosPersonalesService {

    public static final String ACCION_EXPORTADOS = "DATOS_EXPORTADOS";
    public static final String ACCION_ELIMINADA = "CUENTA_ELIMINADA";
    public static final String MENSAJE_ELIMINADA = "Tu cuenta fue eliminada.";

    private static final Logger log = LoggerFactory.getLogger(DatosPersonalesService.class);

    private final UsuarioRepository usuarios;
    private final PerfilArrendadorRepository perfiles;
    private final DocumentoIdentidadRepository documentos;
    private final TokenRepository tokens;
    private final SesionRepository sesiones;
    private final MfaRepository mfas;
    private final CodigoRespaldoRepository codigos;
    private final DesafioMfaRepository desafios;
    private final List<ParticipanteDatosPersonales> participantes;
    private final ReautenticacionPerfil reautenticacion;
    private final PasswordEncoder encoder;
    private final ArchivosPrivados archivos;
    private final AuditoriaService auditoria;
    private final TransactionTemplate transacciones;
    private final Clock reloj;

    public DatosPersonalesService(UsuarioRepository usuarios, PerfilArrendadorRepository perfiles,
                                  DocumentoIdentidadRepository documentos, TokenRepository tokens,
                                  SesionRepository sesiones, MfaRepository mfas, CodigoRespaldoRepository codigos,
                                  DesafioMfaRepository desafios, List<ParticipanteDatosPersonales> participantes,
                                  ReautenticacionPerfil reautenticacion, PasswordEncoder encoder,
                                  ArchivosPrivados archivos, AuditoriaService auditoria,
                                  TransactionTemplate transacciones, Clock reloj) {
        this.usuarios = usuarios;
        this.perfiles = perfiles;
        this.documentos = documentos;
        this.tokens = tokens;
        this.sesiones = sesiones;
        this.mfas = mfas;
        this.codigos = codigos;
        this.desafios = desafios;
        this.participantes = List.copyOf(participantes);
        this.reautenticacion = reautenticacion;
        this.encoder = encoder;
        this.archivos = archivos;
        this.auditoria = auditoria;
        this.transacciones = transacciones;
        this.reloj = reloj;
    }

    /** CA1: el JSON y la fila {@code DATOS_EXPORTADOS} salen de la misma transacción (DT-13). */
    public ExportacionDatosDTO exportar(UsuarioAutenticado usuario, String contrasena, String ip) {
        PerfilService.exigirTitular(usuario);
        reautenticacion.exigir(usuario, contrasena, Operacion.EXPORTACION, ip);
        var id = usuario.usuarioId();
        var titular = new TitularDatos(id, usuario.rol());
        return transacciones.execute(estado -> {
            var cuenta = usuarios.findById(id)
                    .orElseThrow(() -> new IllegalStateException("La cuenta de una sesión vigente no existe"));
            var perfil = perfilDe(cuenta);
            var datosPerfil = PerfilExportadoDTO.de(cuenta, perfil, mfas.existsByUsuarioIdAndActivoTrue(id));
            var secciones = new LinkedHashMap<String, Object>();
            for (var participante : participantes) {
                for (var seccion : participante.exportar(titular)) {
                    if (secciones.containsKey(seccion.nombre())) {
                        throw new IllegalStateException("Sección repetida en la exportación: " + seccion.nombre());
                    }
                    secciones.put(seccion.nombre(), seccion.contenido());
                }
            }
            auditar(id, ACCION_EXPORTADOS, ip, "secciones=" + String.join(",", secciones.keySet()));
            return new ExportacionDatosDTO(Instant.now(reloj), datosPerfil, secciones);
        });
    }

    /**
     * CA2 y CA3. Los impedimentos se leen con la fila de {@code usuario} bloqueada y en la misma transacción que
     * anonimiza (R42); si hay alguno, la transacción se revierte sin cambiar nada.
     */
    public void eliminar(UsuarioAutenticado usuario, String contrasena, String ip) {
        PerfilService.exigirTitular(usuario);
        reautenticacion.exigir(usuario, contrasena, Operacion.ELIMINACION, ip);
        // bcrypt (~100 ms) antes de abrir la transacción: no se calcula con la fila de usuario bloqueada (DT-6).
        var hashNuevo = encoder.encode(TokenAleatorio.generar());
        var id = usuario.usuarioId();
        var titular = new TitularDatos(id, usuario.rol());
        transacciones.executeWithoutResult(estado -> {
            var cuenta = usuarios.findParaActualizar(id)
                    .orElseThrow(() -> new IllegalStateException("La cuenta de una sesión vigente no existe"));
            if (!cuenta.puedeEliminarse()) {
                throw new IllegalStateException("La cuenta de una sesión vigente no está activa");
            }
            var ahora = Instant.now(reloj);
            exigirSinImpedimentos(titular, ahora);

            var archivosPorBorrar = new ArrayList<String>();
            var foto = cuenta.anonimizar(hashNuevo);
            if (foto != null) {
                archivosPorBorrar.add(foto);
            }
            if (cuenta.getRol() == Rol.ARRENDADOR) {
                archivosPorBorrar.addAll(anonimizarArrendador(id));
            }
            for (var tipo : TipoToken.values()) {
                tokens.anularPendientes(id, tipo, ahora);
            }
            codigos.borrarDeUsuario(id);
            mfas.findById(id).ifPresent(mfas::delete);
            desafios.borrarDeUsuario(id);
            participantes.forEach(participante -> participante.anonimizar(titular));
            auditar(id, ACCION_ELIMINADA, ip, "rol=" + cuenta.getRol().name());
            archivosPorBorrar.forEach(clave -> DespuesDelCommit.ejecutar(() -> eliminarSinFallar(clave)));
            // Último paso (DT-11): hace flush y clear, y ninguna entidad del contexto se vuelve a usar.
            sesiones.revocarTodas(id);
        });
    }

    private void exigirSinImpedimentos(TitularDatos titular, Instant ahora) {
        var errores = new ArrayList<ErrorCampoDTO>();
        for (var participante : participantes) {
            for (Impedimento impedimento : participante.impedimentos(titular, ahora)) {
                errores.add(new ErrorCampoDTO(impedimento.campo(), impedimento.mensaje()));
            }
        }
        if (!errores.isEmpty()) {
            throw new ValidacionException(errores);
        }
    }

    // usuario → perfil_arrendador, el orden de toda escritura del perfil (HU-20): la decisión del admin no lo pisa.
    private List<String> anonimizarArrendador(Long id) {
        var perfil = perfiles.findParaActualizar(id)
                .orElseThrow(() -> new IllegalStateException("El arrendador no tiene perfil"));
        perfil.anonimizar();
        var documentosDelArrendador = documentos.findByArrendadorIdOrderByTipoAsc(id);
        var claves = documentosDelArrendador.stream().map(DocumentoIdentidad::getRutaArchivo).toList();
        if (!documentosDelArrendador.isEmpty()) {
            documentos.deleteAllInBatch(documentosDelArrendador);
        }
        return claves;
    }

    private PerfilArrendador perfilDe(Usuario cuenta) {
        if (cuenta.getRol() != Rol.ARRENDADOR) {
            return null;
        }
        return perfiles.findById(cuenta.getId())
                .orElseThrow(() -> new IllegalStateException("El arrendador no tiene perfil"));
    }

    private void auditar(Long usuarioId, String accion, String ip, String detalle) {
        auditoria.registrar(new EventoAuditoria(usuarioId, accion, PerfilService.ENTIDAD_USUARIO, usuarioId, ip,
                detalle));
    }

    // Si falla, queda un archivo huérfano: se avisa en el log sin la clave.
    private void eliminarSinFallar(String clave) {
        try {
            archivos.eliminar(clave);
        } catch (RuntimeException e) {
            log.warn("No se pudo eliminar un archivo de una cuenta eliminada");
        }
    }
}
