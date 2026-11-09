# Exploración HU-26 · Reclamos sobre una reserva

- **Fecha:** 2026-11-08 · **Rama:** feature/HU-26-reclamos-reserva · **Dueña:** Adriana Callupe (`feature_list.json`;
  `docs/historias/HU-26.md` aún dice "Programa: Ariel Enriquez", reasignada según `progress/HU-26.md`).
- **Explorador:** general-purpose (solo lectura; único archivo escrito: este).

## Preguntas

1. Reservas (HU-14): entidad, estados, DAO, servicio, endpoints de "mis reservas" y verificación de propiedad.
2. Reembolsos (HU-16): cómo se dispara uno y qué reutilizar para un reembolso por reclamo.
3. Liquidación (HU-19): cálculo y mecanismo para excluir/congelar una reserva.
4. `administracion` y `comun`: roles, autorización admin, auditoría, correo, errores, rate limiting, archivos.
5. Migraciones: herramienta, carpeta, última versión, tabla de reclamos.
6. Frontend: "mis reservas", `components/ui/`, `src/api/`, rutas, pantallas admin.
7. Ambigüedades y huecos para el spec-author.

## Archivos analizados

Docs: `docs/historias/HU-26.md`, `docs/architecture.md` (§ módulos, puertos, Reembolsos HU-16, Liquidación HU-19,
Mis reservas HU-14, Cancelación HU-15, Datos personales HU-22, Reseñas HU-24, Correo HU-25, Seguridad, Qué NO hacer),
`docs/requerimientos.md` (RF-F-07/08, RN-12, RNF-SEG-12/17/19, USA-04, §9 punto 3), `docs/modelo-datos.md`,
`docs/frontend.md` (tabla de estados), `docs/conventions.md` (migraciones), `feature_list.json`, `progress/HU-26.md`.

Backend (prefijo `2_Backend/src/main/java/pe/edu/ulima/espacioya/`): `reservas/domain/{Reserva,ReservaConHorario}.java`,
`reservas/repository/{ReservaDAO,ReservaRepository}.java`, `reservas/service/{ReservaService,ReservasParaResena}.java`,
`reservas/service/impl/{ReservaServiceImpl,ReservasLiquidablesImpl}.java`, `reservas/controller/ReservaController.java`,
`reservas/dto/ReservaDTO.java`, `pagos/service/{PagoService,GeneradorLiquidaciones,ReembolsoAdminService}.java`,
`pagos/service/impl/PagoServiceImpl.java`, `pagos/service/reembolso/*`, `pagos/service/puertos/ReservasLiquidables.java`,
`pagos/domain/{Reembolso,Pago,ReglasLiquidacion,OrigenReembolso}.java`, `pagos/repository/PagoRepository.java`,
`administracion/**`, `comun/**` (listado + firmas), `cuentas/service/{CuentaService,NotificadorCorreo}.java`,
`espacios/service/CatalogoEspaciosService.java`, `comun/seguridad/ConfiguracionSeguridad.java`.
Migraciones: `2_Backend/src/main/resources/db/migration/`.
Frontend: `3_frontend/src/App.jsx`, `pages/reservas/MisReservas.jsx`, `pages/cuentas/Panel.jsx`, `src/api/*.js`,
`components/ui/*`.

## Hallazgos

### 0. La arquitectura ya prescribe gran parte de HU-26

`docs/architecture.md` deja instrucciones explícitas para HU-26 (leerlas antes de diseñar):

- Línea ~140: puerto **`pagos.ReclamosAbiertos`**, lo define `pagos` y lo implementa `administracion` (RN-12).
- Líneas ~480-482: el reembolso por reclamo usa `origen = RECLAMO`, agrega la fábrica **`Reembolso.porReclamo`** y se
  ejecuta con `SolicitudesReembolso.crear(...)` + `EjecutorReembolsos`, sin tocar el Command; "como el pago puede estar
  ya `REEMBOLSADO`, decide qué estado le deja".
- Líneas ~502-506: abrir reclamo solo si **`ahora < fin + 48 h`**, con `ReservaConHorario.instanteDeFin` y
  `ReglasLiquidacion.ESPERA` (sin hueco ni solape con la liquidación); `ReclamosAbiertos` es un filtro más en el paso de
  selección de `GeneradorLiquidaciones`; una reserva que vuelve a ser liquidable entra sola en la siguiente generación;
  un reembolso por reclamo deja la reserva **fuera entera** de la liquidación.
- Línea ~542: HU-26 puede **ampliar** `ReservaDTO` (sin quitar/cambiar campos; hay pruebas que fijan sus 11 campos).
  HU-24 prefirió una ruta aparte (`GET /api/resenas/pendientes`).
- Líneas ~610: si `reclamo` guarda datos personales, implementar `cuentas.ParticipanteDatosPersonales` (HU-22).
- Línea ~639: reutilizar `comun.texto.SanitizadorTexto.limpiar` para texto libre.
- Líneas ~665-668: agregar correos con un `record` `Correo` + subclase `final` de `FabricaCorreo<Evento>` en el módulo
  dueño del evento, enviado con `DespuesDelCommit`.
- Línea 80: "un reclamo por reserva" como restricción de BD. Línea 828: tabla "Qué NO hacer" (ver hueco H1).

### 1. Reservas (HU-14)

- Entidad: `reservas/domain/Reserva.java` → tabla `reserva` (`V3_3__reservas.sql`, `V4_2`, `V7_3`). Campos: `usuarioId`,
  `espacioId`, `estado`, `precioHoraAplicado`, `montoTotal`, `creadaEn`, `expiraEn`, `canceladaPor`, `canceladaEn`,
  `montoReembolso`, `recordatorioEnviadoEn`. **No guarda arrendador**: sale de `espacio.arrendador_id`.
- Estados (`reservas/domain/EstadoReserva.java`): `PENDIENTE_PAGO`, `CONFIRMADA`, `EXPIRADA`, `CANCELADA`. Leer con
  `estadoEn(ahora)`, no `getEstado()`. `admiteLiquidacion()`: `CONFIRMADA` o `CANCELADA` por USUARIO con reembolso 0.
- Horario: `reserva_bloque`; `reservas/domain/ReservaConHorario.java` da `instanteDeInicio()` e `instanteDeFin()`
  (Lima; horaFin 24 = 00:00 del día siguiente).
- DAO: `reservas/repository/ReservaDAO.java` (`delUsuario(usuarioId)`, `confirmadasDeEspacios(espacioIds)`) e
  implementación `ReservaDAOJpa.java`. Repositorio: `ReservaRepository.java` con `findByIdAndUsuarioId(id, usuarioId)`,
  `findParaActualizar(id)` (`FOR UPDATE`), `findByUsuarioId`.
- Servicios: `ReservaService` (interfaz pública: `crear`, `consultar(usuario, id)`), `ConsultaReservasService`
  (listas), `CancelacionReservaService`, `ReservasParaResena` (interfaz pública angosta para `busqueda`:
  `delUsuario(usuarioId, reservaId)` → 404 si no es suya; `confirmadasDe(usuarioId)`) — **buen modelo** para una
  `ReservasParaReclamo` que use `administracion`.
- Endpoints (`reservas/controller/ReservaController.java`, `/api/reservas`): `GET` (mis reservas, `?grupo=proximas|pasadas
  &pagina=n`, rol USUARIO), `GET /{id}`, `GET|POST /{id}/cancelacion`. Arrendador:
  `ReservasDeMisEspaciosController` en `/api/arrendadores/actual/reservas` (solo `CONFIRMADA`, filtro `espacioId`).
- Propiedad: siempre desde la sesión (`@AuthenticationPrincipal UsuarioAutenticado`). Usuario: `findByIdAndUsuarioId`
  o `reserva.usuarioId == sesion` → si no, `RecursoNoEncontradoException` (404, igual que inexistente; id mal escrito
  también 404 vía `ConversionReservas.idReserva`). Arrendador: por `EspacioBloqueado.arrendadorId` (HU-15) o
  `CatalogoEspaciosService.arrendadoresDe(espacioIds)` / `nombresDelArrendador(arrendadorId)`.
- Orden de bloqueos establecido (HU-13/15): `espacio → reserva` (`DisponibilidadService.bloquearEspacio` → 
  `ReservaRepository.findParaActualizar`).

### 2. Reembolsos (HU-16)

- Tabla `reembolso` (`V4_3__reembolso.sql`): `origen` ya admite `'RECLAMO'` (`ck_reembolso_origen`);
  `uq_reembolso_pago_origen` (un reembolso por pago y origen); `monto > 0`. `OrigenReembolso.RECLAMO` ya existe.
- Disparo hoy: `PagoServiceImpl.reembolsarCancelacion(ReembolsoPorCancelacion)` (`MANDATORY`): busca pago `PAGADO`
  (`PagoRepository.findByReservaIdAndEstado`), valida `0 < monto ≤ pago.monto`, verifica que no exista reembolso del
  origen, `saveAndFlush(Reembolso.porCancelacion(...))`, audita `REEMBOLSO_SOLICITADO`
  (`origen=...;reservaId=...;monto=...`) y `ejecutor.ejecutarDespuesDelCommit(solicitudes.crear(id, Disparo.AUTOMATICO,
  solicitadoPor, ip))`.
- Reutilizable: `pagos/service/reembolso/{SolicitudesReembolso, EjecutorReembolsos, SolicitudReembolso,
  RegistroReembolsos}` sin cambios. Lo natural (por "solo vía interfaz de service"): **agregar a `PagoService` un método
  `reembolsarReclamo(...)`** gemelo de `reembolsarCancelacion`, con `Reembolso.porReclamo(pagoId, monto, ahora)`;
  `administracion` lo llama (dirección `administracion → pagos` permitida). `Disparo` tiene `AUTOMATICO`/`ADMIN`
  (`pagos/service/reembolso/Disparo.java`): el spec decide cuál (lo dispara una acción del admin).
- Estados: `Pago.marcarReembolsado()` pasa a `REEMBOLSADO` **aunque el reembolso sea parcial** (no hay estado
  "parcialmente reembolsado"). Reintento de fallidos: panel existente `/api/admin/reembolsos` (`ReembolsoAdminService`)
  sirve igual para los de origen RECLAMO (ya hay IT con `RECLAMO`: `ReembolsoRestriccionesIT.java`).
- Liquidación: `PagoRepository.pagadosSinLiquidar` excluye todo pago con **cualquier** fila de `reembolso`, así que un
  reembolso por reclamo (total o parcial) saca la reserva de toda liquidación.

### 3. Liquidación semanal (HU-19)

- `pagos/service/GeneradorLiquidaciones.java#generar` (`MANDATORY`): advisory lock `190019`
  (`LiquidacionRepository.bloquearGeneracion`) → corte `Instant.now` → `pagadosSinLiquidar` →
  `ReservasLiquidables.describir(ids)` (implementado por `reservas/service/impl/ReservasLiquidablesImpl.java`) → filtro
  `reserva.admitida() && ReglasLiquidacion.esLiquidable(finEn, corte)` (`corte ≥ fin + 48 h`) → agrupa por arrendador →
  `Liquidacion.generar` (bruto, comisión 10 %, neto) + `liquidacion_detalle`.
- **No existe hoy** ningún mecanismo de retención por reserva; el punto de inserción previsto es el filtro de
  `liquidablesPorArrendador` (línea ~105) con el puerto `pagos.service.puertos.ReclamosAbiertos` (p. ej.
  `Set<Long> conReclamoAbierto(Collection<Long> reservaIds)`), implementado en `administracion`. Modelo de puerto:
  `pagos/service/puertos/ReservasLiquidables.java`.
- `ReglasLiquidacion.ESPERA` (`Duration.ofHours(48)`) es `public static`: reutilizable para la ventana del reclamo.
- `MisLiquidacionesService` / `LiquidacionAdminService` no requieren cambios salvo que el spec quiera mostrar "retenida
  por reclamo".
- Impedimentos HU-22 (`DatosPersonalesService`): "cobros por liquidar" usan `pagadosSinLiquidar` + `describir`; una
  reserva con reclamo abierto sigue contando como por liquidar ahí (ver H8).

### 4. `administracion` y `comun`

- `administracion/` hoy: HU-06 (`RevisionIdentidadController/Service`, `revision_identidad`) y HU-09
  (`CuentaAdminController` en `/api/admin/cuentas`, `CuentaAdminService`, `suspension_cuenta`). Patrón: controlador bajo
  `/api/admin/**`, `@AuthenticationPrincipal`, `IpCliente.de(peticion)`, `Cache-Control: no-store` en lecturas, DTOs con
  `@Valid`; servicio con `exigirAdmin(admin)` privado copiado (rol `"ADMIN"` → si no, `AccesoDenegadoException`) **antes
  de leer**, `@Transactional`, historial propio + auditoría.
- Protección admin: `comun/seguridad/FiltroIpsAdmin.java` (IPs `ADMIN_IPS_PERMITIDAS` sobre `/api/admin/**`, 403
  genérico) + MFA obligatorio del admin (`SesionService.validar`) + rol en servicio. `ConfiguracionSeguridad` solo
  marca rutas públicas; todo lo demás `authenticated()`; **los roles se verifican en el servicio**, no en Spring Security.
- Roles: `cuentas/domain/Rol.java` (`USUARIO`, `ARRENDADOR`, `ADMIN`); `UsuarioAutenticado.rol()` / `.usuarioId()`.
- Auditoría: `comun/auditoria/AuditoriaService.registrar(new EventoAuditoria(usuarioId, accion, entidad, entidadId, ip,
  detalle))`, tabla `auditoria`.
- Correo: `comun/correo/{FabricaCorreo, Correo, EnvioCorreo, FormatoCorreo, SinDatos}`; ejemplos de ConcreteCreator en
  `reservas/service/recordatorio/` y `cuentas/service/correo/`. Correos a usuario/arrendador: `CuentaService.correoDe(id)`
  (y `correoParaRecordatorio` solo para recordatorios). Ejecutar tras commit con `comun/transaccion/DespuesDelCommit`.
  Alternativa existente: `cuentas.service.NotificadorCorreo` (13 métodos), pero la guía de HU-25 pide fábricas en el
  módulo dueño del evento.
- Errores: `comun/error/` (`ReglaNegocioException` [400, con o sin campo], `ValidacionException`,
  `RecursoNoEncontradoException` [404], `AccesoDenegadoException` [403], `DemasiadosIntentosException` [429],
  `ManejadorGlobalErrores`, `ErrorDTO`).
- Rate limiting: `comun/limite/{FiltroLimiteIntentos, LimitadorIntentos, ReglaLimite}` (por IP y ruta/patrón; también
  `LimitadorIntentos.intentar(clave, max, ventana)` para claves propias). RNF-SEG-17 dice "1 reclamo por reserva", que se
  cumple con el `UNIQUE` de BD, no con un limitador.
- Sanitizado: `comun/texto/SanitizadorTexto.limpiar` (puede devolver vacío → validar después).
- Almacenamiento (`comun/almacenamiento/*`): existe, pero **HU-26 excluye fotos** ("sin adjuntar fotos").
- Puerto de datos personales: `cuentas/service/puertos/ParticipanteDatosPersonales.java`; implementaciones con `@Order`
  1-3 (`DatosPersonalesReservasImpl`, `DatosPersonalesPagosImpl`, `DatosPersonalesResenasImpl`).

### 5. Migraciones

- Flyway, `2_Backend/src/main/resources/db/migration/`, numeradas por sprint `V<sprint>_<n>__nombre.sql`;
  `spring.flyway.out-of-order=true`. Última: **`V7_3__recordatorios.sql`**. HU-26 es Sprint 8 → **`V8_x`**; ojo, HU-27 y
  HU-28 (mismo sprint, otros dueños, `pending`) también podrían tomar `V8_1`/`V8_2`: coordinar número (p. ej. `V8_1`
  reclamos y que HU-27 use otro) antes de subir a `develop`.
- **No existe tabla `reclamo` ni `mensaje_reclamo`** ni código de reclamos (solo `OrigenReembolso.RECLAMO`, el `CHECK`
  de `reembolso` y un texto en `3_frontend/src/pages/pagos/textosPagos.js`). Modelo de referencia (`docs/modelo-datos.md`
  línea 37): `reclamo(id, reserva_id único, motivo, descripcion, estado, resolucion)` y
  `mensaje_reclamo(reclamo_id, autor_id, texto, fecha)`; restricción `reclamo.reserva_id` único (línea 60).

### 6. Frontend (`3_frontend/src`)

- Mis reservas: `pages/reservas/MisReservas.jsx` (secciones Próximas/Pasadas con `SeccionReservas.jsx`;
  `tarjetaDeReserva(reserva, accion)` admite un componente de acción por tarjeta: hoy `CancelarReserva` en próximas
  `CONFIRMADA` y `ResenarReserva` en pasadas reseñables según `GET /api/resenas/pendientes`). Un botón "Reportar problema"
  encaja igual que `ResenarReserva` (modal), alimentado por un endpoint tipo "reclamables" o un campo nuevo en `ReservaDTO`.
- Arrendador: `pages/reservas/ReservasDeMisEspacios.jsx` + `TarjetaReservaDeMiEspacio.jsx` (`/panel/reservas`).
- Admin existente: `pages/administracion/{ColaIdentidades, RevisarIdentidad, BuscarCuentas, DetalleCuenta}.jsx`,
  `pages/pagos/{ReembolsosPorAtender, Liquidaciones, DetalleLiquidacion}.jsx`. Enlaces del admin en
  `pages/cuentas/Panel.jsx` (líneas ~69-83). Rutas en `App.jsx` (`/admin/identidades[/:id]`, `/admin/cuentas[/:id]`,
  `/admin/reembolsos`, `/admin/liquidaciones[/:id]`), todas con `<RutaProtegida>` (sin filtro de rol en el cliente).
- `components/ui/`: `Alerta`, `Boton`, `Campo`, `EtiquetaEstado`, `Modal`, `Tarjeta`, `GrupoCasillas`, `Imagen`,
  `Galeria`, `CuentaRegresiva`, `SelectorBloques`.
- `src/api/`: `cliente.js` (`peticion(ruta, {metodo, cuerpo})`), `reservas.js`, `administracion.js` (identidades y
  cuentas), `pagos.js` (reembolsos admin, liquidaciones), `busqueda.js` (reseñas). Textos por módulo en
  `pages/<modulo>/textos*.js`.
- `docs/frontend.md` (tabla de estados, fila "Reclamo | — | abierto | — | —") pide que el spec nombre los estados del
  reclamo y los agregue a esa tabla y a `EtiquetaEstado`.

## Huecos y ambigüedades para el spec-author

- **H1. Respuesta del arrendador (contradicción).** CA1 dice "el arrendador puede responder", pero
  `docs/requerimientos.md` §9 punto 3 lo marca como pendiente con el PO, y la tabla "Qué NO hacer" de
  `architecture.md` (línea 828) dice "Respuesta del arrendador a reseñas y fotos en reclamos | Simplificada: fuera de
  HU-24 y HU-26" (lectura ambigua: ¿"respuesta a reseñas" + "fotos en reclamos", o también respuesta en reclamos?). El
  modelo de datos sí prevé `mensaje_reclamo` (autor_id, texto). Decidir: ¿hilo de mensajes (usuario, arrendador, admin)
  o una sola respuesta del arrendador? ¿cuántas, longitud, hasta cuándo (solo mientras esté abierto)? ¿el usuario puede
  replicar? ¿se notifica por correo?
- **H2. Ventana de 48 h.** La arquitectura fija `ahora < fin + 48 h`. Falta decidir el borde inferior: ¿se puede abrir
  antes de que termine o empiece la reserva (p. ej. "espacio cerrado" al llegar, `ahora ≥ inicio`)? Solo estado leído
  `CONFIRMADA` (¿y una `CANCELADA` por el usuario con 0 % que igual se liquida?).
- **H3. Carrera con la generación de liquidaciones.** Abrir un reclamo justo antes del borde mientras se genera:
  `GeneradorLiquidaciones` toma el advisory lock `190019`, pero abrir un reclamo no. Opciones: tomar el mismo advisory
  lock al crear el reclamo, o releer bajo el bloqueo. Además, si la reserva ya está en `liquidacion_detalle` no debe
  aceptarse (debería ser imposible por la ventana, pero conviene validarlo).
- **H4. Monto del reembolso parcial.** ¿Quién fija el monto (admin escribe un monto ≤ pago.monto, o porcentaje)?
  `PagoServiceImpl` ya valida `0 < monto ≤ pago.monto`. El pago pasa a `REEMBOLSADO` aunque sea parcial y la reserva
  queda fuera de **toda** liquidación (el arrendador no cobra el remanente). Si se quiere liquidar el remanente, cambiar
  `pagadosSinLiquidar` (fuera de lo previsto). Confirmar que es aceptable.
- **H5. Estado de la reserva tras resolver a favor del usuario.** ¿Sigue `CONFIRMADA` (HU-24 P10 asume que sí y puede
  reseñarse)? No hay estado "reembolsada" en `reserva`. Confirmar y no tocar `ck_reserva_cancelacion`.
- **H6. Estados del reclamo.** No hay enum. Proponer p. ej. `ABIERTO`, `RESUELTO_USUARIO`, `RESUELTO_ARRENDADOR`
  (¿`EN_REVISION`?), con `resolucion`/motivo, `admin_id`, `resuelto_en`, `monto_reembolso`. Motivos: ¿catálogo cerrado
  (ESPACIO_CERRADO, DISTINTO_A_LO_PUBLICADO, OTRO) o texto libre? Longitudes de descripción/mensajes.
- **H7. Puerto `ReclamosAbiertos` y dirección de módulos.** `administracion` implementará un puerto de `pagos` y llamará
  a `PagoService` (nuevo método) y a `reservas` (nueva interfaz angosta tipo `ReservasParaReclamo` para validar dueño,
  estado, fin y arrendador). Definir firmas, propagación (`MANDATORY` vs propia) y que `ReservaDAO` exige dueño (una
  consulta del sistema/arrendador no usa el DAO, como `ReservasLiquidablesImpl`).
- **H8. HU-22 (datos personales).** ¿Un reclamo abierto es impedimento para eliminar la cuenta (del usuario o del
  arrendador)? ¿Se exporta la sección `reclamos`? ¿Al anonimizar se reemplazan descripción y mensajes (como
  `Resena.COMENTARIO_ELIMINADO`)? La arquitectura pide implementar `ParticipanteDatosPersonales` (nuevo `@Order` 4) si
  guarda datos personales (los textos libres lo son).
- **H9. Correos.** CA2/CA3: correo a ambos con la resolución. ¿Correo al abrir el reclamo (al arrendador) o al responder?
  Contenido mínimo (sin datos de la otra parte). Patrón Factory Method de HU-25 en `administracion`.
- **H10. Pantallas.** Usuario: botón en `MisReservas` (¿ampliar `ReservaDTO` con `reclamable`/`estadoReclamo` o ruta
  `GET /api/reclamos/...` aparte como HU-24?) y vista de su reclamo. Arrendador: dónde ve y responde (¿en
  `/panel/reservas`, que solo lista `CONFIRMADA`?, ¿pantalla nueva `/panel/reclamos`?). Admin: lista de abiertos y
  detalle/resolución en `/admin/reclamos[/:id]`, enlace en `Panel.jsx`. ¿Paginación?
- **H11. Auditoría (R).** Acciones a registrar: `RECLAMO_ABIERTO`, `RECLAMO_RESPONDIDO`/mensaje, `RECLAMO_RESUELTO`
  (+ `REEMBOLSO_SOLICITADO` que ya audita `PagoService`). Sin textos libres en el detalle (como HU-09: el motivo vive en su
  tabla).
- **H12. Número de migración** `V8_x` compartido con HU-27/HU-28 del mismo sprint (ver §5).
- **H13. `exigirAdmin` duplicado.** Ya está copiado en `CuentaAdminService`, `RevisionIdentidadService` y
  `ReembolsoAdminService`; el comentario de HU-09 sugiere que HU-27 lo extraiga. HU-26 puede copiarlo o coordinar.
- **H14. Concurrencia de resolución.** Dos admins resolviendo a la vez: bloquear la fila `reclamo` (`FOR UPDATE`) y
  definir orden de bloqueos respecto a `reserva`/`pago` (el reembolso bloquea `reembolso → pago` en `REQUIRES_NEW` tras
  commit, sin ciclo si `administracion` no bloquea `pago`).
