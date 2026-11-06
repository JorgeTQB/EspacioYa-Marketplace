package pe.edu.ulima.espacioya.busqueda.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

/**
 * Búsqueda pública (HU-10): valida la entrada, pagina de a {@link #TAMANO_PAGINA} y arma el DTO. HU-23: es el Client
 * del Decorator; valida los filtros contra una lista blanca y busca con la cadena que arma
 * {@link FiltrosBusqueda#envolver}, sin ningún {@code if} sobre qué filtro aplicar.
 */
@Service
public class BusquedaService {

    public static final int TAMANO_PAGINA = 20;

    public static final String CAMPO_TIPO = "tipo";
    public static final String CAMPO_ZONA = "zona";
    public static final String CAMPO_FECHA = "fecha";
    public static final String CAMPO_PAGINA = "pagina";
    public static final String CAMPO_PRECIO_MAXIMO = "precioMaximo";
    public static final String CAMPO_CAPACIDAD_MINIMA = "capacidadMinima";
    public static final String CAMPO_CARACTERISTICA = "caracteristica";

    public static final String MENSAJE_TIPO_INVALIDO = "Elige un tipo de espacio.";
    public static final String MENSAJE_ZONA_INVALIDA = "Elige una zona de la lista.";
    // Mismos textos que HU-08 (CalendarioEspacioService); se copian para no depender de una clase concreta de
    // otro módulo. BusquedaServiceTest comprueba que no se separen.
    public static final String MENSAJE_FECHA_INVALIDA = "Elige una fecha válida.";
    public static final String MENSAJE_FUERA_DE_VENTANA = "Elige una fecha entre hoy y las próximas 8 semanas.";
    public static final String MENSAJE_PAGINA_INVALIDA = "La página no es válida.";
    public static final String MENSAJE_PRECIO_MAXIMO_INVALIDO =
            "El precio máximo debe estar entre S/ 1.00 y S/ 100 000.00, con hasta 2 decimales.";
    public static final String MENSAJE_CAPACIDAD_MINIMA_INVALIDA =
            "La capacidad mínima debe ser un número entero entre 1 y 100 000.";
    public static final String MENSAJE_CARACTERISTICA_INVALIDA = "Elige características de la lista, sin repetir.";

    private static final Pattern SOLO_DIGITOS = Pattern.compile("[0-9]+");
    // HU-23 DT-8: la forma se comprueba antes de convertir, así 1e3, +5 o espacios no pasan.
    private static final Pattern PRECIO = Pattern.compile("[0-9]{1,6}(\\.[0-9]{1,2})?");
    private static final Pattern CAPACIDAD = Pattern.compile("[0-9]{1,6}");
    private static final BigDecimal PRECIO_MINIMO = new BigDecimal("1.00");
    private static final BigDecimal PRECIO_TOPE = new BigDecimal("100000.00");
    private static final int CAPACIDAD_MINIMA = 1;
    private static final int CAPACIDAD_TOPE = 100_000;

    private final BusquedaEspacios busqueda;
    private final CatalogoEspaciosService catalogo;
    private final Clock reloj;

    public BusquedaService(BusquedaEspacios busqueda, CatalogoEspaciosService catalogo, Clock reloj) {
        this.busqueda = busqueda;
        this.catalogo = catalogo;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public ResultadoBusquedaDTO buscar(ConsultaBusqueda consulta) {
        var tipo = consulta.tipo();
        var zona = consulta.zona();
        var errores = new ArrayList<ErrorCampoDTO>();
        if (!catalogo.esTipoValido(tipo)) {
            errores.add(new ErrorCampoDTO(CAMPO_TIPO, MENSAJE_TIPO_INVALIDO));
        }
        if (zona == null || zona.isBlank() || !catalogo.existeZona(zona)) {
            errores.add(new ErrorCampoDTO(CAMPO_ZONA, MENSAJE_ZONA_INVALIDA));
        }
        var dia = fecha(consulta.fecha());
        if (dia == null) {
            errores.add(new ErrorCampoDTO(CAMPO_FECHA, MENSAJE_FECHA_INVALIDA));
        } else if (!VentanaReserva.desde(ZonaHoraria.hoy(reloj)).contiene(dia)) {
            errores.add(new ErrorCampoDTO(CAMPO_FECHA, MENSAJE_FUERA_DE_VENTANA));
        }
        var numeroPagina = pagina(consulta.pagina());
        if (numeroPagina == 0) {
            errores.add(new ErrorCampoDTO(CAMPO_PAGINA, MENSAJE_PAGINA_INVALIDA));
        }
        // HU-23 R24: los errores de los filtros van después de los de HU-10, uno por campo.
        var precioMaximo = precioMaximo(consulta.precioMaximo());
        if (consulta.precioMaximo() != null && precioMaximo == null) {
            errores.add(new ErrorCampoDTO(CAMPO_PRECIO_MAXIMO, MENSAJE_PRECIO_MAXIMO_INVALIDO));
        }
        var capacidadMinima = capacidadMinima(consulta.capacidadMinima());
        if (consulta.capacidadMinima() != null && capacidadMinima == null) {
            errores.add(new ErrorCampoDTO(CAMPO_CAPACIDAD_MINIMA, MENSAJE_CAPACIDAD_MINIMA_INVALIDA));
        }
        var caracteristicas = caracteristicas(consulta.caracteristicas());
        if (caracteristicas == null) {
            errores.add(new ErrorCampoDTO(CAMPO_CARACTERISTICA, MENSAJE_CARACTERISTICA_INVALIDA));
        }
        // La disponibilidad no se calcula si algún parámetro es inválido (HU-10 R29, HU-23 R25).
        if (!errores.isEmpty()) {
            throw new ValidacionException(errores);
        }

        var filtros = new FiltrosBusqueda(precioMaximo, capacidadMinima, caracteristicas);
        var todos = filtros.envolver(busqueda).buscar(new CriteriosBusqueda(tipo, zona, dia));
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

    // null = ausente o fuera de la lista blanca (el llamador distingue con el texto original).
    private static BigDecimal precioMaximo(String texto) {
        if (texto == null || !PRECIO.matcher(texto).matches()) {
            return null;
        }
        var valor = new BigDecimal(texto);
        return valor.compareTo(PRECIO_MINIMO) < 0 || valor.compareTo(PRECIO_TOPE) > 0 ? null : valor;
    }

    private static Integer capacidadMinima(String texto) {
        if (texto == null || !CAPACIDAD.matcher(texto).matches()) {
            return null;
        }
        var valor = Integer.parseInt(texto);
        return valor < CAPACIDAD_MINIMA || valor > CAPACIDAD_TOPE ? null : valor;
    }

    // null = algún código fuera de la lista fija o repetido (HU-23 R22, R23); vacío = filtro ausente.
    private Set<String> caracteristicas(List<String> codigos) {
        var conjunto = new HashSet<String>();
        for (var codigo : codigos) {
            if (!catalogo.esCaracteristicaValida(codigo) || !conjunto.add(codigo)) {
                return null;
            }
        }
        return conjunto;
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
