# HU-02 — Tasks: Registro de arrendador

> Orden de ejecución. El implementer marca `[x]` al completar cada una. Firmas, SQL,
> mensajes y plan de pruebas están en `design.md`. Cada test lleva
> `@DisplayName("HU-02 R<n>: …")`. Antes de empezar, confirmar que el dueño aceptó **D1**
> (`requirements.md`) y las decisiones técnicas DT-1…DT-16 (`design.md` § 1), en
> especial DT-7 (cupo compartido) y DT-13 (tipo de documento en claro).
> Regla: las unitarias e IT de HU-01 no se modifican y deben seguir verdes en cada paso.

## Backend — `comun`

- [x] T1 — `comun/cifrado/CifradorCampos` (AES-256-GCM, IV aleatorio de 12 bytes, tag de 128 bits, formato `"v1:" + base64`, validación de llaves al crearse) y `comun/cifrado/HuellaCampos` (HMAC-SHA256 hex). Cubre: R34, R35, R36, R37, R38, R39. Tests: `CifradorCamposTest` (ida y vuelta; null → null; dos cifrados distintos; byte alterado → excepción; otra llave → excepción; llave vacía / base64 inválido / 16 bytes → `IllegalStateException` con `CIFRADO_LLAVE` en el mensaje y sin el valor; llaves iguales → excepción), `HuellaCamposTest` (determinista; 64 hex; otra llave → otra huella; distinta del SHA-256 sin llave; llave inválida → excepción con `CIFRADO_LLAVE_HUELLA`).
- [x] T2 — `comun/cifrado/ConvertidorCifrado` (`@Component @Converter`, `AttributeConverter<String,String>` que delega en `CifradorCampos`). Cubre: R34. Test: `ArrendadorControllerIT` (T10).
- [x] T3 — Configuración de llaves: `application.properties` (`espacioya.cifrado.llave=${CIFRADO_LLAVE:}`, `espacioya.cifrado.llave-huella=${CIFRADO_LLAVE_HUELLA:}`), `.env.example` (nombres sin valor + cómo generarlas) y `src/test/resources/config/application.properties` con dos llaves fijas de prueba distintas (comentario "solo pruebas"). Cubre: R38. Test: `EspacioYaApplicationIT`, `UsuarioControllerIT` y `PoliticaPrivacidadControllerIT` siguen verdes sin variables de entorno.
- [x] T4 — `comun/limite/ReglaLimite`: componente `grupo`, constructor de 4 argumentos (`grupo = metodo + " " + ruta`) y `clave(ip) = grupo + "|" + ip`. Cubre: R42. Test: `ReglaLimiteTest` (el constructor de 4 argumentos da `"POST /api/usuarios|1.2.3.4"`; dos reglas con el mismo grupo dan la misma clave; `aplicaA` sin cambios).
- [x] T5 — `comun/seguridad/ConfiguracionSeguridad`: `permitAll` para `POST /api/arrendadores`. Cubre: R1. Test: `ArrendadorControllerIT` (T10).

## Backend — `cuentas`

- [x] T6 — Migración `V1_3__perfil_arrendador.sql` (design § 9: columnas `nombre`, `telefono` con `ck_usuario_telefono`; tabla `perfil_arrendador` con FK `ON DELETE CASCADE`, `uq_perfil_arrendador_hash_documento` y los `CHECK`). Cubre: R2, R3, R30, R37. Test: `EspacioYaApplicationIT` (Flyway + `ddl-auto=validate`).
- [x] T7 — `cuentas/domain/`: `TipoDocumento`, `EstadoIdentidad`, `ValidadorDocumento` (normalizar, DNI, RUC longitud, prefijo, verificador módulo 11, mensajes), `Usuario` (M: `nombre`, `telefono`, `registrarArrendador`; `registrar` de HU-01 sin cambios), `PerfilArrendador` (`Persistable<Long>`, `@Convert` en los 3 campos cifrados, `crear` con `NO_INICIADA`). Cubre: R1, R2, R3, R17, R18, R19, R20, R21, R31. Tests: `ValidadorDocumentoTest` (casos de design § 13), `UsuarioTest` (nuevo test de `registrarArrendador`: ARRENDADOR/ACTIVA/verificado, nombre recortado), `PerfilArrendadorTest` (`crear` deja `NO_INICIADA`, `motivoRechazo` nulo, `isNew` true).
- [x] T8 — `cuentas/repository/PerfilArrendadorRepository.findByHashDocumento`. Cubre: R25, R26, R27. Test: `ArrendadorControllerIT` (T10).
- [x] T9 — `cuentas/service/RegistroArrendadorService.registrar` (flujo design § 6: validaciones antes de la BD, hash y huella siempre, correo antes que documento, una transacción con usuario + perfil + auditoría + notificador al final, reintento por `DataIntegrityViolationException`). Cubre: R1, R2, R4, R6, R9, R10, R17–R30, R32, R33, R44. Test: `RegistroArrendadorServiceTest` (Mockito, `Clock` fijo, `TransactionTemplate` que ejecuta el callback): un test por R6, R9, R10, R17 (vía validador), R22–R24 (correo existente: sin `save`, sin notificar, auditoría), R25–R27 (documento existente), R28 (sin consultar repositorios si versión, contraseña o documento fallan), R29/R30 (`DataIntegrityViolationException` → camino existente), R32, R33 (detalle solo `versionPolitica=…`), R44 (excepción del notificador se propaga).
- [x] T10 — `cuentas/dto/RegistroArrendadorDTO` (Bean Validation y mensajes de design § 4, `toString` enmascarado), `cuentas/dto/RegistroArrendadorRecibidoDTO`, `cuentas/controller/ArrendadorController` y `ReglasLimiteCuentas` (M: `GRUPO_REGISTRO`, regla de `/api/arrendadores`, la de `/api/usuarios` pasa al grupo). Cubre: R1–R5, R7, R8, R11–R16, R31, R40, R41, R42, R43. Tests:
  - `RegistroArrendadorDTOTest`: R41.
  - `ArrendadorControllerIT` (Testcontainers; `NotificadorFalso` y `RelojControlable` `@Primary`; `@BeforeEach` borra `auditoria`, `perfil_arrendador`, `usuario`; IP distinta por test en `10.2.x.x`): R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13, R14, R15, R16, R17–R21 (un 400 por mensaje), R22–R24 (correo de un `USUARIO` sigue `USUARIO`), R25–R27, R28, R29 y R30 (dos hilos con `CountDownLatch`), R31, R32, R33, R34 (columnas `v1:…`, sin claro, descifran al original), R37, R40, R42 (3 a `/api/usuarios` + 2 a `/api/arrendadores` → 6.º 429), R43, R44 (notificador falla → 500 y sin filas).

## Frontend

- [x] T11 — Kit: `Campo` admite `tipo="select"` con prop `opciones` (`[{ valor, etiqueta }]`), mismo estilo de borde, error y ayuda, solo tokens. Cubre: pantalla Registro de arrendador (tipo de documento, CA2).
- [x] T12 — `src/api/cuentas.js`: `registrarArrendador({...})` → `POST /api/arrendadores` (sin `rol`). Mover `REQUISITOS_CONTRASENA` a `pages/cuentas/textosCuentas.js` e importarlo en `Registro.jsx`. Cubre: pantalla Registro de arrendador (CA1).
- [x] T13 — `pages/cuentas/RegistroArrendador.jsx` (+ `.module.css`) en `/registro-arrendador` (design § 12). Cubre: CA1 (`Alerta` éxito con `mensaje`), CA3 ⚠ D1 (`Alerta` atención con `aviso` en la confirmación), CA2 (mensaje específico bajo "Número de documento"), errores por campo, correo o documento existente igual que CA1, 429/500/red.
- [x] T14 — `App.jsx`: ruta `/registro-arrendador` y enlace en `/`; `Registro.jsx`: enlace "¿Tienes un espacio? Regístrate como arrendador". Cubre: navegación a la pantalla Registro de arrendador.

## Docs y cierre

- [x] T15 — `docs/modelo-datos.md`: `perfil_arrendador` con `numero_documento (cifrado)`, `hash_documento (único, HMAC)`, `razon_social (cifrada)`, `direccion_fiscal (cifrada)` en lugar de `datos_facturacion`; fila `perfil_arrendador.hash_documento` único en "Restricciones clave". `docs/architecture.md` principio 7: agregar "`perfil_arrendador.hash_documento` único (HU-02)". Cubre: R37 (DT-15).
- [x] T16 — Documentar en `progress/impl_HU-02.md` el mapa `R<n>` → test (R1–R44, todos con al menos un test) y la nota de despliegue: UAT necesita `CIFRADO_LLAVE` y `CIFRADO_LLAVE_HUELLA` y HTTPS (DT-10).
- [x] T17 — Verificación: `./init.sh` (sin `--rapido`) en verde: `./mvnw verify` con todas las pruebas (incluidas las de HU-01 sin cambios), `npm run lint -- --max-warnings=0` y `npm run build` sin errores, y los chequeos de arquitectura (sin `@Autowired` en `src/main`, sin colores fuera de `tokens.css`, controladores sin repositorios, sin secretos trackeados, sin `System.out`).
