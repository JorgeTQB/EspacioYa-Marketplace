# HU-30 · Tasks — Límite de intentos compartido entre réplicas

> Diseño: [`design.md`](design.md). Requirements: [`requirements.md`](requirements.md). Todo en `2_Backend/`
> (paquete `pe.edu.ulima.espacioya.comun.limite`) salvo T11. Sin frontend. **No** se modifica ninguno de los siete
> usuarios del limitador ni ninguna prueba existente.

- [x] T1 — Crear `V9_1__intento_limite.sql` exactamente como design § 10 (comprobar antes en `develop` que `V9_1` sigue
  libre; si HU-31 la tomó, usar el siguiente `n`). Cubre: R21, R22, R23. Test: `ConteoIntentosPostgresIT`.
- [x] T2 — Crear la interfaz `ConteoIntentos` y `ConteoIntentosMemoria(Clock)` moviendo **sin cambios de lógica** el
  mapa, `intentar`, `superaLimite`, `registrar`, `limpiar` y `purgar` de `LimitadorIntentos`. Cubre: R6, R7, R8, R9,
  R12, R15. Test: `ConteoIntentosMemoriaTest`, `LimitadorIntentosTest` (existente).
- [x] T3 — Modificar `LimitadorIntentos`: quitar `@Component`, campo `ConteoIntentos conteo`, constructores
  `LimitadorIntentos(Clock)` (→ `ConteoIntentosMemoria`) y `LimitadorIntentos(ConteoIntentos)`, y los cuatro métodos
  delegando. Cubre: R15. Test: `LimitadorIntentosTest`, `FiltroLimiteIntentosTest` y las 9 unitarias de servicios
  (existentes, sin cambios, en verde).
- [x] T4 — Crear `ConteoIntentosPostgres` (`@Component`) con `RETENCION_REGISTRO`, `CLAVE_BLOQUEO_LIMPIEZA`,
  `NamedParameterJdbcTemplate` sobre el `DataSource`, `TransactionTemplate` `REQUIRES_NEW` para escrituras, *ahora*
  truncado a microsegundos desde el `Clock` y la clave siempre como `HuellaCampos.calcular` (design § 7). Implementar
  `intentar` (guarda `maximo < 1` y upsert con `WHERE` y `RETURNING`), `superaLimite` (guarda de ventana y `SELECT
  count(*)` sin transacción propia), `registrar` (upsert con retención), `limpiar` (`DELETE`) con el SQL de design § 8.
  Cubre: R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13, R14, R17, R18, R19, R20, R21, R22, R23.
  Test: `ConteoIntentosPostgresTest`, `ConteoIntentosPostgresIT`.
- [x] T5 — En `ConteoIntentosPostgres`, `eliminarVencidos()`: en una transacción `REQUIRES_NEW`,
  `pg_try_advisory_xact_lock(CLAVE_BLOQUEO_LIMPIEZA)`; si es `false` devuelve 0; si no, `DELETE … WHERE expira_en <=
  :ahora` y devuelve las filas borradas. Cubre: R24, R25. Test: `ConteoIntentosPostgresIT`.
- [x] T6 — Crear `ConfiguracionLimiteIntentos` con `@Bean LimitadorIntentos limitadorIntentos(ConteoIntentosPostgres)`.
  Cubre: R1, R16. Test: `ConteoIntentosPostgresIT` (R1) y las IT existentes de T10 (R16).
- [x] T7 — Crear `ProgramacionLimpiezaIntentos` (design § 7) y las propiedades de design § 11 en
  `application.properties`, `espacioya.limite.limpieza.activa=false` en `src/test/resources/config/application.properties`
  y las dos variables en `.env.example`. Cubre: R26, R27. Test: `ProgramacionLimpiezaIntentosTest`.
- [x] T8 — Crear (test) `ContratoConteoIntentos` (abstracta, método `crearLimitador(RelojControlable)`) con las pruebas
  de semántica de design § 14, y `ConteoIntentosMemoriaTest` que la extiende. `@DisplayName("HU-30 R<n>: …")` en cada
  una. Cubre: R2, R4, R6, R7, R8, R9, R12. Test: `ConteoIntentosMemoriaTest`.
- [x] T9 — Crear (test) `ConteoIntentosPostgresTest` (Mockito) para R10, R13 y R20, y `ProgramacionLimpiezaIntentosTest`
  (`ApplicationContextRunner`) para R26 y R27 (design § 14). Cubre: R10, R13, R20, R26, R27.
  Test: `ConteoIntentosPostgresTest`, `ProgramacionLimpiezaIntentosTest`.
- [x] T10 — Crear (test) `ConteoIntentosPostgresIT extends ContratoConteoIntentos` con `@Import(TestcontainersConfiguration.class)`,
  `RelojControlable` `@Primary`, una segunda instancia con su propio `HikariDataSource` (cerrado en `@AfterAll`) y las
  pruebas de design § 14: R1, R2, R3, R4, R5, R11, R14, R17 (`@RepeatedTest`, `CountDownLatch`, 10 hilos por
  instancia), R18, R19, R21, R22, R23, R24, R25. Correr además, sin cambiarlas, las IT existentes listadas en design § 14
  (R16). Cubre: R1–R8, R11, R12, R14, R16, R17, R18, R19, R21–R25. Test: `ConteoIntentosPostgresIT` y las IT existentes.
- [x] T11 — Documentación: `docs/modelo-datos.md` (fila `intento_limite` en Transversal y sus restricciones) y
  `docs/architecture.md` (línea en (D) sobre el conteo compartido y la nota del WAF), según design § 15. Sin test.
- [x] T12 — Revisar con `git diff` que no cambió ningún archivo de `InicioSesionService`, `VerificadorMfa`,
  `ReautenticacionPerfil`, `CuentaBancariaService`, `RecuperacionContrasenaService`, `CambioCorreoService`,
  `FiltroLimiteIntentos`, `ReglaLimite`, `ReglasLimite*`, `LimiteCatalogo` ni de las pruebas existentes. Cubre: R15,
  R16 (CA5). Test: las existentes en verde.
- [x] T13 — Documentar en `progress/impl_HU-30.md` el mapa `R<n>` → test (R1–R27), indicando para R15 y R16 las pruebas
  existentes que lo demuestran. Cubre: trazabilidad de R1–R27.
- [x] T14 — Verificación: `./init.sh` en verde (unitarias de las clases tocadas, todas las `*IT` con Docker y el build).
