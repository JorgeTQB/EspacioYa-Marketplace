# Review — HU-30

**Veredicto:** APPROVED

Rama `feature/HU-30-limitador-compartido` (sin commits propios sobre `develop`; todo el trabajo está sin commitear).
Revisado contra `docs/architecture.md`, `docs/conventions.md`, `docs/specs.md`, `docs/verification.md`,
`docs/modelo-datos.md`, `CHECKPOINTS.md`, `docs/historias/HU-30.md` y `specs/HU-30/`.

## Verificación ejecutada por el reviewer

`./init.sh` completo (sin `--rapido`, Docker encendido), 2026-11-15 16:54–17:17, **exit code 0**:
- Unitarias (surefire): `Tests run: 2213, Failures: 0, Errors: 0, Skipped: 0`.
- Integración (failsafe, todas las `*IT`): `Tests run: 1273, Failures: 0, Errors: 0, Skipped: 0`
  (`failsafe-summary.xml` regenerado a las 17:16:35).
- `ConteoIntentosPostgresIT`: 35/35 en verde (reporte regenerado a las 16:59:20 en esta corrida).
- `ConteoIntentosMemoriaTest` 10, `ConteoIntentosPostgresTest` 4, `ProgramacionLimpiezaIntentosTest` 4,
  `LimitadorIntentosTest` 9, `FiltroLimiteIntentosTest` 5: todas en verde.
- Reglas de convenciones, ESLint y `npm run build`: OK. Único aviso: llaves del backend ausentes en el shell
  (no afecta a las pruebas).

## Trazabilidad requirements ↔ tests

(CPIT = `ConteoIntentosPostgresIT`; el contrato `ContratoConteoIntentos` corre en memoria y en PostgreSQL)
- R1: [x] `CPIT.elBeanCuentaEnLaTabla` (bean del contexto deja `cardinality` 1 y 2 en `intento_limite`)
- R2: [x] `CPIT.fallosSumanEntreInstancias` (3 en A + 1 en B → false; +1 en B → true en las dos)
- R3: [x] `CPIT.intentarSumaEntreInstancias` (3 + 2 aceptados, 6.º rechazado en A y en B)
- R4: [x] `CPIT.limpiarEnUnaBorraEnTodas` (`filas = 0`, false en ambas); `ContratoConteoIntentos.limpiarReinicia`
- R5: [x] `CPIT.limpiarLoginCruzado` (`login|<correo>` registrado solo en B, limpiado en A)
- R6: [x] `ContratoConteoIntentos.bordeExactoNoCuentaEnSuperaLimite`, `.bordeExactoNoCuentaEnIntentar`,
  `.ventanaDeslizanteDesdeElMasAntiguo`
- R7: [x] `ContratoConteoIntentos.vencidosCuentanDesdeCero` (supera 1 sí, 2 no → un solo vigente)
- R8: [x] `CPIT.aceptarDescartaLosVencidos` (contenido real del arreglo: 2 instantes, el último = ahora)
- R9: [x] `ContratoConteoIntentos.elRechazadoNoCuenta`, `.aceptaCincoYRechazaElSexto`
- R10: [x] `ConteoIntentosPostgresTest.maximoMenorQueUnoNoEscribe` (0 y -1, sin `getConnection` ni transacción)
- R11: [x] `CPIT.instanteDelRelojDeLaAplicacion` (instante y `expira_en` = reloj de prueba 2026-10)
- R12: [x] `ContratoConteoIntentos.clavesIndependientes`
- R13: [x] `ConteoIntentosPostgresTest.ventanaMayorQueLaRetencionFalla`, `.ventanaIgualALaRetencionSeConsulta`
- R14: [x] `CPIT.registrarDescartaLoMasViejoQueLaRetencion` (borde exacto de 1 h descartado)
- R15: [x] `ConteoIntentosMemoriaTest.constructorConRelojCuentaEnMemoria` + existentes sin cambios en verde
  (`LimitadorIntentosTest`, `FiltroLimiteIntentosTest` y las 9 unitarias de servicios)
- R16: [x] `CPIT.limiteDeRegistroSigueIgual` (429 + `ErrorDTO.MENSAJE_DEMASIADOS_INTENTOS` sumando instancias)
  + IT existentes sin cambios en verde (`SesionControllerIT`, `LimiteCatalogoIT`, `LimiteFichaIT`, `MfaInicioSesionIT`,
  `CambioCorreoControllerIT`, `RecuperacionContrasenaControllerIT`, `CuentaBancariaControllerIT`, etc.)
- R17: [x] `CPIT.intentarConcurrenteAceptaElMaximo`, `.intentarConcurrenteAceptaElRestante` (RepeatedTest 5 veces,
  20 hilos liberados por `CountDownLatch`, dos pools distintos; exactamente 5 / 3 y `cardinality = 5`)
- R18: [x] `CPIT.registrarConcurrenteNoPierde` (`cardinality = 20`)
- R19: [x] `CPIT.escriturasEnTransaccionPropia` (registrar, intentar y limpiar persisten tras `setRollbackOnly`)
- R20: [x] `ConteoIntentosPostgresTest.falloDeLaBasePropaga` (`DataAccessException`, el resultado nunca se fija)
- R21: [x] `CPIT.claveComoHuella` (`clave_hash = huella.calcular`, patrón hex de 64, `row_to_json` sin correo ni IP)
- R22: [x] `CPIT.intentarFijaExpiracion` (sube y nunca retrocede)
- R23: [x] `CPIT.registrarFijaExpiracion` (ahora + 1 h; no recorta un `expira_en` mayor)
- R24: [x] `CPIT.limpiezaBorraSoloVencidas` (incluye la fila con `expira_en = ahora`)
- R25: [x] `CPIT.limpiezaSeOmiteConElBloqueoTomado` (otra conexión con `pg_advisory_xact_lock(300030)` → 0 y no borra)
- R26: [x] `ProgramacionLimpiezaIntentosTest.activaRegistraYEjecuta`, `.retrasoFijoDeLaPropiedad`
- R27: [x] `ProgramacionLimpiezaIntentosTest.apagadaNoRegistra`, `.sinPropiedadNoRegistra`

Todos con `@DisplayName("HU-30 R<n>: …")` y aserciones sobre resultados concretos.

## Trazabilidad con la historia

- CA1: [x] R1, R2, R3, R17
- CA2: [x] R4, R5
- CA3: [x] R6, R7, R8, R11
- CA4: [x] R24, R25, R26, R27
- CA5: [x] R9, R10, R12, R13, R14, R15, R16, R19 — `git diff develop --stat` solo toca `LimitadorIntentos.java`,
  propiedades, `.env.example`, `docs/` y `feature_list.json`. `InicioSesionService`, `VerificadorMfa`,
  `ReautenticacionPerfil`, `CuentaBancariaService`, `RecuperacionContrasenaService`, `CambioCorreoService`,
  `FiltroLimiteIntentos`, `ReglaLimite`, las clases ReglasLimite, `LimiteCatalogo` y todas las pruebas existentes
  quedan **sin cambios**. `ConteoIntentosMemoria` es el cuerpo anterior de `LimitadorIntentos` movido sin cambios de
  lógica. Todas las ventanas usadas con `superaLimite` son de 15 min (menores que `RETENCION_REGISTRO`), así que R13
  no altera a ningún llamador.
- (S/D) conteo atómico: [x] R17, R18, R20 — `ConteoIntentosPostgres.java` líneas 36-49: un único
  `INSERT … ON CONFLICT (clave_hash) DO UPDATE … WHERE (count vigentes) < :maximo RETURNING`. PostgreSQL bloquea la
  fila en conflicto y, en READ COMMITTED, reevalúa SET y WHERE sobre la versión confirmada; dos inserciones
  simultáneas de una clave nueva las serializa la llave primaria. Sin lecturas previas en Java. Fallo cerrado: ninguna
  excepción se captura (`intentar` devuelve true solo si hay fila devuelta; `FiltroLimiteIntentos` no captura).
- (I) claves no legibles: [x] R21–R24 — la clave enviada a SQL es siempre `huella.calcular(clave)` (HMAC-SHA256 con
  `HexFormat.of()`, minúsculas, coherente con `ck_intento_limite_clave_hash`); ninguna columna guarda la clave en claro
  ni se registra en logs; las filas expiran y se borran.
- (D) WAF: [x] R16 + nota de despliegue en `design.md` § 12 y en `docs/architecture.md` (D); sin código.

## Tasks completas

- T1–T14: [x] todas marcadas en `specs/HU-30/tasks.md` y verificadas contra el código (V9_1 libre en
  `origin/develop`, cuya última migración es `V8_2__reportes.sql`).

## Revisión por archivo

- `V9_1__intento_limite.sql`: idéntica a design § 10; restricciones `pk_intento_limite`,
  `ck_intento_limite_clave_hash`, `ck_intento_limite_instantes`; coherente con las filas nuevas de
  `docs/modelo-datos.md` (Transversal y Restricciones clave).
- `ConteoIntentosPostgres.java`: inyección por constructor; REQUIRES_NEW solo en escrituras; `superaLimite` sin
  transacción propia; ahora del `Clock` truncado a microsegundos; `eliminarVencidos` toma `pg_try_advisory_xact_lock`
  y hace el DELETE en la misma transacción (el bloqueo se suelta al confirmar). Clave 300030 distinta de 190019.
- `LimitadorIntentos.java`: sin `@Component`, dos constructores públicos, firmas idénticas.
- `ConfiguracionLimiteIntentos.java` y `ProgramacionLimpiezaIntentos.java`: según design § 7; mismo patrón que
  `ProgramacionExpiracion`; apagado en `src/test/resources/config/application.properties`.
- Sin líneas de más de 120 caracteres en el paquete `comun.limite` (main y test); sin `@Autowired` en `src/main`, sin
  Lombok, sin imports con comodín.
- Historia sin patrón ★; sin frontend; sin endpoints nuevos; sin puertos nuevos entre módulos.

## Checkpoints

- C1: [x] todos los controles automáticos en OK; `./init.sh` completo con exit 0.
- C2: [x] una sola `in_progress` del dueño; `progress/HU-30.md` existe, anota la aprobación del spec por Jorge Quispe
  (2026-11-15) y describe solo la sesión activa. Ítem de historias done con tests: n/a (sigue in_progress).
- C3: [x] paquetes y capas correctos, sin dependencias nuevas entre módulos, esquema solo por Flyway (`V9_1`; ninguna
  migración de `develop` editada), sin secretos; fuera del alcance respetado (sin Redis, sin cambiar máximos ni
  ventanas, WAF solo como nota). DTO, identidad y Adapter: n/a (sin API ni integración externa). Frontend: n/a.
- C4: [x] nombres Test / IT, IT con `@Import(TestcontainersConfiguration.class)`; PostgreSQL real; plazos con
  `RelojControlable` y `Clock.fixed`, sin esperas; concurrencia lanzada a la vez; aserciones concretas; init completo
  con tests y todos verdes; lint y build OK.
- C5: [x] sin archivos sospechosos sin trackear; `progress/history.md` tiene la entrada de la sesión 2026-11-15;
  rama `feature/HU-30-limitador-compartido`; estado `in_progress` correcto. Plantilla de progress: n/a hasta el cierre.
- C6: [x] `specs/HU-30/` completo; EARS y tabla de trazabilidad cubren CA1–CA5 y S/D, I, D; `design.md` con
  diagramas de casos de uso, clases y secuencia, migración, sección de endpoints (ninguno) y alternativas descartadas;
  cada R con test documentado en `progress/impl_HU-30.md`; no resuelve pendientes de §9 sin el dueño (implementa
  RNF-SEG-17 sin cambiar cifras).

## Observaciones (no bloquean)

1. `V9_1` puede chocar con HU-31 (mismo sprint) al unir: si HU-31 llega antes a `develop`, renombrar antes del merge
   (ya previsto en design § 10).
2. Riesgo residual aceptado en design § 16.1: el patrón superaLimite, validar, registrar de los servicios no es
   atómico (tampoco antes); CA5 impide cerrarlo aquí. Candidato a deuda técnica para una historia futura.
3. `ProgramacionLimpiezaIntentosTest.retrasoFijoDeLaPropiedad` comprueba la anotación por reflexión; el
   comportamiento real lo cubre `activaRegistraYEjecuta`. Suficiente en conjunto.
4. Al cerrar: commit en la rama, `progress/HU-30.md` a la plantilla y `feature_list.json` a `done` (lo hace el dueño
   con el orquestador, no el reviewer).

## Cambios requeridos

Ninguno.
