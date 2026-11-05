package pe.edu.ulima.espacioya.espacios.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.espacios.domain.Espacio;
import pe.edu.ulima.espacioya.espacios.domain.EstadoEspacio;
import pe.edu.ulima.espacioya.espacios.domain.VentanaReserva;
import pe.edu.ulima.espacioya.espacios.dto.EspacioDTO;
import pe.edu.ulima.espacioya.espacios.dto.EstadoEspacioDTO;
import pe.edu.ulima.espacioya.espacios.repository.EspacioRepository;
import pe.edu.ulima.espacioya.espacios.repository.FotoEspacioRepository;
import pe.edu.ulima.espacioya.espacios.service.puertos.ReservasDelEspacio;

/**
 * Pausa, reanudación y baja de un espacio (HU-21). Arrendador: rol y correo verificado → propiedad con el bloqueo de
 * la fila → estado → (reanudar: identidad → fotos / baja: reservas por terminar) → cambio → auditoría (DT-4). Admin:
 * rol antes de leer → bloqueo sin filtro de dueño → estado → reservas → cambio → auditoría. Las reservas se leen
 * después del bloqueo, en la misma transacción, así una baja y una reserva nueva del mismo espacio se serializan (R17).
 */
@Service
public class EstadoEspacioService {

    public static final String ACCION_PAUSA = "PAUSA_ESPACIO";
    public static final String ACCION_REANUDACION = "REANUDACION_ESPACIO";
    public static final String ACCION_BAJA = "BAJA_ESPACIO";
    public static final String CAMPO_RESERVAS = "reservas";
    public static final String MENSAJE_SOLO_PUBLICADO = "Solo puedes pausar un espacio publicado.";
    public static final String MENSAJE_SOLO_PAUSADO = "Solo puedes reanudar un espacio pausado.";
    public static final String MENSAJE_BAJA_NO_PERMITIDA = "Solo puedes dar de baja un espacio publicado o pausado.";
    public static final String MENSAJE_RESERVAS_POR_TERMINAR = "Este espacio tiene reservas por terminar. Cancela sus "
            + "reservas futuras (con reembolso del 100 %) y vuelve a intentarlo. Si hay una en curso o pendiente de "
            + "pago, espera a que termine o venza.";
    public static final String MENSAJE_RESERVAS_POR_TERMINAR_ADMIN = "Este espacio tiene reservas por terminar: el "
            + "arrendador debe cancelarlas antes de darlo de baja.";

    private static final String ROL_ADMIN = "ADMIN";

    private final EspacioService espacioService;
    private final EspacioRepository espacios;
    private final FotoEspacioRepository fotos;
    private final ObjectProvider<ReservasDelEspacio> reservas;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    public EstadoEspacioService(EspacioService espacioService, EspacioRepository espacios,
                                FotoEspacioRepository fotos, ObjectProvider<ReservasDelEspacio> reservas,
                                AuditoriaService auditoria, Clock reloj) {
        this.espacioService = espacioService;
        this.espacios = espacios;
        this.fotos = fotos;
        this.reservas = reservas;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Transactional
    public EspacioDTO pausar(UsuarioAutenticado usuario, Long id, String ip) {
        espacioService.habilitacion(usuario);
        var espacio = propio(id, usuario.usuarioId());
        pausar(espacio, usuario.usuarioId(), ip, "");
        return espacioService.aDto(espacio);
    }

    @Transactional
    public EspacioDTO reanudar(UsuarioAutenticado usuario, Long id, String ip) {
        var habilitacion = espacioService.habilitacion(usuario);
        var espacio = propio(id, usuario.usuarioId());
        // R11: si falla más de una regla, gana la primera de este orden.
        if (!espacio.esReanudable()) {
            throw new ReglaNegocioException(MENSAJE_SOLO_PAUSADO);
        }
        if (!habilitacion.identidadVerificada()) {
            throw new ReglaNegocioException(EspacioService.MENSAJE_IDENTIDAD_NO_VERIFICADA);
        }
        if (fotos.countByEspacioId(espacio.getId()) == 0) {
            throw new ReglaNegocioException(EspacioService.MENSAJE_SIN_FOTOS);
        }
        var anterior = espacio.getEstado();
        espacio.reanudar(Instant.now(reloj));
        auditar(usuario.usuarioId(), ACCION_REANUDACION, espacio, ip, transicion(anterior, espacio));
        return espacioService.aDto(espacio);
    }

    @Transactional
    public EspacioDTO darDeBaja(UsuarioAutenticado usuario, Long id, String ip) {
        espacioService.habilitacion(usuario);
        var espacio = propio(id, usuario.usuarioId());
        darDeBaja(espacio, usuario.usuarioId(), ip, MENSAJE_RESERVAS_POR_TERMINAR, "");
        return espacioService.aDto(espacio);
    }

    @Transactional
    public EstadoEspacioDTO pausarComoAdmin(UsuarioAutenticado admin, Long id, String ip) {
        exigirAdmin(admin);
        var espacio = cualquiera(id);
        pausar(espacio, admin.usuarioId(), ip, actorAdmin(espacio));
        return new EstadoEspacioDTO(espacio.getId(), espacio.getEstado().name());
    }

    @Transactional
    public EstadoEspacioDTO darDeBajaComoAdmin(UsuarioAutenticado admin, Long id, String ip) {
        exigirAdmin(admin);
        var espacio = cualquiera(id);
        darDeBaja(espacio, admin.usuarioId(), ip, MENSAJE_RESERVAS_POR_TERMINAR_ADMIN, actorAdmin(espacio));
        return new EstadoEspacioDTO(espacio.getId(), espacio.getEstado().name());
    }

    private void pausar(Espacio espacio, Long usuarioId, String ip, String sufijo) {
        if (!espacio.esPausable()) {
            throw new ReglaNegocioException(MENSAJE_SOLO_PUBLICADO);
        }
        var anterior = espacio.getEstado();
        espacio.pausar(Instant.now(reloj));
        auditar(usuarioId, ACCION_PAUSA, espacio, ip, transicion(anterior, espacio) + sufijo);
    }

    // R16: el estado se comprueba antes de consultar las reservas.
    private void darDeBaja(Espacio espacio, Long usuarioId, String ip, String mensajeReservas, String sufijo) {
        if (!espacio.admiteBaja()) {
            throw new ReglaNegocioException(MENSAJE_BAJA_NO_PERMITIDA);
        }
        if (tieneReservasPorTerminar(espacio.getId())) {
            throw new ReglaNegocioException(CAMPO_RESERVAS, mensajeReservas);
        }
        var anterior = espacio.getEstado();
        espacio.darDeBaja(Instant.now(reloj));
        auditar(usuarioId, ACCION_BAJA, espacio, ip, transicion(anterior, espacio) + sufijo);
    }

    /**
     * Alguna reserva activa (CONFIRMADA o PENDIENTE_PAGO vigente) con un bloque que aún no termina (DT-5). La ventana
     * de reserva alcanza: ninguna reserva va más allá de hoy + 55 y los bloques de días anteriores ya terminaron. Sin
     * bean del puerto, no hay reservas (como HU-08 R21).
     */
    private boolean tieneReservasPorTerminar(Long espacioId) {
        var puerto = reservas.getIfAvailable();
        if (puerto == null) {
            return false;
        }
        var ventana = VentanaReserva.desde(ZonaHoraria.hoy(reloj));
        var ahoraLocal = ZonaHoraria.ahora(reloj);
        return puerto.bloquesReservados(espacioId, ventana.desde(), ventana.hasta()).stream()
                .anyMatch(bloque -> bloque.terminaDespuesDe(ahoraLocal));
    }

    private Espacio propio(Long id, Long arrendadorId) {
        return espacios.findParaActualizar(id, arrendadorId).orElseThrow(RecursoNoEncontradoException::new);
    }

    private Espacio cualquiera(Long id) {
        return espacios.findParaReservar(id).orElseThrow(RecursoNoEncontradoException::new);
    }

    private static void exigirAdmin(UsuarioAutenticado admin) {
        if (admin == null || !ROL_ADMIN.equals(admin.rol())) {
            throw new AccesoDenegadoException();
        }
    }

    private static String actorAdmin(Espacio espacio) {
        return ";actor=ADMIN;arrendadorId=" + espacio.getArrendadorId();
    }

    private static String transicion(EstadoEspacio anterior, Espacio espacio) {
        return "estado=" + anterior + "->" + espacio.getEstado();
    }

    private void auditar(Long usuarioId, String accion, Espacio espacio, String ip, String detalle) {
        auditoria.registrar(new EventoAuditoria(usuarioId, accion, EspacioService.ENTIDAD_ESPACIO, espacio.getId(), ip,
                detalle));
    }
}
