package pe.edu.ulima.espacioya.reservas.service;

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;

/**
 * Interfaz pública de {@code reservas} para {@code administracion} (HU-27 DT-17 a DT-24). No es un puerto:
 * {@code administracion → reservas} está permitida.
 */
public interface CancelacionPorModeracion {

    /**
     * MANDATORY: se une a la decisión del admin, que ya ocultó el espacio. Rol ADMIN antes de leer (R76) → bloqueo del
     * espacio → bloqueo de sus reservas {@code CONFIRMADA} en orden de id → para cada una que aún no empieza: Strategy
     * con {@code ADMIN} (100 %), cancelación, bloques inactivos, reembolso tras el commit, auditoría
     * {@code RESERVA_CANCELADA} y aviso solo al usuario. Las ya iniciadas o terminadas no cambian (R65). Cualquier
     * excepción se propaga y revierte toda la decisión (R72).
     */
    ReservasCanceladasPorModeracion cancelarPorFraude(UsuarioAutenticado admin, Long espacioId, String ip);
}
