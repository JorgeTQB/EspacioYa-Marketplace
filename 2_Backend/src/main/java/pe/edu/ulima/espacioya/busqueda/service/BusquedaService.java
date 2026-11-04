package pe.edu.ulima.espacioya.busqueda.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.busqueda.dto.ResultadoBusquedaDTO;
import pe.edu.ulima.espacioya.busqueda.dto.ResultadoEspacioDTO;
import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.comun.error.ErrorCampoDTO;
import pe.edu.ulima.espacioya.comun.error.ValidacionException;
import pe.edu.ulima.espacioya.espacios.domain.VentanaReserva;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.espacios.service.EspacioPublicado;

/** Búsqueda pública (HU-10): valida la entrada, pagina de a {@link #TAMANO_PAGINA} y arma el DTO. */
@Service
public class BusquedaService {

    public static final int TAMANO_PAGINA = 20;

    public static final String CAMPO_TIPO = "tipo";
    public static final String CAMPO_ZONA = "zona";
    public static final String CAMPO_FECHA = "fecha";
    public static final String CAMPO_PAGINA = "pagina";

    public static final String MENSAJE_TIPO_INVALIDO = "Elige un tipo de espacio.";
    public static final String MENSAJE_ZONA_INVALIDA = "Elige una zona de la lista.";
    // Mismos textos que HU-08 (CalendarioEspacioService); se copian para no depender de una clase concreta de
    // otro módulo. BusquedaServiceTest comprueba que no se separen.
    public static final String MENSAJE_FECHA_INVALIDA = "Elige una fecha válida.";
    public static final String MENSAJE_FUERA_DE_VENTANA = "Elige una fecha entre hoy y las próximas 8 semanas.";
    public static final String MENSAJE_PAGINA_INVALIDA = "La página no es válida.";

    private static final Pattern SOLO_DIGITOS = Pattern.compile("[0-9]+");

    private final BusquedaEspacios busqueda;
    private final CatalogoEspaciosService catalogo;
    private final Clock reloj;

    public BusquedaService(BusquedaEspacios busqueda, CatalogoEspaciosService catalogo, Clock reloj) {
        this.busqueda = busqueda;
        this.catalogo = catalogo;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public ResultadoBusquedaDTO buscar(String tipo, String zona, String fecha, String pagina) {
        var errores = new ArrayList<ErrorCampoDTO>();
        if (!catalogo.esTipoValido(tipo)) {
            errores.add(new ErrorCampoDTO(CAMPO_TIPO, MENSAJE_TIPO_INVALIDO));
        }
        if (zona == null || zona.isBlank() || !catalogo.existeZona(zona)) {
            errores.add(new ErrorCampoDTO(CAMPO_ZONA, MENSAJE_ZONA_INVALIDA));
        }
        var dia = fecha(fecha);
        if (dia == null) {
            errores.add(new ErrorCampoDTO(CAMPO_FECHA, MENSAJE_FECHA_INVALIDA));
        } else if (!VentanaReserva.desde(ZonaHoraria.hoy(reloj)).contiene(dia)) {
            errores.add(new ErrorCampoDTO(CAMPO_FECHA, MENSAJE_FUERA_DE_VENTANA));
        }
        var numeroPagina = pagina(pagina);
        if (numeroPagina == 0) {
            errores.add(new ErrorCampoDTO(CAMPO_PAGINA, MENSAJE_PAGINA_INVALIDA));
        }
        // La disponibilidad no se calcula si algún parámetro es inválido (R29).
        if (!errores.isEmpty()) {
            throw new ValidacionException(errores);
        }

        var todos = busqueda.buscar(new CriteriosBusqueda(tipo, zona, dia));
        var desde = (long) (numeroPagina - 1) * TAMANO_PAGINA;
        var deLaPagina = todos.stream().skip(desde).limit(TAMANO_PAGINA).toList();
        var hayMas = todos.size() > desde + TAMANO_PAGINA;
        Map<Long, String> fotos = deLaPagina.isEmpty() ? Map.of()
                : catalogo.fotosPrincipales(deLaPagina.stream().map(EspacioPublicado::id).toList());
        var resultados = deLaPagina.stream()
                .map(espacio -> new ResultadoEspacioDTO(espacio.id(), espacio.nombre(), espacio.zona(),
                        espacio.zonaNombre(), espacio.precioHora(), fotos.get(espacio.id())))
                .toList();
        return new ResultadoBusquedaDTO(numeroPagina, resultados, hayMas);
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

    // Sin parámetro, la página 1. 0 = página no válida (fuera de 1..Integer.MAX_VALUE o no solo dígitos).
    private static int pagina(String texto) {
        if (texto == null) {
            return 1;
        }
        if (!SOLO_DIGITOS.matcher(texto).matches()) {
            return 0;
        }
        try {
            return Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
