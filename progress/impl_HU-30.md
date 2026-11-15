# HU-30 · Informe de implementación — Límite de intentos compartido entre réplicas

- **Fecha:** 2026-11-15 · **Agente:** implementer · **Rama:** `feature/HU-30-limitador-compartido`
- **Estado:** `in_progress` (pendiente de reviewer; no se marcó `done`). Sin commit, push ni PR.
- **Tasks:** T1–T14 de `specs/HU-30/tasks.md` completas.

## Archivos

Creados (`2_Backend/src/main/java/pe/edu/ulima/espacioya/comun/limite/`):

- `ConteoIntentos.java` — interfaz interna (mismas cuatro firmas).
- `ConteoIntentosMemoria.java` — el cuerpo anterior de `LimitadorIntentos`, sin cambios de lógica.
- `ConteoIntentosPostgres.java` — `@Component`; upsert atómico `INSERT … ON CONFLICT … DO UPDATE … WHERE … RETURNING`,
  `REQUIRES_NEW` en escrituras, huella HMAC, *ahora* del `Clock` truncado a µs, `RETENCION_REGISTRO` = 1 h,
  `CLAVE_BLOQUEO_LIMPIEZA` = 300 030, `eliminarVencidos()` con `pg_try_advisory_xact_lock`.
- `ConfiguracionLimiteIntentos.java` — bean `LimitadorIntentos` sobre `ConteoIntentosPostgres`.
- `ProgramacionLimpiezaIntentos.java` — `@EnableScheduling`, `@ConditionalOnProperty`, `fixedDelayString`.
- `2_Backend/src/main/resources/db/migration/V9_1__intento_limite.sql` — exactamente design § 10 (`V9_1` libre en
  `origin/develop`).

Creados (pruebas, `2_Backend/src/test/java/pe/edu/ulima/espacioya/comun/limite/`):

- `ContratoConteoIntentos.java` (abstracta), `ConteoIntentosMemoriaTest.java`, `ConteoIntentosPostgresTest.java`,
  `ProgramacionLimpiezaIntentosTest.java`, `ConteoIntentosPostgresIT.java`.

Modificados:

- `2_Backend/src/main/java/.../comun/limite/LimitadorIntentos.java` — sin `@Component`; delega en `ConteoIntentos`;
  constructores `(Clock)` (memoria) y `(ConteoIntentos)`. Firmas públicas iguales.
- `2_Backend/src/main/resources/application.properties` — `espacioya.limite.limpieza.activa` / `.intervalo`.
- `2_Backend/src/test/resources/config/application.properties` — `espacioya.limite.limpieza.activa=false`.
- `2_Backend/.env.example` — `LIMITE_LIMPIEZA_ACTIVA`, `LIMITE_LIMPIEZA_INTERVALO` (comentadas, sin secretos).
- `docs/modelo-datos.md` — fila `intento_limite` (Transversal) y fila de restricciones.
- `docs/architecture.md` — párrafo en (D): conteo compartido en PostgreSQL y nota del WAF.
- `specs/HU-30/tasks.md` (checkboxes), `progress/HU-30.md` (bitácora).

Sin cambios (T12, `git status` y `git diff origin/develop` vacío en `cuentas/`, `pagos/`, `busqueda/`): los siete
usuarios del limitador, `FiltroLimiteIntentos`, `ReglaLimite`, `ReglasLimite*`, `LimiteCatalogo` y **todas** las
pruebas existentes. No hay puertos nuevos entre módulos (no cambia esa tabla de `docs/architecture.md`).

## Trazabilidad

`CPIT` = `ConteoIntentosPostgresIT`; las pruebas de `ContratoConteoIntentos` corren dos veces: en
`ConteoIntentosMemoriaTest` (memoria) y en `CPIT` (PostgreSQL).

- R1 → `CPIT.elBeanCuentaEnLaTabla`
- R2 → `CPIT.fallosSumanEntreInstancias`; `ContratoConteoIntentos.superaLimiteAlQuintoSinRegistrar`
- R3 → `CPIT.intentarSumaEntreInstancias`
- R4 → `CPIT.limpiarEnUnaBorraEnTodas`; `ContratoConteoIntentos.limpiarReinicia`
- R5 → `CPIT.limpiarLoginCruzado`
- R6 → `ContratoConteoIntentos.ventanaDeslizanteDesdeElMasAntiguo`, `.bordeExactoNoCuentaEnSuperaLimite`,
  `.bordeExactoNoCuentaEnIntentar`
- R7 → `ContratoConteoIntentos.vencidosCuentanDesdeCero`
- R8 → `ContratoConteoIntentos.aceptaCincoYRechazaElSexto`, `.ventanaDeslizanteDesdeElMasAntiguo`;
  `CPIT.aceptarDescartaLosVencidos` (contenido del arreglo)
- R9 → `ContratoConteoIntentos.elRechazadoNoCuenta`, `.aceptaCincoYRechazaElSexto`
- R10 → `ConteoIntentosPostgresTest.maximoMenorQueUnoNoEscribe`
- R11 → `CPIT.instanteDelRelojDeLaAplicacion`
- R12 → `ContratoConteoIntentos.clavesIndependientes`
- R13 → `ConteoIntentosPostgresTest.ventanaMayorQueLaRetencionFalla`, `.ventanaIgualALaRetencionSeConsulta`
- R14 → `CPIT.registrarDescartaLoMasViejoQueLaRetencion`
- R15 → `ConteoIntentosMemoriaTest.constructorConRelojCuentaEnMemoria` y todo el contrato sobre
  `new LimitadorIntentos(reloj)`; existentes sin cambios y en verde: `LimitadorIntentosTest`,
  `FiltroLimiteIntentosTest`, `InicioSesionServiceTest`, `VerificadorMfaTest`, `VerificacionMfaServiceTest`,
  `MfaServiceTest`, `ReautenticacionPerfilTest`, `DatosPersonalesServiceTest`, `CambioCorreoServiceTest`,
  `RecuperacionContrasenaServiceTest`, `CuentaBancariaServiceTest`
- R16 → `CPIT.limiteDeRegistroSigueIgual` (429 con `ErrorDTO.MENSAJE_DEMASIADOS_INTENTOS` sumando dos instancias);
  existentes sin cambios y en verde sobre el conteo compartido: `SesionControllerIT`, `LimiteCatalogoIT`,
  `LimiteFichaIT`, `UsuarioControllerIT`, `ArrendadorControllerIT`, `RecuperacionContrasenaControllerIT`,
  `CambioCorreoControllerIT`, `MfaControllerIT`, `MfaInicioSesionIT`, `DatosPersonalesControllerIT`,
  `FacturacionControllerIT`, `CuentaBancariaControllerIT`
- R17 → `CPIT.intentarConcurrenteAceptaElMaximo`, `CPIT.intentarConcurrenteAceptaElRestante` (`@RepeatedTest(5)` cada
  una, 10 hilos por instancia liberados con `CountDownLatch`)
- R18 → `CPIT.registrarConcurrenteNoPierde`
- R19 → `CPIT.escriturasEnTransaccionPropia` (registrar, intentar y limpiar dentro de una transacción revertida)
- R20 → `ConteoIntentosPostgresTest.falloDeLaBasePropaga`
- R21 → `CPIT.claveComoHuella`
- R22 → `CPIT.intentarFijaExpiracion`
- R23 → `CPIT.registrarFijaExpiracion`
- R24 → `CPIT.limpiezaBorraSoloVencidas` (incluye la fila con `expira_en` = ahora)
- R25 → `CPIT.limpiezaSeOmiteConElBloqueoTomado`
- R26 → `ProgramacionLimpiezaIntentosTest.activaRegistraYEjecuta`, `.retrasoFijoDeLaPropiedad`
- R27 → `ProgramacionLimpiezaIntentosTest.apagadaNoRegistra`, `.sinPropiedadNoRegistra`

## Verificación

`./init.sh` completo (sin `--rapido`, Docker encendido), 2026-11-15: **[OK] Entorno listo**, código de salida 0.

- Unitarias (surefire, todas: cambió `application.properties`): **2213 pruebas, 0 fallas, 0 errores, 0 omitidas**.
- Integración (failsafe, todas las `*IT`): **1273 pruebas, 0 fallas, 0 errores, 0 omitidas** (incluye las 35 de
  `ConteoIntentosPostgresIT` y las IT existentes de R16).
- Validación de trazabilidad de `init.sh`: sin avisos para HU-30 (R1–R27 con `@DisplayName("HU-30 R<n>: …")`).
- Reglas de convenciones (sin `@Autowired`, Lombok, imports comodín, prints): OK. ESLint y `npm run build`: OK.
- Único aviso: faltan las llaves del backend en el entorno de shell (no afecta a las pruebas, que traen las suyas).

## Notas para el reviewer

- La R16 por IT nueva usa el límite de registro (5/h por IP) porque no necesita datos previos; los 429 de servicios
  (`DemasiadosIntentosException`) los siguen probando las IT existentes listadas arriba, que no se tocaron.
- `ConteoIntentosPostgresIT.limpiezaBorraSoloVencidas` compara lo borrado con el `count(*)` de vencidas tomado justo
  antes, porque la tabla no se vacía entre pruebas (design § 14).
- Sin tasks pendientes ni desvíos del spec.
