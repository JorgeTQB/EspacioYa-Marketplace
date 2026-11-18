# Exploración HU-21 · Pausar o dar de baja una publicación

> Explorador (general-purpose), 2026-11-04. Solo lectura; ningún archivo de código tocado.
> Rutas relativas a la raíz. `B/` = `2_Backend/src/main/java/pe/edu/ulima/espacioya/`, `F/` = `3_frontend/src/`.

Historia (`docs/historias/HU-21.md`): CA1 pausar → deja de aparecer en búsquedas y las reservas existentes se mantienen.
CA2 baja con reservas futuras → ver las afectadas y cancelarlas (reembolso 100 %) **antes** de completar la baja.
STRIDE: (E) solo el arrendador dueño **o un admin**, validado en backend; (R) registro de pausas y bajas.
Dueño: Sebastian Garate (programa) · Adriana Callupe (prueba y documenta). Depende de HU-07, HU-15.

## Preguntas

1. Espacio: entidad, tabla, migraciones, máquina de estados; ¿existen PAUSADO / BAJA?; dónde se cambia el estado y cómo se valida al dueño.
2. Búsqueda y detalle: filtro de estado; qué tocar para que pausado/baja no aparezca.
3. Reservas: creación, retención, ¿se valida estado del espacio?; cómo listar reservas futuras de un espacio.
4. Cancelación y reembolso: servicio, cancelación por arrendador, 100 %, patrones, reutilización desde otro módulo.
5. Admin: cómo actúa sobre recursos ajenos; ¿ya puede pausar espacios?
6. Interacción con HU-20 (identidad → EN_REVISION) y HU-19 (liquidaciones).
7. Auditoría: componente y nombres de acciones de espacios.
8. Requisitos (RF, RN, RNF, rate limits).
9. Frontend: Mis espacios, edición, componentes UI reutilizables, funciones `api/`.
10. Endpoints existentes de cambio de estado de espacio.

## Archivos analizados

- Docs: `docs/historias/HU-21.md`, `docs/historias/HU-27.md`, `docs/architecture.md` (§ principios, puertos, disponibilidad, reservas, pagos, reembolsos, liquidación, mis reservas, cancelación, estados), `docs/requerimientos.md`, `docs/modelo-datos.md`, `docs/frontend.md`, `specs/HU-07/design.md`, `specs/HU-20/design.md`, `feature_list.json`, `progress/HU-21.md`.
- Backend: `B/espacios/domain/{Espacio,EstadoEspacio}.java`, `B/espacios/service/{EspacioService,FotoEspacioService,CalendarioEspacioService}.java`, `B/espacios/service/impl/{CatalogoEspaciosServiceImpl,DisponibilidadServiceImpl}.java`, `B/espacios/service/puertos/{ReservasDelEspacio,BloqueReservado}.java`, `B/espacios/controller/EspacioController.java`, `B/espacios/repository/EspacioRepository.java`, `B/reservas/service/CancelacionReservaService.java`, `B/reservas/service/ConsultaReservasService.java`, `B/reservas/service/impl/{ReservaServiceImpl,ReservasDelEspacioImpl,ReservasPorCobrarImpl}.java`, `B/reservas/controller/ReservasDeMisEspaciosController.java`, `B/reservas/repository/{ReservaDAO,ReservaBloqueRepository}.java`, `B/reservas/domain/{Reserva,QuienCancela,ReembolsoCancelaArrendador}.java`, `B/reservas/dto/ReservaDeMiEspacioDTO.java`, `B/pagos/domain/ReglasPago.java`, `B/administracion/controller/*`, `B/administracion/service/CuentaAdminService.java`, `B/cuentas/domain/Rol.java`, `B/comun/auditoria/*`, `B/comun/seguridad/{ConfiguracionSeguridad,FiltroIpsAdmin}.java`.
- Migraciones: `2_Backend/src/main/resources/db/migration/V2_2__espacio.sql`, `V1_2__auditoria.sql` (última: `V6_1__perfil_usuario.sql`).
- Frontend: `F/api/{espacios,reservas}.js`, `F/pages/espacios/{MisEspacios,EditarEspacio,DisponibilidadEspacio}.jsx`, `F/pages/reservas/{ReservasDeMisEspacios,CancelarReserva}.jsx`, `F/components/ui/{Modal,EtiquetaEstado}.jsx`, `F/App.jsx`.

---

## 1. Espacio: entidad, estados y propiedad

- **Entidad** `B/espacios/domain/Espacio.java:23-25` (`@Table(name="espacio")`), campo `estado` `:58-60` (`EnumType.STRING`, 20). Nace `BORRADOR` (`crear`, `:71-79`). `publicar` `:109-115` (solo desde BORRADOR, `IllegalStateException` si no). `esEditable` `:121-124` = BORRADOR, PUBLICADO, **PAUSADO**; `esPublicable` `:126-128` = solo BORRADOR. **No hay** métodos `pausar`, `reanudar` ni `darDeBaja`.
- **Enum** `B/espacios/domain/EstadoEspacio.java:3-9`: `BORRADOR, PUBLICADO, PAUSADO, BAJA, OCULTO` (ya existen; se llama `BAJA`, no `DADO_DE_BAJA`).
- **Tabla/CHECK** `V2_2__espacio.sql:36-53`; `ck_espacio_estado` `:50` ya admite `'PAUSADO','BAJA','OCULTO'` → **no hace falta migración** para estados (HU-07 DT-10, `specs/HU-07/design.md:36`: "evita una migración en HU-21/HU-27"). No hay columnas de "pausado_en/baja_en/motivo".
- **Docs**: `docs/modelo-datos.md:22` lista los 5 estados (HU-07, HU-10, HU-21, HU-27). Máquina de estados oficial `docs/architecture.md:562-564`: `BORRADOR → PUBLICADO` (requiere identidad verificada) `⇄ PAUSADO`; `PUBLICADO → BAJA`; `OCULTO` por moderación (HU-27); arrendador suspendido → espacios no se muestran pero conservan su estado.
- **Dónde se cambia estado**: solo `EspacioService.publicar` `B/espacios/service/EspacioService.java:131-150` (orden R16: publicable → identidad verificada → fotos; audita `PUBLICACION_ESPACIO` con `estado=ANTERIOR->NUEVO`).
- **Validación de dueño**: la identidad sale de la sesión (`/api/arrendadores/actual/...`, `EspacioController.java:26-28`). `EspacioService` comprueba en orden rol → correo verificado → propiedad → estado (`:35-39`): `arrendadorHabilitado`/`habilitacion` `:153-164` (vía `CuentaService.habilitacionArrendador`, exige rol ARRENDADOR; correo no verificado → `AccesoDenegadoException`), y la propiedad se resuelve en la consulta `espacioParaActualizar` `:166-168` → `EspacioRepository.findParaActualizar` `B/espacios/repository/EspacioRepository.java:25-28` (`PESSIMISTIC_WRITE`, filtra `arrendadorId`; ajeno = inexistente = 404). `findParaReservar` `:30-33` es el mismo bloqueo sin filtro de dueño.
- `BAJA` y `OCULTO` ya bloquean edición, fotos y calendario: `EspacioService.editar:120-122`, `FotoEspacioService.java:197`, `CalendarioEspacioService.java:167` (todos con `esEditable`, mensaje `MENSAJE_ESTADO_NO_EDITABLE` "Este espacio ya no se puede editar.").

## 2. Búsqueda y ficha pública

Toda la visibilidad pública usa la regla HU-09 D1: **`estado == PUBLICADO` y arrendador `ACTIVA`**. Pausar o dar de baja cambia solo `estado`, así que **CA1 "deja de aparecer" sale gratis**; no hay que tocar `busqueda`.

- Búsqueda: `CatalogoEspaciosServiceImpl.publicados` `B/espacios/service/impl/CatalogoEspaciosServiceImpl.java:57-68` → `EspacioRepository.buscarPublicados(EstadoEspacio.PUBLICADO, …)` / `buscarPublicadosExcluyendo` (`EspacioRepository.java:35-46`). La consume `B/busqueda/service/BusquedaBase.java:24` (Decorator HU-23 envuelve esto).
- Búsqueda por fecha: `DisponibilidadServiceImpl.conBloquesLibres` `B/espacios/service/impl/DisponibilidadServiceImpl.java:82-89` (filtra PUBLICADO).
- Ficha y su disponibilidad: `CatalogoEspaciosServiceImpl.publicado/esVisible` `:89-103` → privado `visible` `:135-143` (PUBLICADO + `cuentaActiva`); `B/busqueda/service/FichaService.java:41,50` responde 404 genérico si no es visible. `horasLibres` `DisponibilidadServiceImpl.java:64-72` (lista vacía si no PUBLICADO).
- Fotos públicas: `FotoEspacioService.ver` `B/espacios/service/FotoEspacioService.java:121-136` (`publica` = PUBLICADO y activa; si no, solo el dueño con sesión, `Cache-Control: no-store`).
- Lo único a vigilar: que ninguna regla nueva introduzca `!= BAJA` en vez de `== PUBLICADO`. Tests de visibilidad existentes forzaban PAUSADO con `JdbcTemplate` (`specs/HU-07/design.md:931,970`).

## 3. Reservas: creación, retención y reservas futuras

- **Crear** `B/reservas/service/impl/ReservaServiceImpl.java:84-125`: solo rol USUARIO (`:51`); orden de bloqueos `usuario → espacio → reserva`: `cuentas.bloquearCuenta` `:117` → `disponibilidad.retenerSiLibres` `:121` → `expiracion.liberarVencidas` `:125`.
- **¿Valida estado?** Sí: `DisponibilidadServiceImpl.retenerSiLibres` `:127-142` toma `findParaReservar` (bloqueo de fila del espacio) y filtra `PUBLICADO` + `cuentaActiva` → 404 si no. Por eso **un PAUSADO/BAJA ya no recibe reservas nuevas**, y como pausar/baja tomará `findParaActualizar` (mismo bloqueo de fila), crear reserva y cambiar estado se serializan.
- **Pago de una pendiente**: antes del checkout `ReservasPorCobrarImpl.deUsuario` `B/reservas/service/impl/ReservasPorCobrarImpl.java:50-57` pasa `catalogo.esVisible` → `ReglasPago.motivoNoDisponible` `B/pagos/domain/ReglasPago.java:24-36` da `ESPACIO_NO_VISIBLE` (no se abre checkout nuevo). **Pero** el webhook (`bloquear` `:59-75`, `confirmar` `:77-85`) confirma aunque el espacio ya no se vea (HU-13 D6, `docs/architecture.md:231-232`): un checkout ya abierto puede dejar `CONFIRMADA` una reserva de un espacio pausado o **dado de baja**.
- **Reserva activa** (`docs/architecture.md:135`, puerto): `PENDIENTE_PAGO` vigente o `CONFIRMADA`. `PENDIENTE_PAGO → CANCELADA` no existe (HU-12 S7, `architecture.md:566-567`); vencen a los 15 min.
- **Puerto ya pensado para HU-21**: `B/espacios/service/puertos/ReservasDelEspacio.java:18-24` `bloquesReservados(espacioId, desde, hasta)` → `BloqueReservado(fecha, horaInicio, pagado)` (`BloqueReservado.java`: **sin `reservaId`**). Implementación `B/reservas/service/impl/ReservasDelEspacioImpl.java:35-41` (`ReservaBloqueRepository.bloquesActivos`, `ReservaBloqueRepository.java:38-46`). `docs/architecture.md:295-296`: "HU-21 (baja con reservas): usa el mismo puerto con la ventana completa (`desde`, `hasta` de `VentanaReserva`)". Llega por `ObjectProvider` (`DisponibilidadServiceImpl.reservasDelDia:154-160`); sin bean, "no hay reservas". Ojo: filtra por fecha, no por hora → incluye bloques de hoy ya empezados/terminados.
- **"Futuras" en reservas**: `ReservaConHorario.esProxima(ahora, ahoraLocal)` (estado `PENDIENTE_PAGO`/`CONFIRMADA` leído con `estadoEn` y fin posterior a ahora) — `architecture.md:450`: "HU-21 y HU-22 pueden reutilizarla". DAO `B/reservas/repository/ReservaDAO.java:14-24`: `delUsuario` y `confirmadasDeEspacios(espacioIds)` (solo CONFIRMADA; la propiedad la garantiza el servicio con `CatalogoEspaciosService.nombresDelArrendador`).
- Listado existente para el arrendador: `GET /api/arrendadores/actual/reservas?grupo=proximas|pasadas&pagina=n` (`ReservasDeMisEspaciosController.java:38-43` → `ConsultaReservasService.reservasDeMisEspacios:88-103`), de 10 en 10, **todas** sus reservas `CONFIRMADA` de todos sus espacios, sin filtro por espacio. `ReservaDeMiEspacioDTO` sí trae `espacioId` (`B/reservas/dto/ReservaDeMiEspacioDTO.java`).

## 4. Cancelación y reembolso (HU-15/HU-16)

- `B/reservas/service/CancelacionReservaService.java`: `vistaPrevia` `:85-102` (readOnly) y `confirmar` `:113-146`. Orden en confirmar: rol de la ruta (`exigirRol:202-206`) → id → `montoReembolsoVisto` → `findEspacioIdById` → `disponibilidad.bloquearEspacio` (lock espacio) → `findParaActualizar` (lock reserva) → propiedad (`esPropia:165-171`, ARRENDADOR = `EspacioBloqueado.arrendadorId`) → `motivoNoCancelable` (`Reserva.java:114-126`: YA_CANCELADA, NO_CONFIRMADA, YA_INICIADA) → Strategy → compara monto visto → `reserva.cancelar` → `bloques.desactivarDeReserva` → `PagoService.reembolsarCancelacion` si > 0 → auditoría `RESERVA_CANCELADA` → `AvisosCancelacion` (correos después del commit). Una transacción.
- **Cancelación por arrendador ya existe**: `GET/POST /api/arrendadores/actual/reservas/{id}/cancelacion` (`ReservasDeMisEspaciosController.java:45-58`, `QuienCancela.ARRENDADOR`). 100 % forzado por la estrategia `B/reservas/domain/ReembolsoCancelaArrendador.java` (Strategy: `PoliticaReembolso`, Context `CalculoReembolso.politicaFija()`, `architecture.md:474-481`). `QuienCancela` solo tiene `USUARIO`, `ARRENDADOR` (`B/reservas/domain/QuienCancela.java:5-9`, `rolRequerido()`); **no hay ADMIN**.
- Reembolso: `PagoService.reembolsarCancelacion` (MANDATORY) → fila `reembolso PENDIENTE` + Command `SolicitudReembolso` ejecutado después del commit por `EjecutorReembolsos` (HU-16, `architecture.md:350-395`). Falla de pasarela → `FALLIDO`, la cancelación responde 200 igual; el admin reintenta en `/api/admin/reembolsos`.
- **Reutilización y dirección de dependencias** (`architecture.md:126-128`: `comun ← cuentas ← espacios ← reservas → pagos → cuentas`; `administracion → (todos)`): `espacios` **no puede** llamar a `reservas` (sería ciclo). Por tanto la baja que vive en `espacios` no puede invocar `CancelacionReservaService`; solo puede **consultar** reservas vía el puerto `ReservasDelEspacio`. `architecture.md:493-494`: "HU-21 puede reutilizar `CancelacionReservaService` con `QuienCancela.ARRENDADOR` o agregar una estrategia". El CA2 dice "debo cancelarlas antes de completar la baja": encaja con que el arrendador cancele una a una con la ruta HU-15 existente y luego la baja verifique que no queda ninguna.

## 5. Admin

- Rol `ADMIN` en `B/cuentas/domain/Rol.java`. Rutas `/api/admin/**`: `FiltroIpsAdmin` (`B/comun/seguridad/FiltroIpsAdmin.java:21`, IP permitida antes de autenticar, `ConfiguracionSeguridad.java:40`) + MFA (HU-17) + el **servicio** exige rol ADMIN antes de leer (`CuentaAdminService.exigirAdmin`, `B/administracion/service/CuentaAdminService.java:57-82`, `ROL_ADMIN` `:40`).
- Controladores admin: `RevisionIdentidadController` (`/api/admin/arrendadores`, `:34-62`), `CuentaAdminController` (`/api/admin/cuentas`, `/{id}/suspension`, `/{id}/reactivacion`, `:33-63`), más `pagos` `ReembolsoAdminController` y `LiquidacionAdminController`.
- **No existe** ninguna ruta/servicio admin sobre espacios. La suspensión (HU-09) **no cambia** `espacio.estado`: los oculta por el filtro `cuentaActiva`/`arrendadoresNoActivos` (`architecture.md:153-156`, RN-01). `OCULTO` es de HU-27 (moderación admin, sprint 8, pending).
- Frontend admin: `F/App.jsx:254-281` (`/admin/identidades`, `/admin/cuentas`, `/admin/cuentas/:id`, páginas en `F/pages/administracion/`); ninguna lista espacios del arrendador.
- Si el admin pausa/da de baja: la dirección `administracion → espacios` está permitida; haría falta un método público nuevo en `espacios` (sin filtrar por `arrendadorId`) y una ruta bajo `/api/admin/**`. El admin no puede cancelar reservas (QuienCancela sin ADMIN).

## 6. HU-20 y HU-19

- **HU-20** (`architecture.md:200-207`, `specs/HU-20/design.md:31` D12 y `:541` alternativa F descartada): al volver la identidad a `EN_REVISION`, los espacios publicados **siguen visibles** y sus reservas siguen; `EspacioService.publicar` rechaza publicar nuevos (`MENSAJE_IDENTIDAD_NO_VERIFICADA`, `EspacioService.java:139-141`). HU-20 explícitamente **no** pausa espacios. Implicación para HU-21: decidir si **reanudar** (PAUSADO → PUBLICADO) exige identidad `VERIFICADA` como publicar (si no, un arrendador en revisión podría pausar y reanudar libremente; si sí, pausar un espacio en revisión lo deja sin poder volver).
- **HU-19**: `PagoRepository.pagadosSinLiquidar` excluye cualquier pago con fila en `reembolso` (`architecture.md:419`), así que las reservas canceladas por el arrendador (100 %) quedan fuera de la liquidación automáticamente. `CatalogoEspaciosService.arrendadoresDe` y `nombres` no filtran estado (`architecture.md:241-245`), así que las reservas pasadas de un espacio en BAJA se siguen liquidando con su nombre. Una baja **no** requiere cambios en `pagos`. `ReservasLiquidables.admitida` = `CONFIRMADA` o cancelada por usuario con 0 (`architecture.md:137`).

## 7. Auditoría

- Componente `B/comun/auditoria/AuditoriaService.java:19-22` `registrar(EventoAuditoria)` (`@Transactional` REQUIRED: se une a la transacción del llamador y se revierte con ella). `EventoAuditoria(usuarioId, accion, entidad, entidadId, ip, detalle)` (`EventoAuditoria.java`). Tabla `V1_2__auditoria.sql` (`accion VARCHAR(60)`, `entidad VARCHAR(60)`, `ip VARCHAR(45)`).
- Acciones de espacios existentes: `CREACION_ESPACIO`, `EDICION_ESPACIO`, `PUBLICACION_ESPACIO` (`EspacioService.java:43-46`, entidad `"espacio"`, helper `auditar:197-199`; publicación con detalle `estado=BORRADOR->PUBLICADO`); `FOTO_ESPACIO_AGREGADA/ELIMINADA/RECHAZADA` (`FotoEspacioService.java:42-44`); `HORARIO_ESPACIO_ACTUALIZADO`, `BLOQUEO_FECHA_CREADO/ELIMINADO` (`CalendarioEspacioService.java:44-46`). Convención sugerida: `PAUSA_ESPACIO`, `REANUDACION_ESPACIO`, `BAJA_ESPACIO` (+ quizá `BAJA_ESPACIO_RECHAZADA` con reservas pendientes), detalle `estado=X->Y[;actor=ADMIN]`. Cada `RESERVA_CANCELADA` ya se audita aparte.
- IP: `B/comun/web/IpCliente.de(peticion)` en el controlador.

## 8. Requisitos

- `docs/requerimientos.md:70` **RF-B-09** pausar: deja de aparecer en búsquedas y se mantienen las reservas. `:71` **RF-B-10** baja con reservas futuras: mostrar las afectadas y exigir cancelarlas (100 %) antes de completar.
- `:224` **RN-15** búsqueda solo publicados, no pausados ni ocultos, de no suspendidos. `:225` **RN-16** para dar de baja con reservas futuras hay que cancelarlas antes (100 %). Simplificación de cancelación por arrendador → 100 % (`architecture.md:579-580`).
- `:157` **RNF-SEG-12** auditoría (incluye "cambios de espacios"); `:164` **RNF-SEG-19** propiedad antes de cada edición (cita HU-21). Fila de trazabilidad `:259`.
- **Rate limits**: no hay ninguno para HU-21 ni para escrituras de espacios (las únicas reglas `limite.regla` son de `busqueda`: `ReglasLimiteBusqueda.java:15`, `ReglasLimiteFicha.java:20,25`). La historia no pide (D).

## 9. Frontend

- **Mis espacios** `F/pages/espacios/MisEspacios.jsx:12-88`: lista con `Tarjeta` (acciones `Editar`, `Disponibilidad`, `:65-70`) y `EtiquetaEstado` `:77`. Sin acciones de estado.
- **Editar espacio** `F/pages/espacios/EditarEspacio.jsx`: encabezado con `EtiquetaEstado` `:225`, aviso si PUBLICADO `:240`, bloque "Publicación" solo si BORRADOR `:347-355` (`publicar` `:198-203`). Sitio natural para un bloque "Pausar / Reanudar / Dar de baja". `F/pages/espacios/DisponibilidadEspacio.jsx:23` ya trata `BAJA` y `OCULTO` como no editables.
- **UI reutilizable** `F/components/ui/`: `Modal.jsx:8-52` (`abierto`, `titulo`, `onConfirmar`, `onCerrar`, `textoConfirmar`, `textoCerrar`, `variante` `primario`|`peligro-confirmar`, `cargando`); `docs/frontend.md:190` lo prevé para "dar de baja"; `docs/frontend.md:124-127` "Dar de baja" es botón `peligro` y `peligro-confirmar` dentro del Modal. `EtiquetaEstado.jsx:10-15` ya mapea `PAUSADO` "Pausado" (atención) y `BAJA` "De baja" (neutro). También `Alerta`, `Boton`, `Tarjeta`.
- **Cancelar reserva reutilizable** `F/pages/reservas/CancelarReserva.jsx:46` `CancelarReserva({ reserva, obtener, confirmar, onCancelada })` (vista previa + Modal + confirmación), usado en `F/pages/reservas/ReservasDeMisEspacios.jsx:69-78` con `obtenerCancelacionDeMiEspacio`/`cancelarReservaDeMiEspacio`. Sirve tal cual para la lista de "reservas afectadas" de CA2. `SeccionReservas.jsx` pagina listas.
- **API** `F/api/espacios.js`: base `MIS_ESPACIOS = '/api/arrendadores/actual/espacios'` `:3`; `listarMisEspacios`, `obtenerMiEspacio`, `editarEspacio`, `publicarEspacio` (`POST …/{id}/publicacion`, `:25-27`), fotos, horario, bloqueos, disponibilidad. `F/api/reservas.js`: `listarReservasDeMisEspacios({grupo,pagina})` `:18`, `obtenerCancelacionDeMiEspacio` `:36`, `cancelarReservaDeMiEspacio` `:40`. No hay funciones de pausa/baja.
- Rutas `F/App.jsx:213-250` (`/panel/espacios`, `/panel/espacios/:id`, `/panel/espacios/:id/disponibilidad`, reservas de mis espacios).

## 10. Endpoints de cambio de estado existentes

Solo `POST /api/arrendadores/actual/espacios/{id}/publicacion` (`EspacioController.java:60-64`, devuelve `EspacioDTO`). El patrón REST del repo es sustantivo de acción como subrecurso (`/publicacion`, `/cancelacion`, `/suspension`, `/reactivacion`), lo que sugiere `…/{id}/pausa`, `…/{id}/reanudacion` (o `DELETE …/pausa`), `…/{id}/baja` (con `GET …/{id}/baja` como vista previa de reservas afectadas, paralelo a la vista previa de HU-15). No hay endpoints de estado para admin.

---

## Huecos / decisiones para el spec

1. **Transiciones exactas.** Arquitectura: `PUBLICADO ⇄ PAUSADO`, `PUBLICADO → BAJA`. ¿Se permite `PAUSADO → BAJA` (muy probable que sí en la práctica)? ¿`BORRADOR → BAJA`? ¿`OCULTO` (HU-27) queda fuera de pausar/reanudar/baja? `BAJA` final e irreversible (ya no editable). Agregar `pausar`, `reanudar`, `darDeBaja` (o `motivoNo…`) a `Espacio` con `IllegalStateException` como `publicar`.
2. **Reanudar y HU-20.** ¿`PAUSADO → PUBLICADO` exige identidad `VERIFICADA` (como publicar) y al menos una foto? HU-20 D12 dejó publicados visibles en `EN_REVISION`; decidir si reanudar en revisión se permite.
3. **Qué son "reservas futuras" para la baja.** `CONFIRMADA` con inicio > ahora (cancelables por HU-15) es claro. Huecos: (a) `PENDIENTE_PAGO` vigente: no se puede cancelar (HU-12 S7) y el webhook puede confirmarla aunque el espacio esté en BAJA (HU-13 D6) → ¿la baja se rechaza hasta que expire (≤ 15 min) o se cuenta como afectada con mensaje "espera"? (b) reserva `CONFIRMADA` **en curso** hoy (YA_INICIADA, no cancelable): ¿bloquea la baja hasta que termine o no cuenta? El puerto `bloquesReservados` filtra por fecha, no por hora, así que incluye bloques de hoy ya pasados: hay que filtrar por instante.
4. **Cómo obtener la lista de afectadas** respetando dependencias (`espacios` no puede llamar a `reservas`). Opciones: (A) nuevo método en el puerto `ReservasDelEspacio` que devuelva ids/horario de reservas futuras (el puerto lo implementa `reservas`); `espacios` sirve `GET …/espacios/{id}/baja` con la lista; (B) nueva ruta en `reservas` (p. ej. filtro `?espacioId=` en `/api/arrendadores/actual/reservas` o método nuevo en `ReservaDAO` con dueño en la firma) y `espacios` solo comprueba "¿quedan activas?" con el puerto existente. `BloqueReservado` no trae `reservaId`; ampliarlo afecta los dobles `@Primary` de las IT (`architecture.md:283-286`).
5. **Cancelación en lote vs una a una.** CA2 "debo cancelarlas antes de completar la baja": ¿el arrendador cancela cada una con el flujo HU-15 existente (reutiliza `CancelarReserva.jsx` y `POST …/reservas/{id}/cancelacion`, cero backend nuevo de cancelación) y luego confirma la baja, o un botón "cancelar todas y dar de baja"? Un lote atómico tendría que vivir en `reservas` (orquestador `reservas → espacios`), con N reembolsos post-commit y N pares de correos.
6. **Carrera baja vs reserva nueva/confirmación.** La baja debe tomar `findParaActualizar` (lock de fila del espacio) y comprobar reservas activas **bajo ese bloqueo**; `retenerSiLibres` y el webhook (`bloquearEspacio`) toman el mismo lock, así se serializan. Documentar el orden `espacio` primero.
7. **Admin.** STRIDE (E) dice "dueño o admin". No hay endpoint admin de espacios. Decidir alcance: ¿ruta `/api/admin/espacios/{id}/pausa|baja` (administracion → espacios, IP permitida + MFA + rol en servicio) con UI en `DetalleCuenta`, o solo backend, o se difiere a HU-27? El admin no puede cancelar reservas (`QuienCancela` sin ADMIN): ¿un admin da de baja solo sin reservas futuras, o se agrega estrategia/ruta? ¿Correo al arrendador cuando actúa el admin (HU-27 sí lo pide para ocultar)?
8. **Auditoría y motivo.** Nombres de acción (`PAUSA_ESPACIO`, `REANUDACION_ESPACIO`, `BAJA_ESPACIO`), detalle `estado=X->Y`, actor admin, ¿motivo obligatorio para baja/admin? ¿Auditar el intento de baja rechazado por reservas pendientes? No hay columnas de fecha de pausa/baja: ¿bastan `actualizado_en` + auditoría (sin migración)?
9. **Efectos colaterales de pausado.** Pausado sigue editable (`esEditable`), incluso calendario y fotos (ya así por HU-07). Las reservas existentes se mantienen (CA1) y el arrendador puede cancelarlas por HU-15. ¿Avisar a usuarios con reservas cuando se pausa? (no lo pide ningún CA).
10. **Ficha/reservas de un espacio en BAJA.** `nombres`/`nombresDelArrendador` no filtran estado: las reservas pasadas siguen mostrando el nombre; "Ver espacio" en `ReservasDeMisEspacios.jsx:35` lleva al editor, que con BAJA queda de solo lectura. Confirmar que "Mis espacios" sigue listando los BAJA (o los separa).
11. **Frontend.** Ubicación de acciones (Mis espacios y/o Editar), Modal `primario` para pausar y `peligro-confirmar` para baja, textos (`textosEspacios.js`), pantalla/sección de reservas afectadas con `CancelarReserva`, agregar funciones a `F/api/espacios.js`.
12. **Sin rate limit** definido ni pedido; confirmarlo como fuera de alcance.
