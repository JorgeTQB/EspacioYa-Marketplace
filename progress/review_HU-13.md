# Review — HU-13

**Veredicto:** APPROVED

Vuelta 2, del 2026-10-31, sobre la adenda (D7 revisada, D8, D9, S15–S20, C1–C5; T25–T43), sin commitear en `feature/HU-13-pago-tarjeta`.

**Alcance del veredicto.** Aprueba el código, las pruebas y la documentación de la adenda. **No dice que Mercado Pago funcione:** T23 no se ha ejecutado, y S1, S5 y S6 siguen sin comprobar. Tampoco cubre la vista en navegador: ningún agente abrió las pantallas, y esa prueba está pendiente con el dueño (`progress/impl_HU-13.md`, "Pendiente para el dueño").

## Verificación

- `./init.sh` completo, ejecutado por este reviewer: **salida 0, `[OK] Entorno listo`**. 824 unitarias y 587 de integración, sin fallos, errores ni omitidas. ESLint sin advertencias y build en verde. Sin avisos de `R<n>` sin prueba.
- El `[WARN]` de `CIFRADO_LLAVE`, `CIFRADO_LLAVE_HUELLA` y `ALMACENAMIENTO_LLAVE_FIRMA` es del entorno de esta terminal.
- Comprobado con un script: R1–R87 tienen `@DisplayName`, y todas las pruebas del mapa de `progress/impl_HU-13.md` existen y declaran su `R<n>`. Sin imports sin usar en los 50 archivos Java de `pagos`, y las clases nuevas no pasan de 120 columnas.
- Los conteos por clase de `progress/doc_HU-13.md` §9.2 coinciden con los reportes de surefire y failsafe: 184 unitarias y 155 de integración en las clases nuevas.

## T1–T22 y T24 no se alteraron salvo lo que indica la adenda

- `WebhookPagoService`, `CheckoutFacade`, `PagoController`, `WebhookPagoController`, `MercadoPagoAdapter`, `PasarelaPago`, `AvisosPago`, `ReservasPorCobrarImpl`, `pagos/domain/`, `V4_1__pago.sql` y `ConfiguracionSeguridad`: fecha de modificación anterior a mi aprobación de la vuelta 1 (21:48–21:53, frente a 22:46). Es evidencia indirecta: son archivos sin commitear, así que no hay línea base en git. Coincide con las sumas de verificación que declara el implementer, que no pude repetir.
- Cambios de la adenda, leídos uno por uno: `PasarelaSimulada`, `EvaluacionCobro` (`exigirDisponible` solo extrae `exigirRol`, misma conducta), `SesionesCheckout.existe`, y un método nuevo en cada repositorio.
- Pruebas: `WebhookPagoServiceTest` (20) y `PagoConcurrenciaIT` (6) mantienen sus conteos. `WebhookPagoIT` sube de 59 a 63, solo por R81 y R82. `iniciaElPago` y `creaUnCheckoutSimulado` cambian a la URL de R59, como pide la adenda. Las pruebas de otras historias siguen solo con líneas agregadas.
- `docs/historias/HU-13.md`: CA1 y CA4 intactos, como manda C5.

## Trazabilidad requirements ↔ tests (R59–R87)

R1–R58: ver la vuelta 1. Siguen verdes; R1, R24, R40, R52 y R56 quedan precisados por la adenda sin dejar de estar cubiertos.

- R59: [x] `PagoSimuladoIT.pagarLlevaALaPantalla`, `PagoControllerIT.iniciaElPago`
- R60: [x] `PagoSimuladoIT.disponibleConCheckout`, `noDisponibleSinCheckout`, `noDisponibleSinPagoDisponible`, `noDisponibleConEspacioPausadoOTresRechazos`
- R61: [x] `PagoSimuladoIT.consultarNoEscribe`
- R62: [x] `PagoSimuladoIT.pasaPorElWebhook`, `SimulacionPagoTest.ordenDelCaminoFeliz`
- R63: [x] `PagoSimuladoIT.aprobadoConfirma`, `aprobadoConEspacioPausado`
- R64: [x] `PagoSimuladoIT.aprobadoEnviaLosCorreos`, `sinCommitNoHayCorreos`
- R65: [x] `PagoSimuladoIT.tardioConReservaVencida`, `tardioConReservaExpirada`
- R66: [x] `PagoSimuladoIT.rechazado`
- R67: [x] `PagoSimuladoIT.tercerRechazoCierra`
- R68: [x] `PagoSimuladoIT.aprobadoRepetido`
- R69: [x] `PagoSimuladoIT.dosAprobadosALaVez` (hilos reales, 5 rondas)
- R70: [x] `PagoSimuladoIT.dosRechazos`, `SimulacionPagoTest.rechazarSaleDelConteo`
- R71: [x] `PagoSimuladoIT.ignoraLoDemasDelCuerpo`
- R72: [x] `PagoSimuladoIT.trasReiniciar`, `PasarelaSimuladaTest.funcionaTrasReiniciar`
- R73: [x] `PagoSimuladoIT.resultadoInvalido`
- R74: [x] `PagoSimuladoIT.otrosRoles`
- R75: [x] `PagoSimuladoIT.idInvalido`, `reservaAjena`
- R76: [x] `PagoSimuladoIT.sinSesion`
- R77: [x] `PagoSimuladoIT.tresRechazos`
- R78: [x] `PagoSimuladoIT.sinCheckout`
- R79: [x] `PagoSimuladoIT.rolAntesQueReserva`, `reservaAntesQueRechazos`, `rechazosAntesQueCheckout`, `noRepiteLaVigencia`
- R80: [x] `PagoSimuladoIT.noExponeDatosDelPago`, `SimulacionPagoTest.losDtoSoloTienenSuCampo`
- R81: [x] `WebhookPagoIT.sinBeansDeSimulacion`, `pagarNoLlevaALaPantalla`
- R82: [x] `WebhookPagoIT.simulacionNoExisteConSesion`, `simulacionSinSesion`
- R83: [x] `PagoSimuladoSinSecretoIT.elWebhookRechazaTodo`, `sinFirma`
- R84: [x] `PagoSimuladoSinSecretoIT.laPantallaApruebaIgual`
- R85: [x] `PagoSimuladoIT.elTokenNoSale`, `PasarelaSimuladaTest.cadaInstanciaTieneSuToken`
- R86: [x] `PagoSimuladoIT.auditaQuienDecidio`, `auditaCadaIntento`
- R87: [x] `PagoSimuladoIT.idConPrefijo`

## Trazabilidad con la historia

- CA1: [x] R1–R7, R23–R28, R30–R32, R35–R38, R48, R50, R51; con la simulada, R59, R60, R62–R64, R72
- CA2: [x] R49–R51, R64
- CA3: [x] R1, R6, R19, R20, R39, R66, R70, y las pantallas de design § 10 y § 14.10
- CA4: [x] R4, R10, R11, R19, R34, R60, R65. El texto de CA1 y CA4 lo decide el dueño con el PO (C5)
- (I/T) PCI DSS: [x] R1, R3, R20, R54, R80. En UAT no hay pasarela certificada: declarado (C1)
- (S/T) Estado solo por webhook con firma verificada: [x] R8, R21, R24–R26, R31, R62, R85. Firma real sin comprobar: declarado (C2, S1)
- (S/T) Webhook idempotente: [x] R42–R46, R68, R69
- (T) Monto en servidor: [x] R2, R3, R33, R71
- (D) 3 intentos por reserva cada 30 min: [x] R13, R19, R39–R41, R67, R77
- (R) Registro de cada transacción: [x] R9, R29, R30, R33, R34, R39, R52, R53, R86, R87

## Tasks completas

- T1–T22, T24: [x]
- T25–T43: [x]
- T23: sin casilla, en "Diferida por decisión del dueño (D8)". No se rechaza, por indicación del orquestador y C4. Sigue sin ejecutar.

## Revisión dirigida

- **Con `mercadopago`, el endpoint y la pantalla no funcionan:** [x] `PagoSimuladoController` y `SimulacionPago` llevan el mismo `@ConditionalOnProperty` que `PasarelaSimulada`, y `SimulacionPago` inyecta esa clase concreta. Con `mercadopago` los beans no existen (`sinBeansDeSimulacion`), `GET` y `POST` con sesión responden 404 sin escribir (`simulacionNoExisteConSesion`) y sin sesión 401. La ruta del frontend existe en el build, pero sin un 200 solo muestra el error, sin título, datos ni botones.
- **Un solo camino de confirmación:** [x] `SimulacionPago` no toca `pago` ni `ReservasPorCobrar`: su única salida es `WebhookPagoService.recibir`. `Reserva.confirmar` sigue con un solo llamador, y `pago` solo se guarda en `WebhookPagoService:95`. Vigencia, tardío, 3 rechazos, idempotencia, bloqueos y correos tras el commit salen del mismo código, y `PagoSimuladoIT` lo comprueba con filas y correos exactos, incluido `dosAprobadosALaVez`.
- **Autorización:** [x] Sesión, rol `USUARIO` y reserva propia, con la identidad tomada de `@AuthenticationPrincipal`. Reserva ajena: 404, sin llamar a `recibir`. El cuerpo solo acepta `APROBADO` o `RECHAZADO`; monto, estado y usuario se ignoran (`ignoraLoDemasDelCuerpo`).
- **S16:** [x] `firmaValida` no acepta el secreto si está vacío, y una firma vacía no iguala al token. `PagoSimuladoSinSecretoIT` responde 401 con firma vacía, con cualquier valor, con el secreto de pruebas y sin encabezado. El token son 32 bytes de `SecureRandom` por instancia, comparados en tiempo constante, y no aparece en respuestas, auditoría, `pago` ni logs. Sigue el riesgo aceptado RS-4 si alguien define el secreto en UAT: documentado en README, `.env.example` y `application.properties`.
- **Montos:** [x] Cada `POST` escribe `monto_total` antes de `recibir`, así que un reinicio no rompe un pago pendiente (`trasReiniciar`). Auditoría `PAGO_SIMULADO` con usuario e IP, prefijo `sim-` y `ultimos4 = 4242`, fijo.
- **`MercadoPagoAdapter` sin cambios:** [x] Ver la sección de T1–T22; `MercadoPagoAdapterTest` sigue con 40 pruebas.
- **Frontend:** [x] Aviso de simulación visible cuando hay botones y cuando no los hay. Sin campos, un solo primario, `RutaProtegida` y ningún dato sensible. Con `replace`, Atrás no vuelve a los botones. DEF-01 y DEF-02 corregidos, verificados leyendo el código.
- **Coherencia:** [x] RF-E-04, D7 revisada, README, `.env.example`, `application.properties`, `docs/architecture.md` (Stack, principio 6, PCI DSS, propiedades, filas `sim-`), `docs/frontend.md` y `docs/requerimientos.md` §9 coinciden con el código.
- **`progress/doc_HU-13.md`:** [x] §2, §3, §5, §8, §9 y §11 coinciden con el código, y declara la simulación y sus límites. Las 64 pruebas citadas existen. Queda texto viejo en §1 y §13: DEF-05.
- **Nada sugiere que Mercado Pago se probó en la aplicación:** [x] Revisados `progress/`, `specs/HU-13/`, `docs/`, README y configuración. Lo que se llama real es el pago de prueba aislado del dueño, descrito como hecho fuera de la aplicación. Ver la observación sobre `puntaAPunta`.

## Checkpoints

- C1: [x]
- C2: [x] Las de `done` aún no aplican. Aprobación del dueño de la adenda anotada en `progress/HU-13.md`.
- C3: [x] Con la excepción C3, aceptada por el dueño: el cliente elige `resultado`, solo con la simulada y con auditoría. Sin puerto nuevo.
- C4: [x] `dosAprobadosALaVez` lanza la concurrencia con hilos reales.
- C5: n/a hasta el cierre. Sin archivos sospechosos; el trabajo está en su rama; `feature_list.json` sigue `in_progress`, correcto.
- C6: [x] Toda historia `done`: n/a. T23 sin casilla por decisión del dueño (C4). `docs/requerimientos.md` §9 registra lo que queda abierto.

## Cambios requeridos

Ninguno bloqueante.

## Estado de los defectos

Detalle en `progress/doc_HU-13.md` §10.

- **DEF-01:** corregido en `ResumenReserva.jsx:119-128`. Verificado leyendo el código; sin reproducir en navegador.
- **DEF-02:** corregido en `ResumenReserva.jsx:104-112`. Verificado leyendo el código; sin reproducir en navegador.
- **DEF-03:** corregido. Comprobados los cinco puntos de T35 en la guía y `read -s` en `README.md`. La guía sigue sin ejecutarse.
- **DEF-04:** corregido en RF-E-04. CA4 de la historia sigue con el dueño y el PO (C5).
- **DEF-05 (nuevo, baja, abierto):** `progress/doc_HU-13.md` conserva texto anterior a la aprobación de la adenda. La línea 33 dice que está pendiente de aprobación, la línea 76 dice "S15, a confirmar", y la línea 620 dice "sin código aún", sin una fila para la implementación en §13. El dueño ya aprobó la adenda (`progress/HU-13.md`, línea 105). No afecta al código ni a lo que el informe declara sobre la simulación, y `progress/HU-13.md` decía que §1 estaba al día. Se corrige antes de copiar §1 y §13 al informe.

## Pendientes y observaciones, sin defecto

- **Con el dueño:** probar en el navegador aprobar, rechazar, pago tardío, DEF-01 y DEF-02. Lo que se vea distinto de la guía es un hallazgo y vuelve a revisión.
- **Con el PO:** C5, y que Ariel Enriquez conozca D7 revisada y D8.
- Cada `APROBADO` repetido escribe una fila `PAGO_SIMULADO` sin tope (`auditaCadaIntento`). Exige sesión y una reserva propia, y `design.md` § 14.4 descartó el límite. Riesgo bajo en UAT.
- `WebhookPagoIT.puntaAPunta` se titula "de punta a punta", pero corre contra `StubMercadoPago`. No debe citarse en el informe como prueba con Mercado Pago sin esa aclaración.
- `design.md` § 14.9 dice que `SimulacionPagoTest` comprueba el token, pero lo hace `PasarelaSimuladaTest.armaLaNotificacion`.
- Siguen vigentes las observaciones de la vuelta 1 sobre `MercadoPagoAdapter.decimal`, `ReglasPago.idDeReserva` y `SesionesCheckout.cerrar`.

---

## Vuelta 1 (2026-10-30): antes de la adenda

**Veredicto de la vuelta 1:** APPROVED. No cubre la adenda; se conserva como registro. Sus defectos están al día en la vuelta 2.

Revisión del 2026-10-30 sobre el código sin commitear de `feature/HU-13-pago-tarjeta` (base `develop` en `1fbf140`).

**Alcance del veredicto.** Aprueba el código, las pruebas y la documentación, con T23 abierta por justificación. No equivale a `done`: S1 (firma real) sigue sin probarse y, si T23 (a) falla, la historia pasa a `blocked`. Cualquier cambio de código que salga de T23 vuelve a revisión.

### Verificación

- `./init.sh` completo, ejecutado por este reviewer: **salida 0, `[OK] Entorno listo`**. 792 unitarias y 531 de integración, sin fallos, errores ni omitidas. ESLint sin advertencias y build en verde. Reglas de arquitectura, colores y secretos en verde.
- El `[WARN]` de `CIFRADO_LLAVE`, `CIFRADO_LLAVE_HUELLA` y `ALMACENAMIENTO_LLAVE_FIRMA` es del entorno de esta terminal: no falla ninguna prueba.
- Comprobado con un script: los 58 `R<n>` tienen `@DisplayName("HU-13 R<n>: …")`, y todas las pruebas citadas en `progress/impl_HU-13.md` existen y declaran su `R<n>`. Sin líneas de más de 120 columnas ni imports sin usar en `pagos`.
- No se abrió la pantalla en un navegador: el frontend se revisó leyendo el código.

### Trazabilidad requirements ↔ tests

Se cita la prueba que mejor lo verifica; el mapa completo está en `progress/impl_HU-13.md`.

- R1: [x] `PagoControllerIT.iniciaElPago`, `WebhookPagoIT.iniciaContraLaPasarela`
- R2: [x] `MercadoPagoAdapterTest.enviaMontoMonedaYReferencia`
- R3: [x] `PagoControllerIT.ignoraElCuerpo`
- R4: [x] `MercadoPagoAdapterTest.enviaElVencimiento`, `WebhookPagoIT.elEnlaceVenceAntes`
- R5: [x] `MercadoPagoAdapterTest.enviaLaConfiguracionDelCheckout`, `sinHttpsNoHayRetorno`, `conHttpsHayRetorno`
- R6: [x] `PagoControllerIT.reutilizaElCheckout`
- R7: [x] `PagoConcurrenciaIT.dosPagarALaVez`
- R8: [x] `PagoControllerIT.iniciarNoConfirma`
- R9: [x] `PagoControllerIT.auditaElInicio`
- R10: [x] `PagoControllerIT.expirada`, `vencidaSinProcesar`, `yaConfirmada`
- R11: [x] `PagoControllerIT.ultimoTramo`
- R12: [x] `PagoControllerIT.espacioPausado`, `arrendadorSuspendido`
- R13: [x] `PagoControllerIT.tresRechazos`, `ventanaDeRechazos`
- R14: [x] `PagoControllerIT.otrosRoles`
- R15: [x] `PagoControllerIT.idInvalido`, `reservaAjena`
- R16: [x] `PagoControllerIT.sinSesion`
- R17: [x] `WebhookPagoIT.pasarelaCaidaAlIniciar`, `MercadoPagoAdapterTest.crearConTiempoAgotado`
- R18: [x] `PagoControllerIT.rolAntesQueReserva`, `reservaAntesQueVigencia`, `rechazosAlFinal`
- R19: [x] `PagoControllerIT.estadoDelUltimoPago`, `estadoConIntentosAgotados`, `estadoSinPagoDisponible`
- R20: [x] `PagoControllerIT.noExponeDatosDelPago`
- R21: [x] `PagoControllerIT.estadoNoEscribe`
- R22: [x] `WebhookPagoIT.sinSesion`
- R23: [x] `WebhookPagoIT.otrosFormatos`
- R24: [x] `WebhookPagoIT.firmaMala`, `firmaAlterada`, `MercadoPagoAdapterTest.aceptaElVector`, `rechazaFirmaMalformada`
- R25: [x] `WebhookPagoIT.consultaElPago`, `WebhookPagoServiceTest.consultaAntesDeBloquear`
- R26: [x] `WebhookPagoIT.elCuerpoMiente`, `liveModeNoImporta`
- R27: [x] `WebhookPagoIT.consultaFallida`
- R28: [x] `WebhookPagoIT.sinResultado`
- R29: [x] `WebhookPagoIT.referenciaInexistente`, `referenciaInvalida`
- R30: [x] `WebhookPagoIT.guardaElPago`, `montoSinCeroFinal`, `sinUltimos4`
- R31: [x] `WebhookPagoIT.confirmaLaReserva`, `ReservaTest.noConfirmaUnaVencida`
- R32: [x] `WebhookPagoIT.bloquesPagados`, `noSePuedeBloquearLaFecha`
- R33: [x] `WebhookPagoIT.otroMonto`, `otraMoneda`
- R34: [x] `WebhookPagoIT.tardioConExpirada`, `tardioConVencidaSinProcesar`, `segundoPagoSobreConfirmada`
- R35: [x] `WebhookPagoIT.espacioNoVisible`, `cuentasSuspendidas`
- R36: [x] `PagoConcurrenciaIT.evaluaLaVigenciaTrasElBloqueo`, `ReservasPorCobrarImplTest.ordenDeLosBloqueos`
- R37: [x] `PagoConcurrenciaIT.webhookFrenteAExpiracion`, `laExpiracionEsperaAlWebhook`
- R38: [x] `PagoConcurrenciaIT.exigenUnaTransaccion`
- R39: [x] `WebhookPagoIT.rechazado`, `rechazadoFueraDeOrden`
- R40: [x] `WebhookPagoIT.tercerRechazoCierra`
- R41: [x] `WebhookPagoIT.cierreFallido`
- R42: [x] `WebhookPagoIT.aprobadoRepetido`, `rechazoRepetido`
- R43: [x] `PagoConcurrenciaIT.dosWebhooksIguales`
- R44: [x] `WebhookPagoServiceTest.violacionDeLaUnicidad`, `otraViolacion`. Sin IT, y es correcto: el bloqueo de filas hace inalcanzable el `UNIQUE` por la API, y `WebhookPagoIT.unicidadDeLaTransaccion` confirma el nombre real de la restricción.
- R45: [x] `WebhookPagoIT.unicidadDeLaTransaccion`
- R46: [x] `WebhookPagoIT.unPagadoPorReserva`
- R47: [x] `WebhookPagoIT.checksDePago`, `estadosDePago`, `unCheckoutPorReserva`
- R48: [x] `WebhookPagoIT.comprobante`
- R49: [x] `WebhookPagoIT.avisoAlArrendador`
- R50: [x] `WebhookPagoIT.sinConfirmarNoHayCorreos`, `DespuesDelCommitTest.noCorreConRollback`
- R51: [x] `WebhookPagoIT.correoFallido`, `AvisosPagoTest.unFalloNoImpideElOtro`
- R52: [x] `WebhookPagoIT.auditaElPago`, `auditaLosDemasEstados`
- R53: [x] `WebhookPagoIT.auditaLaConfirmacion`
- R54: [x] `WebhookPagoIT.soloUltimos4`, `auditoriaSinSecretos`
- R55: [x] `SeleccionPasarelaTest.mercadoPago`, `WebhookPagoIT.usaElAdaptador`
- R56: [x] `SeleccionPasarelaTest.simulada`, `PagoControllerIT.usaLaSimulada`
- R57: [x] `SeleccionPasarelaTest.sinPropiedad`, `otroValor`
- R58: [x] `SeleccionPasarelaTest.sinLlave`, `sinSecreto`, `sinUrl`

### Trazabilidad con la historia

- CA1: [x] R1–R7, R23–R28, R30–R32, R35–R38, R48, R50, R51
- CA2: [x] R49, R50, R51
- CA3: [x] R1, R6, R19, R20, R39, y la pantalla de design § 10
- CA4: [x] R4, R10, R11, R19, R34, y la pantalla de design § 10. "Expira junto con la reserva" lo corrigió D3 (ver DEF-04)
- (I/T) Checkout PCI DSS, la plataforma nunca recibe ni guarda la tarjeta: [x] R1, R3, R20, R54. Checkout alojado; solo `ultimos4`
- (S/T) Estado cambia solo por webhook con firma verificada: [x] R8, R21, R24–R26, R31. `Reserva.confirmar` solo se llama desde `ReservasPorCobrarImpl:82`, y el puerto solo desde `WebhookPagoService:101`
- (S/T) Webhook idempotente: [x] R42–R46
- (T) Monto calculado en servidor: [x] R2, R3, R33
- (D) Máximo 3 intentos fallidos por reserva cada 30 min: [x] R13, R19, R39–R41
- (R) Registro de cada transacción: [x] R9, R29, R30, R33, R34, R39, R52, R53

### Tasks completas

- T1–T22: [x]
- T23: [ ] Justificada en `progress/impl_HU-13.md` (la ejecuta el dueño con sus credenciales). Abierta a propósito; ver "T23" abajo.
- T24: [x]

### Revisión dirigida

- **Patrones:** [x] Adapter y Facade, sin Factory Method: la selección es `@ConditionalOnProperty` en cada implementación. `MercadoPagoAdapter` solo traduce y ningún tipo del proveedor sale de él. `CheckoutFacade` es la única entrada de `PagoController`, coordina sin reglas y el webhook no pasa por ella.
- **Firma:** [x] `WebhookPagoService.recibir` la verifica primero y no hay otro camino hacia `consultarPago`. Manifiesto `id:…;request-id:…;ts:…;`, HMAC-SHA256 en hexadecimal y `MessageDigest.isEqual`. Con `type` distinto de `payment` termina antes de firmar, sin efectos. El vector aceptado por el validador oficial pasa; falta la prueba real (S1).
- **Nada confía en la notificación:** [x] El controlador no declara cuerpo; estado, monto, moneda, últimos 4 y referencia salen de `consultarPago`. `elCuerpoMiente` lo prueba de punta a punta.
- **Idempotencia y concurrencia:** [x] Dentro de una transacción: `espacio → reserva`, `exists`, `INSERT`, `confirmar`, auditoría. La relectura es real: la proyección `findEspacioIdById` no deja copia vieja en el contexto, y en READ COMMITTED el `exists` ve lo confirmado por quien tenía el bloqueo. `expirarVencidas` revalida bajo bloqueo (`ExpiracionReservas:75`). Sin inversión de orden frente a `crear`, HU-08 ni el proceso. Doble clic: `cargando` deshabilita el botón y R7 cubre el servidor.
- **D2, S3 y S4:** [x] `PAGADO_TARDIO` y `MONTO_DISTINTO` no confirman, y `Reserva.confirmar` lanza si no es vigente.
- **D3 y D4:** [x] `expira_en − margen`, límite exacto en 61 s y 60 s, conteo por filas `RECHAZADO`, 429 y cierre tras el commit solo si hay 3 o más.
- **PCI DSS:** [x] Ningún dato de tarjeta salvo `ultimos4`. Los `log.*` de `pagos` no llevan datos, `llamar` no propaga el cuerpo del proveedor, los DTO y el `NotificadorCorreoSimulado` tampoco. No hay actuator.
- **Secretos:** [x] Ningún valor real en el repositorio (`APP_USR-`, `TEST-`, URL de webhook.site): solo `secreto-solo-pruebas` y `llave-solo-pruebas`, marcados. `PAGOS_PASARELA` sin valor por defecto: sin él no hay bean y el backend no arranca, así que UAT no cae en la simulada por omisión. El aviso de S11 está en `progress/HU-13.md` y el README.
- **Dependencias:** [x] `pagos` no importa `reservas` ni `espacios`; `reservas` implementa `ReservasPorCobrar`; `pagos → cuentas` y `reservas → pagos` están permitidas. Sin ciclos.
- **Frontend:** [x] `?pago=` solo elige el mensaje, un solo primario, mensajes genéricos sin datos sensibles, llamadas por `src/api/pagos.js`, sondeo acotado a 10 lecturas. Coincide con design § 10. Dos defectos menores: DEF-01 y DEF-02.
- **Otras historias:** [x] Solo cambia lo declarado. `CuentaServiceImpl` reemplaza `despuesDelCommit` por `DespuesDelCommit.ejecutar`, igual salvo el texto del `log.warn`. `ConfiguracionSeguridad` suma solo la ruta exacta del webhook. `DisponibilidadService`, `Reserva` y `ReservaRepository` solo agregan. Las pruebas existentes solo ganan líneas: 0 borradas en los 9 archivos modificados.
- **`progress/doc_HU-13.md`:** [x] §3, §8, §9 y §11 coinciden con el código. Las 35 pruebas citadas existen y los conteos por clase de §9.2 coinciden con los reportes de surefire y failsafe. §8.3 coincide con `V4_1__pago.sql`. Defectos agregados a §10.

### T23: pasos para el dueño

- **Seguros:** sí. Las credenciales se leen con `read -s`, no pasan por el chat ni por archivos.
- **Suficientes para confirmar S1:** sí. El criterio descansa en un pago real: una fila `PAGADO` solo se crea si la firma pasó con el adaptador real, y un `firma inválida` en el log cuenta como fallo. Si conviven una notificación válida y una inválida, el camino de fallo lleva a `blocked` y a la pregunta correcta. Como el panel y `notification_url` usan la misma URL, un éxito no distingue cuál de los dos la firmó, pero eso no cambia el resultado: las notificaciones reales pasan.
- **Claros:** con cuatro huecos menores, en DEF-03. El orquestador puede corregirlos antes de pasarle T23 al dueño; ninguno cambia el código.

### Checkpoints

- C1: [x]
- C2: [x] Las de `done` no aplican a esta historia todavía. Aprobación del dueño anotada en `progress/HU-13.md`.
- C3: [x]
- C4: [x] `crearConTiempoAgotado` espera 7 s reales, pero es un tiempo de HTTP, no un plazo de negocio.
- C5: n/a hasta el cierre (entrada en `history.md` y vuelta a la plantilla, tras T23). Sin archivos sospechosos; el trabajo está en su rama; `feature_list.json` sigue `in_progress`, correcto.
- C6: [x] Toda historia `done`: n/a. `docs/requerimientos.md` §9 no tiene pendientes de pagos que un `R<n>` resuelva.

### Cambios requeridos

Ninguno bloqueante.

### Defectos y pendientes no bloqueantes

Ninguno viola un `R<n>`, un CA, un control STRIDE ni una regla dura. Quedan abiertos en `progress/doc_HU-13.md` §10.

1. **DEF-01 (baja)** — `3_frontend/src/pages/reservas/ResumenReserva.jsx:129-141`: `pagar()` no restablece `pagando` tras `window.location.assign`. Si el usuario vuelve con Atrás y el navegador restaura la página de su caché (bfcache), "Pagar" queda en "Enviando…" y deshabilitado hasta recargar. Deducido del código, no reproducido. Corrección sugerida: `pagando` en `false` al recibir `pageshow` con `persisted`.
2. **DEF-02 (baja)** — `ResumenReserva.jsx:104-106`: durante el sondeo, una sola lectura fallida reemplaza el resumen por el error y termina la espera (`actual.datos` queda `null`). Recargar lo recupera. Corrección sugerida: durante el sondeo, conservar los últimos datos y contar la lectura fallida.
3. **DEF-03 (baja)** — `progress/impl_HU-13.md`, T23:
   - Línea 42: si el panel sigue sin mostrar la firma secreta, no hay indicación de qué hacer. Falta decirlo, porque sin ella no se puede empezar.
   - Línea 83, paso (b): usa `$PAGOS_LLAVE_SECRETA`, que solo existe en la terminal que corre el backend. En otra, la petición va sin llave y `JSON.parse(d).map` falla. Debe repetir el `read -s` ahí.
   - Línea 64: `docker exec -it` puede fallar en Git Bash con mintty ("the input device is not a TTY"). La consulta no necesita `-it`.
   - Líneas 31-37: falta recordar que siguen haciendo falta las variables del README (`CIFRADO_LLAVE` y las otras dos).
   - `README.md:232-235` lleva a escribir las credenciales en el comando `export`, y quedan en el historial: debería usar `read -s`, como T23.
4. **DEF-04 (baja, decide el dueño)** — `docs/requerimientos.md:111`, RF-E-04, sigue diciendo que el checkout expira junto con la reserva, contra RN-08 y D3. El implementer ya lo señaló; T20 no lo incluía.

Observaciones, sin defecto:

- `MercadoPagoAdapter.decimal` deja pasar un `NumberFormatException` si `transaction_amount` no es numérico: sería 500 y no 503 (R27). La pasarela reintenta igual y Mercado Pago siempre envía un número.
- `PAGO_EN_REVISION` promete el comprobante también si la reserva expiró en la espera, y en ese caso el pago sería `PAGADO_TARDIO`, sin comprobante (`ResumenReserva.jsx:47`, `textosReservas.js:14`). El texto viene del design.
- `ReglasPago.idDeReserva` es la tercera copia de esa validación (`FichaService`, `ReservaServiceImpl`). Candidata a `comun` cuando haya una cuarta.
- `SesionesCheckout.cerrar` retiene la conexión durante el `PUT`, hasta 10 s, solo en el tercer rechazo. Está señalado por el implementer; el riesgo es bajo.

---

## Adenda AC003 (2026-11-16)

**Veredicto:** APROBADO

Alcance: `requirements.md` "Adenda 2026-11-16", `design.md` § 15, T44–T45, `progress/impl_HU-13.md` § "Adenda AC003".

### Trazabilidad adenda ↔ prueba
- AC003 / R43 (R42, R44, R45, R48, R49, R53): [x] `PagoConcurrenciaIT.cincoWebhooksIguales`, `@DisplayName("HU-13 R43 (AC003): cinco notificaciones iguales a la vez dejan una fila, una confirmación y un correo de cada tipo")`.
  - Hay `NOTIFICACIONES_IGUALES` (5) solicitudes `notificar(id)` por `aLaVez` y `RONDAS` (5). Cada ronda trae `notificador.reiniciar()`, `vigente()`, un id nuevo y `borrarReservas()`.
  - Aserción 1: `estados(...).containsOnly(200).hasSize(5)`.
  - Aserción 2: `filasConTransaccion(id) == 1` y `pagosDe(reserva).hasSize(1)`.
  - Aserción 3: `estadoDeReserva == "CONFIRMADA"` y `confirmacionesDe(reserva) == 1`. La auditoría se filtra por `entidad_id`.
  - Aserción 4: `comprobantes().hasSize(1)` y `avisos().hasSize(1)`.

### Alcance del cambio
- `git diff`: solo hay inserciones (+38), con 1 constante, 1 `@Test` y 2 ayudantes privados. Ninguna prueba existente se modificó.
- No se tocaron `src/main`, `StubMercadoPago`, los soportes ni `src/test/resources`.

### Tasks
- T44: [x]
- T45: [x]. Las tres corridas están en el informe, y yo corrí `./init.sh` completo (ver abajo).

### `./init.sh` completo (reviewer, 2026-11-16)
`Tests run: 1279, Failures: 0, Errors: 0, Skipped: 0`, con la línea `[OK] Entorno listo. Puedes empezar a trabajar.` y exit 0. No hubo `[FAIL]`.
En failsafe, `PagoConcurrenciaIT` da `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`. `cincoWebhooksIguales` tardó 0.371 s.

### Checkpoints aplicables
- C1: [x]
- C2: [x]
- C3: [x]. No se tocó producción.
- C4: [x]
- C6: [x]
- C5: n/a

### Observaciones (no bloqueantes)
1. **El formato `"HU-13 R43 (AC003): …"` no lo cuenta el regex de `init.sh`** (línea 170). Es la misma situación que en HU-12: R43 ya está cubierto y el formato viene del prompt.
2. **La ruta de R44 (violación de unicidad atrapada) no se ejercitó.** El bloqueo serializa a las 5 notificaciones antes de leer el id, como documenta el implementer. La aserción vale por R42 o por R44, así que la evidencia de AC003 debe decir que el freno fue R42.
