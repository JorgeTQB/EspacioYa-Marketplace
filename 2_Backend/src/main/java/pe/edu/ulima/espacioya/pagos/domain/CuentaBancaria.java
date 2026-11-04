package pe.edu.ulima.espacioya.pagos.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import pe.edu.ulima.espacioya.comun.cifrado.ConvertidorCifrado;

/**
 * Cuenta bancaria vigente del arrendador (HU-18): una sola fila por arrendador ({@code uq_cuenta_bancaria_arrendador}).
 * El número se guarda cifrado; el estado no se guarda, se calcula con {@link #estado(Instant)} (RN-11).
 */
@Entity
@Table(name = "cuenta_bancaria")
public class CuentaBancaria {

    /** RN-11: tras cambiar la cuenta, la siguiente liquidación no se paga antes de este plazo. */
    public static final Duration RETENCION = Duration.ofHours(72);

    private static final int DIGITOS_VISIBLES = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "arrendador_id", nullable = false, updatable = false)
    private Long arrendadorId;

    @Column(nullable = false, length = 60)
    private String banco;

    @Convert(converter = ConvertidorCifrado.class)
    @Column(nullable = false, columnDefinition = "text")
    private String numero;

    @Column(nullable = false, length = 150)
    private String titular;

    @Column(name = "retener_hasta")
    private Instant retenerHasta;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private Instant creadaEn;

    @Column(name = "actualizada_en", nullable = false)
    private Instant actualizadaEn;

    protected CuentaBancaria() {
    }

    /** Primera cuenta: sin retención (R17). */
    public static CuentaBancaria registrar(Long arrendadorId, String banco, String numero, String titular,
                                           Instant ahora) {
        var cuenta = new CuentaBancaria();
        cuenta.arrendadorId = arrendadorId;
        cuenta.banco = banco.strip();
        cuenta.numero = numero;
        cuenta.titular = titular.strip();
        cuenta.retenerHasta = null;
        cuenta.creadaEn = ahora;
        cuenta.actualizadaEn = ahora;
        return cuenta;
    }

    /** Reemplaza los datos y reinicia la retención: {@code ahora + 72 h} (R12, P6). */
    public void cambiar(String banco, String numero, String titular, Instant ahora) {
        this.banco = banco.strip();
        this.numero = numero;
        this.titular = titular.strip();
        this.retenerHasta = ahora.plus(RETENCION);
        this.actualizadaEn = ahora;
    }

    /** Mismo banco (sin distinguir mayúsculas ni espacios extremos) y mismo número (R16). */
    public boolean esLaMisma(String otroBanco, String otroNumero) {
        return normalizar(banco).equals(normalizar(otroBanco)) && numero.equals(otroNumero);
    }

    public boolean estaRetenida(Instant ahora) {
        return retenerHasta != null && ahora.isBefore(retenerHasta);
    }

    public EstadoCuentaBancaria estado(Instant ahora) {
        return estaRetenida(ahora) ? EstadoCuentaBancaria.RETENIDA : EstadoCuentaBancaria.DISPONIBLE;
    }

    /** {@code ****} y los 4 últimos dígitos del número. */
    public String enmascarado() {
        return enmascarar(numero);
    }

    /**
     * Número completo descifrado. Es el único acceso a él: solo para el detalle admin de una liquidación pagable
     * (HU-19 D4), que lo audita. Nunca a un log, a la auditoría ni a otra respuesta.
     */
    public String numeroParaTransferir() {
        return numero;
    }

    public static String enmascarar(String numero) {
        var visibles = numero.substring(Math.max(0, numero.length() - DIGITOS_VISIBLES));
        return "****" + visibles;
    }

    private static String normalizar(String banco) {
        return banco.strip().toLowerCase(Locale.ROOT);
    }

    public Long getId() {
        return id;
    }

    public Long getArrendadorId() {
        return arrendadorId;
    }

    public String getBanco() {
        return banco;
    }

    public String getTitular() {
        return titular;
    }

    public Instant getRetenerHasta() {
        return retenerHasta;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }

    public Instant getActualizadaEn() {
        return actualizadaEn;
    }
}
