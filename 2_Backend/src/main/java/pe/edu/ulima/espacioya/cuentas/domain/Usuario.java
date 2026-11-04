package pe.edu.ulima.espacioya.cuentas.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String correo;

    @Column(name = "hash_contrasena", nullable = false, length = 100)
    private String hashContrasena;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    @Column(name = "correo_verificado", nullable = false)
    private boolean correoVerificado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoUsuario estado;

    @Column(name = "version_politica", nullable = false, length = 20)
    private String versionPolitica;

    @Column(name = "fecha_consentimiento", nullable = false)
    private Instant fechaConsentimiento;

    @Column(length = 100)
    private String nombre;

    @Column(length = 9)
    private String telefono;

    protected Usuario() {
    }

    public static Usuario registrar(String correoNormalizado, String hashContrasena,
                                    String versionPolitica, Instant ahora) {
        var usuario = new Usuario();
        usuario.correo = correoNormalizado;
        usuario.hashContrasena = hashContrasena;
        usuario.rol = Rol.USUARIO;
        usuario.estado = EstadoUsuario.ACTIVA;
        // Sin correo real hasta HU-25: la cuenta nace con el correo verificado (decisión S5 de HU-01).
        usuario.correoVerificado = true;
        usuario.versionPolitica = versionPolitica;
        usuario.fechaConsentimiento = ahora;
        return usuario;
    }

    public static Usuario registrarArrendador(String correoNormalizado, String hashContrasena, String nombre,
                                              String telefono, String versionPolitica, Instant ahora) {
        var usuario = registrar(correoNormalizado, hashContrasena, versionPolitica, ahora);
        usuario.rol = Rol.ARRENDADOR;
        usuario.nombre = nombre.trim();
        usuario.telefono = telefono;
        return usuario;
    }

    // Solo por el comando interno de creación de admins (HU-06, D4): nunca desde la API.
    public static Usuario registrarAdmin(String correoNormalizado, String hashContrasena, String versionPolitica,
                                         Instant ahora) {
        var usuario = registrar(correoNormalizado, hashContrasena, versionPolitica, ahora);
        usuario.rol = Rol.ADMIN;
        return usuario;
    }

    public static String normalizarCorreo(String correo) {
        return correo == null ? null : correo.trim().toLowerCase(Locale.ROOT);
    }

    public boolean esAdmin() {
        return rol == Rol.ADMIN;
    }

    // Orden fijo: el motivo solo va a la auditoría; al cliente siempre le llega el mismo 401 genérico. La IP
    // permitida del admin (HU-06, D1) la comprueba InicioSesionService: es un dato de la solicitud, no de la cuenta.
    public Optional<MotivoRechazoInicioSesion> motivoRechazoInicioSesion() {
        if (!correoVerificado) {
            return Optional.of(MotivoRechazoInicioSesion.CORREO_NO_VERIFICADO);
        }
        if (estado != EstadoUsuario.ACTIVA) {
            return Optional.of(MotivoRechazoInicioSesion.CUENTA_NO_ACTIVA);
        }
        return Optional.empty();
    }

    // El admin no recupera su contraseña por correo (HU-04 S2, HU-06 D1; HU-17 lo mantiene, P4).
    public Optional<MotivoRechazoInicioSesion> motivoRechazoRecuperacion() {
        if (esAdmin()) {
            return Optional.of(MotivoRechazoInicioSesion.ROL_NO_PERMITIDO);
        }
        return motivoRechazoInicioSesion();
    }

    public void cambiarContrasena(String nuevoHash) {
        hashContrasena = nuevoHash;
    }

    // Un admin nunca se suspende, tampoco a sí mismo: no tiene recuperación ni otro admin que lo reactive (HU-09 D2).
    public boolean esGestionablePorAdmin() {
        return (rol == Rol.USUARIO || rol == Rol.ARRENDADOR) && estado != EstadoUsuario.ELIMINADA;
    }

    public boolean puedeSuspenderse() {
        return esGestionablePorAdmin() && estado == EstadoUsuario.ACTIVA;
    }

    public boolean puedeReactivarse() {
        return esGestionablePorAdmin() && estado == EstadoUsuario.SUSPENDIDA;
    }

    public void suspender() {
        if (!puedeSuspenderse()) {
            throw new IllegalStateException("La cuenta no se puede suspender: " + rol + " " + estado);
        }
        estado = EstadoUsuario.SUSPENDIDA;
    }

    public void reactivar() {
        if (!puedeReactivarse()) {
            throw new IllegalStateException("La cuenta no se puede reactivar: " + rol + " " + estado);
        }
        estado = EstadoUsuario.ACTIVA;
    }

    public Long getId() {
        return id;
    }

    public String getCorreo() {
        return correo;
    }

    public String getHashContrasena() {
        return hashContrasena;
    }

    public Rol getRol() {
        return rol;
    }

    public EstadoUsuario getEstado() {
        return estado;
    }

    public boolean isCorreoVerificado() {
        return correoVerificado;
    }

    public String getVersionPolitica() {
        return versionPolitica;
    }

    public Instant getFechaConsentimiento() {
        return fechaConsentimiento;
    }

    public String getNombre() {
        return nombre;
    }

    public String getTelefono() {
        return telefono;
    }
}
