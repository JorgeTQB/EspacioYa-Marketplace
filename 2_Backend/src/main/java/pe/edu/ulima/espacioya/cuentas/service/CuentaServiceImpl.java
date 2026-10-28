package pe.edu.ulima.espacioya.cuentas.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.domain.EstadoIdentidad;
import pe.edu.ulima.espacioya.cuentas.domain.Rol;
import pe.edu.ulima.espacioya.cuentas.dto.HabilitacionArrendadorDTO;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

@Service
public class CuentaServiceImpl implements CuentaService {

    private final UsuarioRepository usuarios;
    private final PerfilArrendadorRepository perfiles;

    public CuentaServiceImpl(UsuarioRepository usuarios, PerfilArrendadorRepository perfiles) {
        this.usuarios = usuarios;
        this.perfiles = perfiles;
    }

    @Override
    @Transactional(readOnly = true)
    public HabilitacionArrendadorDTO habilitacionArrendador(UsuarioAutenticado usuario) {
        if (!Rol.ARRENDADOR.name().equals(usuario.rol())) {
            throw new AccesoDenegadoException();
        }
        var cuenta = usuarios.findById(usuario.usuarioId())
                .orElseThrow(() -> new IllegalStateException("La cuenta de una sesión vigente no existe"));
        var perfil = perfiles.findById(usuario.usuarioId())
                .orElseThrow(() -> new IllegalStateException("El arrendador no tiene perfil"));
        return new HabilitacionArrendadorDTO(cuenta.isCorreoVerificado(),
                perfil.getEstadoIdentidad() == EstadoIdentidad.VERIFICADA);
    }
}
