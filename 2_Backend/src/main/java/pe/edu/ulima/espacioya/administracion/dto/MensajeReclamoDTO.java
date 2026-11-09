package pe.edu.ulima.espacioya.administracion.dto;

import java.time.Instant;

/** Un mensaje del hilo: sin el id del autor, solo su papel (HU-26 R23, R32). */
public record MensajeReclamoDTO(Long id, String papel, String texto, Instant fecha) {
}
