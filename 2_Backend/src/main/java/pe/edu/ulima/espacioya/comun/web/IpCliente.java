package pe.edu.ulima.espacioya.comun.web;

import jakarta.servlet.http.HttpServletRequest;

public final class IpCliente {

    private IpCliente() {
    }

    public static String de(HttpServletRequest solicitud) {
        return solicitud.getRemoteAddr();
    }
}
