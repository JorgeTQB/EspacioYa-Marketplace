# Exploración HU-25 · Recordatorio de reserva por correo

> Explorador de solo lectura, 2026-11-07. Rama `feature/HU-25-recordatorio-reserva`. Solo hechos del código y de `docs/`;
> las decisiones quedan para `spec-author`.

## Contexto de la historia (docs)

- `docs/historias/HU-25.md`: R2 · S7 · tamaño S · depende de HU-13 · ★ Factory Method + Adapter. CA1 recordatorio 24 h
  antes de una reserva **confirmada** al usuario **y** al arrendador; CA2 se puede desactivar en el perfil; CA3 los
  correos de pago y cancelación no se desactivan. STRIDE: (I) sin tarjeta ni documentos; (E) destinatarios desde la
  reserva en el servidor.
- `docs/requerimientos.md`: RF-D-10 (`:101`), RF-D-11 (`:102`), RF-A-26 (`:56`, activar/desactivar en el perfil),
  RN-14 (`:223`), RNF-SEG-01 (`:146`), RNF-SEG-16 (`:161`), RNF-ARQ-08 (`:186`, Adapter de correo), RNF-USA-04 (`:195`),
  §8 fila `:280` ("HU-25 formaliza Factory Method + Adapter" sobre el envío de correo detrás de interfaz propia).
  **§9-8 (`:295`) sigue abierto:** "Envío de recordatorios (RF-D-10): requiere un proceso programado … confirmarlo en
  el diseño de HU-25". HU-25 debe cerrarlo.
- `docs/architecture.md`: `:56` `comun` incluye "correo"; `:64` `comun` no depende de ningún módulo; `:115` integraciones
  externas siempre detrás de Adapter; `:741` "HU-25 ★ Factory Method, Adapter — `comun/correo`: crear el correo según
  el evento; adaptador del proveedor de correo"; `:750` no usar el SDK del correo fuera de su adaptador; `:216-217`
  "HU-25 implementa los dos métodos nuevos de `NotificadorCorreo` (HU-20) en el adaptador real; el token
  `VERIFICACION` sigue libre para el correo de registro". `:126` dirección de módulos
  `comun ← cuentas ← espacios ← reservas → pagos → cuentas`.
- `docs/modelo-datos.md:13`: `usuario` lista **`recordatorios_activos`** (HU-25) — **no existe aún** en BD ni entidad.
  `:16`: token `VERIFICACION` "queda para HU-25". No hay tabla de notificaciones/recordatorios enviados en el modelo.
- Notas heredadas en specs: `specs/HU-01/design.md:386-391` (columna `recordatorios_activos` la agrega HU-25 con su
  migración; `Usuario.registrar` fija `correo_verificado=true` y "cuando HU-25 traiga el correo real, basta con cambiar
  la fábrica"); `specs/HU-01/requirements.md` S5 (verificación simulada; "el adaptador real lo conecta HU-25");
  `specs/HU-04/requirements.md` D2 y S10 (mover el puerto a `comun` es de HU-25; revisar la diferencia de tiempo de
  respuesta con envío real); `specs/HU-15/design.md:540-543` ("HU-25 (Factory Method) los tendrá que crear como dos
  eventos más") y `:668` (rechazó métodos `default` vacíos en `NotificadorCorreo`); `specs/HU-24/design.md:283` (V7_2
  tomada por HU-24; HU-25 también es sprint 7).

---

## 1. ¿Existe envío de correo? Adapter/Factory de modelo

**Archivos analizados:** `2_Backend/src/main/java/pe/edu/ulima/espacioya/cuentas/service/NotificadorCorreo.java`,
`.../cuentas/service/NotificadorCorreoSimulado.java`, `.../cuentas/service/{ComprobantePago,AvisoReservaPagada,
AvisoCancelacionUsuario,AvisoCancelacionArrendador,AvisoCambioCuentaBancaria,AvisoCuenta}.java`,
`.../pagos/service/AvisosPago.java`, `.../reservas/service/AvisosCancelacion.java`,
`2_Backend/src/main/resources/application.properties`, `2_Backend/.env.example`, `2_Backend/pom.xml`,
`.../pagos/service/pasarela/{PasarelaPago,MercadoPagoAdapter,PasarelaSimulada}.java`,
`.../pagos/service/reembolso/SolicitudesReembolso.java`.

**Hallazgos:**

- **No hay envío real.** Solo existe el puerto `cuentas.service.NotificadorCorreo` (interfaz, 13 métodos) y una única
  implementación `cuentas.service.NotificadorCorreoSimulado` (`@Component`, solo escribe `log.info` sin correo ni
  datos; con `espacioya.correo.simulado.registrar-enlace=true` registra los enlaces de recuperación y cambio de correo).
  Métodos actuales:
  `notificarVerificacion(correo)`, `notificarRecuperacion(correo, enlace)`, `notificarIdentidadAprobada(correo)`,
  `notificarIdentidadRechazada(correo, motivo)`, `notificarCuentaSuspendida(correo, motivo)`,
  `notificarCuentaReactivada(correo)`, `notificarPagoConfirmado(correo, ComprobantePago)`,
  `notificarReservaPagada(correoArrendador, AvisoReservaPagada)`,
  `notificarCancelacionAlUsuario(correo, AvisoCancelacionUsuario)`,
  `notificarCancelacionAlArrendador(correo, AvisoCancelacionArrendador)`,
  `notificarCambioCuentaBancaria(correo, AvisoCambioCuentaBancaria)`, `notificarCambioCorreo(correoNuevo, enlace)`,
  `notificarAvisoCambioCorreo(correoActual, correoNuevoEnmascarado)`.
  El Javadoc dice: "HU-25 lo moverá a `comun/correo`". Los records de datos (`ComprobantePago`, `Aviso*`) viven
  también en `cuentas.service`.
- **No existe `comun/correo/`** (paquetes de `comun`: `almacenamiento`, `auditoria`, `cifrado`, `configuracion`,
  `error`, `limite`, `seguridad`, `texto`, `transaccion`, `web`).
- **Quién usa el puerto (main):** `CuentaServiceImpl`, `RegistroService`, `RegistroArrendadorService`,
  `RecuperacionContrasenaService`, `CambioCorreoService` (cuentas), `pagos.service.AvisosPago`,
  `pagos.service.CuentaBancariaService`, `reservas.service.AvisosCancelacion`. Moverlo a `comun` toca esas clases
  y **28 archivos** en total (incl. tests, ver §6).
- **Patrón de envío existente:** `AvisosPago.programar(ReservaBloqueada, PagoConsultado)` y
  `AvisosCancelacion.programar(...)` leen los correos con `CuentaService.correoDe(id)` **dentro** de la transacción y
  envían con `DespuesDelCommit.ejecutar(...)`, **uno por sincronización** (el fallo de uno no impide el otro). Estos
  correos NO consultan ninguna preferencia (CA3 ya se cumple hoy; no debe condicionarse a `recordatorios_activos`).
- **Configuración:** `application.properties` solo tiene `espacioya.correo.simulado.registrar-enlace`
  (`CORREO_SIMULADO_REGISTRAR_ENLACE`, también en `.env.example:17-19`). No hay `spring.mail.*`, host SMTP, remitente
  ni selector de proveedor.
- **Dependencias (`pom.xml`):** Spring Boot **4.1.2**, Java 25. Starters: webmvc, validation, data-jpa, flyway (+
  flyway-database-postgresql), postgresql, security; tests: *-test starters, testcontainers (junit-jupiter,
  postgresql). **No hay `spring-boot-starter-mail`, ni GreenMail, ni SDK de proveedor de correo.**
- **Adapter de modelo (HU-13):** `pagos.service.pasarela.PasarelaPago` (Target, sin tipos del proveedor; fallas →
  `comun.error.ServicioExternoException`) con `MercadoPagoAdapter` (`RestClient` + `JdkClientHttpRequestFactory` con
  timeouts, sin SDK; valida propiedades obligatorias en el constructor y falla al arrancar) y `PasarelaSimulada`, cada
  una `@Component @ConditionalOnProperty(name="espacioya.pagos.pasarela", havingValue="mercadopago"|"simulada")`.
  `docs/architecture.md:396`: "no hay fábrica"; la propiedad está vacía por defecto para que cada entorno elija (sin
  valor el backend no arranca). Es el modelo directo para elegir `NotificadorCorreoSimulado` vs. adaptador real.
- **Factory de modelo:** solo `pagos.service.reembolso.SolicitudesReembolso` ("Fábrica del ConcreteCommand (HU-16)",
  `crear(reembolsoId, disparo, solicitadoPor, ip)`), que es una fábrica simple, no Factory Method. No hay ningún
  Factory Method en el código: HU-25 sería la primera (crear el correo según el evento, `architecture.md:741`).
  Otros patrones ★ ya documentados para inspirar estructura: Strategy `reservas.domain.PoliticaReembolso` +
  `Reembolso*` (HU-15), Decorator `busqueda.service.FiltroBusqueda` (HU-23), Command `pagos.service.reembolso` (HU-16).

## 2. Entidad/tabla de reserva y confirmación tras el pago

**Archivos analizados:** `.../reservas/domain/{Reserva,EstadoReserva,ReservaBloque,ReservaConHorario}.java`,
`.../reservas/repository/{ReservaRepository,ReservaBloqueRepository,ReservaDAO,HorarioReserva}.java`,
`.../reservas/service/impl/{ReservasPorCobrarImpl,ReservasLiquidablesImpl}.java`,
`.../pagos/service/{WebhookPagoService,AvisosPago}.java`, `.../pagos/service/puertos/{ReservasPorCobrar,
ReservaBloqueada}.java`, `db/migration/V3_3__reservas.sql`, `docs/architecture.md:133-139,233-249,384-402`.

**Hallazgos:**

- **`reservas.domain.Reserva`** (`@Table("reserva")`): `id`, `usuarioId` (`usuario_id`), `espacioId`, `estado`
  (`EstadoReserva`), `precioHoraAplicado`, `montoTotal`, `creadaEn`, `expiraEn`, `canceladaPor` (`QuienCancela`),
  `canceladaEn`, `montoReembolso`. **No guarda fecha/hora de inicio ni `arrendadorId`.** Métodos: `pendiente(...)`,
  `esVigente`, `estaVencida`, `estadoEn(ahora)`, `expirar`, `confirmar(ahora)`, `motivoNoCancelable`, `cancelar`,
  `admiteLiquidacion`.
- **`EstadoReserva`**: `PENDIENTE_PAGO, CONFIRMADA, EXPIRADA, CANCELADA` (CHECK `ck_reserva_estado` en V3_3).
- **Fecha/hora**: salen de `reserva_bloque` (`ReservaBloque`: `reservaId`, `espacioId`, `fecha` `LocalDate`,
  `horaInicio` 0–23, `activo`). `ReservaBloqueRepository.horarios(reservaIds)` → `HorarioReserva(reservaId, min fecha,
  min hora, max hora)` (sin filtrar por `activo`). `ReservaConHorario(reserva, fecha, horaInicio, horaFin)` tiene
  `instanteDeInicio()` (Lima → `Instant`, HU-15 R14) e `instanteDeFin()`. Ya existe `ix_reserva_usuario_estado`;
  **no hay índice** por fecha de bloques útil para "reservas que empiezan dentro de 24 h" salvo
  `uq_reserva_bloque_activo (espacio_id, fecha, hora_inicio) WHERE activo` (empieza por `espacio_id`).
- **Relación con usuario/correo**: solo `usuario_id` (FK a `usuario`). El correo se obtiene vía
  `CuentaService.correoDe(usuarioId)` (sin sesión, 404 si no existe; "nunca se devuelve a un cliente",
  `architecture.md:162-163`). No hay método en `CuentaService` que devuelva correos o preferencias en lote.
- **Arrendador**: se obtiene de `espacios` con `CatalogoEspaciosService.arrendadoresDe(espacioIds)` → `Map<Long,Long>`
  (HU-19, sin filtro de estado) y el nombre con `CatalogoEspaciosService.nombres(espacioIds)` (HU-12, sin filtro de
  visibilidad). `ReservasLiquidablesImpl.describir` es un ejemplo de consulta por lote del sistema (sin sesión) que
  arma reserva + arrendador + espacio + horario.
- **Confirmación (HU-13)**: único camino `pagos.service.WebhookPagoService.recibir → registrar` (en
  `TransactionTemplate`): `reservas.bloquear(id)` (`ReservasPorCobrarImpl.bloquear`, `MANDATORY`, bloquea espacio →
  reserva) → `INSERT pago` → si `PAGADO`: `reservas.confirmar(id, evaluadaEn)` (`ReservasPorCobrarImpl.confirmar`:
  `Reserva.confirmar` + auditoría `RESERVA_CONFIRMADA`, `origen=WEBHOOK`) y `avisos.programar(bloqueada, consultado)`
  (`AvisosPago`, dos correos tras el commit). `ReservaBloqueada` ya trae `usuarioId`, `arrendadorId`, `espacioNombre`,
  `fecha`, `horaInicio`, `horaFin`, `montoTotal`.
- **Eventos de dominio / listeners: no hay ninguno.** Grep de `ApplicationEventPublisher`, `@EventListener`,
  `@TransactionalEventListener`, `@Async`, `@EnableAsync` en `2_Backend/src`: **0 resultados**. Toda notificación es
  una llamada directa + `comun.transaccion.DespuesDelCommit`.
- **Caso borde a considerar:** una reserva confirmada con menos de 24 h de antelación (se puede reservar hoy para
  dentro de unas horas: `VentanaReserva` permite desde hoy) nunca "cruza" la marca de 24 h; y una reserva cancelada
  entre la programación y el envío (HU-15) no debe recibir recordatorio.

## 3. Tareas programadas, registro de notificaciones y migraciones

**Archivos analizados:** `.../reservas/service/{ProgramacionExpiracion,ExpiracionReservas}.java`,
`application.properties`, `src/test/resources/config/application.properties`, `db/migration/*`,
`docs/conventions.md:81-99`, `feature_list.json` (HU-25), `specs/HU-24/design.md:283`.

**Hallazgos:**

- **Único proceso programado:** `reservas.service.ProgramacionExpiracion` (`@Configuration(proxyBeanMethods=false)
  @EnableScheduling @ConditionalOnProperty(name="espacioya.reservas.expiracion.activa", havingValue="true")`,
  método `@Scheduled(fixedDelayString="${espacioya.reservas.expiracion.intervalo}")` que delega en
  `ExpiracionReservas.expirarVencidas()`). Propiedades `RESERVAS_EXPIRACION_ACTIVA` (true) y
  `RESERVAS_EXPIRACION_INTERVALO` (`PT1M`). **`@EnableScheduling` está puesto en esa clase condicional**: si un
  segundo proceso (recordatorios) se apaga/enciende con otra propiedad, debe declarar su propio `@EnableScheduling` o
  moverlo a un sitio incondicional; si no, con la expiración apagada el `@Scheduled` nuevo no correría.
- **Modelo del proceso a copiar:** `ExpiracionReservas.expirarVencidas()` → lee ids con una consulta
  (`ReservaRepository.findIdsVencidas(ahora)`), luego **una transacción por fila** con `TransactionTemplate` y
  `findParaActualizar(id)` (`PESSIMISTIC_WRITE`) re-evaluando la condición (R16), atrapa `RuntimeException` por fila
  con `log.warn` sin datos personales, audita con `EventoAuditoria(null, ACCION, "reserva", id, null,
  "origen=PROCESO;...")`. Respeta el orden de bloqueos `usuario → espacio → reserva` tomando solo `reserva`.
- En pruebas el proceso está **apagado** (`src/test/resources/config/application.properties:11`
  `espacioya.reservas.expiracion.activa=false`) y las IT llaman al método directamente.
- **Registro de notificaciones enviadas: no existe** ninguna tabla ni columna (ni `recordatorio_enviado_en`, ni tabla
  `notificacion`). `docs/modelo-datos.md` tampoco la define. La idempotencia ("no mandar dos veces el recordatorio")
  hoy no tiene soporte; la tabla `auditoria` (`V1_2`) es lo único persistente de eventos.
- **Preferencia:** `usuario.recordatorios_activos` figura en `docs/modelo-datos.md:13` pero **no está** en
  `V1_1__usuarios.sql`, ni en `V6_1__perfil_usuario.sql`, ni en `cuentas.domain.Usuario` (campos: `correo`,
  `hashContrasena`, `rol`, `correoVerificado`, `estado`, `versionPolitica`, `fechaConsentimiento`, `nombre`,
  `telefono`, `correoPendiente`, `fotoRuta`, `fotoTipoMime`).
- **Flyway:** `spring.flyway.out-of-order=true`; `ddl-auto=validate`. Migraciones existentes: `V1_1`–`V1_5`,
  `V2_1`–`V2_4`, `V3_1`–`V3_3`, `V4_1`–`V4_3`, `V5_1`–`V5_3`, `V6_1`, `V7_1__caracteristicas.sql` (HU-23),
  `V7_2__resena.sql` (HU-24). HU-25 es **sprint 7** (`feature_list.json`) → **siguiente libre: `V7_3__...`**
  (confirmar en `develop` antes de crearla, `docs/conventions.md:94-99`).
- `ck_token_tipo` ya admite `VERIFICACION` (`V6_1`), libre si HU-25 conectara el correo de verificación (fuera de sus
  CA; decisión del spec).

## 4. Componentes transversales en `comun`

**Archivos analizados:** `.../comun/**` (listado completo), `ConfiguracionReloj.java`, `ZonaHoraria.java`,
`DespuesDelCommit.java`, `auditoria/EventoAuditoria.java`, `docs/architecture.md:285-296,402-403`.

**Hallazgos:**

- **Reloj:** `comun.configuracion.ConfiguracionReloj` → bean `Clock reloj()` = `Clock.systemUTC()`; se inyecta
  `Clock` y se usa `Instant.now(reloj)`. `comun.configuracion.ZonaHoraria` (`LIMA`, `hoy(reloj)`, `ahora(reloj)`):
  toda regla de calendario usa Lima. "24 h antes" debe calcularse sobre `ReservaConHorario.instanteDeInicio()` (Lima →
  Instant).
- **Transacciones/correo:** `comun.transaccion.DespuesDelCommit.ejecutar(Runnable)` (solo tras commit, atrapa y
  registra el fallo, exige transacción activa). Es el camino estándar de todos los correos.
- **Auditoría:** `comun.auditoria.AuditoriaService.registrar(EventoAuditoria(usuarioId, accion, entidad, entidadId,
  ip, detalle))`. Convención de acciones en MAYÚSCULAS (`RESERVA_EXPIRADA`, `RESERVA_CONFIRMADA`, `PERFIL_ACTUALIZADO`).
- **Errores:** `comun.error.ServicioExternoException` (503 con `ErrorDTO.MENSAJE_SERVICIO_EXTERNO`; "también servirá a
  correo", `architecture.md:403`), `ValidacionException`/`ErrorCampoDTO`, `AccesoDenegadoException`,
  `RecursoNoEncontradoException`, `DemasiadosIntentosException`, `ReglaNegocioException`; manejador
  `ManejadorGlobalErrores`.
- **Rate limiting:** `comun.limite.LimitadorIntentos`, `FiltroLimiteIntentos`, `ReglaLimite` (por ruta/IP). Solo
  aplicaría si se agrega una ruta nueva de preferencias (el `PUT /perfil` actual no tiene cupo propio).
- **Seguridad:** `UsuarioAutenticado` (`@AuthenticationPrincipal`), `ConfiguracionSeguridad`; identidad siempre de la
  sesión (RNF-SEG-01).
- **Texto:** `comun.texto.SanitizadorTexto` (escapado de texto libre; útil si el cuerpo del correo lleva el nombre del
  espacio en HTML).
- **Cifrado:** `CifradorCampos` (no aplica a correo).

## 5. Frontend (preferencia de recordatorios)

**Archivos analizados:** `3_frontend/src/api/cuentas.js`, `3_frontend/src/api/cliente.js` (referencia),
`3_frontend/src/pages/cuentas/Perfil.jsx`, `3_frontend/src/components/ui/*.jsx`, `3_frontend/src/App.jsx`,
`3_frontend/package.json`, backend `cuentas/controller/PerfilController.java`, `cuentas/dto/{PerfilDTO,DatosPerfilDTO,
PerfilExportadoDTO}.java`, `cuentas/service/PerfilService.java`.

**Hallazgos:**

- **Backend del perfil (HU-20):** `PerfilController` en `/api/usuarios/actual`: `GET`/`PUT /perfil`
  (`DatosPerfilDTO(nombre, telefono)`, sin contraseña), `PUT`/`GET`/`DELETE /foto`, `POST /cambios-correo`. Todas con
  `no-store` y la cuenta de la sesión. `PerfilDTO(rol, nombre, telefono, correo, correoPendiente, tieneFoto,
  facturacion)` — sin campo de recordatorios. `PerfilService.actualizarDatos(usuario, datos, ip)` audita
  `PERFIL_ACTUALIZADO` con `campos=...`. `ADMIN` recibe 403 en todo el perfil (HU-20 D7) — relevante: CA1 habla de
  "usuario o arrendador".
- **Exportación HU-22:** `PerfilExportadoDTO` (bloque `perfil` de "descargar mis datos") no incluye la preferencia;
  si se agrega la columna, considerar exportarla (dato personal de configuración).
- **API front:** `3_frontend/src/api/cuentas.js:131-153`: `obtenerPerfil()`, `actualizarDatosPerfil({nombre,
  telefono})`, `subirFotoPerfil`, `obtenerFotoPerfil`, `quitarFotoPerfil`, `solicitarCambioCorreo`,
  `confirmarCambioCorreo`, `actualizarFacturacion`, `exportarMisDatos`, `eliminarMiCuenta` (todas sobre `peticion` de
  `api/cliente.js`). No hay función de preferencias.
- **Pantalla:** `3_frontend/src/pages/cuentas/Perfil.jsx` (ruta `/panel/perfil` en `App.jsx:177-182`, protegida),
  organizada en `Tarjeta`s (`TITULO_DATOS_PERSONALES`, `TITULO_FOTO`, `TITULO_CORREO`, `TITULO_FACTURACION`) +
  `DatosPersonales.jsx`; textos en `pages/cuentas/textosCuentas.js`, errores con `erroresCampo.js`. Una tarjeta nueva
  "Recordatorios" encaja ahí.
- **Componentes UI (`3_frontend/src/components/ui/`):** `Alerta`, `Boton`, `Campo` (soporta `tipo="checkbox"` con
  `valor` booleano, lo usa `GrupoCasillas`), `GrupoCasillas`, `Tarjeta`, `Modal`, `EtiquetaEstado`, `Imagen`,
  `Galeria`, `CuentaRegresiva`, `SelectorBloques`.
- **Pruebas front:** no hay ningún `*.test.js(x)` ni script `test` en `package.json` (solo `build` y `lint`).

## 6. Estructura de las pruebas

**Archivos analizados:** `2_Backend/src/test/java/pe/edu/ulima/espacioya/{RelojControlable,TestcontainersConfiguration,
MfaDePrueba}.java`, `.../pagos/controller/PagosSoporte.java`, `.../reservas/controller/ReservasControllerSoporte.java`,
`.../reservas/service/AvisosCancelacionTest.java`, `.../pagos/service/AvisosPagoTest.java`,
`src/test/resources/config/application.properties`, `pom.xml` (failsafe).

**Hallazgos:**

- **Unitarias `*Test`** (`./2_Backend/mvnw -f 2_Backend/pom.xml test`): JUnit 5 + Mockito
  (`@ExtendWith(MockitoExtension.class)`, `@Mock CuentaService`, `@Mock NotificadorCorreo`) + AssertJ. Para probar
  `DespuesDelCommit` sin Spring: `TransactionSynchronizationManager.initSynchronization()` en `@BeforeEach`, luego se
  disparan `getSynchronizations().forEach(TransactionSynchronization::afterCommit)` y se limpia en `@AfterEach`
  (`AvisosCancelacionTest`, `AvisosPagoTest`, `comun/transaccion/DespuesDelCommitTest`). Los datos de reserva se arman
  con `Reserva.pendiente(...)` + `ReservaConHorario`.
- **Integración `*IT`** (`verify`, `maven-failsafe-plugin`): `@SpringBootTest` + MockMvc + **PostgreSQL real con
  Testcontainers** (`TestcontainersConfiguration`). Clases base abstractas `*Soporte` por módulo
  (`ReservasControllerSoporte`, `PagosSoporte`, `PerfilSoporte`, `DatosPersonalesSoporte`, `EspaciosControllerSoporte`,
  `ReembolsosSoporte`, `LiquidacionesSoporte`) con `@TestConfiguration static class Dobles` que registra beans
  `@Primary`: `RelojControlable` (`2_Backend/src/test/java/pe/edu/ulima/espacioya/RelojControlable.java`, `extends
  Clock`, arranca en `2026-10-28T15:00:00Z` = miércoles 10:00 Lima) y un **doble a mano de `NotificadorCorreo`** que
  guarda los correos en `CopyOnWriteArrayList` y puede fallar a pedido (`PagosSoporte.NotificadorFalso`, con flag
  `falla`). Limpian tablas con `JdbcTemplate` (orden por FK sin cascada).
- **No hay GreenMail ni ningún servidor SMTP de prueba**; ningún `@MockitoBean`.
- **Dobles que implementan `NotificadorCorreo` completo** (cada método nuevo del puerto los obliga a cambiar, salvo que
  el diseño evite agregar métodos): `cuentas/controller/{UsuarioControllerIT, ArrendadorControllerIT,
  RecuperacionContrasenaControllerIT, PerfilSoporte, DatosPersonalesSoporte}`, `reservas/controller/
  CancelacionReservaControllerIT`, `pagos/controller/PagosSoporte`, `administracion/controller/{CuentaAdminControllerIT,
  RevisionIdentidadControllerIT}` → **9 dobles**. Además usan el tipo: `CuentaServiceImplTest`, `CambioCorreoServiceTest`,
  `RecuperacionContrasenaServiceTest`, `RegistroServiceTest`, `RegistroArrendadorServiceTest`,
  `CuentaBancariaServiceTest`, `LiquidacionAdminServiceTest`, `AvisosPagoTest`, `AvisosCancelacionTest`, `WebhookPagoIT`,
  `PagoSimuladoIT`. Mover el puerto de paquete cambia imports en todos ellos (20 archivos con `import
  ...cuentas.service.{NotificadorCorreo|Aviso*|ComprobantePago}`, 71 imports).
- El último commit (`19a3c98`) indica que solo se corren las pruebas unitarias necesarias para la HU; conviene que
  `tasks.md` liste qué `*Test`/`*IT` corre cada tarea.

---

## Resumen para el spec

1. Sin correo real ni `comun/correo`: HU-25 crea el paquete, mueve (o envuelve) `NotificadorCorreo` y define el
   Adapter del proveedor (patrón `@ConditionalOnProperty` de `PasarelaPago`) y el Factory Method por evento. Falta
   dependencia (`spring-boot-starter-mail` u HTTP del proveedor) y propiedades.
2. Reserva sin fecha de inicio propia: el inicio sale de `reserva_bloque` (`ReservaConHorario.instanteDeInicio()`);
   arrendador por `CatalogoEspaciosService.arrendadoresDe`; correo por `CuentaService.correoDe`.
3. Un solo `@Scheduled` (expiración), con `@EnableScheduling` condicionado; sin tabla de notificaciones enviadas ni
   eventos de dominio. Migración nueva: `V7_3`. Falta `usuario.recordatorios_activos`.
4. Reutilizar `Clock`/`ZonaHoraria`, `DespuesDelCommit`, `AuditoriaService`, `ServicioExternoException`.
5. Front: tarjeta nueva en `Perfil.jsx` + función en `api/cuentas.js`; `Campo tipo="checkbox"` sirve; sin tests front.
6. Tests: Mockito + `RelojControlable` + dobles a mano; 9 dobles implementan el puerto completo.
