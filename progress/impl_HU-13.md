# Implementación — HU-13 · Pago con tarjeta

- **Estado:** T1–T22, T24 y T25–T43 terminadas. **T23 no se ha ejecutado:** queda diferida por D8, como texto sin casilla. Mercado Pago no se probó dentro de la aplicación y S1, S5 y S6 siguen sin comprobar.
- **Pantallas:** ningún agente abrió la pantalla de pago simulado en un navegador. La prueba es del dueño: ver "Pendiente para el dueño".
- **Rama:** `feature/HU-13-pago-tarjeta`. Sin commit ni push.
- **Migración:** `V4_1__pago.sql`; `V4_1` seguía libre en `origin/develop` el 2026-10-30.
- **Patrones:** Adapter (`PasarelaPago`, `MercadoPagoAdapter`, `PasarelaSimulada`) y Facade (`CheckoutFacade`). Sin Factory Method: la pasarela se elige con `@ConditionalOnProperty`.

## Verificación

`./init.sh` completo sobre el código final de la adenda, con Docker: **salida 0**.

| Bloque | Resultado |
|---|---|
| Pruebas unitarias | 824, sin fallos, errores ni omitidas |
| Pruebas de integración | 587, sin fallos, errores ni omitidas |
| ESLint y `npm run build` | Sin advertencias; compila |
| Reglas de arquitectura, colores y secretos | En verde |
| `R<n>` sin prueba para HU-13 | Ninguno: sin avisos de `init.sh` |

Las clases nuevas de esta historia suman 184 pruebas unitarias y 155 de integración. El aviso de `./init.sh` sobre `CIFRADO_LLAVE` y las otras llaves es del entorno de esta terminal, no de la historia.

**Lo que estas pruebas no demuestran.** Mercado Pago no se probó dentro de la aplicación: `MercadoPagoAdapter` solo corrió contra `StubMercadoPago`, y la firma solo se comprobó con el vector del validador oficial (S1). El token de la pasarela simulada no es una firma de proveedor. Ningún agente abrió las pantallas en un navegador.

## Pendiente para el dueño

**Probar la pantalla de pago simulado en el navegador. Ningún agente ha abierto estas pantallas:** solo se comprobó su API con pruebas automáticas, y que el frontend compila y pasa el lint. Lo que se vea distinto de esta guía es un hallazgo.

### Preparación

Necesitas las variables del README, paso 2 (`CIFRADO_LLAVE`, `CIFRADO_LLAVE_HUELLA` y `ALMACENAMIENTO_LLAVE_FIRMA`), una cuenta con rol `USUARIO` y un espacio publicado con horario libre. No hacen falta credenciales de Mercado Pago ni `PAGOS_SECRETO_WEBHOOK`.

Desde la raíz del repo, en terminales separadas:

```bash
docker compose -f 1_Database/compose.yaml up -d
PAGOS_PASARELA=simulada ./2_Backend/mvnw -f 2_Backend/pom.xml spring-boot:run
```

```bash
npm --prefix 3_frontend run dev
```

El log del backend debe decir `Pasarela de pago SIMULADA activa`. Para mirar los resultados, en una tercera terminal:

```bash
docker exec espacioya-postgres psql -U espacioya -d espacioya -c "SELECT id, reserva_id, id_transaccion_pasarela, estado, monto, ultimos4 FROM pago ORDER BY id DESC LIMIT 6;" -c "SELECT id, estado, expira_en FROM reserva ORDER BY id DESC LIMIT 4;"
```

Cada caso usa **una reserva nueva**. En `http://localhost:5173`, inicia sesión como `USUARIO`, abre el espacio, elige bloques, pulsa **Reservar** y anota el número de la URL `/reservas/<id>`.

### 1. Aprobar

1. Pulsa **Pagar**.
2. **Debes ver** `/reservas/<id>/pago-simulado`, con el título «Pago simulado», arriba el aviso «Simulación: esta pantalla no es una pasarela de pago. No se cobra nada ni se usa ninguna tarjeta.», el espacio, la fecha, el horario y el monto, y dos botones: **Aprobar pago simulado**, primario, y **Rechazar pago simulado**, secundario. **Ningún campo para escribir.**
3. Pulsa **Aprobar pago simulado**.
4. **Debes ver** `/reservas/<id>?pago=exito`, la etiqueta **Confirmada** y «Tu reserva está confirmada. Te enviamos el comprobante por correo.», sin **Pagar** ni contador.
5. **En la consulta:** reserva `CONFIRMADA` y una fila `PAGADO` con `sim-<id>-APROBADO-1`, el monto de la reserva y `4242`. **En el log:** `Envío de comprobante de pago simulado` y `Envío de aviso de reserva pagada simulado`. El correo es simulado hasta HU-25.
6. Pulsa **Atrás** en el navegador: no debes volver a los botones.

### 2. Rechazar

1. En una reserva nueva, pulsa **Pagar** y luego **Rechazar pago simulado**.
2. **Debes ver** `/reservas/<id>?pago=fallo`, la etiqueta **Pendiente de pago**, el contador, «Tu pago no se aprobó. Puedes intentarlo de nuevo mientras tu reserva siga vigente.» y **Pagar**.
3. **En la consulta:** reserva `PENDIENTE_PAGO` y una fila `RECHAZADO` con `sim-<id>-RECHAZADO-1`.
4. Pulsa **Pagar**: vuelve a la misma pantalla. Rechaza otras dos veces.
5. **Tras el tercero debes ver** «Alcanzaste el máximo de intentos de pago…», sin **Pagar**. **En la consulta:** tres filas `RECHAZADO`, con terminación `-1`, `-2` y `-3`, y la reserva sigue `PENDIENTE_PAGO`.

### 3. Pago tardío

1. En una reserva nueva, pulsa **Pagar** y **deja abierta** la pantalla de pago simulado. **No la recargues:** al recargarla, con la reserva vencida ya no ofrece botones.
2. Haz que la reserva venza. Lo más corto, con el número de tu reserva:

```bash
docker exec espacioya-postgres psql -U espacioya -d espacioya -c "UPDATE reserva SET expira_en = now() WHERE id = <id>;"
```

   También puedes esperar los 15 minutos. Este `UPDATE` es solo para tu base local: no lo hagas en UAT.

3. En la pantalla que dejaste abierta, pulsa **Aprobar pago simulado**.
4. **Debes ver** «Recibimos un pago que no pudimos aplicar a esta reserva. Gestionaremos su devolución.» y «Se acabó el tiempo. Liberamos los bloques: puedes reservar de nuevo.». **La reserva no queda Confirmada.**
5. **En la consulta:** una fila `PAGADO_TARDIO` con `sim-<id>-APROBADO-1`, y la reserva en `EXPIRADA`, o en `PENDIENTE_PAGO` hasta que corra el proceso de expiración, que pasa cada minuto. **En el log:** ningún `Envío de comprobante de pago simulado` por esta reserva.
6. Abre otra vez `/reservas/<id>/pago-simulado`. **Debes ver** el aviso de simulación, «Este pago simulado ya no está disponible.» y «Volver a la reserva», sin botones.

### Correcciones que tampoco se han visto (DEF-01 y DEF-02)

- **DEF-01:** pulsa **Pagar** y luego **Atrás**. El botón debe decir **Pagar**, no «Enviando…». Si el navegador recarga la página en lugar de restaurarla, se verá bien aunque el arreglo no actúe, así que esto solo confirma el caso que tu navegador produzca.
- **DEF-02:** abre a mano `/reservas/<id>?pago=exito` de una reserva pendiente, y apaga el backend mientras dice «Estamos confirmando tu pago…». La reserva debe seguir en pantalla y, unos 30 segundos después, debe decir «Tu pago sigue en revisión…».

### Resultado

| Caso | Resultado | Nota |
|---|---|---|
| 1. Aprobar | _pendiente_ | |
| 2. Rechazar y tres rechazos | _pendiente_ | |
| 3. Pago tardío | _pendiente_ | |
| DEF-01 y DEF-02 | _pendiente_ | |

## Guía para cuando se conecte Mercado Pago (T23, no ejecutada)

**No se ha ejecutado y no es un pendiente de esta entrega:** Eduardo Saldaña decidió diferirla (D8). Es la guía para el día en que se conecte. **Las credenciales no se pegan en el chat ni en archivos del repositorio**: solo en tu terminal. Si (a) falla, la historia pasa a `blocked` con la pregunta para el dueño; si ya está `done`, vuelve a `in_progress`.

**Antes de cambiar a `mercadopago`:** espera a que no haya reservas pendientes, porque una pendiente conserva la URL de la pantalla simulada (RS-6), y decide qué hacer con las filas `sim-` de `pago` (RS-5).

### Qué hace falta

- El vendedor y el comprador de prueba de Mercado Pago, y un navegador en ventana privada para el comprador.
- Un túnel HTTPS hacia el puerto 8080 de tu máquina (por ejemplo `cloudflared` o `ngrok`) o el backend en UAT.
- Variables, solo por nombre:

| Variable | Valor |
|---|---|
| `PAGOS_PASARELA` | `mercadopago` |
| `PAGOS_LLAVE_SECRETA` | Llave de las credenciales de prueba del vendedor de prueba |
| `PAGOS_SECRETO_WEBHOOK` | Firma secreta de *Tus integraciones > Webhooks > Configurar notificación* |
| `PAGOS_URL_NOTIFICACION` | `https://<tu-túnel>/api/pagos/webhook` |
| `FRONTEND_URL` | Opcional. Solo si el frontend también va por HTTPS: sin él, el comprador no vuelve solo a la reserva (S12) y se abre `/reservas/<id>` a mano |

Siguen haciendo falta las variables del README, paso 2: `CIFRADO_LLAVE`, `CIFRADO_LLAVE_HUELLA` y `ALMACENAMIENTO_LLAVE_FIRMA`.

### Preparación

1. Levanta el túnel hacia `http://localhost:8080` y anota la URL pública.
2. En *Tus integraciones > Webhooks > Configurar notificación*, de la aplicación del vendedor de prueba, registra `https://<tu-túnel>/api/pagos/webhook`, marca el evento **Pagos** y copia la firma secreta. El panel no te dejó leerla la última vez: si sigue sin mostrarla, regenerarla es válido entonces, porque la prueba usa un pago nuevo y la firma vieja no se necesita.
3. Desde la raíz del repo, en terminales separadas:

```bash
docker compose -f 1_Database/compose.yaml up -d
read -r -s -p "Llave secreta: " PAGOS_LLAVE_SECRETA; echo
read -r -s -p "Firma secreta: " PAGOS_SECRETO_WEBHOOK; echo
export PAGOS_LLAVE_SECRETA PAGOS_SECRETO_WEBHOOK
export PAGOS_PASARELA=mercadopago
export PAGOS_URL_NOTIFICACION=https://<tu-túnel>/api/pagos/webhook
./2_Backend/mvnw -f 2_Backend/pom.xml spring-boot:run
```

```bash
npm --prefix 3_frontend run dev
```

`read -s` evita que los valores queden en el historial. El arranque es la primera comprobación: sin alguna de las tres, el backend no arranca (R58).

Para mirar los resultados:

```bash
docker exec espacioya-postgres psql -U espacioya -d espacioya -c "SELECT id, reserva_id, estado, monto, ultimos4 FROM pago ORDER BY id;" -c "SELECT id, estado FROM reserva ORDER BY id DESC LIMIT 5;" -c "SELECT accion, detalle FROM auditoria WHERE accion LIKE 'PAGO%' OR accion IN ('RESERVA_CONFIRMADA', 'CHECKOUT_INICIADO') ORDER BY id DESC LIMIT 10;"
```

### (a) Una notificación real pasa la firma (S1)

1. Sin pagar nada: en el panel, usa **Simular notificación** con el tipo `payment` y mira la respuesta. **503 significa que la firma pasó** y falló la consulta del id falso (S10). **401 significa que la firma no coincide.**
2. Con un 401, revisa que `PAGOS_SECRETO_WEBHOOK` sea la firma de esa aplicación y repite. Si sigue en 401, no declares el fallo todavía: el pago real del paso 3 es la prueba que decide.
3. Crea una reserva como `USUARIO`, pulsa **Pagar** y paga en la ventana privada con el comprador de prueba y una tarjeta de prueba, con el titular que Mercado Pago indique para un pago aprobado.
4. Observa el log del backend y el del túnel.

**Lo confirma:** el túnel muestra `POST /api/pagos/webhook?...type=payment...` con **200**; el log **no** dice `Notificación de pago rechazada por firma inválida`; `pago` tiene una fila `PAGADO`.

**Qué anotar:** si la firma pasó con una `notification_url` por preferencia, y el nombre real del parámetro en la URL del túnel (S2 ya apunta a `data.id`).

**Si falla:** con firma inválida en un pago real, el secreto del panel no firma las notificaciones por preferencia. Cambia la historia a `blocked` y pregunta si se usa la URL del panel en lugar de `notification_url`. Eso cambia el spec: no se ajusta el código sin él.

### (b) Tipos de pago excluidos (S5)

La variable solo existe en la terminal del backend: en una terminal nueva, repite el `read -s`.

```bash
read -r -s -p "Llave secreta: " PAGOS_LLAVE_SECRETA; echo
curl -s https://api.mercadopago.com/v1/payment_methods -H "Authorization: Bearer $PAGOS_LLAVE_SECRETA" | node -e "let d='';process.stdin.on('data',c=>d+=c).on('end',()=>console.log([...new Set(JSON.parse(d).map(m=>m.payment_type_id))].join('\n')))"
```

**Lo confirma:** todo lo que imprime y no es tarjeta (`credit_card`, `debit_card`, `prepaid_card`) está en `TIPOS_EXCLUIDOS` de `MercadoPagoAdapter`: `ticket`, `atm`, `bank_transfer`, `digital_wallet`, `digital_currency`. Y el checkout del paso (a) solo ofreció tarjeta, sin cuotas.

**Si falla:** anota el tipo que falta; agregarlo a la lista es un cambio de una línea. Si **Pagar** respondió 503 y el log dice `La pasarela respondió 400 al crear el checkout`, Mercado Pago rechazó un tipo de la lista: anota cuál.

### (c) El cierre del checkout (S6)

1. Crea otra reserva y pulsa **Pagar**.
2. Paga 3 veces desde el mismo enlace con la tarjeta de prueba y el titular que Mercado Pago indique para un rechazo. Entre intentos, vuelve a la reserva y pulsa **Pagar**: debe ser el mismo enlace.
3. Tras el tercer rechazo, mira el log y abre otra vez el enlace.

**Lo confirma:** `pago` tiene 3 filas `RECHAZADO`; el log **no** dice `La pasarela respondió ... al cerrar el checkout`; el enlace ya no deja pagar; la reserva muestra «Alcanzaste el máximo de intentos…», sin **Pagar**, y `POST /api/reservas/<id>/pago` responde 429.

**Si falla:** el `PUT` es de mejor esfuerzo (R41, riesgo aceptado en S6): el corte real es el 429. Anota el código del log; no bloquea la historia.

### (d) De punta a punta

Usa los pagos de (a) y (c).

**Lo confirma, pago aprobado:** la reserva queda `CONFIRMADA`; `pago` tiene `PAGADO`, el monto de la reserva y los últimos 4 dígitos; la auditoría tiene `PAGO_APROBADO` y `RESERVA_CONFIRMADA`; el log dice `Envío de comprobante de pago simulado` y `Envío de aviso de reserva pagada simulado`; la pantalla dice «Tu reserva está confirmada…».

**Lo confirma, pago rechazado:** una fila `RECHAZADO`, auditoría `PAGO_RECHAZADO`, la reserva sigue `PENDIENTE_PAGO` y la pantalla dice «Tu pago no se aprobó…» con **Pagar**.

### Resultado de T23

| Punto | Resultado | Nota |
|---|---|---|
| (a) Firma real (S1) y parámetro (S2) | _pendiente_ | |
| (b) Tipos excluidos (S5) | _pendiente_ | |
| (c) Cierre del checkout (S6) | _pendiente_ | |
| (d) Aprobado y rechazado de punta a punta | _pendiente_ | |

## Trazabilidad

Generado de los `@DisplayName("HU-13 R<n>: …")` del código de pruebas. Los 87 `R<n>` tienen al menos una prueba; R59–R87 son los de la adenda.

- R1 → `PagoControllerIT.iniciaElPago`, `WebhookPagoIT.iniciaContraLaPasarela`, `WebhookPagoIT.puntaAPunta`, `ReglasPagoTest.disponible`, `CheckoutFacadeTest.iniciaEnOrden`, `EvaluacionCobroTest.disponible`, `MercadoPagoAdapterTest.devuelveElInitPoint`
- R2 → `WebhookPagoIT.iniciaContraLaPasarela`, `MercadoPagoAdapterTest.enviaMontoMonedaYReferencia`, `SesionesCheckoutTest.guardaLaFila`
- R3 → `PagoControllerIT.ignoraElCuerpo`
- R4 → `WebhookPagoIT.elEnlaceVenceAntes`, `MercadoPagoAdapterTest.enviaElVencimiento`, `SesionesCheckoutTest.creaElCheckout`
- R5 → `MercadoPagoAdapterTest.enviaLaConfiguracionDelCheckout`, `MercadoPagoAdapterTest.sinHttpsNoHayRetorno`, `MercadoPagoAdapterTest.conHttpsHayRetorno`
- R6 → `PagoControllerIT.reutilizaElCheckout`, `SesionesCheckoutTest.reutilizaElCheckout`
- R7 → `PagoConcurrenciaIT.dosPagarALaVez`, `SesionesCheckoutTest.releeAlPerderLaCarrera`, `SesionesCheckoutTest.otraViolacionSeRelanza`
- R8 → `PagoControllerIT.iniciarNoConfirma`, `CheckoutFacadeTest.iniciarNoCambiaNada`
- R9 → `PagoControllerIT.auditaElInicio`, `CheckoutFacadeTest.auditaElInicio`
- R10 → `PagoControllerIT.expirada`, `PagoControllerIT.vencidaSinProcesar`, `PagoControllerIT.yaConfirmada`, `ReglasPagoTest.noVigente`, `CheckoutFacadeTest.noDisponible`, `EvaluacionCobroTest.noVigente`
- R11 → `PagoControllerIT.ultimoTramo`, `ReglasPagoTest.ultimoTramo`, `EvaluacionCobroTest.ultimoTramo`
- R12 → `PagoControllerIT.espacioPausado`, `PagoControllerIT.arrendadorSuspendido`, `ReglasPagoTest.espacioNoVisible`, `EvaluacionCobroTest.espacioNoVisible`, `ReservasPorCobrarImplTest.deUsuarioConEspacioNoVisible`
- R13 → `PagoControllerIT.dosRechazos`, `PagoControllerIT.tresRechazos`, `PagoControllerIT.ventanaDeRechazos`, `PagoControllerIT.soloCuentanLosRechazados`, `ReglasPagoTest.rechazos`, `ReglasPagoTest.ventanaDeRechazos`, `EvaluacionCobroTest.intentosAgotados`
- R14 → `PagoControllerIT.otrosRoles`, `EvaluacionCobroTest.otroRol`
- R15 → `PagoControllerIT.idInvalido`, `PagoControllerIT.reservaAjena`, `ReglasPagoTest.idBienEscrito`, `ReglasPagoTest.idMalEscrito`, `EvaluacionCobroTest.idMalEscrito`, `EvaluacionCobroTest.ajena`, `ReservasPorCobrarImplTest.deUsuarioAjena`
- R16 → `PagoControllerIT.sinSesion`
- R17 → `ManejadorGlobalErroresTest.servicioExternoResponde503`, `WebhookPagoIT.pasarelaCaidaAlIniciar`, `WebhookPagoIT.reintentoTrasLaCaida`, `MercadoPagoAdapterTest.crearConError`, `MercadoPagoAdapterTest.crearSinInitPoint`, `MercadoPagoAdapterTest.crearConTiempoAgotado`, `SesionesCheckoutTest.pasarelaCaida`
- R18 → `PagoControllerIT.rolAntesQueReserva`, `PagoControllerIT.reservaAntesQueVigencia`, `PagoControllerIT.rechazosAlFinal`, `ReglasPagoTest.ordenDeLasComprobaciones`, `EvaluacionCobroTest.ordenRolAntesQueReserva`, `EvaluacionCobroTest.ordenConTresRechazos`
- R19 → `PagoControllerIT.estadoSinPagos`, `PagoControllerIT.estadoDelUltimoPago`, `PagoControllerIT.estadoConIntentosAgotados`, `PagoControllerIT.estadoSinPagoDisponible`, `CheckoutFacadeTest.estadoConPago`, `CheckoutFacadeTest.estadoSinPagos`, `EvaluacionCobroTest.resumenDisponible`, `EvaluacionCobroTest.resumenAgotado`, `EvaluacionCobroTest.resumenSinRol`
- R20 → `PagoControllerIT.iniciaElPago`, `PagoControllerIT.estadoSinPagos`, `PagoControllerIT.noExponeDatosDelPago`, `CheckoutFacadeTest.losDtoNoExponenDatosDelPago`
- R21 → `PagoControllerIT.estadoNoEscribe`, `CheckoutFacadeTest.estadoNoEscribe`
- R22 → `WebhookPagoIT.sinSesion`
- R23 → `WebhookPagoIT.otrosFormatos`, `MercadoPagoAdapterTest.otrosFormatosSeIgnoran`, `PasarelaSimuladaTest.otroFormato`, `WebhookPagoServiceTest.otroFormato`
- R24 → `WebhookPagoIT.firmaMala`, `WebhookPagoIT.firmaAlterada`, `WebhookPagoIT.sinIdOSinFirma`, `WebhookPagoIT.aceptaDataId`, `MercadoPagoAdapterTest.aceptaElVector`, `MercadoPagoAdapterTest.aceptaDataId`, `MercadoPagoAdapterTest.dataIdPunteadoTienePrioridad`, `MercadoPagoAdapterTest.rechazaLoAlterado`, `MercadoPagoAdapterTest.rechazaFirmaMalformada`, `MercadoPagoAdapterTest.rechazaSinIdOSinSolicitud`, `PasarelaSimuladaTest.rechazaLoQueNoTraeElSecreto`, `PasarelaSimuladaTest.sinSecretoNadaPasa`, `WebhookPagoServiceTest.firmaInvalida`
- R25 → `WebhookPagoIT.consultaElPago`, `MercadoPagoAdapterTest.traduceUnPagoAprobado`, `WebhookPagoServiceTest.consultaAntesDeBloquear`
- R26 → `WebhookPagoIT.elCuerpoMiente`, `WebhookPagoIT.liveModeNoImporta`, `MercadoPagoAdapterTest.traduceUnPagoAprobado`, `PasarelaSimuladaTest.consultaUnPagoSimulado`
- R27 → `ManejadorGlobalErroresTest.servicioExternoResponde503`, `WebhookPagoIT.consultaFallida`, `MercadoPagoAdapterTest.consultaConError`, `MercadoPagoAdapterTest.aprobadoSinMonto`, `PasarelaSimuladaTest.idDesconocido`, `WebhookPagoServiceTest.consultaFallida`
- R28 → `WebhookPagoIT.sinResultado`, `MercadoPagoAdapterTest.otrosEstadosNoTienenResultado`, `WebhookPagoServiceTest.sinResultado`
- R29 → `WebhookPagoIT.referenciaInexistente`, `WebhookPagoIT.referenciaInvalida`, `MercadoPagoAdapterTest.referenciaNula`, `WebhookPagoServiceTest.sinReserva`, `WebhookPagoServiceTest.referenciaInvalida`, `ReservasPorCobrarImplTest.reservaInexistente`
- R30 → `WebhookPagoIT.guardaElPago`, `WebhookPagoIT.montoSinCeroFinal`, `WebhookPagoIT.sinUltimos4`, `ReglasPagoTest.pagado`, `MercadoPagoAdapterTest.ultimos4Ausentes`, `WebhookPagoServiceTest.aprobadoGuardaPagado`
- R31 → `WebhookPagoIT.confirmaLaReserva`, `WebhookPagoIT.puntaAPunta`, `WebhookPagoServiceTest.aprobadoConfirma`, `ReservaTest.confirmaUnaVigente`, `ReservaTest.noConfirmaUnaVencida`, `ReservaTest.noConfirmaOtrosEstados`, `ReservasPorCobrarImplTest.confirmaYAudita`
- R32 → `WebhookPagoIT.bloquesPagados`, `WebhookPagoIT.noSePuedeBloquearLaFecha`, `ReservasPorCobrarImplTest.confirmarNoTocaLosBloques`
- R33 → `WebhookPagoIT.otroMonto`, `WebhookPagoIT.otraMoneda`, `ReglasPagoTest.montoDistinto`, `WebhookPagoServiceTest.montoDistinto`
- R34 → `WebhookPagoIT.tardioConExpirada`, `WebhookPagoIT.tardioConVencidaSinProcesar`, `WebhookPagoIT.segundoPagoSobreConfirmada`, `ReglasPagoTest.tardio`, `WebhookPagoServiceTest.tardio`, `ReservaTest.noConfirmaUnaVencida`, `ReservaTest.noConfirmaOtrosEstados`, `ReservasPorCobrarImplTest.confirmarNoVigente`
- R35 → `DisponibilidadServiceImplTest.bloquearEspacioNoFiltra`, `WebhookPagoIT.espacioNoVisible`, `WebhookPagoIT.cuentasSuspendidas`
- R36 → `DisponibilidadServiceImplTest.bloquearEspacioDevuelveLoLeidoBajoBloqueo`, `DisponibilidadServiceImplTest.bloquearEspacioInexistente`, `PagoConcurrenciaIT.evaluaLaVigenciaTrasElBloqueo`, `ReservasPorCobrarImplTest.ordenDeLosBloqueos`, `ReservasPorCobrarImplTest.devuelveLaReservaBloqueada`, `ReservasPorCobrarImplTest.venceMientrasEspera`
- R37 → `PagoConcurrenciaIT.webhookFrenteAExpiracion`, `PagoConcurrenciaIT.laExpiracionEsperaAlWebhook`
- R38 → `PagoConcurrenciaIT.exigenUnaTransaccion`
- R39 → `WebhookPagoIT.rechazado`, `WebhookPagoIT.rechazadoFueraDeOrden`, `MercadoPagoAdapterTest.traduceUnPagoRechazado`, `WebhookPagoServiceTest.rechazado`, `WebhookPagoServiceTest.rechazadoFueraDeOrden`
- R40 → `WebhookPagoIT.tercerRechazoCierra`, `MercadoPagoAdapterTest.cierraElCheckout`, `SesionesCheckoutTest.cierra`, `SesionesCheckoutTest.cerrarSinCheckout`, `WebhookPagoServiceTest.dosRechazosNoCierran`, `WebhookPagoServiceTest.tresRechazosCierranTrasElCommit`
- R41 → `DespuesDelCommitTest.atrapaElFallo`, `WebhookPagoIT.cierreFallido`, `MercadoPagoAdapterTest.cerrarConError`, `PasarelaSimuladaTest.cerrarNoFalla`
- R42 → `WebhookPagoIT.aprobadoRepetido`, `WebhookPagoIT.rechazoRepetido`, `WebhookPagoServiceTest.repetido`
- R43 → `PagoConcurrenciaIT.dosWebhooksIguales`
- R44 → `WebhookPagoServiceTest.violacionDeLaUnicidad`, `WebhookPagoServiceTest.otraViolacion`
- R45 → `WebhookPagoIT.unicidadDeLaTransaccion`
- R46 → `WebhookPagoIT.unPagadoPorReserva`
- R47 → `WebhookPagoIT.checksDePago`, `WebhookPagoIT.estadosDePago`, `WebhookPagoIT.unCheckoutPorReserva`
- R48 → `DespuesDelCommitTest.correTrasElCommit`, `CuentaServiceImplTest.correoDeDevuelveElCorreo`, `CuentaServiceImplTest.correoDeInexistente`, `WebhookPagoIT.comprobante`, `AvisosPagoTest.nadaAntesDelCommit`, `AvisosPagoTest.enviaElComprobante`
- R49 → `DespuesDelCommitTest.correTrasElCommit`, `CuentaServiceImplTest.correoDeDevuelveElCorreo`, `WebhookPagoIT.avisoAlArrendador`, `AvisosPagoTest.enviaElAviso`
- R50 → `DespuesDelCommitTest.noCorreConRollback`, `WebhookPagoIT.sinConfirmarNoHayCorreos`, `AvisosPagoTest.nadaAntesDelCommit`
- R51 → `DespuesDelCommitTest.atrapaElFallo`, `WebhookPagoIT.correoFallido`, `AvisosPagoTest.unFalloNoImpideElOtro`
- R52 → `PagoSimuladoIT.idConPrefijo`, `WebhookPagoIT.auditaElPago`, `WebhookPagoIT.auditaLosDemasEstados`, `WebhookPagoServiceTest.auditaAprobado`, `WebhookPagoServiceTest.auditaLosDemas`
- R53 → `WebhookPagoIT.auditaLaConfirmacion`, `ReservasPorCobrarImplTest.confirmaYAudita`
- R54 → `WebhookPagoIT.soloUltimos4`, `WebhookPagoIT.auditoriaSinSecretos`
- R55 → `WebhookPagoIT.usaElAdaptador`, `SeleccionPasarelaTest.mercadoPago`
- R56 → `PagoControllerIT.usaLaSimulada`, `PasarelaSimuladaTest.creaUnCheckoutSimulado`, `PasarelaSimuladaTest.aceptaElSecreto`, `SeleccionPasarelaTest.simulada`
- R57 → `SeleccionPasarelaTest.sinPropiedad`, `SeleccionPasarelaTest.otroValor`
- R58 → `SeleccionPasarelaTest.sinLlave`, `SeleccionPasarelaTest.sinSecreto`, `SeleccionPasarelaTest.sinUrl`, `SeleccionPasarelaTest.elErrorNombraLaVariable`
- R59 → `PagoControllerIT.iniciaElPago`, `PagoSimuladoIT.pagarLlevaALaPantalla`, `PasarelaSimuladaTest.creaUnCheckoutSimulado`
- R60 → `PagoSimuladoIT.disponibleConCheckout`, `PagoSimuladoIT.noDisponibleSinCheckout`, `PagoSimuladoIT.noDisponibleSinPagoDisponible`, `PagoSimuladoIT.noDisponibleConEspacioPausadoOTresRechazos`, `SimulacionPagoTest.consultaDisponible`, `SimulacionPagoTest.consultaSinCheckout`, `SimulacionPagoTest.consultaSinPagoDisponible`
- R61 → `PagoSimuladoIT.consultarNoEscribe`, `SimulacionPagoTest.consultarSoloLee`
- R62 → `PagoSimuladoIT.pasaPorElWebhook`, `PasarelaSimuladaTest.armaLaNotificacion`, `SimulacionPagoTest.ordenDelCaminoFeliz`, `SimulacionPagoTest.aprobarEsSiempreElIntentoUno`
- R63 → `PagoSimuladoIT.aprobadoConfirma`, `PagoSimuladoIT.aprobadoConEspacioPausado`
- R64 → `PagoSimuladoIT.aprobadoEnviaLosCorreos`, `PagoSimuladoIT.sinCommitNoHayCorreos`
- R65 → `PagoSimuladoIT.tardioConReservaVencida`, `PagoSimuladoIT.tardioConReservaExpirada`
- R66 → `PagoSimuladoIT.rechazado`
- R67 → `PagoSimuladoIT.tercerRechazoCierra`
- R68 → `PagoSimuladoIT.aprobadoRepetido`, `SimulacionPagoTest.aprobarEsSiempreElIntentoUno`
- R69 → `PagoSimuladoIT.dosAprobadosALaVez`
- R70 → `PagoSimuladoIT.dosRechazos`, `SimulacionPagoTest.rechazarSaleDelConteo`
- R71 → `PagoSimuladoIT.ignoraLoDemasDelCuerpo`, `SimulacionPagoTest.elMontoSaleDeLaReserva`
- R72 → `PagoSimuladoIT.trasReiniciar`, `PasarelaSimuladaTest.funcionaTrasReiniciar`
- R73 → `PagoSimuladoIT.resultadoInvalido`
- R74 → `PagoSimuladoIT.otrosRoles`, `EvaluacionCobroTest.simulacionConOtroRol`, `EvaluacionCobroTest.exigirRolConUsuario`, `SimulacionPagoTest.otroRol`, `SimulacionPagoTest.consultarExigeElRol`
- R75 → `PagoSimuladoIT.idInvalido`, `PagoSimuladoIT.reservaAjena`, `EvaluacionCobroTest.simulacionConIdMalEscrito`, `EvaluacionCobroTest.simulacionConReservaAjena`, `SimulacionPagoTest.reservaAjena`
- R76 → `PagoSimuladoIT.sinSesion`
- R77 → `PagoSimuladoIT.tresRechazos`, `EvaluacionCobroTest.simulacionConRechazos`, `SimulacionPagoTest.intentosAgotados`
- R78 → `PagoSimuladoIT.sinCheckout`, `SesionesCheckoutTest.existe`, `SimulacionPagoTest.sinCheckout`
- R79 → `PagoSimuladoIT.rolAntesQueReserva`, `PagoSimuladoIT.reservaAntesQueRechazos`, `PagoSimuladoIT.rechazosAntesQueCheckout`, `PagoSimuladoIT.noRepiteLaVigencia`, `EvaluacionCobroTest.simulacionNoMiraVigencia`
- R80 → `PagoSimuladoIT.disponibleConCheckout`, `PagoSimuladoIT.noExponeDatosDelPago`, `SimulacionPagoTest.losDtoSoloTienenSuCampo`
- R81 → `WebhookPagoIT.sinBeansDeSimulacion`, `WebhookPagoIT.pagarNoLlevaALaPantalla`
- R82 → `WebhookPagoIT.simulacionNoExisteConSesion`, `WebhookPagoIT.simulacionSinSesion`
- R83 → `PagoSimuladoSinSecretoIT.elWebhookRechazaTodo`, `PagoSimuladoSinSecretoIT.sinFirma`
- R84 → `PagoSimuladoSinSecretoIT.laPantallaApruebaIgual`, `PasarelaSimuladaTest.sinSecretoLaInternaPasa`
- R85 → `PagoSimuladoIT.elTokenNoSale`, `PasarelaSimuladaTest.cadaInstanciaTieneSuToken`
- R86 → `PagoSimuladoIT.auditaQuienDecidio`, `PagoSimuladoIT.auditaCadaIntento`, `SimulacionPagoTest.auditaQuienDecidio`
- R87 → `PagoSimuladoIT.idConPrefijo`

**R44** no tiene IT: el bloqueo de la fila serializa las notificaciones y el `UNIQUE` no se puede alcanzar por la API, así que se prueba en `WebhookPagoServiceTest` con el mensaje real de PostgreSQL, y el nombre de la restricción lo confirma `WebhookPagoIT.unicidadDeLaTransaccion`.

## Archivos

**Nuevos, backend:**

- `db/migration/V4_1__pago.sql`
- `pagos/domain/`: `EstadoPago`, `MotivoNoDisponible`, `Pago`, `CheckoutPago`, `ReglasPago`
- `pagos/repository/`: `PagoRepository`, `CheckoutPagoRepository`
- `pagos/dto/`: `CheckoutDTO`, `EstadoPagoDTO`
- `pagos/service/`: `CheckoutFacade`, `EvaluacionCobro`, `SesionesCheckout`, `WebhookPagoService`, `AvisosPago`
- `pagos/service/pasarela/`: `PasarelaPago`, `MercadoPagoAdapter`, `PasarelaSimulada`, `SolicitudCheckout`, `CheckoutCreado`, `NotificacionRecibida`, `PagoConsultado`, `ResultadoPasarela`, `FirmaInvalidaException`
- `pagos/service/puertos/`: `ReservasPorCobrar`, `ReservaPorCobrar`, `ReservaBloqueada`
- `pagos/controller/`: `PagoController`, `WebhookPagoController`
- `reservas/service/ReservasPorCobrarImpl`, `espacios/service/EspacioBloqueado`, `cuentas/service/ComprobantePago`, `cuentas/service/AvisoReservaPagada`
- `comun/transaccion/DespuesDelCommit`, `comun/error/ServicioExternoException`

**Nuevos, adenda:** `pagos/service/SimulacionPago`, `pagos/controller/PagoSimuladoController`, `pagos/dto/SimulacionDTO`, `pagos/dto/SimularPagoDTO`.

**Modificados por la adenda:** `PasarelaSimulada`, `EvaluacionCobro`, `SesionesCheckout`, `PagoRepository`, `CheckoutPagoRepository`, comentarios de `application.properties` y `2_Backend/.env.example`. **Sin cambios, con la misma suma de verificación antes y después:** `WebhookPagoService`, `CheckoutFacade`, `PagoController`, `WebhookPagoController`, `MercadoPagoAdapter` y `PasarelaPago`. Sin migración.

**Modificados, backend:** `comun/error/ErrorDTO`, `comun/error/ManejadorGlobalErrores`, `comun/seguridad/ConfiguracionSeguridad`, `cuentas/service/CuentaService`, `CuentaServiceImpl`, `NotificadorCorreo`, `NotificadorCorreoSimulado`, `espacios/service/DisponibilidadService`, `DisponibilidadServiceImpl`, `reservas/domain/Reserva`, `reservas/repository/ReservaRepository`, `application.properties`, `src/test/resources/config/application.properties`, `2_Backend/.env.example`.

**Pruebas nuevas:** `ReglasPagoTest`, `DespuesDelCommitTest`, `MercadoPagoAdapterTest`, `PasarelaSimuladaTest`, `SeleccionPasarelaTest`, `ReservasPorCobrarImplTest`, `EvaluacionCobroTest`, `SesionesCheckoutTest`, `CheckoutFacadeTest`, `AvisosPagoTest`, `WebhookPagoServiceTest`, `PagoControllerIT`, `WebhookPagoIT`, `PagoConcurrenciaIT`. Adenda: `SimulacionPagoTest`, `PagoSimuladoIT`, `PagoSimuladoSinSecretoIT`. Soporte: `StubMercadoPago`, `PagosSoporte`, `PagosConMercadoPagoSoporte`.

**Pruebas existentes que cambiaron:**

- Por la adenda: `PasarelaSimuladaTest.creaUnCheckoutSimulado` y `PagoControllerIT.iniciaElPago` cambian a la URL de R59, como pide `requirements.md`. `PasarelaSimuladaTest`, `EvaluacionCobroTest`, `SesionesCheckoutTest` y `WebhookPagoIT` ganan casos. `WebhookPagoServiceTest` y `PagoConcurrenciaIT` no cambiaron: el simulado no creó otro camino.
- `ManejadorGlobalErroresTest`, `ReservaTest`, `DisponibilidadServiceImplTest` y `CuentaServiceImplTest`: se agregaron casos; ninguno existente cambió.
- `RevisionIdentidadControllerIT`, `CuentaAdminControllerIT`, `UsuarioControllerIT`, `ArrendadorControllerIT` y `RecuperacionContrasenaControllerIT`: solo los dos métodos nuevos en su doble de `NotificadorCorreo`.

**Frontend:** `src/api/pagos.js` (nuevo), `pages/reservas/ResumenReserva.jsx`, `ResumenReserva.module.css`, `textosReservas.js`. Adenda: `pages/pagos/PagoSimulado.jsx`, `PagoSimulado.module.css` y `textosPagos.js` (nuevos), `src/App.jsx`.

**Docs:** `docs/requerimientos.md` (RN-08, RF-E-04 y § 9), `docs/architecture.md`, `docs/modelo-datos.md`, `docs/frontend.md`, `README.md`, `progress/HU-13.md`, `progress/doc_HU-13.md`.

## Tasks

T1–T22, T24 y T25–T43 con `[x]`. **T23 no tiene casilla ni está hecha:** queda como texto en "Diferida por decisión del dueño (D8)", y la guía de arriba no se ha ejecutado. El reviewer aprobó (`progress/review_HU-13.md`) y `feature_list.json` pasó a `done` el 2026-10-31: **código terminado, no Mercado Pago funcionando**. Quedan abiertos S1, S5 y S6, y la prueba del dueño en el navegador.

## Lo que el reviewer debe mirar

Adiciones pequeñas respecto del `design.md`, sin requirement nuevo:

1. **`ReglasPago` ganó tres métodos:** `intentosAgotados`, `inicioVentanaRechazos` e `idDeReserva`. El último porque `pagos` no puede usar el de `reservas` y `FichaService` ya tiene otra copia; esa regla existe ahora en tres módulos.
2. **`EvaluacionCobro.resumen` y el record `ResumenCobro`:** dan a `CheckoutFacade.estado` lo que necesita sin poner reglas en la Facade.
3. **`NotificacionRecibida` copia los encabezados a un mapa sin mayúsculas**, en su constructor y no en el controlador como dice el design § 6: así también lo cubren las pruebas de los adaptadores.
4. **`DespuesDelCommit`:** el texto del `log.warn` dejó de hablar de correo, porque ahora también cierra checkouts. La conducta no cambió.
5. **`PasarelaSimulada`:** por la ruta pública, con el secreto vacío rechaza todo pago (R83). Por `curl`, si no recuerda el monto tras reiniciar, `consultarPago` falla como un 404 de la pasarela real (RS-7). La pantalla no tiene ese límite (R72).
6. **`MercadoPagoAdapter`:** un pago aprobado o rechazado sin `transaction_amount` cuenta como consulta fallida (R27): no hay monto que guardar.

Comportamientos que conviene conocer:

7. **R57 y mayúsculas:** `@ConditionalOnProperty` compara sin distinguirlas, así que `Simulada` o `MERCADOPAGO` son válidos. Sigue sin haber arranque con un valor distinto, pero "otro valor" se lee como otro texto, no como otra capitalización.
8. **`MercadoPagoAdapterTest.crearConTiempoAgotado` tarda unos 7 segundos:** usa el tiempo de lectura real de DT-15, que es constante.
9. **La carrera de R37:** el primer intento solo producía `EXPIRADA+PAGADO_TARDIO` en 30 de 30 rondas, porque el webhook hace HTTP antes de bloquear. Con la espera variable salieron las dos salidas (9 y 21 en 30 rondas). Como depende del tiempo, la salida `CONFIRMADA` frente al proceso la garantiza `laExpiracionEsperaAlWebhook`, que es determinista.
10. **`SesionesCheckout.cerrar` lee `checkout_pago` y hace el `PUT` en `afterCommit`,** como dice el design: la lectura funciona tras el commit, pero la conexión sigue tomada durante el HTTP, hasta 10 s. Solo ocurre en el tercer rechazo. No se cambió para no desviarse del design.
11. **Perdedor de la carrera de R7:** su preferencia queda creada en Mercado Pago, sin guardar y sin que nadie reciba su URL. Vence sola.
12. **Pantallas:** ninguna se abrió en un navegador. Lint y build están en verde. La pantalla simulada se probó solo por su API, y DEF-01 y DEF-02 son correcciones sin reproducir.

Adiciones pequeñas de la adenda respecto del `design.md`, sin requirement nuevo:

13. **`SimularPagoDTO.M_RESULTADO`:** mensaje propio para las dos validaciones, como `CrearReservaDTO`. Sin él, el 400 mostraría el texto por defecto de Bean Validation. El design no lo fijaba.
14. **`disponible = false`:** la pantalla muestra solo lo que dice la fila de § 14.10: el aviso, el mensaje y "Volver a la reserva", sin título ni tarjeta.
15. **`firmaValida` de `PasarelaSimulada`** hace las dos comparaciones siempre, con `|`, para no revelar por tiempo cuál falló.
16. **`PagoSimuladoIT.sinCommitNoHayCorreos`:** el `doThrow` va sobre `AopTestUtils.getUltimateTargetObject(puerto)`. Sobre el proxy transaccional la llamada exige una transacción y deja a Mockito con un stub a medias, que contamina la prueba siguiente.
17. **`PAGO_SIMULADO` queda aunque `recibir` falle**, a propósito (DT-24): la auditoría va en su propia transacción, antes de `recibir`. `sinCommitNoHayCorreos` lo comprueba.

Contradicciones aceptadas por el dueño, sin resolver aquí: C1 a C5 de `design.md` § 14.13. **C5 sigue por decidir con Ariel Enriquez:** CA1 y CA4 de `docs/historias/HU-13.md` no se tocaron.
