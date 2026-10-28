# Exploración HU-02 — Registro de arrendador

> Explorador (general-purpose) para el orquestador · 2026-10-27 · solo lectura del repo.
> Historia: `docs/historias/HU-02.md` (dueño Adriana Callupe, prueba Sebastian Garate,
> R1·S1, tamaño S, depende de HU-01 `done`). Trazas: RF-A-04…06, RNF-SEG-12/14/18,
> RNF-CUM-03, RN-01, RNF-USA-03 (`docs/requerimientos.md` l. 34-36, 157-163, 172, 194, 210).
> Patrón ★: ninguno (`docs/requerimientos.md` l. 240).

## Qué pide HU-02 (resumen)

- CA1: formulario con datos válidos + aceptación de privacidad → cuenta con rol
  **arrendador** y estado **"correo pendiente"**.
- CA2: RUC/DNI con formato inválido → error de validación específico.
- CA3: al verificar el correo e ingresar → "correo verificado" + aviso de que debe
  verificar su identidad para publicar.
- STRIDE: (E) rol asignado en backend, nunca del cliente; (I) facturación cifrada en
  tránsito y en reposo; (R) auditar creación con fecha, hora e IP.
- `docs/requerimientos.md` §8 (l. 284): HU-02 es la **primera** historia que necesita el
  componente transversal "Cifrado de campos sensibles en reposo" (RNF-SEG-14); lo
  reutilizan HU-05, 17 y 18. Según `docs/architecture.md` l. 54 va en `comun` (cifrado).

## Preguntas

1. Backend `cuentas`/`comun` de HU-01: qué hay y cómo se modelan rol y estado.
2. Migraciones Flyway vs `docs/modelo-datos.md`.
3. Patrones de test unitario e IT.
4. Frontend: kit, `src/api/`, páginas y rutas; cómo extender para el arrendador.
5. Decisiones de HU-01 (`specs/HU-01/`, `progress/history.md`) y reglas de `docs/requerimientos.md`.

## Archivos analizados

- `docs/historias/HU-02.md`, `docs/modelo-datos.md`, `docs/requerimientos.md`,
  `docs/architecture.md`, `docs/frontend.md`, `docs/conventions.md`
- `specs/HU-01/design.md` (y requirements/tasks por encima), `progress/history.md`,
  `progress/HU-02.md`, `feature_list.json`
- `2_Backend/src/main/java/pe/edu/ulima/espacioya/{cuentas,comun}/**` (todos)
- `2_Backend/src/main/resources/db/migration/V1_1__usuarios.sql`, `V1_2__auditoria.sql`,
  `application.properties`, `2_Backend/.env.example`, `2_Backend/pom.xml`
- `2_Backend/src/test/java/pe/edu/ulima/espacioya/**`
- `3_frontend/src/{App.jsx,main.jsx,api/*,components/ui/*,pages/cuentas/*}`, `3_frontend/package.json`

---

## 1. Backend reutilizable (paquete `pe.edu.ulima.espacioya`)

### `cuentas/domain/`
- `Rol.java` — enum `USUARIO, ARRENDADOR, ADMIN`. **`ARRENDADOR` ya existe** (también en
  el CHECK de BD).
- `EstadoUsuario.java` — enum `ACTIVA, SUSPENDIDA, ELIMINADA`. No hay valor de "correo
  pendiente": ese estado se modela con el booleano `usuario.correo_verificado`
  (`docs/architecture.md` l. 178-181).
- `Usuario.java` — entidad JPA `usuario`; campos `id, correo, hashContrasena, rol,
  correoVerificado, estado, versionPolitica, fechaConsentimiento`; constructor protegido,
  sin setters. Fábrica `Usuario.registrar(correoNormalizado, hash, version, ahora)`
  (l. 49-61) **fija `rol = Rol.USUARIO`** y `correoVerificado = true` (decisión S5 de
  HU-01, comentario l. 56). `normalizarCorreo` (l. 63-65) es estático y reutilizable.
  HU-02 necesitará otra fábrica (p. ej. `Usuario.registrarArrendador(...)`) o un parámetro
  de rol interno — nunca desde el DTO.
- `PoliticaContrasena.java` — `cumple(contrasena, correoNormalizado)` + constante
  `REQUISITOS`; reutilizable tal cual.

### `cuentas/service/`
- `RegistroService.java` — `registrar(RegistroUsuarioDTO, ip)` (l. 47-63): valida versión
  de política (→ `ReglaNegocioException("versionPolitica", …)`), contraseña
  (→ `ReglaNegocioException("contrasena", …)`), calcula hash **siempre** (anti-enumeración
  por tiempo), y en `TransactionTemplate` busca por correo: si existe audita
  `REGISTRO_CORREO_EXISTENTE` y no hace nada más (respuesta 202 idéntica); si no, guarda,
  audita `REGISTRO_USUARIO` con detalle `versionPolitica=…` y llama al notificador.
  Captura `DataIntegrityViolationException` por carrera en `uq_usuario_correo` (l. 59-62).
  Métodos privados (`registrarEnTransaccion`, `auditar`) atados a `RegistroUsuarioDTO` y a
  `Usuario.registrar` → para HU-02 hay que extraer el núcleo común o crear un
  `RegistroArrendadorService` que repita el flujo (incluido el anti-enumeración y la
  carrera). La creación del perfil del arrendador debe ir en la misma transacción.
- `NotificadorCorreo` (puerto, `notificarVerificacion(correo)`) +
  `NotificadorCorreoSimulado` (solo log INFO sin el correo). Reutilizable.
- `PoliticaPrivacidad` — lee `espacioya.privacidad.version-vigente` (=`2026-10`).

### `cuentas/dto/`
- `RegistroUsuarioDTO` (record con `@NotBlank @Email @Size(254)` correo, `@NotNull`
  contrasena, `@NotNull @AssertTrue` aceptaPrivacidad, `@NotBlank` versionPolitica; mensajes
  como constantes públicas; `toString()` enmascara correo y contraseña — patrón a imitar
  para no filtrar RUC/DNI ni facturación en logs).
- `MensajeDTO(mensaje)`, `VersionPoliticaDTO(version)`.

### `cuentas/controller/`
- `UsuarioController` — `POST /api/usuarios` → 202 `MensajeDTO`
  (`MENSAJE_REGISTRO_RECIBIDO`), IP con `IpCliente.de(peticion)`. Al no aceptar `rol` en el
  DTO, Jackson ignora campos extra (probado en IT: `rol`/`estado` enviados se ignoran).
- `PoliticaPrivacidadController` — `GET /api/politicas-privacidad/vigente`.
- `ReglasLimiteCuentas` (`@Configuration`) — bean `ReglaLimite("POST", "/api/usuarios",
  5, 1h)`. Un endpoint nuevo (p. ej. `POST /api/arrendadores`) necesita **su propio bean
  `ReglaLimite`**; ojo que la clave es `metodo + ruta + ip`, así que sería un contador
  separado del de `/api/usuarios` (decidir si deben compartir cupo: §9.7 dice "registros por IP").

### `cuentas/repository/`
- `UsuarioRepository extends JpaRepository<Usuario, Long>` con `findByCorreo`.

### `comun/`
- `error/`: `ReglaNegocioException(campo, mensaje)` → 400 `ErrorDTO(MENSAJE_VALIDACION,
  [ErrorCampoDTO(campo, mensaje)])`; `ManejadorGlobalErrores` (l. 21-58) maneja
  `MethodArgumentNotValidException` (errores por campo, sirve para CA2 con `@Pattern` o
  validador propio), cuerpo ilegible, y genérico 500. Para CA2 basta lanzar
  `ReglaNegocioException("numeroDocumento", "<mensaje específico>")` o usar Bean Validation.
- `auditoria/`: `AuditoriaService.registrar(EventoAuditoria(usuarioId, accion, entidad,
  entidadId, ip, detalle))`, fecha con `Clock` → cubre (R) de HU-02. Nueva acción sugerida
  `REGISTRO_ARRENDADOR`; **no** meter datos de facturación/documento en `detalle`.
- `limite/`: `LimitadorIntentos` (ventana deslizante en memoria), `ReglaLimite` (record,
  coincidencia exacta de ruta), `FiltroLimiteIntentos` (toma todos los beans `ReglaLimite`).
- `seguridad/ConfiguracionSeguridad.java`: CSRF deshabilitado (sin sesión hasta HU-03),
  `permitAll` solo para `GET /api/politicas-privacidad/vigente` y `POST /api/usuarios`
  (l. 31-34); **un endpoint nuevo de registro de arrendador debe agregarse aquí**. CORS con
  bean `corsConfigurationSource`; BCrypt(12).
- `configuracion/ConfiguracionReloj` (bean `Clock` UTC), `web/IpCliente` (`getRemoteAddr()`).
- **No existe** componente de cifrado (`comun/cifrado/`): ni clase, ni dependencia en
  `pom.xml` (solo starters web/validation/jpa/flyway/security/postgresql), ni variable de
  llave en `.env.example` / `application.properties`. HU-02 debe crearlo (p. ej.
  `AttributeConverter` JPA con AES-GCM de `javax.crypto`, llave por variable de entorno
  `ESPACIOYA_CIFRADO_LLAVE` o similar, documentada en `.env.example` sin valor).

## 2. Migraciones Flyway vs `docs/modelo-datos.md`

Existentes (`2_Backend/src/main/resources/db/migration/`):
- `V1_1__usuarios.sql`: tabla `usuario` (id identity, correo único y CHECK normalizado,
  hash_contrasena, rol con CHECK `('USUARIO','ARRENDADOR','ADMIN')`, correo_verificado
  `DEFAULT FALSE`, estado con CHECK `('ACTIVA','SUSPENDIDA','ELIMINADA')`, version_politica,
  fecha_consentimiento).
- `V1_2__auditoria.sql`: tabla `auditoria` (FK `usuario_id → usuario(id)`, índices por
  usuario y fecha).
- Siguiente número libre: `V1_3__…` (convención `docs/conventions.md`; no editar las ya
  aplicadas).

Comparación con `docs/modelo-datos.md` l. 13-14:
- `usuario` en el modelo también tiene **`nombre`, `telefono`, `foto_url`,
  `recordatorios_activos`**, que V1_1 no creó. "Datos de contacto" del arrendador (HU-02)
  probablemente necesita `nombre` y `telefono` → `ALTER TABLE usuario ADD COLUMN …`
  (nullable, porque los USUARIO de HU-01 no los tienen) en V1_3. `foto_url` y
  `recordatorios_activos` son de HU-20/HU-25: no tocarlos.
- **`perfil_arrendador`** no existe: `usuario_id` (PK/FK 1-1 a usuario, según ER l. 53),
  `tipo_documento` (DNI, RUC), `numero_documento` (cifrado), `datos_facturacion`
  (cifrados), `estado_identidad` (NO_INICIADA, EN_REVISION, VERIFICADA, RECHAZADA),
  `motivo_rechazo`. HU-02 la crea; nace con `estado_identidad = NO_INICIADA`. Columnas
  cifradas → tipo `TEXT`/`BYTEA` (el tamaño de texto cifrado no coincide con el claro),
  y por estar cifrado no se puede poner CHECK de formato en BD ni índice único útil sobre
  `numero_documento` (el cifrado AES-GCM con IV aleatorio no es determinístico).
- Tabla `token` (VERIFICACION) del modelo **no se creó** en HU-01 (S5): no hay
  verificación de correo real.
- `spring.jpa.hibernate.ddl-auto=validate`: las entidades nuevas deben coincidir exacto
  con la migración.

## 3. Patrones de test (`2_Backend/src/test/java/pe/edu/ulima/espacioya/`)

- Unitarios JUnit 5 + Mockito + AssertJ:
  - `cuentas/service/RegistroServiceTest.java`: `@ExtendWith(MockitoExtension.class)`,
    mocks de repo/encoder/notificador/auditoría, `TransactionTemplate` sobre un
    `PlatformTransactionManager` mockeado, `Clock.fixed`, `ReflectionTestUtils.setField`
    para el id; `ArgumentCaptor<EventoAuditoria>`.
  - `cuentas/domain/UsuarioTest.java`, `PoliticaContrasenaTest.java` (límites de reglas)
    → imitar para un validador de RUC/DNI (DNI 8 dígitos, RUC 11 dígitos y prefijo
    10/15/17/20 + dígito verificador módulo 11, si se decide).
  - `comun/limite/LimitadorIntentosTest.java` con `RelojControlable`.
  - `comun/error/ManejadorGlobalErroresTest.java` (MockMvc standalone).
  - `cuentas/service/NotificadorCorreoSimuladoTest.java` (`OutputCaptureExtension`:
    verificar que datos personales no van al log — útil para RUC/DNI/facturación).
- IT (failsafe, sufijo `*IT`, Testcontainers `postgres:17`):
  - `cuentas/controller/UsuarioControllerIT.java` (543 l.): `@SpringBootTest` +
    `@AutoConfigureMockMvc` + `@Import({TestcontainersConfiguration.class, Dobles.class})`;
    `Dobles` define `NotificadorFalso` `@Primary` (captura correos, puede fallar) y
    `RelojControlable` `@Primary` (l. 78-112). `@BeforeEach` borra `auditoria` y
    `usuario` con `JdbcTemplate` (l. 113-119) y asigna **una IP distinta por test**
    (`siguienteIp()`, l. 121-124) por el limitador singleton. Helpers `registrarDesde`,
    `usuario(correo)` con `queryForMap`, `auditoria(accion)`. `@DisplayName("HU-01 Rn: …")`
    para trazabilidad R→test. Incluye pruebas de campos extra ignorados (rol), carrera con
    `CountDownLatch`, 429, CORS.
  - `PoliticaPrivacidadControllerIT.java`, `EspacioYaApplicationIT.java`.
- `RelojControlable.java`, `TestcontainersConfiguration.java` en la raíz de test: reutilizables.

## 4. Frontend (`3_frontend/src/`)

- Kit `components/ui/`: `Boton` (variante, cargando→"Enviando…", deshabilitado, tipo),
  `Campo` (etiqueta, nombre, tipo, valor, onChange, error, ayuda; `checkbox` con valor
  booleano; **no soporta `select`**), `Alerta` (exito/atencion/error/info). Cada uno con
  `.module.css` y tokens de `styles/tokens.css`. **No existen** `EtiquetaEstado`,
  `Tarjeta`, `Modal` (`docs/frontend.md` l. 160-175: si falta uno se agrega al kit, no se
  improvisa). Para elegir tipo de documento DNI/RUC hace falta extender `Campo` con
  `tipo="select"` + `opciones` (o radio), anotándolo en "Pantallas" del design.
- `api/cliente.js`: `peticion(ruta, {metodo, cuerpo})`, lanza `{mensaje, errores}`.
- `api/cuentas.js`: `obtenerVersionPrivacidad()`, `registrarUsuario({...})` → agregar
  `registrarArrendador({...})` con la misma forma (sin campo `rol`).
- `pages/cuentas/Registro.jsx` (119 l.): estado local por campo, `erroresPorCampo`
  (l. 12-14) mapea `errores[]` a `{campo: mensaje}`, carga versión al montar, botón
  deshabilitado sin aceptación/versión, al 202 reemplaza el form por `Alerta exito`.
  `REQUISITOS_CONTRASENA` duplicado como constante local. Opciones: (a) página nueva
  `pages/cuentas/RegistroArrendador.jsx` en ruta `/registro/arrendador` (o
  `/registro-arrendador`), copiando la estructura y agregando campos de contacto
  (nombre, teléfono), tipo/número de documento y facturación; (b) extraer partes
  comunes (correo/contraseña/aceptación) a un componente compartido. `App.jsx` (l. 15-26)
  solo tiene rutas `/`, `/registro`, `/privacidad`; el inicio enlaza a `/registro`.
- No hay login, sesión ni panel (HU-03) → nada donde mostrar el aviso de CA3 "al ingresar".

## 5. Decisiones de HU-01 y reglas aplicables

- **S5 (HU-01, Jorge Quispe 2026-10-27):** sin correo real hasta HU-25; la cuenta nace con
  `correo_verificado = true`; no hay tabla `token` ni endpoint de verificación
  (`specs/HU-01/design.md` l. 11-15 y §13 alt. 5; `progress/history.md`).
  `docs/architecture.md` l. 171 ya dice: "Correo pendiente … Hoy no se llega a este
  estado: mientras el envío de correo sea simulado (HU-01, S5), la cuenta nace con el
  correo verificado".
- Respuesta 202 idéntica si el correo ya existe (anti-enumeración, RNF-SEG-13), hash
  siempre calculado, auditoría `REGISTRO_CORREO_EXISTENTE`.
- Rate limiting: 5 registros por IP por hora, contando todos los intentos (§9.7).
- Política de contraseña §9.5 en `PoliticaContrasena`.
- `comun/correo/` lo diseña HU-25 (no adelantar).
- Reglas transversales: rol nunca del cliente (RNF-SEG-18, `docs/architecture.md` l. 220);
  entidades no salen del backend (RNF-ARQ-03); identidad desde sesión (aplica a HU-02 solo
  en la parte de CA3); secretos en variables de entorno con `.env.example` (RNF-ARQ-07);
  consentimiento con fecha/hora/IP/versión (RNF-CUM-03 cita HU-02 CA1); mensajes de error
  claros para RUC/DNI (RNF-USA-03); nunca decir "verificado" a secas (RN-01).
- `docs/frontend.md` l. 134-135: `EtiquetaEstado` para identidad `NO_INICIADA` = neutro;
  cuenta "correo pendiente" = neutro.

---

## Huecos / decisiones abiertas

1. **CA1 "correo pendiente" vs S5.** Con el correo simulado la cuenta nace con
   `correo_verificado = true` y `docs/architecture.md` l. 171 dice que hoy no se llega a
   "correo pendiente". ¿HU-02 sigue S5 (nace verificado, como HU-01) o crea el arrendador
   con `correo_verificado = false`? Lo segundo dejaría al arrendador sin forma de
   verificar (no hay token ni endpoint, ver hueco 2). Decisión del dueño.
2. **CA3 depende de cosas que no existen:** verificación de correo (token/endpoint, HU-25 /
   S5) e "ingresar" (login y sesión, HU-03, `pending`, que además depende de HU-02).
   Decidir si CA3 se reduce a: el backend expone el estado (correo verificado +
   `estado_identidad = NO_INICIADA`) y el aviso "debes verificar tu identidad para
   publicar" se muestra en la confirmación del registro, dejando el aviso tras login para
   HU-03.
3. **Componente de cifrado (RNF-SEG-14) no existe y HU-02 es quien lo construye** (§8 de
   requerimientos). Decidir algoritmo (AES-256-GCM con `javax.crypto`, sin dependencia
   nueva, vs Jasypt/Bouncy Castle), forma (`AttributeConverter` JPA en `comun/cifrado/`),
   nombre de la variable de entorno de la llave, comportamiento si falta la llave
   (fallar al arrancar), y llave de prueba para las IT. Tipo de columna (`TEXT` base64 o
   `BYTEA`).
4. **Qué son los "datos de contacto y facturación".** El modelo solo dice
   `datos_facturacion (cifrados)` en un campo. Definir: ¿nombre y teléfono en `usuario`
   (columnas del modelo aún no creadas) y facturación = razón social + dirección fiscal?
   ¿Un solo blob JSON cifrado o columnas separadas? ¿Teléfono obligatorio y con qué formato?
5. **Reglas exactas de RUC/DNI (CA2).** DNI: 8 dígitos. RUC: 11 dígitos; ¿validar prefijo
   (10/15/17/20) y dígito verificador módulo 11? ¿Coherencia tipo↔número (si tipo=RUC, 11
   dígitos)? Mensajes exactos de error. Como el número va cifrado, la validación es solo en
   servicio/DTO, no en BD.
6. **Unicidad del documento.** ¿Se impide que dos arrendadores registren el mismo
   RUC/DNI? Con cifrado no determinístico no hay índice único posible; habría que guardar
   un hash (HMAC) aparte. Si no se exige, dejarlo explícito.
7. **Endpoint y rate limiting.** ¿`POST /api/arrendadores` (nuevo, requiere
   `permitAll` en `ConfiguracionSeguridad` y bean `ReglaLimite`) o extender
   `POST /api/usuarios`? ¿El límite de 5/h por IP es compartido entre ambos registros
   (§9.7 "registros por IP") o separado? `ReglaLimite.clave` incluye la ruta, así que
   compartir requiere ajustar `comun/limite`.
8. **Correo ya existente.** Mantener la respuesta 202 idéntica (anti-enumeración). ¿Qué
   pasa si el correo pertenece a un USUARIO existente que quiere ser arrendador?
   ¿Se convierte (no, sin sesión no se puede autenticar) o simplemente 202 sin cambios?
   Probablemente lo segundo; conversión queda para otra historia.
9. **Impacto en tests de HU-01:** `UsuarioControllerIT.preparar()` hace
   `DELETE FROM usuario`; si `perfil_arrendador` tiene FK sin `ON DELETE CASCADE`, la
   limpieza de esas IT fallará cuando existan filas de arrendador del IT nuevo (misma BD
   del contenedor compartido). Definir `ON DELETE CASCADE` o limpiar `perfil_arrendador`
   primero en ambos IT.
10. **Refactor de `RegistroService`.** Decidir si se extrae un núcleo común
    (validación de versión/contraseña, hash, carrera, auditoría) para no duplicar la lógica
    anti-enumeración, o se crea un servicio separado. Cualquier cambio debe mantener verdes
    las 29 unitarias + 26 IT de HU-01.
11. **Frontend:** `Campo` no soporta `select`/radio para tipo de documento → extender el
    kit y anotarlo en "Pantallas". Ruta de la nueva pantalla y enlace desde `/` o desde
    `/registro` ("¿Tienes un espacio? Regístrate como arrendador"). `EtiquetaEstado` no
    existe: si CA3 muestra estado, HU-02 sería la primera en construirlo.
12. **Cifrado en tránsito (I):** TLS es de despliegue (UAT); en local es HTTP. Dejar en el
    spec que se cubre por la infraestructura, no por código.
13. `docs/modelo-datos.md` no se actualiza salvo que el design cambie nombres/estados;
    si HU-02 añade p. ej. `hash_documento` o divide `datos_facturacion`, el PR debe
    actualizarlo (regla en l. 3-7 de ese archivo).
