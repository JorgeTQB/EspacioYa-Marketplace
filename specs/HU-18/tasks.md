# HU-18 — Tasks: Registro de cuenta bancaria del arrendador

> Orden de ejecución. Cada task de backend lista los `R<n>` que cubre y su test; las de frontend, la pantalla o el CA
> visible (se verifican con lint, build y la caja negra en UAT). Cada test lleva `@DisplayName("HU-18 R<n>: …")`.
> Antes de empezar, resolver con el dueño las preguntas abiertas P1–P9 de `requirements.md`.

## Base de datos y dominio

- [x] T1 — Crear `V5_2__cuenta_bancaria.sql` (revisar en `develop` el siguiente `n` libre del sprint 5) con
  `uq_cuenta_bancaria_arrendador`, FK a `perfil_arrendador` y los `CHECK`. Cubre: R17, R20, R25. Test: `CuentaBancariaRepositoryIT`.
- [x] T2 — Crear `EstadoCuentaBancaria` y `CuentaBancaria` (número con `ConvertidorCifrado`, `registrar`, `cambiar`,
  `esLaMisma`, `estaRetenida`, `estado`, `enmascarar`, `RETENCION` de 72 h). Cubre: R12, R16, R17, R18, R19, R20.
  Test: `CuentaBancariaTest`.
- [x] T3 — Crear `CuentaBancariaRepository` (`findByArrendadorId`, `findParaActualizar`). Cubre: R5, R25.
  Test: `CuentaBancariaRepositoryIT` (la columna guarda `v1:…` y no el número; segunda fila del mismo arrendador viola la restricción).

## Extensiones de `cuentas` (usadas por `pagos`)

- [x] T4 — Agregar `confirmarContrasena` y `razonSocialDe` a `CuentaService` y `CuentaServiceImpl` (bcrypt con
  `PasswordEncoder`; `razonSocialDe` devuelve solo la razón social, 404 sin perfil). Cubre: R7, R11.
  Test: `CuentaServiceImplTest`.
- [x] T5 — Crear `AvisoCambioCuentaBancaria`; agregar `notificarCambioCuentaBancaria` a `NotificadorCorreo` y a
  `NotificadorCorreoSimulado` (sin escribir correo, banco ni cuenta en el log); actualizar los dobles de prueba que
  implementan la interfaz. Cubre: R13, R22. Test: `NotificadorCorreoSimuladoTest`.

## Servicio y API (`pagos`)

- [x] T6 — Crear los DTO `GuardarCuentaBancariaDTO` (validación y `toString` sin `numero` ni `contrasena`),
  `CuentaBancariaDTO` y `MiCuentaBancariaDTO`. Cubre: R6, R10, R19, R22. Test: `GuardarCuentaBancariaDTOTest`
  (Bean Validation por campo y `toString`).
- [x] T7 — Crear `CuentaBancariaService.consultar`. Cubre: R2, R4, R5, R18, R19.
  Test: `CuentaBancariaServiceTest` (rol, sin cuenta, `RETENIDA` antes y `DISPONIBLE` después de 72 h con reloj controlable).
- [x] T8 — Crear `CuentaBancariaService.guardar`: rol, identidad, límite, contraseña y transacción con
  `bloquearCuenta` (orden de DT-7). Cubre: R2, R3, R7, R8, R9, R11, R12, R16, R17, R24, R25.
  Test: `CuentaBancariaServiceTest` (un test por R; R3 sin contar fallo; R8 sin llamar a `confirmarContrasena`; R16 sin retención).
- [x] T9 — Auditoría de registro, cambio y contraseña fallida, sin número completo ni contraseña. Cubre: R7, R22, R23, R24.
  Test: `CuentaBancariaServiceTest` (detalle de cada acción).
- [x] T10 — Aviso por correo con `DespuesDelCommit` solo en el cambio: no en el registro, no en el rechazo, no con
  rollback, y un fallo del envío no revierte. Cubre: R13, R14, R15. Test: `CuentaBancariaServiceTest`.
- [x] T11 — Crear `CuentaBancariaController` (`GET` y `PUT /api/arrendadores/actual/cuenta-bancaria`, `no-store`).
  Cubre: R1, R2, R4, R5, R21. Test: `CuentaBancariaControllerIT`.
- [x] T12 — Pruebas de integración de punta a punta: CA1 (registro y consulta enmascarada, columna cifrada), CA2
  (cambio con contraseña, correo capturado, `retener_hasta` +72 h, estado `RETENIDA`), CA3 (identidad no verificada,
  400 sin escritura), contraseña incorrecta ×5 → 429, otro rol 403, sin sesión 401, dos `PUT` simultáneos.
  Cubre: R1–R25. Test: `CuentaBancariaControllerIT`.

## Frontend

- [x] T13 — Agregar `obtenerCuentaBancaria` y `guardarCuentaBancaria` a `src/api/pagos.js`. Cubre: pantalla Cuenta bancaria (CA1, CA2, CA3).
- [x] T14 — Agregar `DISPONIBLE` ("Disponible para liquidaciones", éxito) y `RETENIDA` ("Retenida 72 h", atención) a
  `EtiquetaEstado.jsx`. Cubre: pantalla Cuenta bancaria (CA1, CA2).
- [x] T15 — Crear `pages/pagos/CuentaBancaria.jsx` + `CuentaBancaria.module.css` y los textos en `textosPagos.js`:
  consulta, formulario, aviso de retención, aviso de identidad no verificada y errores por campo. Cubre: pantalla
  Cuenta bancaria (CA1, CA2, CA3).
- [x] T16 — Registrar la ruta `/panel/cuenta-bancaria` (con `RutaProtegida`) en `App.jsx` y el enlace "Cuenta
  bancaria" para `ARRENDADOR` en `Panel.jsx`. Cubre: pantalla Cuenta bancaria.

## Documentación y cierre

- [x] T17 — Actualizar `docs/modelo-datos.md` (columnas y restricciones de `cuenta_bancaria`), `docs/architecture.md`
  (nuevos métodos de `CuentaService`, estado de la cuenta bancaria y lo que deja HU-18 a HU-19) y `docs/frontend.md`
  (estados `DISPONIBLE` y `RETENIDA`, ruta `/panel/cuenta-bancaria`). Cubre: R19, R25.
- [x] T18 — Registrar el mapa R↔test en `progress/impl_HU-18.md` y completar `.env.example` si hiciera falta
  (no se agregan variables). Cubre: R1–R25.
- [x] T19 — Verificación final: `./init.sh` en verde (lint, build del frontend, pruebas unitarias e integración).
  Cubre: R1–R25.
