# Review — HU-18

**Veredicto:** APPROVED (con observaciones menores no bloqueantes)

`./init.sh` completo (sin `--rapido`, JDK 25.0.5 por PATH y JAVA_HOME, Docker activo) ejecutado por el revisor: exit 0.
Backend 1243 pruebas unitarias + 755 de integración sin fallos ni errores; ESLint y `npm run build` OK; todas las reglas de arquitectura del arnés OK.

## Trazabilidad requirements <-> tests
Todos los `@DisplayName("HU-18 R<n>: ...")` existen y comprueban resultados concretos (códigos HTTP, campos de DTO, fila en BD, detalle de auditoría, correo capturado).
- R1: [x] CuentaBancariaControllerIT (401 sin sesión/token inventado, sin tocar la tabla)
- R2: [x] CuentaBancariaServiceTest (consultar/guardar otro rol), CuentaBancariaControllerIT (USUARIO 403)
- R3: [x] ServiceTest (sin identidad, y identidad perdida bajo bloqueo), IT CA3
- R4: [x] ServiceTest (id de sesión), IT (solo la propia cuenta)
- R5: [x] ServiceTest, IT CA1
- R6: [x] GuardarCuentaBancariaDTOTest, IT
- R7: [x] ServiceTest, CuentaServiceImplTest.confirmarContrasena*, IT (auditoría con usuario e IP)
- R8: [x] ServiceTest (5 fallos, ventana vence), IT (6.ª = 429)
- R9: [x] ServiceTest, IT
- R10: [x] GuardarCuentaBancariaDTOTest (límites 10/20, 2/60, declaración), IT
- R11: [x] ServiceTest, CuentaServiceImplTest.razonSocialDe*, IT CA1 (titular = razón social)
- R12: [x] CuentaBancariaTest, ServiceTest, IT CA2 (+72 h exactas)
- R13: [x] ServiceTest (tras commit), IT CA2 (correo, banco, últimos 4, fecha)
- R14: [x] ServiceTest, IT CA1 (sin correo)
- R15: [x] ServiceTest (fallo de envío, rollback), IT (falloDelCorreoNoRevierte)
- R16: [x] CuentaBancariaTest, ServiceTest, IT
- R17: [x] CuentaBancariaTest, CuentaBancariaRepositoryIT, IT
- R18: [x] CuentaBancariaTest, ServiceTest, IT (Clock controlable, sin escritura)
- R19: [x] CuentaBancariaTest, DTOTest (solo enmascarado), IT (el cuerpo no contiene el número)
- R20: [x] CuentaBancariaTest (ConvertidorCifrado), RepositoryIT y IT (columna `v1:` y se descifra)
- R21: [x] IT (`Cache-Control: no-store` en GET y PUT); el controlador lo fija en ambas rutas
- R22: [x] DTOTest.toString, NotificadorCorreoSimuladoTest, ServiceTest/IT (detalle de auditoría sin número ni contraseña)
- R23: [x] ServiceTest, IT (usuario, IP, entidad, detalle `banco=..;cuenta=****1111`)
- R24: [x] ServiceTest, IT (anterior/nueva enmascaradas + retenerHasta)
- R25: [x] RepositoryIT (UNIQUE, CHECK, FK), ServiceTest (orden de bloqueo), IT dosPutSimultaneos (200/200, una fila, un solo correo)

## Trazabilidad con la historia
- CA1: [x] R5, R10, R11, R17, R18, R20
- CA2: [x] R6, R7, R12-R16, R18
- CA3: [x] R3
- (S/T) contraseña: [x] R6-R9 · (S/T) correo: [x] R13-R15 · (S/T) retención 72 h: [x] R12, R17, R18
- (I) cifrado: [x] R20 · (I) enmascarado: [x] R5, R19, R21, R22
- (R) auditoría: [x] R7, R23, R24
- Implementación verificada en código: orden DT-7 en `CuentaBancariaService.guardar` (rol -> identidad -> límite -> contraseña -> transacción con `bloquearCuenta` -> relectura de identidad -> `findParaActualizar`); aviso con `DespuesDelCommit`; el simulado de correo no registra correo, banco ni cuenta.

## Tasks completas
- T1-T19: [x] todas marcadas y cumplidas (T19 confirmado por el revisor).

## Seguridad
- Número cifrado en reposo (`@Convert(ConvertidorCifrado)`, `TEXT`, sin CHECK de formato justificado en la migración): OK.
- Número completo ausente de DTO de salida, logs, auditoría, correo y `toString` (`numero=***`): OK.
- Contraseña bcrypt vía `CuentaService.confirmarContrasena` (puerto de `cuentas`), límite 5/15 min con `LimitadorIntentos`, 429 genérico: OK.
- Retención 72 h solo en cambio; aviso por correo solo en cambio y tras commit: OK.
- Identidad solo de la sesión; rutas sin id: OK.

## Arquitectura / convenciones
- `pagos` usa `cuentas` solo por `CuentaService` y `NotificadorCorreo` (interfaces); sin controller->repository; inyección por constructor; DTO `record`; migración `V5_2__cuenta_bancaria.sql` nueva (no edita ninguna previa). Frontend: sin estilos en línea, sin colores fuera de tokens, llamadas vía `src/api/pagos.js`.
- Alcance: git status solo contiene HU-18 y sus dobles de prueba (por el nuevo método de la interfaz `NotificadorCorreo`) más docs/feature_list.json; nada fuera de alcance.

## Checkpoints
- C1: [x] · C2: [x] (una `in_progress` del dueño; `progress/HU-18.md` existe)
- C3: [x] · C4: [x] (Clock fijo, IT con Testcontainers, concurrencia con dos hilos)
- C5: n/a para el revisor / pendiente del dueño: el trabajo está en `develop`, no en `feature/HU-18-...`; crear la rama antes del commit; falta la entrada en `progress/history.md` y devolver `progress/HU-18.md` a plantilla al cerrar.
- C6: [x]

## Observaciones no bloqueantes
1. `progress/impl_HU-18.md` quedó desactualizado: la tabla "Verificación" dice que las IT y `./init.sh` no se ejecutaron y que lint/build no corrieron, mientras la sección "Tasks" dice verde. Ya está verde (confirmado); conviene alinear el texto.
2. R25 pide impedir `numero` vacío en BD: la migración solo tiene `NOT NULL` en `numero` (CHECK imposible sobre texto cifrado; documentado en el comentario del SQL). Aceptable.
3. `CuentaBancariaControllerIT` usa `java.util.ArrayList` totalmente calificado (estilo menor).
4. Las suposiciones P1-P9 de requirements.md se dan por aceptadas por el dueño al haber pasado a `in_progress`.
