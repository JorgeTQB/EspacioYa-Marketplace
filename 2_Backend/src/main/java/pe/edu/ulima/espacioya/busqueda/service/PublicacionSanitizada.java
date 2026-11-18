package pe.edu.ulima.espacioya.busqueda.service;

import pe.edu.ulima.espacioya.busqueda.domain.Resena;
import pe.edu.ulima.espacioya.busqueda.dto.CrearResenaDTO;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.texto.SanitizadorTexto;

/**
 * ★ Decorator, rol <b>ConcreteDecorator</b> (HU-24, R45, R46): limpia el comentario <b>antes</b> de reenviar. Va
 * primero en la cadena, fuera de la transacción: un comentario que queda vacío no abre transacción ni lee la reserva
 * (R22).
 */
public final class PublicacionSanitizada extends PublicacionResenaDecorador {

    private static final String CAMPO_COMENTARIO = "comentario";

    public PublicacionSanitizada(PublicacionResena envuelta) {
        super(envuelta);
    }

    @Override
    public Resena publicar(SolicitudResena solicitud) {
        var limpio = SanitizadorTexto.limpiar(solicitud.comentario());
        if (limpio.isEmpty()) {
            throw new ReglaNegocioException(CAMPO_COMENTARIO, CrearResenaDTO.M_COMENTARIO);
        }
        return super.publicar(solicitud.conComentario(limpio));
    }
}
