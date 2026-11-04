package pe.edu.ulima.espacioya.pagos.domain;

import java.util.ArrayList;
import java.util.List;

/** Avisos para el admin sobre el arrendador de una liquidación (HU-19 D6). No bloquean nada; se calculan al leer. */
public enum AvisoArrendador {
    CUENTA_NO_ACTIVA,
    IDENTIDAD_NO_VERIFICADA;

    /** Los avisos que aplican, en el orden del enum. */
    public static List<AvisoArrendador> de(boolean cuentaNoActiva, boolean identidadNoVerificada) {
        var avisos = new ArrayList<AvisoArrendador>();
        if (cuentaNoActiva) {
            avisos.add(CUENTA_NO_ACTIVA);
        }
        if (identidadNoVerificada) {
            avisos.add(IDENTIDAD_NO_VERIFICADA);
        }
        return List.copyOf(avisos);
    }
}
