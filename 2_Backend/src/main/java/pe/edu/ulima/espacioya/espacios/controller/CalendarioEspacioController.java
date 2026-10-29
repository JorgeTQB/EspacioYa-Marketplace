package pe.edu.ulima.espacioya.espacios.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.web.IpCliente;
import pe.edu.ulima.espacioya.espacios.dto.BloqueoFechaDTO;
import pe.edu.ulima.espacioya.espacios.dto.BloqueosDTO;
import pe.edu.ulima.espacioya.espacios.dto.DisponibilidadDiaDTO;
import pe.edu.ulima.espacioya.espacios.dto.HorarioDTO;
import pe.edu.ulima.espacioya.espacios.service.CalendarioEspacioService;

// Las fechas llegan como texto y las valida el servicio, para señalar siempre el campo fecha (HU-08 DT-14).
@RestController
@RequestMapping("/api/arrendadores/actual/espacios/{id}")
public class CalendarioEspacioController {

    private final CalendarioEspacioService calendario;

    public CalendarioEspacioController(CalendarioEspacioService calendario) {
        this.calendario = calendario;
    }

    @GetMapping("/horario")
    public HorarioDTO horario(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id) {
        return calendario.consultarHorario(usuario, id);
    }

    @PutMapping("/horario")
    public HorarioDTO reemplazarHorario(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id,
                                        @Valid @RequestBody HorarioDTO horario, HttpServletRequest peticion) {
        return calendario.reemplazarHorario(usuario, id, horario, IpCliente.de(peticion));
    }

    @GetMapping("/bloqueos")
    public BloqueosDTO bloqueos(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id) {
        return calendario.listarBloqueos(usuario, id);
    }

    @PostMapping("/bloqueos")
    @ResponseStatus(HttpStatus.CREATED)
    public BloqueoFechaDTO bloquear(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id,
                                    @Valid @RequestBody BloqueoFechaDTO bloqueo, HttpServletRequest peticion) {
        return calendario.bloquear(usuario, id, bloqueo, IpCliente.de(peticion));
    }

    @DeleteMapping("/bloqueos/{fecha}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desbloquear(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id,
                            @PathVariable String fecha, HttpServletRequest peticion) {
        calendario.desbloquear(usuario, id, fecha, IpCliente.de(peticion));
    }

    @GetMapping("/disponibilidad")
    public DisponibilidadDiaDTO disponibilidad(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                               @PathVariable Long id,
                                               @RequestParam(name = "fecha", required = false) String fecha) {
        return calendario.disponibilidad(usuario, id, fecha);
    }
}
