package pe.edu.ulima.espacioya.busqueda.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.busqueda.dto.BloqueLibreDTO;
import pe.edu.ulima.espacioya.busqueda.dto.DisponibilidadPublicaDTO;
import pe.edu.ulima.espacioya.busqueda.dto.FichaPublicaDTO;
import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.espacios.domain.VentanaReserva;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.espacios.service.DisponibilidadService;

/**
 * Ficha pública de un espacio y sus bloques libres del día (HU-11). Todo lo que no es una ficha visible (id mal
 * escrito, inexistente, no publicado, arrendador no activo) responde el mismo 404 (DT-5, DT-6).
 */
@Service
public class FichaService {

    private static final Pattern ID_BIEN_ESCRITO = Pattern.compile("[0-9]{1,19}");

    private final CatalogoEspaciosService catalogo;
    private final DisponibilidadService disponibilidad;
    private final ResenaService resenas;
    private final Clock reloj;

    public FichaService(CatalogoEspaciosService catalogo, DisponibilidadService disponibilidad, ResenaService resenas,
                        Clock reloj) {
        this.catalogo = catalogo;
        this.disponibilidad = disponibilidad;
        this.resenas = resenas;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public FichaPublicaDTO ficha(String id) {
        var espacioId = idValido(id);
        if (espacioId == null) {
            throw new RecursoNoEncontradoException();
        }
        // El resumen se pide solo con la ficha en la mano: un espacio no visible no llega a contar reseñas.
        var ficha = catalogo.publicado(espacioId).orElseThrow(RecursoNoEncontradoException::new);
        return FichaPublicaDTO.de(ficha, resenas.resumen(espacioId));
    }

    @Transactional(readOnly = true)
    public DisponibilidadPublicaDTO disponibilidad(String id, String fecha) {
        // Primero el recurso y después la fecha: un espacio invisible con fecha mala sigue siendo 404 (R23).
        var espacioId = idValido(id);
        if (espacioId == null || !catalogo.esVisible(espacioId)) {
            throw new RecursoNoEncontradoException();
        }
        var dia = fecha(fecha);
        if (dia == null) {
            throw new ReglaNegocioException(BusquedaService.CAMPO_FECHA, BusquedaService.MENSAJE_FECHA_INVALIDA);
        }
        if (!VentanaReserva.desde(ZonaHoraria.hoy(reloj)).contiene(dia)) {
            throw new ReglaNegocioException(BusquedaService.CAMPO_FECHA, BusquedaService.MENSAJE_FUERA_DE_VENTANA);
        }
        var bloques = disponibilidad.horasLibres(espacioId, dia).stream()
                .map(hora -> new BloqueLibreDTO(hora, hora + 1))
                .toList();
        return new DisponibilidadPublicaDTO(dia, bloques);
    }

    // null = no es un id bien escrito: 1 a 19 dígitos, sin desbordar un long y mayor que 0.
    static Long idValido(String texto) {
        if (texto == null || !ID_BIEN_ESCRITO.matcher(texto).matches()) {
            return null;
        }
        try {
            var id = Long.parseLong(texto);
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ISO_LOCAL_DATE es estricto: 2026-02-30 no es una fecha. null = no es una fecha bien escrita.
    private static LocalDate fecha(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
