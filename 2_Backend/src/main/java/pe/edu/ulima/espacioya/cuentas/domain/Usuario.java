package pe.edu.ulima.espacioya.cuentas.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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

    public static final String TEXTO_ANONIMIZADO = "Cuenta eliminada";

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

    // HU-20 D1: el correo pedido y aún no confirmado; usuario.correo no cambia hasta confirmar.
    @Column(name = "correo_pendiente", length = 254)
    private String correoPendiente;

    // HU-20 D15: clave de ArchivosPrivados (carpeta "perfiles"), nunca una URL.
    @Column(name = "foto_ruta", length = 60)
    private String fotoRuta;

    @Column(name = "foto_tipo_mime", length = 20)
    private String fotoTipoMime;

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

    public static String enmascararCorreo(String correo) {
        if (correo == null || correo.isEmpty()) {
            return "***";
        }
        var arroba = correo.lastIndexOf('@');
        if (arroba <= 0) {
            return correo.charAt(0) + "***";
        }
        return correo.charAt(0) + "***" + correo.substring(arroba);
    }

    /** @return los nombres de los campos que cambiaron ("nombre", "telefono"); un valor en blanco se guarda nulo. */
    public List<String> actualizarDatos(String nombreNuevo, String telefonoNuevo) {
        var nombreLimpio = limpiar(nombreNuevo);
        var telefonoLimpio = limpiar(telefonoNuevo);
        var campos = new ArrayList<String>();
        if (!Objects.equals(nombre, nombreLimpio)) {
            nombre = nombreLimpio;
            campos.add("nombre");
        }
        if (!Objects.equals(telefono, telefonoLimpio)) {
            telefono = telefonoLimpio;
            campos.add("telefono");
        }
        return List.copyOf(campos);
    }

    private static String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    public void solicitarCambioCorreo(String correoNormalizado) {
        if (correoNormalizado == null || correoNormalizado.equals(correo)) {
            throw new IllegalArgumentException("El correo pendiente debe ser distinto del actual");
        }
        correoPendiente = correoNormalizado;
    }

    public void confirmarCambioCorreo() {
        if (correoPendiente == null) {
            throw new IllegalStateException("La cuenta no tiene un correo pendiente");
        }
        correo = correoPendiente;
        correoPendiente = null;
    }

    /** @return la ruta anterior (o null) para borrarla tras el commit. */
    public String cambiarFoto(String ruta, String tipoMime) {
        var anterior = fotoRuta;
        fotoRuta = ruta;
        fotoTipoMime = tipoMime;
        return anterior;
    }

    /** @return la ruta quitada (o null si no había foto). */
    public String quitarFoto() {
        return cambiarFoto(null, null);
    }

    public boolean tieneFoto() {
        return fotoRuta != null;
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

    // HU-22: el titular elimina su propia cuenta; el admin no tiene a quién pedir supresión (P4 de HU-17).
    public boolean puedeEliminarse() {
        return (rol == Rol.USUARIO || rol == Rol.ARRENDADOR) && estado == EstadoUsuario.ACTIVA;
    }

    /**
     * Supresión (HU-22 R21–R23): el correo pasa al marcador (libera el original para otro registro, R33), los datos
     * personales quedan nulos y la contraseña es un hash que nadie conoce. Se conservan el rol y el consentimiento.
     *
     * @return la ruta de la foto quitada (o null) para borrarla tras el commit.
     * @throws IllegalStateException si la cuenta no es {@code USUARIO}/{@code ARRENDADOR} {@code ACTIVA}.
     */
    public String anonimizar(String hashNuevo) {
        if (!puedeEliminarse()) {
            throw new IllegalStateException("La cuenta no se puede eliminar: " + rol + " " + estado);
        }
        if (hashNuevo == null || hashNuevo.isBlank()) {
            throw new IllegalArgumentException("La anonimización exige un hash nuevo");
        }
        var foto = quitarFoto();
        correo = marcadorCorreo(id);
        nombre = null;
        telefono = null;
        correoPendiente = null;
        hashContrasena = hashNuevo;
        estado = EstadoUsuario.ELIMINADA;
        return foto;
    }

    // .invalid es un TLD reservado (RFC 2606): nunca recibe correo. En minúsculas por ck_usuario_correo_normalizado.
    public static String marcadorCorreo(Long id) {
        return "eliminada-" + id + "@espacioya.invalid";
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

    public String getCorreoPendiente() {
        return correoPendiente;
    }

    public String getFotoRuta() {
        return fotoRuta;
    }

    public String getFotoTipoMime() {
        return fotoTipoMime;
    }
}
