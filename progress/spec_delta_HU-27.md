# HU-27 — Delta del spec (2026-11-09)

> Solo lo que cambió tras las respuestas de Kevin Chan. Spec completo: `specs/HU-27/`. Status: `spec_ready`.

## Respuestas del dueño incorporadas

1. **Ocultar es definitivo, sin restaurar** → P8 queda **confirmado**; se quitó la sección "Preguntas abiertas" (ahora
   "Decisiones del dueño"). Sin cambios de comportamiento (R38, R55).
2. **Ocultar un espacio por fraude cancela sus reservas confirmadas** → P9 reescrito; P10 ampliado; nueva P17.

## Decisiones tomadas para (2)

| Tema | Decisión | Por qué |
|------|----------|---------|
| ¿Cuándo es "por fraude"? (P17, DT-17) | Lo decide el **admin** con un tercer valor de `decision`: `OCULTAR_POR_FRAUDE`, solo para reportes de **espacio** (en reseña → 400 *fraude solo en espacios*). `OCULTAR` sin más sigue sin tocar reservas. Se guarda en `reporte.por_fraude`. | El motivo `FRAUDE` lo elige el reportante y puede faltar o mezclarse entre hermanos. Spam o fotos inapropiadas no justifican cancelar. |
| ¿Qué se cancela? (DT-18) | Reservas `CONFIRMADA` del espacio **que aún no empiezan** (misma regla que HU-15). No se tocan las ya iniciadas o terminadas (su vía es el reclamo de HU-26), las `PENDIENTE_PAGO` (no pueden iniciar el pago con el espacio oculto, HU-13 D6, y expiran en 15 min), las `EXPIRADA` ni las `CANCELADA`. | Reutiliza `Reserva.cancelar` sin relajar reglas de HU-13/HU-15. |
| ¿Quién cancela? (DT-19) | `QuienCancela.ADMIN` nuevo + ConcreteStrategy `ReembolsoPorModeracion` (`MODERACION`, **100 %**) en el Strategy ★ de HU-15. Migración: `ck_reserva_cancelada_por` admite `ADMIN`. Las rutas de HU-15 rechazan `ADMIN` (403). | Cancelar como `ARRENDADOR` falsearía la auditoría, la exportación HU-22 y el correo. |
| Reembolso (DT-20) | `PagoService.reembolsarCancelacion` por `monto_total`, `solicitadoPor` = admin; Command de HU-16 tras el commit. Sin cambios en `pagos`. | Mismo mecanismo que HU-15. |
| Liquidación (DT-21) | Sin cambios: `Reserva.admiteLiquidacion` ya excluye toda `CANCELADA` que no sea del usuario con 0 %. El arrendador no cobra esas reservas. | HU-19 D3. |
| Correos (DT-22) | A cada usuario afectado: el aviso de cancelación existente ("Cancelada por: EspacioYa", 100 %, monto), sin el motivo ni la palabra "fraude". Al arrendador: **un solo** correo (el de CA3) con cuántas reservas se cancelaron y que no entran en sus liquidaciones; no recibe un aviso por reserva. | Reutiliza `CorreoCancelacionUsuario`; `QuienCancelo` gana el caso `ADMIN`. |
| Atomicidad (DT-23) | Todo en la transacción de `decidir`; orden `reporte → espacio → reserva (id asc) → pago`. Cualquier fallo revierte todo (espacio, reportes, reservas, reembolsos, auditoría, correos, pasarela). | Mismo orden `espacio → reserva → pago` de HU-13/HU-15: una cancelación simultánea del usuario se serializa. |
| Módulos | Nueva interfaz pública `reservas.service.CancelacionPorModeracion` (no puerto; `administracion → reservas` permitida), la llama `ReporteAdminService` tras `ModeracionEspacios.ocultar`. | `espacios` no orquesta reembolsos. |

## Requirements

- **Modificados:** R32, R33 (`por_fraude`), R39 (sin cancelar reservas), R43 (valor `OCULTAR_POR_FRAUDE`), R45
  (`reservasCanceladas` en la respuesta), R46 (solo para `OCULTAR`), R47 (resumen de cancelaciones en el correo al
  arrendador), R51 (`decision` y `reservasCanceladas` en la auditoría), R54 (tampoco `RESERVA_CANCELADA` ni
  `REEMBOLSO_SOLICITADO` si se rechaza). Definiciones nuevas: *decisión de ocultar*, *reserva por iniciar*, mensaje
  *fraude solo en espacios*. Trazabilidad ampliada.
- **Nuevos:** R63 (fraude en reseña → 400), R64 (cancelar por iniciar), R65 (no tocar las demás), R66 (reembolso 100 %
  con `PagoService`), R67 (política `MODERACION`), R68 (no liquidable), R69 (correo a usuarios), R70 (sin aviso por
  reserva al arrendador), R71 (auditoría `RESERVA_CANCELADA`), R72 (rollback total), R73 (orden de bloqueos y
  concurrencia con HU-15), R74 (`cancelada_por = ADMIN` en la base), R75 (`ck_reporte_fraude`), R76 (rol en
  `CancelacionPorModeracion`), R77 (HU-15 rechaza `ADMIN`), R78 (`decision` derivada de `por_fraude`).

## Design

DT-11 reescrito; DT-17 a DT-24 nuevos; archivos nuevos/modificados en `reservas` y `cuentas` (`QuienCancelo`);
`V8_2` agrega `por_fraude`, `ck_reporte_fraude`, el cambio de `ck_reserva_cancelada_por` e
`ix_reserva_espacio_confirmada`; diagrama de secuencia "ocultar por fraude"; pantalla `/admin/reportes/:id` con la
opción "Ocultar por fraude y cancelar sus reservas" (solo espacios) y su modal; alternativas A8–A13; riesgos y docs a
actualizar ampliados.

## Tasks

- **Nuevas:** T32–T37 (Strategy `MODERACION`, `QuienCancela.ADMIN`, rechazo en HU-15, `QuienCancelo`,
  `CancelacionPorModeracion` y sus tests), que van tras T12, y T38 (`OcultarPorFraudeIT`), que va tras T23.
- **Ampliadas:** T1, T2, T3, T4, T18, T19, T20, T21, T28, T30, T31 (ahora R1–R78).

## Preguntas que quedan para el dueño (no bloquean)

1. **Pago aprobado tras ocultar.** Una reserva `PENDIENTE_PAGO` cuyo checkout se abrió antes de ocultar puede
   confirmarse después por el webhook (HU-13 D6) y quedar `CONFIRMADA` en un espacio oculto (ventana máxima de 15 min).
   El spec lo acepta como riesgo. ¿Vale así, o abrimos una historia para que el webhook reembolse al 100 % si el
   espacio está `OCULTO`?
2. **Reservas ya iniciadas o terminadas del espacio fraudulento.** No se cancelan; el usuario puede abrir un reclamo
   (HU-26), y si nadie lo abre se liquidan al arrendador 48 h después del fin. ¿Hace falta además frenar su liquidación
   (cambia HU-19, fuera de este spec)?
3. **Confirmar la UX de P17:** el admin elige explícitamente "Ocultar por fraude y cancelar sus reservas", aunque ningún
   reporte tenga el motivo `FRAUDE`. ¿Está bien?
