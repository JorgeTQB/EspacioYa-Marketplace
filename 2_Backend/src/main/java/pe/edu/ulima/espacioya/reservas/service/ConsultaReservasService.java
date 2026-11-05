package pe.edu.ulima.espacioya.reservas.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.ErrorCampoDTO;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ValidacionException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.reservas.domain.GrupoReservas;
import pe.edu.ulima.espacioya.reservas.domain.HistorialReservas;
import pe.edu.ulima.espacioya.reservas.domain.Pagina;
import pe.edu.ulima.espacioya.reservas.domain.ReservaConHorario;
import pe.edu.ulima.espacioya.reservas.dto.PaginaReservasDTO;
import pe.edu.ulima.espacioya.reservas.dto.ReservaDTO;
import pe.edu.ulima.espacioya.reservas.dto.ReservaDeMiEspacioDTO;
import pe.edu.ulima.espacioya.reservas.repository.ReservaDAO;

/**
 * "Mis reservas" y "Reservas de mis espacios" (HU-14). Cliente del DAO y del Iterator (★): pide las reservas siempre con
 * el dueño de la sesión, fija el corte con un solo instante, recorre el grupo con {@link HistorialReservas} y pagina con
 * {@link Pagina}. Solo lee: sin auditoría ni bloqueos (RNF-SEG-12, R38).
 */
@Service
public class ConsultaReservasService {

    public static final int TAMANO_PAGINA = 10;
    public static final String CAMPO_GRUPO = "grupo";
    public static final String CAMPO_PAGINA = "pagina";
    public static final String MENSAJE_GRUPO = "Elige próximas o pasadas.";
    // Mismo texto que BusquedaService.MENSAJE_PAGINA_INVALIDA (HU-10); se repite porque reservas no importa busqueda.
    public static final String MENSAJE_PAGINA = "La página no es válida.";
    public static final String NOMBRE_USUARIO_POR_DEFECTO = "Usuario de EspacioYa";
    public static final String CAMPO_ESPACIO = "espacioId";
    public static final String MENSAJE_ESPACIO = "El espacio no es válido.";

    private static final String ROL_USUARIO = "USUARIO";
    private static final String ROL_ARRENDADOR = "ARRENDADOR";
    private static final Pattern SOLO_DIGITOS = Pattern.compile("[0-9]+");

    private final ReservaDAO dao;
    private final CatalogoEspaciosService catalogo;
    private final CuentaService cuentas;
    private final Clock reloj;

    public ConsultaReservasService(ReservaDAO dao, CatalogoEspaciosService catalogo, CuentaService cuentas,
                                   Clock reloj) {
        this.dao = dao;
        this.catalogo = catalogo;
        this.cuentas = cuentas;
        this.reloj = reloj;
    }

    /**
     * @throws AccesoDenegadoException si el rol no es USUARIO, antes de validar y sin consultar (R2).
     * @throws ValidacionException si {@code grupo} o {@code pagina} no son válidos, con todos los errores (R29–R31).
     */
    @Transactional(readOnly = true)
    public PaginaReservasDTO<ReservaDTO> misReservas(UsuarioAutenticado usuario, String grupo, String pagina) {
        exigirRol(usuario, ROL_USUARIO);
        var solicitud = validar(grupo, pagina);
        var ahora = Instant.now(reloj);
        var deLaPagina = paginar(dao.delUsuario(usuario.usuarioId()), solicitud, ahora);
        Map<Long, String> nombres = deLaPagina.elementos().isEmpty() ? Map.of()
                : catalogo.nombres(deLaPagina.elementos().stream().map(r -> r.reserva().getEspacioId()).toList());
        var reservas = deLaPagina.elementos().stream()
                .map(r -> ConversionReservas.aDto(r.reserva(), nombres.get(r.reserva().getEspacioId()), r.fecha(),
                        r.horaInicio(), r.horaFin(), ahora))
                .toList();
        return new PaginaReservasDTO<>(solicitud.pagina(), reservas, deLaPagina.hayMas());
    }

    /**
     * @throws AccesoDenegadoException si el rol no es ARRENDADOR, antes de validar y sin consultar (R3).
     * @throws ValidacionException si {@code grupo} o {@code pagina} no son válidos, con todos los errores (R29–R31).
     */
    @Transactional(readOnly = true)
    public PaginaReservasDTO<ReservaDeMiEspacioDTO> reservasDeMisEspacios(UsuarioAutenticado usuario, String grupo,
                                                                          String pagina) {
        return reservasDeMisEspacios(usuario, grupo, pagina, null);
    }

    /**
     * Como la de 3 parámetros; con {@code espacioId}, solo las de ese espacio propio (HU-21 R19–R22, DT-7).
     *
     * @throws ValidacionException si {@code grupo}, {@code pagina} o {@code espacioId} no son válidos, con todos los
     *         errores (HU-21 R20).
     * @throws RecursoNoEncontradoException si {@code espacioId} no existe o es de otro arrendador (HU-21 R21).
     */
    @Transactional(readOnly = true)
    public PaginaReservasDTO<ReservaDeMiEspacioDTO> reservasDeMisEspacios(UsuarioAutenticado usuario, String grupo,
                                                                          String pagina, String espacioId) {
        exigirRol(usuario, ROL_ARRENDADOR);
        var errores = new ArrayList<ErrorCampoDTO>();
        var filtro = espacioId == null ? null : idDeEspacio(espacioId);
        if (espacioId != null && filtro == null) {
            errores.add(new ErrorCampoDTO(CAMPO_ESPACIO, MENSAJE_ESPACIO));
        }
        var solicitud = validar(grupo, pagina, errores);
        var ahora = Instant.now(reloj);
        var espacios = catalogo.nombresDelArrendador(usuario.usuarioId());
        if (filtro != null && !espacios.containsKey(filtro)) {
            throw new RecursoNoEncontradoException();
        }
        if (espacios.isEmpty()) {
            return new PaginaReservasDTO<>(solicitud.pagina(), List.of(), false);
        }
        var ids = filtro == null ? espacios.keySet() : Set.of(filtro);
        var deLaPagina = paginar(dao.confirmadasDeEspacios(ids), solicitud, ahora);
        // Solo los nombres de la página: minimización (RNF-SEG-16).
        Map<Long, String> nombres = deLaPagina.elementos().isEmpty() ? Map.of()
                : cuentas.nombres(deLaPagina.elementos().stream().map(r -> r.reserva().getUsuarioId()).toList());
        var reservas = deLaPagina.elementos().stream()
                .map(r -> new ReservaDeMiEspacioDTO(r.reserva().getId(), r.reserva().getEspacioId(),
                        espacios.get(r.reserva().getEspacioId()),
                        nombres.getOrDefault(r.reserva().getUsuarioId(), NOMBRE_USUARIO_POR_DEFECTO),
                        r.reserva().estadoEn(ahora).name(), r.fecha(), r.horaInicio(), r.horaFin(),
                        r.reserva().getMontoTotal()))
                .toList();
        return new PaginaReservasDTO<>(solicitud.pagina(), reservas, deLaPagina.hayMas());
    }

    // Un solo instante por solicitud: la clasificación y el estado del DTO usan el mismo (DT-4).
    private static Pagina<ReservaConHorario> paginar(List<ReservaConHorario> reservas, Solicitud solicitud,
                                                     Instant ahora) {
        var ahoraLocal = LocalDateTime.ofInstant(ahora, ZonaHoraria.LIMA);
        var recorrido = new HistorialReservas(reservas, ahora, ahoraLocal).recorrer(solicitud.grupo());
        return Pagina.de(recorrido, solicitud.pagina(), TAMANO_PAGINA);
    }

    private static void exigirRol(UsuarioAutenticado usuario, String rol) {
        if (usuario == null || !rol.equals(usuario.rol())) {
            throw new AccesoDenegadoException();
        }
    }

    private static Solicitud validar(String grupo, String pagina) {
        return validar(grupo, pagina, new ArrayList<>());
    }

    // Los errores de grupo y página van antes de los que ya trae la lista (espacioId), en el orden de los parámetros.
    private static Solicitud validar(String grupo, String pagina, List<ErrorCampoDTO> previos) {
        var errores = new ArrayList<ErrorCampoDTO>();
        Optional<GrupoReservas> elegido = GrupoReservas.desde(grupo);
        if (elegido.isEmpty()) {
            errores.add(new ErrorCampoDTO(CAMPO_GRUPO, MENSAJE_GRUPO));
        }
        var numero = pagina(pagina);
        if (numero == 0) {
            errores.add(new ErrorCampoDTO(CAMPO_PAGINA, MENSAJE_PAGINA));
        }
        errores.addAll(previos);
        if (!errores.isEmpty()) {
            throw new ValidacionException(errores);
        }
        return new Solicitud(elegido.get(), numero);
    }

    // HU-21 DT-7: solo dígitos y entre 1 y Long.MAX_VALUE; null = no válido.
    private static Long idDeEspacio(String texto) {
        if (!SOLO_DIGITOS.matcher(texto).matches()) {
            return null;
        }
        try {
            var id = Long.parseLong(texto);
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Criterio de HU-10: sin parámetro, la página 1. 0 = no válida (fuera de 1..Integer.MAX_VALUE o no solo dígitos).
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

    private record Solicitud(GrupoReservas grupo, int pagina) {
    }
}
