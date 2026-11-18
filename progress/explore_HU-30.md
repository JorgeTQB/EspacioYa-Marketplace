# Exploración HU-30 · Límite de intentos compartido entre réplicas

Informe de solo lectura. Rutas relativas a la raíz del repo; `B/` = `2_Backend/src/main/java/pe/edu/ulima/espacioya/`,
`T/` = `2_Backend/src/test/java/pe/edu/ulima/espacioya/`.

---

## 1. API de `LimitadorIntentos` (`B/comun/limite/LimitadorIntentos.java`)

- `@Component` (l.13), clase concreta (no interfaz). Constructor `LimitadorIntentos(Clock reloj)` (l.19): **sí usa el
  `Clock` inyectado** (bean `Clock.systemUTC()` en `B/comun/configuracion/ConfiguracionReloj.java:12-13`; las *IT lo
  sustituyen por un `RelojControlable` `@Primary`, p. ej. `T/busqueda/controller/LimiteCatalogoIT.java:56-64`).
  → La versión en BD debe seguir tomando "ahora" de `Instant.now(reloj)`, **no** de `now()` de PostgreSQL, o las
  pruebas que avanzan el reloj se rompen.
- Estado: `ConcurrentMap<String, Deque<Instant>> intentosPorClave` (l.16): por clave guarda **cada instante** de
  intento (log de marcas de tiempo), no un contador.
- **Ventana deslizante** (sliding log), no fija: `purgar(deque, ahora - ventana)` (l.61-65) descarta las marcas con
  `instante <= ahora - ventana` (`!isAfter(limite)`); una marca exactamente en el borde ya no cuenta. La ventana se
  pasa en cada llamada (no se guarda con la clave).

| Método | Líneas | Semántica exacta |
| --- | --- | --- |
| `boolean intentar(clave, maximo, ventana)` | 23-37 | Atómico vía `compute`. Purga vencidas; acepta si `vigentes < maximo` y **solo entonces** añade `ahora`. Un intento rechazado **no se registra** (comentario l.29: si contara, quien insiste nunca sale del bloqueo). Si queda vacío borra la entrada. Devuelve `true` = aceptado. |
| `boolean superaLimite(clave, maximo, ventana)` | 39-47 | Solo consulta: `computeIfPresent`, purga y devuelve `vigentes >= maximo`. **No registra**. Clave inexistente → `false`. Borra la entrada si queda vacía. |
| `void registrar(clave)` | 49-55 | Añade `ahora` sin comprobar límite **ni purgar** (la purga ocurre en la siguiente `intentar`/`superaLimite`). No recibe ventana. |
| `void limpiar(clave)` | 57-59 | `remove(clave)`: borra todos los intentos de la clave. |
| `purgar` (privado, estático) | 61-65 | Saca del frente las marcas `<= limite`. |

No hay más métodos públicos. **Limpieza de entradas**: solo perezosa (al consultar una clave vacía se elimina); no
existe proceso programado, así que claves que nunca se vuelven a consultar quedan en memoria para siempre (fuga lenta
que CA4 resuelve en BD).

**Concurrencia**: atómica por clave dentro de un proceso gracias a `ConcurrentHashMap.compute`/`computeIfPresent`
(los `ArrayDeque` solo se tocan dentro de la lambda). Entre réplicas no hay nada compartido (el problema de HU-30).
Nota: el patrón de los servicios "`superaLimite` → acción → `registrar`" **no es atómico ni hoy** (check-then-act);
solo `intentar` es check-and-increment atómico.

**Transacciones**: el mapa no es transaccional, así que hoy un `registrar` hecho dentro de una transacción que luego
se revierte **se conserva**. Ver § 2 (MFA) — la versión en BD debe preservar eso (transacción propia,
`REQUIRES_NEW`).

## 2. Usos

`ReglaLimite.clave(ip)` = `grupo + "|" + ip` (`B/comun/limite/ReglaLimite.java:68-70`). Las demás claves son
`PREFIJO + valor`. Correos ya normalizados con `Usuario.normalizarCorreo`.

| Uso | Clave | Máx. | Ventana | Métodos | Limpia |
| --- | --- | --- | --- | --- | --- |
| `B/cuentas/service/InicioSesionService.java` | `login|<correo normalizado>` (l.42, 75) | 5 (`MAXIMO_FALLOS` l.37) | 15 min (l.38) | `superaLimite` l.76 → 429 `DemasiadosIntentosException` (+auditoría `LOGIN_BLOQUEADO`); fallo de credenciales → `registrar` l.86; éxito → `limpiar` l.93 (después de la transacción de `responder`, l.92) | Sí, al login correcto |
| `B/cuentas/service/VerificadorMfa.java` | `mfa|<usuarioId>` (l.35) | 10 (`MAXIMO_FALLOS_USUARIO` l.31) | 15 min (l.32) | `bloqueado()` → `superaLimite` l.52; `registrarFallo()` → `registrar` l.88; `limpiarFallos()` → `limpiar` l.93 | Sí |
| ↳ llamadores de VerificadorMfa | — | — | — | `B/cuentas/service/VerificacionMfaService.java`: `bloqueado` l.111 y `registrarFallo` l.122 **dentro** de `transacciones.execute` (l.90); `limpiarFallos` l.93 fuera. `B/cuentas/service/MfaService.java`: `bloqueado` l.125/150 y `registrarFallo` l.133/160 dentro de `transacciones.execute` (l.95/111), `limpiarFallos` l.98/113 fuera. Comentario DT-11 (l.87-88 / l.93): el fallo debe quedar guardado aunque se responda error. Hoy esas transacciones hacen commit (la excepción se lanza fuera), pero el registro en el limitador no depende de ello. | |
| `B/cuentas/service/ReautenticacionPerfil.java` | `perfil|<usuarioId>` (l.30, 73-75) | 5 (l.23) | 15 min (l.24) | `exigir()`: `superaLimite` l.53 → 429; contraseña incorrecta → `registrar` l.60; correcta → `limpiar` l.65. `registrarFallo()` → `registrar` l.70 | Sí |
| ↳ llamadores | — | — | — | `exigir` desde `CambioCorreoService` l.92, `FacturacionService` l.76, `DatosPersonalesService` l.99 (EXPORTACION) y l.127 (ELIMINACION); `registrarFallo` desde `FacturacionService` l.89 (documento en uso, D13). Cupo compartido entre operaciones (HU-22 DT-4, l.33). | |
| `B/pagos/service/CuentaBancariaService.java` | `cuenta-bancaria|<usuarioId>` (l.46, 87) | 5 (l.38) | 15 min (l.39) | `guardar()`: `superaLimite` l.88 → 429; contraseña incorrecta → `registrar` l.92 (fuera de transacción, comentario l.93); correcta → `limpiar` l.97 | Sí |
| `B/cuentas/service/RecuperacionContrasenaService.java` | `recuperacion|<correo normalizado>` (l.49) | 3 (`MAXIMO_SOLICITUDES` l.34) | 1 h (l.35) | `solicitar()`: `intentar` l.85 **antes** de buscar la cuenta (D3, S9); rechazado → auditoría `RECUPERACION_NO_EMITIDA` motivo `LIMITE_SUPERADO` y retorna sin error (no 429) | No limpia la suya; en `restablecer()` hace `limpiar("login|"+correo)` l.150 (S7) — **CA2 cruzado** |
| `B/cuentas/service/CambioCorreoService.java` | `cambio-correo|<usuarioId>` (l.53) | 3 (l.42) | 1 h (l.43) | `solicitar()`: `intentar` l.101 después de reautenticar (D18) → 429 | No |
| `B/comun/limite/FiltroLimiteIntentos.java` | `<grupo>|<ip>` (`IpCliente.de(solicitud)`) | según regla | según regla | `OncePerRequestFilter`; por cada `ReglaLimite` que aplica (método + `PathPattern`, l.41) y **una sola vez por grupo** (`gruposContados`, l.38-41) llama `intentar` l.42; rechazo → 429 JSON `ErrorDTO.MENSAJE_DEMASIADOS_INTENTOS` (l.50-55) | No |

Reglas por IP (beans `ReglaLimite` recogidos como `List<ReglaLimite>`, l.28):

| Bean | Archivo | Método/ruta | Grupo | Máx./ventana |
| --- | --- | --- | --- | --- |
| `reglaLimiteRegistro` | `B/cuentas/controller/ReglasLimiteCuentas.java:19-21` | POST `/api/usuarios` | `registro` | 5 / 1 h (constantes l.13-14) |
| `reglaLimiteRegistroArrendador` | ídem l.24-27 | POST `/api/arrendadores` | `registro` (cupo compartido, l.15) | 5 / 1 h |
| `reglaLimiteBusqueda` | `B/busqueda/controller/ReglasLimiteBusqueda.java:13-16` | GET `/api/espacios` | `catalogo` | `LimiteCatalogo` |
| `reglaLimiteFicha` / `Disponibilidad` / `Resenas` | `B/busqueda/controller/ReglasLimiteFicha.java:19-32` | GET `/api/espacios/{id}`, `/{id}/disponibilidad`, `/{id}/resenas` | `catalogo` | `LimiteCatalogo` |

`LimiteCatalogo` (`B/busqueda/controller/LimiteCatalogo.java:22-31`) lee `espacioya.limite.catalogo.maximo` y
`.ventana` y valida máximo ≥ 1 y ventana positiva. Propiedades en `2_Backend/src/main/resources/application.properties:91-92`:
`espacioya.limite.catalogo.maximo=${LIMITE_CATALOGO_MAXIMO:120}`, `espacioya.limite.catalogo.ventana=${LIMITE_CATALOGO_VENTANA:PT1M}`.
El catálogo es la clave de **mayor tráfico** (hasta 120 inserciones/min por IP con un log por intento): relevante para
elegir entre "una fila por intento" y "contador por ventana"; ojo, CA5 exige mismo comportamiento observable y los
tests actuales fijan semántica **deslizante** (ver § 3).

No hay otros usos de `LimitadorIntentos` en `src/main` (grep completo). El cupo de 3 reservas pendientes (HU-12) y el
de 10 reportes/24 h (HU-27, `docs/architecture.md:736-738`, `countByReportanteIdAndCreadoEnAfter`) **no** lo usan.

## 3. Pruebas que no deben romperse

**Unitarias que construyen `new LimitadorIntentos(reloj)` directamente** (si la clase pasa a necesitar repositorio/BD,
todas cambian de constructor; considerar mantener una implementación en memoria para pruebas o extraer interfaz):

- `T/comun/limite/LimitadorIntentosTest.java` (l.28): fija la semántica —5 aceptados y 6.º rechazado (l.33-40);
  rechazados no cuentan ni alargan (l.44-54); ventana **deslizante desde el intento más antiguo** (l.57-71: 1.º a t0,
  4 más a t0+10, a t0+59 rechaza, a t0+60 acepta uno y el siguiente rechaza); claves independientes (l.75-82);
  `superaLimite` al 5.º (l.86-95) y sin registrar (l.99-108); deja de contar a los 15 min exactos (l.112-122, borde
  `<=`); `limpiar` reinicia (l.126-138); claves de login independientes (l.142-149).
- `T/comun/limite/FiltroLimiteIntentosTest.java` (l.33): un intento por grupo (l.56-71), 429 con JSON exacto
  (l.75-87), rechazadas no cuentan con ventana deslizante a 30 s (l.91-110), grupos distintos (l.114-124), ruta sin
  regla (l.128-139).
- `T/cuentas/service/InicioSesionServiceTest.java` (l.94; además `mock(LimitadorIntentos.class)` l.420),
  `VerificadorMfaTest.java` (l.60), `VerificacionMfaServiceTest.java` (l.95), `MfaServiceTest.java` (l.91),
  `ReautenticacionPerfilTest.java` (l.50, `Clock.fixed`), `DatosPersonalesServiceTest.java` (l.125),
  `CambioCorreoServiceTest.java` (l.93), `RecuperacionContrasenaServiceTest.java` (l.85; l.274-288 combinan
  `superaLimite`/`intentar` con máximo 3 y 4; l.500-520 `login|` limpiado al restablecer),
  `T/pagos/service/CuentaBancariaServiceTest.java` (l.78).
- Relacionadas sin limitador directo: `T/comun/limite/ReglaLimiteTest.java`, `T/busqueda/controller/LimiteCatalogoTest.java`,
  `ReglasLimiteFichaTest.java`, `T/comun/error/ManejadorGlobalErroresTest.java`.

**Integración (*IT, Testcontainers `postgres:17`, `T/TestcontainersConfiguration.java`)** que ejercitan 429 de
límites: `T/busqueda/controller/LimiteCatalogoIT.java` (máximo bajado a 3/PT1M por propiedades l.34; IPs únicas
10.11.x.x l.71-75; reloj `@Primary`), `LimiteFichaIT.java`, `T/cuentas/controller/SesionControllerIT.java`,
`UsuarioControllerIT.java`, `ArrendadorControllerIT.java`, `RecuperacionContrasenaControllerIT.java`,
`CambioCorreoControllerIT.java`, `MfaControllerIT.java`, `MfaInicioSesionIT.java`, `DatosPersonalesControllerIT.java`,
`FacturacionControllerIT.java`, `T/pagos/controller/CuentaBancariaControllerIT.java` (grep de `429|isTooManyRequests`:
25 archivos). Ninguna IT inyecta ni resetea `LimitadorIntentos`: se aíslan usando IPs/correos únicos por prueba y
confían en que el estado vive lo que el contexto. Con BD el estado persiste igual dentro del contenedor del contexto;
como los relojes de prueba están en 2026-10/11, la purga programada y las consultas deben usar el `Clock` de Spring.
Los procesos programados están apagados en pruebas (`2_Backend/src/test/resources/config/application.properties:13-15`):
seguir el mismo patrón para el de purga (propiedad `...activa=false` en test).

## 4. Migraciones y convenciones de tablas

- Flyway. Carpeta `2_Backend/src/main/resources/db/migration/`. Esquema solo por Flyway, Hibernate `ddl-auto=validate`
  (`application.properties:10-13`); `spring.flyway.out-of-order=true` (l.15-18).
- Nombre `V<sprint>_<n>__<descripcion>.sql` (`docs/conventions.md:81-83`); el número sale del `sprint` de la historia
  en `feature_list.json` y es el siguiente `n` libre en `develop` (l.94-98); una migración no depende de otra de número
  mayor (l.100-103). No se usa H2 (l.111).
- Última existente: `V8_2__reportes.sql`. No hay ninguna `V9_*`. HU-30 es sprint 9 (`feature_list.json:617`) → le toca
  **`V9_1__...`**; HU-31 (también sprint 9, `feature_list.json:636`) podría competir por el número.
- Estilo SQL (`V8_2__reportes.sql`, `V1_4__sesion.sql`): comentario inicial `-- HU-XX: ...`; tablas en singular y
  snake_case; `id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`; fechas `TIMESTAMPTZ` con sufijo `_en`
  (`creado_en`, `expira_en`); restricciones nombradas `fk_<tabla>_<col>`, `ck_<tabla>_<regla>`, `uq_<tabla>_<col>`,
  índices `ix_<tabla>_<cols>`; hashes como `CHAR(64)` con `CHECK (col ~ '^[0-9a-f]{64}$')` (`V1_4__sesion.sql:7,12`).
- `docs/modelo-datos.md`: cada historia crea solo sus tablas con su migración (l.3-7) y, si cambia el modelo, lo
  actualiza en el mismo PR; tabla "Entidades principales" (l.11-41, grupo `Transversal` tiene `auditoria` l.41 — lugar
  natural para la nueva tabla) y "Restricciones clave" (l.43-80). Precedente de secretos: `token.hash_token`,
  `sesion.hash_token` = SHA-256 hex, nunca en claro (l.16-17); `codigo_respaldo.hash_codigo` = HMAC-SHA256 hex (l.19).
  Para la STRIDE (I) de HU-30, hay utilidades existentes: `TokenAleatorio.hash` (SHA-256) y `HuellaCampos`/`huella`
  (HMAC con `espacioya.cifrado.llave-huella`); un SHA-256 simple de `login|correo` es reversible por diccionario de
  correos, el HMAC no.

## 5. Procesos programados seguros con varias réplicas

- `B/reservas/service/ProgramacionExpiracion.java`: `@Configuration(proxyBeanMethods=false)` + `@EnableScheduling` +
  `@ConditionalOnProperty("espacioya.reservas.expiracion.activa"="true")` (l.12-15); `@Scheduled(fixedDelayString =
  "${espacioya.reservas.expiracion.intervalo}")` (l.23) delega en `ExpiracionReservas.expirarVencidas()`.
- `B/reservas/service/recordatorio/ProgramacionRecordatorios.java`: mismo patrón, con **su propio** `@EnableScheduling`
  porque el otro se apaga con su propiedad (l.9-16, 24).
- Propiedades: `application.properties:96-97` (expiración `true`, `PT1M`) y `:100-101` (recordatorios `true`, `PT5M`);
  en test `false`.
- Seguridad multi-réplica: **no** hay ShedLock ni líder; cada réplica corre el proceso y la idempotencia viene de la
  BD: `ExpiracionReservas.expirarVencidas()` (`B/reservas/service/ExpiracionReservas.java:50-63`) lee ids candidatos,
  luego **una transacción por fila** (`TransactionTemplate`) con `reservas.findParaActualizar(id)` (`@Lock
  PESSIMISTIC_WRITE`) y vuelve a comprobar la condición bajo bloqueo (l.74-78); errores por fila se registran y no
  detienen el resto. `RecordatoriosReserva.enviarPendientes()` igual (`.../recordatorio/RecordatoriosReserva.java:67-97`,
  relee bajo bloqueo R18).
- Para la purga de HU-30 basta un `DELETE ... WHERE <vencido>` en una sentencia (idempotente: si dos réplicas lo
  corren, la segunda borra 0 filas), o serializar con el bloqueo consultivo existente (§ 6).

## 6. Patrones de escritura atómica en PostgreSQL

- **No existe** ningún `INSERT ... ON CONFLICT` ni `JdbcTemplate` en `src/main` (grep `ON CONFLICT|nativeQuery|
  JdbcTemplate|SKIP LOCKED`). La única consulta nativa: `B/pagos/repository/LiquidacionRepository.java:30-32`,
  `pg_advisory_xact_lock(:clave)` con `@Transactional(propagation = MANDATORY)` y clave constante `CLAVE_GENERACION =
  190_019L` (l.24) — precedente de bloqueo consultivo.
- Unicidad por excepción: varios servicios capturan `DataIntegrityViolationException` y comprueban el nombre de la
  restricción (`ReservaServiceImpl.java:95,169`, `SesionesCheckout.java:64,73`, `WebhookPagoService.java:75,131`,
  `ReclamoService.java:105,237`, `ReporteService.java:75,134`, `PublicacionTransaccional.java:29,38`,
  `LiquidacionAdminService.java:97,212`).
- `@Modifying` JPQL: `TokenRepository.java:19-27` (UPDATE condicional que devuelve filas afectadas, `flushAutomatically
  = true, clearAutomatically = true`), `SesionRepository.java:16,20`, `DesafioMfaRepository.java:23-25` (DELETE),
  `CodigoRespaldoRepository.java:18`, `ReservaBloqueRepository.java:27`, `HorarioSemanalRepository.java:23`,
  `ReclamoRepository.java:52`, `MensajeReclamoRepository.java:20`, `ResenaRepository.java:44`.
- Bloqueo de fila: `@Lock(PESSIMISTIC_WRITE)` + `findParaActualizar` en `UsuarioRepository.java:23`,
  `ReservaRepository.java:47,52,62`, `MfaRepository.java:17`, `DesafioMfaRepository.java:18-20`,
  `EspacioRepository.java:27,32`, etc.
- Transacción propia independiente del llamador: `B/pagos/service/reembolso/RegistroReembolsos.java:35-42`
  (`new TransactionTemplate(ptm)` + `PROPAGATION_REQUIRES_NEW`, explicado en l.17-21). Útil porque `VerificadorMfa.
  registrarFallo` y `bloqueado` se llaman dentro de transacciones del llamador (§ 2) y `AuditoriaService.registrar`
  (`B/comun/auditoria/AuditoriaService.java:20-23`) es `@Transactional` REQUIRED.

## 7. Requerimientos

- `docs/requerimientos.md:162` **RNF-SEG-17 (D) Rate limiting**: registros por IP por hora; login 5 fallos / 15 min;
  recuperación 3 por correo / hora; búsqueda, ficha y filtros por IP (compartido); 3 pagos fallidos por reserva / 30
  min; 3 reservas pendientes por usuario; intentos de código MFA por sesión; 1 reclamo por reserva; 10 reportes por
  usuario al día; contraseña de perfil (cambio de correo y facturación) 5 fallos / 15 min por cuenta; cambio de correo
  3 por cuenta / hora.
- `docs/requerimientos.md:277` (matriz transversal): RNF-SEG-17 lo introducen HU-01/03/04 y lo extienden HU-10, 11,
  13, 17, 23, 26, 27; aclara que el cupo de reservas pendientes no usa `LimitadorIntentos` (cuenta eventos en ventana).
- `docs/architecture.md:861` MFA: 5 fallos por desafío (en `desafio_mfa`) y 10 por cuenta en 15 min (limitador);
  `docs/architecture.md:868-869` remite a RNF-SEG-17.
- No hay RNF que mencione réplicas ni Redis; HU-30 deja Redis/ElastiCache fuera del alcance y la regla WAF para la
  guía de despliegue (`docs/historias/HU-30.md:26-28`).

## Puntos de atención para el spec

1. Mantener la semántica deslizante y "rechazado no cuenta" que fijan `LimitadorIntentosTest` y
   `FiltroLimiteIntentosTest` (CA3/CA5); el borde de ventana es `instante <= ahora - ventana` → no cuenta.
2. `intentar` debe ser atómico entre réplicas (STRIDE S/D): p. ej. bloqueo por clave (`pg_advisory_xact_lock(hash)` o
   fila por clave con `SELECT ... FOR UPDATE` tras upsert) + conteo + inserción en una transacción.
3. Las escrituras del limitador deben ir en transacción propia (`REQUIRES_NEW`) para no depender del commit del
   llamador (MFA) ni alargar sus bloqueos de `usuario`.
4. "Ahora" desde el `Clock` inyectado; la purga programada borra con `ahora - ventana máxima` o guarda la expiración
   por fila (la ventana no se conoce en `registrar`, l.49-55: se necesita decidir cómo calcular el vencimiento —p. ej.
   guardar `expira_en` usando la ventana mayor conocida o cambiar firma, pero CA5 prohíbe cambiar los servicios).
5. Las 11 pruebas unitarias que hacen `new LimitadorIntentos(reloj)` y el `mock(LimitadorIntentos.class)` necesitan
   un plan (interfaz + implementación en memoria para unitarias, implementación JDBC/JPA en producción e *IT nueva
   con dos "réplicas" simuladas).
6. Migración `V9_1__...` (sprint 9), tabla en grupo Transversal de `docs/modelo-datos.md`, clave guardada como
   HMAC/SHA-256 hex `CHAR(64)`.
