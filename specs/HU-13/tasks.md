# HU-13 — Tasks: Pago con tarjeta

> Orden de ejecución. El implementer marca `[x]` al completar cada una. Firmas, SQL, mensajes y plan de pruebas están en
> `design.md`. Cada test lleva `@DisplayName("HU-13 R<n>: …")`. Antes de empezar, confirmar que el dueño aceptó los
> supuestos **S1–S14** (`requirements.md`); D1–D7 y H1–H6 ya son suyos. Regla: las unitarias e IT de las demás historias
> siguen verdes en cada paso; solo cambian los tests de T3 y T5. Antes de crear la migración, actualizar `develop` y usar
> el siguiente `V4_n` libre (hoy `V4_1`).
>
> **Adenda 2026-10-30:** T25–T43, al final, sobre D7 revisada, D8, D9 y DEF-01 a DEF-04. **Empiezan cuando el dueño apruebe la
> adenda y acepte S15–S20.** T1–T22 y T24 ya están hechas y nada se renumera. T23 está diferida y **no se ha ejecutado**.

## Backend — base

- [x] T1 — Migración `V4_1__pago.sql` (design § 8: `pago`, `checkout_pago`, `CHECK`, `uq_pago_id_transaccion`, `uq_pago_reserva_pagado`, `uq_checkout_pago_reserva`, índice). Cubre: R45, R46, R47. Test: `EspacioYaApplicationIT` (Flyway + `ddl-auto=validate` tras T2); R45–R47 con `INSERT` por `JdbcTemplate` en `WebhookPagoIT` (T15).
- [x] T2 — `pagos/domain/`: `EstadoPago`, `MotivoNoDisponible`, `Pago`, `CheckoutPago`, `ReglasPago`; `pagos/repository/`: `PagoRepository`, `CheckoutPagoRepository`. Cubre: R10–R13, R19, R30, R33, R34, R39. Test: `ReglasPagoTest` (orden de R18; `expira_en − 61 s`, `− 60 s`; 2 y 3 rechazos; `241.5` frente a `241.50`; otro monto; `USD`; no vigente).
- [x] T3 — `comun`: `transaccion/DespuesDelCommit` (mover el helper de `CuentaServiceImpl`, que pasa a usarlo sin cambiar de conducta), `error/ServicioExternoException`, `ErrorDTO.MENSAJE_SERVICIO_EXTERNO`, su manejador en `ManejadorGlobalErrores`, y `permitAll` de `POST /api/pagos/webhook` en `ConfiguracionSeguridad`. Cubre: R17, R22, R27, R51. Test: `DespuesDelCommitTest` (corre tras el commit; no corre con rollback; atrapa el fallo), `ManejadorGlobalErroresTest` (M: 503 sin detalle), y `CuentaServiceImplTest` y las IT de HU-06 y HU-09 siguen verdes.

## Backend — módulos existentes

- [x] T4 — `espacios`: `EspacioBloqueado` (N), `DisponibilidadService.bloquearEspacio` e `Impl` (`MANDATORY`, `findParaReservar`, sin filtros). `reservas`: `Reserva.confirmar(Instant)`, `ReservaRepository.findEspacioIdById`. Cubre: R31, R35, R36, R38. Test: `ReservaTest` (M: `confirmar` de vigente; de vencida, `CONFIRMADA` o `EXPIRADA` lanza), `DisponibilidadServiceImplTest` (M: `PAUSADO` y arrendador no activo no filtran).
- [x] T5 — `cuentas`: `CuentaService.correoDe` + `Impl`; `ComprobantePago`, `AvisoReservaPagada` (N); `NotificadorCorreo.notificarPagoConfirmado` y `notificarReservaPagada` en la interfaz, en `NotificadorCorreoSimulado` (sin escribir datos en el log) y en los cinco dobles (design § 3). Cubre: R48, R49. Test: `CuentaServiceImplTest` (M: devuelve el correo; inexistente → 404); las IT de los cinco dobles siguen verdes.

## Backend — pasarela (★ Adapter)

- [x] T6 — `pagos/service/pasarela/`: `PasarelaPago`, `SolicitudCheckout`, `CheckoutCreado`, `NotificacionRecibida`, `PagoConsultado`, `ResultadoPasarela`, `FirmaInvalidaException`. Sin tipos del proveedor en las firmas. Cubre: R26, R55. Test: en T7 y T8.
- [x] T7 — `MercadoPagoAdapter` (`RestClient` con timeouts de DT-15; HMAC con `MessageDigest.isEqual`; `@ConditionalOnProperty`; falla al crear el bean sin llave, secreto o URL de notificación). **Solo traduce:** ninguna regla de negocio. Propiedades de design § 3 en `application.properties` y `src/test/resources/config/application.properties` (`simulada`, secreto de prueba con comentario "SOLO PARA PRUEBAS"), y variables sin valores en `2_Backend/.env.example`. Cubre: R2, R4, R5, R17, R23–R28, R54, R55, R57, R58. Test: `MercadoPagoAdapterTest` (doble `com.sun.net.httpserver`: cuerpo exacto, `-05:00`, `back_urls` solo con HTTPS, `init_point` y no `sandbox_init_point`; 500, 404 y tiempo agotado → `ServicioExternoException`; vector de design § 11; `data.id`, `data_id`, `x-signature` malformada; `approved`, `rejected` y otros; últimos 4 ausentes).
- [x] T8 — `PasarelaSimulada` (`@ConditionalOnProperty`, `log.warn` al arrancar, montos en memoria, comparación en tiempo constante). Cubre: R56. Test: `PasarelaSimuladaTest`, `SeleccionPasarelaTest` (`ApplicationContextRunner`: `mercadopago`, `simulada`, vacío, otro valor, llaves faltantes; cubre R55–R58).

## Backend — `reservas` y cobro

- [x] T9 — `pagos/service/puertos/` (`ReservasPorCobrar`, `ReservaPorCobrar`, `ReservaBloqueada`) y `reservas/service/ReservasPorCobrarImpl` (design § 6, `MANDATORY` en `bloquear` y `confirmar`, auditoría `RESERVA_CONFIRMADA`). Cubre: R31, R32, R36, R38, R53. Test: `ReservasPorCobrarImplTest` (Mockito, `InOrder`: proyección → `bloquearEspacio` → `findParaActualizar` → reloj; `deUsuario` con espacio no visible; detalle exacto de auditoría).

## Backend — inicio del pago (★ Facade)

- [x] T10 — `EvaluacionCobro` y `SesionesCheckout` (`TransactionTemplate`, `catch` de `uq_checkout_pago_reserva`, cerrar). Cubre: R2, R4, R6, R7, R10–R15, R17, R18. Test: `EvaluacionCobroTest` (orden completo de R18; sin pasarela al rechazar), `SesionesCheckoutTest` (reutiliza; `venceEn`; relee al perder la carrera; otra violación se relanza).
- [x] T11 — `CheckoutFacade` (`iniciar`, `estado`, auditoría `CHECKOUT_INICIADO`). **Coordina, sin reglas.** Cubre: R1, R8, R9, R19, R20, R21. Test: `CheckoutFacadeTest` (`InOrder` de los colaboradores; sin `ReservasPorCobrar.confirmar`; detalle exacto).
- [x] T12 — `pagos/dto/`, `PagoController` (`POST` y `GET /api/reservas/{id}/pago`, id como texto, `IpCliente.de`, `@AuthenticationPrincipal`). Cubre: R1, R3, R15, R16, R19. Test: T14.

## Backend — webhook

- [x] T13 — `WebhookPagoService` (flujo de design § 6, sin `@Transactional`, `TransactionTemplate`, `catch` de `uq_pago_id_transaccion`), `AvisosPago` y `WebhookPagoController` (no lee el cuerpo; firma inválida → `log.warn` y 401 vacío). Cubre: R23–R35, R39–R44, R48–R52, R54. Test: `WebhookPagoServiceTest` (Mockito: consulta antes de `bloquear`; sin `confirmar` en tardío, distinto ni rechazado; cerrar solo con 3; violación por nombre → fin; otra se relanza), `AvisosPagoTest` (lee correos dentro de la transacción; un fallo no impide el otro).

## Backend — integración

- [x] T14 — `PagoControllerIT` (design § 11, `simulada`, `RelojControlable`, limpia `pago` y luego `reserva`). Cubre: R1, R3, R6, R8–R21.
- [x] T15 — `StubMercadoPago` (soporte de prueba) y `WebhookPagoIT` (`MercadoPagoAdapter` real y HMAC calculado en la prueba). Incluye firma mala, cuerpo que miente, formatos `topic=…`, `data.id` y `data_id`, tardío, vencida sin procesar, `CONFIRMADA` con segundo pago, D6, rechazos fuera de orden, tercer rechazo, restricciones, auditoría y correos. Comprueba que el bloque queda `pagado` y que el arrendador ya no puede bloquear esa fecha. Cubre: R22–R36, R39–R42, R44–R54.
- [x] T16 — `PagoConcurrenciaIT` (**obligatoria**, `docs/verification.md`: hilos reales, `CountDownLatch`). Dos "Pagar" (R7); dos webhooks iguales: una fila y dos correos (R43); webhook frente a `expirarVencidas`: solo `CONFIRMADA`+`PAGADO` o `EXPIRADA`+`PAGADO_TARDIO` (R37); los tres métodos sin transacción → `IllegalTransactionStateException` (R38). Cubre: R7, R37, R38, R43.

## Frontend

- [x] T17 — `src/api/pagos.js` (N: `iniciarPago`, `obtenerEstadoPago`) y `pages/reservas/textosReservas.js` (M: textos de design § 10). Cubre: pantalla Resumen (CA1, CA3, CA4).
- [x] T18 — `pages/reservas/ResumenReserva.jsx` (M, + `.module.css`): "Pagar" como único primario, redirección en la misma pestaña, `?pago=` solo para el mensaje, sondeo acotado sin `SE_ACABO_EL_TIEMPO`, y cada fila de la tabla de design § 10. Cubre: CA1 (pagar y esperar la confirmación), CA3 (mensaje genérico y reintento), CA4 (sin checkout y reservar de nuevo), D2, D3, D4, D6.

## Docs y cierre

- [x] T19 — Avisar en `progress/HU-13.md`: a **todo el equipo** (S11: definir `PAGOS_PASARELA=simulada` en local tras el merge; `NotificadorCorreo` ganó métodos), a Ariel Enriquez (S14: corrección de RN-08, estados nuevos y retiro de Factory Method, pendiente de confirmar; HU-16 puede atender `PAGADO_TARDIO`), a Kevin Chan (HU-15 crea `PagoService`) y a Adriana Callupe (HU-14: `CONFIRMADA` ya existe). Cubre: S11, S14.
- [x] T20 — Docs del mismo PR (D2, D3, Reemplazos): `docs/requerimientos.md` (corregir RN-08), `docs/architecture.md` (tabla de puertos con la forma final de `ReservasPorCobrar`; **corregir la frase "`EspacioRetenido` no trae `arrendadorId`, HU-13 lo amplía"**; `bloquearEspacio`, `correoDe`, `DespuesDelCommit`; propiedades; "Pago" con `PAGADO_TARDIO` y `MONTO_DISTINTO`; "El checkout expira junto con la reserva"; fila "Reembolso de pagos que llegan tras expirar" de fuera del alcance; `id_transaccion_pasarela` y `uq_pago_reserva_pagado` en el principio 7; proveedor en "Stack"), `docs/modelo-datos.md` (`pago`, `checkout_pago`, restricciones, diagrama), `docs/frontend.md` (fila Pago de "Color de cada estado"; "HU-13 pone aquí Pagar"), `README.md` (pasarela en local, simulación y túnel). Cubre: D2, D3, D7, S11, S13.
- [x] T21 — Registrar para el orquestador en `progress/HU-13.md`, sin tocar `init.sh`: la regla de secretos solo mira nombres de archivo y conviene ampliarla a contenidos (`APP_USR-`, `TEST-`), tarea del arnés con `"sdd": false`. Cubre: explorador 15.
- [x] T22 — Verificación automática: `./init.sh` en verde (unitarias, IT con Docker, `npm run lint && npm run build`, revisión de colores y secretos) y `progress/impl_HU-13.md` con el mapa R → test (`@DisplayName`) y la lista de tests existentes que cambiaron. Cubre: R1–R58.
- [x] T24 — Completar en `progress/doc_HU-13.md` lo que le toca al implementer: verificar §3, §8 y §11 contra el código y quitar su marca de borrador, y llenar §9 con resultados reales. §2 lo cierra el orquestador tras `APPROVED`. Cubre: material del informe.

## Diferida por decisión del dueño (D8)

**T23 NO SE HA EJECUTADO.** Eduardo Saldaña decidió el 2026-10-30 no conectar Mercado Pago por ahora (D8). Va como texto sin
casilla porque no se puede marcar hecha sin mentir y una casilla abierta impediría cerrar la historia. Moverla aquí **no
comprueba nada**: S1, S5 y S6 siguen abiertos (T40 los registra en `docs/requerimientos.md` § 9).

T23 — Prueba con Mercado Pago real, junto con el dueño (túnel o UAT, credenciales de prueba, vendedor y comprador de prueba). Confirmar y anotar el resultado en `progress/impl_HU-13.md`: **(a) una notificación real pasa la firma (⚠ S1)**, con el nombre real del parámetro (⚠ S2); (b) `GET /v1/payment_methods` y la lista de tipos excluidos (⚠ S5); (c) el `PUT` de cierre (⚠ S6); (d) un pago aprobado confirma la reserva de punta a punta y un rechazo se registra. Si (a) falla, la historia pasa a `blocked` con la pregunta para el dueño (si ya está `done`, vuelve a `in_progress`). Cubre: R1, R5, R24, R30, R31, R39, R40. Guía de pasos: `progress/impl_HU-13.md`, corregida por T35.

## Adenda 2026-10-30 — Backend (D9, ★ Adapter)

Regla: `WebhookPagoService`, `CheckoutFacade`, `PagoController`, `WebhookPagoController`, `MercadoPagoAdapter` y `PasarelaPago` no cambian (design § 14). Tampoco hay migración. Si una task parece exigirlo, parar y avisar al dueño.

- [x] T25 — `PasarelaSimulada` (M): `crearCheckout` devuelve `<urlRetorno>/pago-simulado`; token interno con `SecureRandom`; `idNotificado` acepta el secreto configurado (si existe) o el token, en tiempo constante; `notificacionDe` fuera de `PasarelaPago` (design § 14.5, § 14.6); Javadoc y `log.warn` sin "no usar en UAT", coherentes con D7 revisada. Cubre: R59, R62, R72, R83, R84, R85. Test: `PasarelaSimuladaTest` (M: `creaUnCheckoutSimulado` con la URL nueva; lo de `notificacionDe` pasa `idNotificado` y `consultarPago`, también en una instancia nueva; sin secreto la notificación interna pasa y `sinSecretoNadaPasa` sigue verde; dos instancias, dos tokens) y `PagoControllerIT.iniciaElPago` (M: URL nueva).
- [x] T26 — `CheckoutPagoRepository.existsByReservaId`, `PagoRepository.countByReservaIdAndEstado`, `SesionesCheckout.existe`, `EvaluacionCobro.exigirRol` (y que `exigirDisponible` la use, sin cambiar de conducta) y `exigirCheckoutNoAgotado`. Cubre: R74, R75, R77, R78, R79. Test: `EvaluacionCobroTest` (M: rol, ajena, id mal escrito, 2 y 3 rechazos, orden, y que **no** mira vigencia ni espacio), `SesionesCheckoutTest` (M: `existe`).
- [x] T27 — `pagos/dto/`: `SimulacionDTO`, `SimularPagoDTO`. `pagos/service/SimulacionPago` (`@ConditionalOnProperty`, sin `@Transactional`, inyecta `PasarelaSimulada`, sin `ReservasPorCobrar`), con `consultar` y `simular` y la auditoría `PAGO_SIMULADO` (design § 14.6). Cubre: R60, R61, R62, R70, R71, R78, R79, R80, R86. Test: `SimulacionPagoTest` (`InOrder` comprobaciones → notificación → auditoría → `recibir`; una sola llamada a `recibir`; sin fila no la llama; `intento` 1 para `APROBADO` y conteo más 1 para `RECHAZADO`; monto de la reserva; componentes de los `record`).
- [x] T28 — `pagos/controller/PagoSimuladoController` (`@ConditionalOnProperty`, `GET` y `POST /api/reservas/{id}/pago/simulacion`, id como texto, `@Valid`, `IpCliente.de`, `@AuthenticationPrincipal`, `POST` responde 204). Sin reglas y sin importar repositorios. Cubre: R60, R62, R73, R74, R75, R76. Test: T29.
- [x] T29 — `PagoSimuladoIT` (design § 14.12: simulada, `RelojControlable`, doble de `NotificadorCorreo`, espía de `PasarelaPago`, limpia `pago` y luego `reserva`). Incluye aprobado con filas y correos exactos, tardío con el reloj pasado `expira_en` y con `EXPIRADA`, tres rechazos, `APROBADO` repetido, **dos `APROBADO` a la vez con hilos reales**, cuerpo con `monto` y `estado`, reinicio con la fila de `checkout_pago` creada por `JdbcTemplate`, todos los errores y su orden. Cubre: R59–R80, R85–R87.
- [x] T30 — `PagoSimuladoSinSecretoIT` (propiedad `espacioya.pagos.secreto-webhook=` vacía, como UAT: webhook 401 con cualquier firma y la pantalla aprueba). En `WebhookPagoIT` (M, contexto `mercadopago`): sin `SimulacionPago` ni `PagoSimuladoController`; `GET` y `POST …/simulacion` con sesión 404 y sin sesión 401, filas sin cambios. Cubre: R81, R82, R83, R84.

## Adenda 2026-10-30 — Frontend

- [x] T31 — `src/api/pagos.js` (M: `obtenerSimulacion`, `simularPago`) y `pages/pagos/textosPagos.js` (N: textos de design § 14.10). Cubre: pantalla Pago simulado (D9).
- [x] T32 — `pages/pagos/PagoSimulado.jsx` + `.module.css` (N) y ruta `/reservas/:id/pago-simulado` con `RutaProtegida` en `src/App.jsx` (M): aviso de simulación siempre visible, sin campos, un solo primario, cada fila de design § 14.10; con la lectura en error no muestra título, datos ni botones. Cubre: CA1 (aprobar), CA3 (rechazar y reintentar), CA4 (sin botones), D2, D4, D8, D9.
- [x] T33 — **DEF-01:** `ResumenReserva.jsx` (M): `pageshow` con `persisted` restablece `pagando` y relee (design § 14.11). Cubre: pantalla Resumen (CA1, CA3).
- [x] T34 — **DEF-02:** `ResumenReserva.jsx` (M): durante el sondeo, una lectura fallida conserva los últimos datos y cuenta como sondeo; al agotarse, `PAGO_EN_REVISION` (design § 14.11). Cubre: pantalla Resumen (CA1).

## Adenda 2026-10-30 — Docs y cierre

- [x] T35 — **DEF-03**, ajustado a D8. En `progress/impl_HU-13.md`: titular la sección como guía para cuando se conecte Mercado Pago, con "no ejecutada", sin decir que es un pendiente de esta entrega. Agregar: (1) **si el panel no deja leer la firma secreta, regenerarla es válido entonces, porque la prueba usa un pago nuevo y la firma vieja no se necesita**; (2) en (b), repetir el `read -s` de `PAGOS_LLAVE_SECRETA` en esa terminal, porque la variable solo existe en la del backend; (3) consulta con `docker exec` sin `-it`; (4) recordar que siguen haciendo falta las variables del README (`CIFRADO_LLAVE`, `CIFRADO_LLAVE_HUELLA`, `ALMACENAMIENTO_LLAVE_FIRMA`); (5) antes de cambiar a `mercadopago`, esperar a que no haya reservas pendientes (RS-6) y decidir qué hacer con las filas `sim-` (RS-5). En `README.md`, usar `read -s` para las credenciales de Mercado Pago, como la guía. Cubre: DEF-03, D8.
- [x] T36 — **DEF-04:** en `docs/requerimientos.md`, RF-E-04: "Hacer que el checkout expire 1 minuto antes que la reserva (margen configurable, RN-08), no iniciar un pago en ese último minuto y permitir reservar de nuevo.". No tocar CA4 de `docs/historias/HU-13.md` (C5, decide el dueño). Cubre: DEF-04, D3.
- [x] T37 — D7 revisada en la configuración: `2_Backend/.env.example` (quitar "La simulada nunca se usa en UAT"; UAT usa `simulada` hasta conectar Mercado Pago, con `PAGOS_SECRETO_WEBHOOK` **vacío**, S16), comentarios de `application.properties` y `README.md`: "Solo la primera vez" (la razón ya no es que UAT no quede en la simulada), **Pagar** abre la pantalla simulada, tabla de pruebas con **Aprobar** y **Rechazar** sin terminal, `curl` como opción solo local y su límite tras reiniciar (RS-7), y tabla de variables de UAT. Cubre: D7, D9, S11, S16.
- [x] T38 — `docs/architecture.md`: "Stack" (UAT con la simulada hasta conectar Mercado Pago, no conectado por ahora, D8); tabla "Pagos: qué deja HU-13" (`SimulacionPago`, `PagoSimuladoController`, `notificacionDe` fuera de `PasarelaPago`, `WebhookPagoService` sigue siendo el único que confirma); `espacioya.pagos.*` (`secreto-webhook` vacío en UAT); "Lo que deben saber las historias que siguen" (filas `sim-`, `reembolsar` también en `PasarelaSimulada`). En "PCI DSS" y el principio 6, una nota que remita a C2 y C3 de design § 14.13, sin reescribir la regla. Sin puerto nuevo. Cubre: D7, D8, D9.
- [x] T39 — `docs/frontend.md`: ruta `/reservas/:id/pago-simulado` y `pages/pagos/`; corregir "Con la pasarela simulada no hay checkout: el retorno es inmediato y el pago se simula por la API"; cómo se marca la simulación y que solo el backend la habilita. Sin estados ni colores nuevos. Cubre: pantalla Pago simulado, D9.
- [x] T40 — `docs/requerimientos.md` § 9: nuevo pendiente "Mercado Pago sin conectar (HU-13, D8, Eduardo Saldaña, 2026-10-30)": UAT usa la pasarela simulada; T23 **no se ha ejecutado**; falta comprobar la **firma real de una notificación (S1)**, los **tipos de pago excluidos (S5)** y el **cierre del checkout (S6)**; hasta entonces RNF-CUM-01 y RNF-CUM-02 no se demuestran en UAT (C1, C2); guía en `progress/impl_HU-13.md`. Cubre: D8, S1, S5, S6.
- [x] T41 — Avisos en `progress/HU-13.md`: a Ariel Enriquez (PO y caja negra: D7 revisada, D8, D9; decidir C5; casos sin terminal; `MONTO_DISTINTO` no es caso de pantalla, S20; HU-16 y `reembolsar`), a todo el equipo (S11 ajustado; filas `sim-`, RS-5) y a Eduardo Saldaña (HU-19 y HU-28, RS-5). Cubre: S11, S14, D7.
- [x] T42 — `progress/impl_HU-13.md`: mapa R59–R87 → test, archivos y tests que cambiaron. `progress/doc_HU-13.md`: verificar contra el código lo marcado como pendiente de la adenda en §2, §3, §5, §8 y §11, llenar §9 con resultados reales y la evidencia de DEF-01 a DEF-04 en §10. Cubre: R59–R87, material del informe.
- [x] T43 — Verificación automática: `./init.sh` en verde (unitarias, IT con Docker, `npm run lint && npm run build`, revisión de colores y secretos), sin avisos de `R<n>` sin prueba para HU-13. Cubre: R1–R87.
