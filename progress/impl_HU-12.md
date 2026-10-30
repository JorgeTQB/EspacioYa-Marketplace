# Implementación — HU-12 · Reserva con bloqueo temporal

- **Fecha:** 2026-10-30 · **Rama:** `feature/HU-12-reserva-bloqueo-temporal` · **Agente:** implementer
- **Tasks:** T1–T23, todas `[x]` en `specs/HU-12/tasks.md`. Ninguna quedó sin completar.
- **Migración:** `V3_3__reservas.sql`. `V3_3` estaba libre: `develop` (local y origin) en `a8234a6`, última `V3_2`.

## Verificación

`./init.sh` completo en verde el 2026-10-30 tras la vuelta 1 del review, con Docker encendido: 631 pruebas unitarias y
432 de integración sin fallos, ESLint sin advertencias, `npm run build` correcto y revisión de colores, secretos y
arquitectura sin hallazgos. Las 45 `R<n>` tienen `@DisplayName("HU-12 R<n>: …")`. Único aviso, ajeno a la historia:
faltan las llaves locales del backend, que no afectan a las pruebas (traen las suyas) y solo impiden `spring-boot:run`.

**Sin ejecutar en un navegador:** ninguna pantalla de HU-12, incluido el arreglo del contador. El frontend no tiene
pruebas: lo verificado es lint y build. Ver "Pendiente para el dueño".

## Comprobación del contador en el navegador

**Hecha por el dueño (Eduardo Saldaña, 2026-10-30):** bajó hasta `00:00` sin subir y pasó sola a "Expirada". Pasos usados:

El reviewer pidió comprobar en el navegador el cambio requerido 1 y este agente no tiene navegador. El arreglo se dedujo
del código, no se vio funcionar. Para no esperar 15 minutos:

1. Encender la base (`docker compose -f 1_Database/compose.yaml up -d`), el backend con sus llaves (README, "2. Backend")
   y el frontend (`npm --prefix 3_frontend run dev`).
2. Entrar con una cuenta de rol `USUARIO`, abrir la ficha de un espacio publicado con horario, elegir un bloque y pulsar
   "Reservar". Anotar el id de `/reservas/<id>`.
3. Dejar a la reserva 30 segundos de vida:
   `docker exec espacioya-postgres psql -U espacioya -d espacioya -c "UPDATE reserva SET creada_en = now() - interval '15 minutes', expira_en = now() + interval '30 seconds' WHERE id = <id>"`
4. Recargar `/reservas/<id>` con la pestaña de red de las herramientas de desarrollo abierta.

**Resultado esperado:** el contador baja hasta `00:00` sin volver nunca a un tiempo mayor. Como mucho unos segundos
después, la etiqueta pasa a "Expirada", aparece "Se acabó el tiempo. Liberamos los bloques: puedes reservar de nuevo." y
el contador desaparece. En la pestaña de red hay una o dos lecturas de `/api/reservas/<id>` al final, sin un bucle de
peticiones. El bloque vuelve a verse libre en la ficha. Si el contador salta a un tiempo mayor, el defecto sigue ahí.

## Trazabilidad

Rutas de prueba bajo `2_Backend/src/test/java/pe/edu/ulima/espacioya/`. `RCIT` = `reservas/controller/ReservaControllerIT`,
`CIT` = `reservas/controller/ReservaConcurrenciaIT`, `SIT` = `reservas/service/ReservaServiceImplTest`.

- R1 → `RCIT.creaLaReservaConSuDto`, `RCIT.reservaTodoElHorario`, `SIT.creaLaReserva`, `CrearReservaDTOTest.solicitudValida`
- R2 → `RCIT.guardaLaFilaDeReserva`, `ReservaTest.naceConLaRetencionDeQuinceMinutos`, `SIT.creaLaReserva`,
  `SIT.elInstanteSeTomaTrasRetenerElEspacio`
- R3 → `RCIT.guardaPrecioYMonto`, `RCIT.elPrecioQuedaCopiado`, `ReservaTest.calculaElMontoSinRedondeo`,
  `ReservaTest.montoMaximo`, `SIT.elMontoSaleDelServidor`, `DisponibilidadServiceImplTest.retenerDevuelveNombreYPrecio`
- R4 → `RCIT.guardaLosBloques`, `SIT.guardaUnBloquePorHora`
- R5 → `RCIT.ignoraLoQueDecideElServidor`, `CrearReservaDTOTest.ignoraCamposDesconocidos`
- R6 → `ReservasDelEspacioImplIT.vigenteYConfirmada`, `.todasLasFilasActivas`, `.reservasNoActivas`, `.otroEspacio`,
  `.extremosDelRango`, `ReservasDelEspacioImplTest.traduceBloques`, `RCIT.laRetencionSaleDeLaRutaPublica`
- R7 → `ReservasDelEspacioImplTest.loteConUnaConsulta`, `.loteVacioNoConsulta`, `ReservasDelEspacioImplIT.loteContraLaBase`
- R8 → `RCIT.laRetencionSaleDeLaRutaPublica`, `RCIT.unaConfirmadaNoSeOfrece`
- R9 → `RCIT.expiraALosQuinceMinutos`, `ExpiracionReservasTest.expiraYDesactiva`, `ReservaTest.limiteDeVencimiento`,
  `ReservaTest.expiraUnaVencida`
- R10 → `RCIT.expiraALosQuinceMinutos`, `ExpiracionReservasTest.expiraYDesactiva`
- R11 → `RCIT.noTocaVigentesNiConfirmadas`, `RCIT.expiraALosQuinceMinutos`, `ExpiracionReservasTest.sinVencidas`,
  `ReservaTest.noExpiraUnaVigente`, `ReservaTest.noExpiraOtrosEstados`
- R12 → `ReservasDelEspacioImplIT.vencidaSinProcesar`, `.loteContraLaBase`, `RCIT.vencidaSinProcesarNoOcupa`
- R13 → `ProgramacionExpiracionTest.activaEjecutaElProceso`, `.valoresPorDefecto`
- R14 → `ProgramacionExpiracionTest.inactivaNoPrograma`
- R15 → `ExpiracionReservasTest.unFalloNoDetieneLasDemas`
- R16 → `ExpiracionReservasTest.yaNoVencidaTrasElBloqueo`, `ReservaTest.noExpiraUnaVigente`, `ReservaTest.noExpiraOtrosEstados`
- R17 → `RCIT.reservaUnBloqueVencidoSinProcesar` (trampa del `flush`), `ExpiracionReservasTest.liberaLasVencidasQueChocan`,
  `SIT.ordenDeLaTransaccion`, `SIT.elInstanteSeTomaTrasRetenerElEspacio`
- R18 → `RCIT.bloqueRetenido`, `.bloqueConfirmado`, `.fechaBloqueada`, `.horaYaEmpezada`, `.horaFueraDeHorario`,
  `SIT.conflictoDeHorario`, `DisponibilidadServiceImplTest.retenerBloqueaAntesDeCalcular`, `.retenerHoraReservada`,
  `.retenerFechaBloqueada`, `.retenerHoraPasadaOFueraDeHorario`
- R19 → `CIT.dosUsuariosElMismoBloque` (5 rondas), `CIT.coincidenEnUnBloqueDeVarios`
- R20 → `RCIT.indiceImpideDosActivas`
- R21 → `RCIT.indiceIgnoraLasInactivas`
- R22 → `RCIT.violacionRealDelIndice` (PostgreSQL real), `SIT.violacionDelIndiceUnico`
- R23 → `SIT.otraViolacionSeRelanza`
- R24 → `CIT.reservaYBloqueoDeFecha` (5 rondas)
- R25 → `CIT.exigenUnaTransaccion`
- R26 → `RCIT.cupoSuperado`, `SIT.cupoSuperado`
- R27 → `RCIT.soloCuentanLasVigentesPropias`, `SIT.cupoSuperado`
- R28 → `CIT.elCupoSeSerializaPorUsuario` (5 rondas), `CuentaServiceImplTest.bloquearCuentaBloqueaLaFila`,
  `.bloquearCuentaInexistente`, `SIT.ordenDeLaTransaccion`
- R29 → `RCIT.espacioInvalido`, `CrearReservaDTOTest.rechazaEspacioInvalido`
- R30 → `RCIT.fechaInvalida`, `CrearReservaDTOTest.rechazaFechaInvalida`, `SIT.fechaInexistenteAntesQueSeleccion`
- R31 → `RCIT.fechaFueraDeVentana`, `SIT.fechaFueraDeVentana`, `SIT.extremosDeLaVentana`
- R32 → `RCIT.horasInvalidas`, `CrearReservaDTOTest.rechazaHorasSinForma`, `.elementosQueNoSonEnterosSeLeenComoNull`,
  `SeleccionBloquesTest.aceptaSeleccionValida`, `.rechazaSeleccionInvalida`, `.ordenaLasHoras`,
  `SIT.seleccionInvalidaAntesDelCupo`
- R33 → `RCIT.espacioInexistente`, `.espacioNoPublicado`, `.arrendadorSuspendido`, `SIT.espacioNoVisible`,
  `DisponibilidadServiceImplTest.retenerNoPublicado`, `.retenerArrendadorNoActivo`
- R34 → `RCIT.formaAntesQueRol`, `RCIT.ordenDeLasComprobaciones`, `SIT.rolAntesQueFecha`,
  `SIT.fechaInexistenteAntesQueSeleccion`, `SIT.fechaFueraDeVentana`, `SIT.seleccionInvalidaAntesDelCupo`,
  `SIT.cupoAntesQueEspacio`
- R35 → `RCIT.sinSesion`, `RCIT.tokenInvalido`
- R36 → `RCIT.rolSinPermiso`, `SIT.rolDistintoDeUsuario`
- R37 → `RCIT.consultaSuReserva`, `.horarioDeUnaExpirada`, `.nombreDeUnEspacioNoVisible`, `SIT.consultaUnaVigente`,
  `CatalogoEspaciosServiceImplTest.nombresSinFiltroDeVisibilidad`, `.nombresVacio`
- R38 → `RCIT.ajenaInexistenteYMalEscrita`, `SIT.idMalEscrito`, `SIT.ajenaOInexistente`
- R39 → `RCIT.vencidaSeLeeExpiradaSinEscribir`, `SIT.consultaUnaVencida`, `ReservaTest.estadoEnUnaVencida`,
  `ReservaTest.estadoEnUnaConfirmada`
- R40 → `RCIT.segundosRestantes`, `SIT.consultaUnaVigente`, `SIT.consultaUnaVencida`, `ReservaTest.segundosRestantes`,
  `ReservaTest.segundosRestantesDeOtrosEstados`
- R41 → `RCIT.noExponeDatosDePersonas`, `SIT.elDtoNoExponeDatosDePersonas`
- R42 → `RCIT.auditaLaCreacion`, `SIT.auditaLaCreacion`
- R43 → `RCIT.elProcesoAuditaLaExpiracion`, `RCIT.reservaUnBloqueVencidoSinProcesar`, `ExpiracionReservasTest.auditaLaExpiracion`
- R44 → `RCIT.losRechazosNoSeAuditan`, `RCIT.violacionRealDelIndice`, `SIT.cupoSuperado`, `SIT.conflictoDeHorario`
- R45 → `RCIT.checkDeEstado`, `.checkDeMontos`, `.checkDeVencimiento`, `.checkDeHora`

## Notas para el reviewer

1. **Test de otra historia que falló y se ajustó (T9 pide documentarlo antes de tocarlo).**
   `CalendarioEspacioControllerIT.calculaAlConsultar` (HU-08 R28) exigía que `bloqueo_fecha` fuera la única tabla con
   `bloque` en el nombre, para demostrar que los bloques libres no se guardan. `reserva_bloque`, que exige
   `docs/modelo-datos.md`, también coincide. No es una regresión: esa tabla guarda las retenciones de HU-12, no bloques
   calculados. El ajuste solo admite ese nombre en la lista esperada; R28 sigue verificándose igual, con la cantidad de
   bloques tras cambiar el horario. T9 no lo había previsto.
2. **Desvío menor de `design.md` § 4, a favor de R30 y R32.** El diseño ponía
   `@JsonDeserialize(contentUsing = FichaEspacioDTO.SoloEntero.class)` en `horas` y nada en `fecha`. Con eso,
   `"horas": 14` o `"fecha": {}` fallan en Jackson y la respuesta es 400 **sin campo**, y R30 y R32 piden el error en su
   campo. Se usan `CrearReservaDTO.SoloListaDeEnteros` y `SoloTexto`, que mantienen lo que fija T10 (`[14.5]` y
   `["14"]` se leen como elemento `null`). Los mensajes y las respuestas del diseño no cambian.
3. **"Reservar" no se muestra si el día no tiene bloques libres.** El diseño dice "sin sesión, siempre activo"; se
   entendió como "habilitado cuando se muestra", porque reservar sin bloques no tiene sentido. Cambiarlo es una línea
   de `FichaEspacio.jsx`.
4. **`ReservasControllerSoporte` (nuevo, no previsto en tasks):** clase base de `ReservaControllerIT` y
   `ReservaConcurrenciaIT`, como `EspaciosControllerSoporte`, para compartir un solo contexto y un solo PostgreSQL.
5. **`RutaProtegida` sigue sin guardar el retorno.** Un visitante que abre `/reservas/:id` directamente va al login y
   luego a `/panel`. Está fuera de S3 y de T19, y queda anotado en `docs/frontend.md`.
6. **Comentario de `FichaControllerIT`:** corregido en la vuelta 1, a pedido del review.
7. **Docs.** `docs/architecture.md` afirmaba que `EspaciosControllerSoporte` ya tenía `@Primary`: quedó corregido tras T9.
   La nueva sección "Reservas: qué deja HU-12" recoge el orden de bloqueos, la expiración y lo que necesita HU-13.

## Vuelta 1 del review (`progress/review_HU-12.md`)

**Cambios requeridos**

1. **Contador (CA2).** `ResumenReserva.jsx`: la `key` de `CuentaRegresiva` pasa de `recarga`, que cambiaba al pedir, a
   `actual.lectura`, que cambia al guardar cada respuesta. Con `segundos = 0`, el contador espera su primer tic de 1 s y
   vuelve a leer. Ese segundo se mantiene a propósito: `segundosRestantes` redondea hacia abajo, así que con 0 queda
   menos de 1 s real y la siguiente lectura ya responde `EXPIRADA`; leer al instante solo produciría un bucle de
   peticiones durante ese segundo. `CuentaRegresiva.jsx` no necesitó cambios.
2. **Pruebas.** `@DisplayName` de `DisponibilidadServiceImplIT.horasLibresContraLaBase` sin R21, que sigue cubierto en
   `DisponibilidadServiceImplTest.sinPuertoNoHayReservas`, y comentario de `FichaControllerIT`.
3. **Líneas de más de 120 caracteres.** Corregidas las 7 listadas; ningún `.java` de `reservas` supera el límite.

**Observación 1, IT de R22.** `RCIT.violacionRealDelIndice`: una `CANCELADA` con su bloque activo y un `POST` de dos
horas, una de ellas esa. Responde 400 *horario no disponible*, sin filas nuevas ni `RESERVA_CREADA`. La prueba confirma
antes que la hora figura libre, de modo que el rechazo solo puede venir del índice. **No reveló ningún defecto:**
`ReservaServiceImpl` no cambió por esto.

**Observaciones 2 y 3, incluidas a pedido del dueño (Eduardo Saldaña, 2026-10-30).**

- **Observación 2.** `sesion/retorno.js` (nuevo): `retornoValido(ruta)` solo acepta una sola barra inicial. Lo usan
  `IniciarSesion.jsx`, que con un retorno no válido va a `/panel`, y `ProveedorSesion.jsx`. Además de `//host` rechaza
  `/\host`, que no estaba en el pedido: el navegador lee esa barra invertida como `/`, así que falla igual. Se comprobó
  ejecutando la función con Node (`/panel`, `/`, una ruta con `?fecha=` → aceptadas; `//evil.com`, `/\evil.com`,
  `https://evil.com`, `evil`, `undefined`, `null`, `5` → `null`). No hay prueba automática, porque el frontend no tiene.
- **Observación 3, desviación de `design.md` § 6 aprobada por el dueño.** El diseño toma `ahora` justo después de
  `bloquearCuenta`. Ahora el cupo lee el reloj en ese punto, porque R34 lo exige antes del espacio, y `ahora` se toma
  **después** de `retenerSiLibres`, para `liberarVencidas`, `Reserva.pendiente` y por tanto `creada_en` y `expira_en`.
  Ese instante es igual o posterior al que usó el puerto, así que `liberarVencidas` ve toda reserva que el puerto dio por
  libre. **No cambia ninguna firma**, y el orden `usuario → espacio → reserva` sigue igual. Consecuencia: los 15 minutos
  empiezan tras la espera del bloqueo, no antes. `design.md` no se editó. Cubre: `SIT.elInstanteSeTomaTrasRetenerElEspacio`
  (Mockito, con un reloj que avanza durante `retenerSiLibres`). No hay IT: reproducirlo exigiría que una reserva venza
  justo durante la espera del bloqueo.

## Tests existentes que cambiaron

- `busqueda/controller/BusquedaControllerIT`: `@Primary` en `reservasFalsas()` y comentario de clase (T9, S10).
- `busqueda/controller/FichaControllerIT`: comentario de clase (review, cambio 2).
- `espacios/controller/EspaciosControllerSoporte`: `@Primary` en `reservasDelEspacioFalso()` y comentario (T9, S10).
- `espacios/service/DisponibilidadServiceImplIT`: comentario de clase (T9) y `@DisplayName` sin R21 (review, cambio 2).
- `espacios/controller/CalendarioEspacioControllerIT`: lista esperada de `calculaAlConsultar` (ver nota 1).
- `cuentas/service/CuentaServiceImplTest`, `espacios/service/DisponibilidadServiceImplTest` y
  `espacios/service/CatalogoEspaciosServiceImplTest`: solo se agregaron pruebas (T4, T5, T6).

## Archivos

**Backend, nuevos.** `reservas/domain/` (`EstadoReserva`, `Reserva`, `ReservaBloque`, `SeleccionBloques`);
`reservas/repository/` (`BloqueActivo`, `ReservaRepository`, `ReservaBloqueRepository`); `reservas/dto/`
(`CrearReservaDTO`, `ReservaDTO`); `reservas/service/` (`ReservaService`, `ReservaServiceImpl`, `ExpiracionReservas`,
`ProgramacionExpiracion`, `ReservasDelEspacioImpl`); `reservas/controller/ReservaController`;
`espacios/service/EspacioRetenido`; `db/migration/V3_3__reservas.sql`.

**Backend, modificados.** `cuentas/service/CuentaService` e `Impl`; `espacios/repository/EspacioRepository`;
`espacios/service/DisponibilidadService` e `Impl`; `espacios/service/CatalogoEspaciosService` e `Impl`;
`application.properties`; `src/test/resources/config/application.properties`; `.env.example`.

**Pruebas nuevas.** `reservas/domain/ReservaTest`, `SeleccionBloquesTest`; `reservas/dto/CrearReservaDTOTest`;
`reservas/service/ReservaServiceImplTest`, `ExpiracionReservasTest`, `ProgramacionExpiracionTest`,
`ReservasDelEspacioImplTest`, `ReservasDelEspacioImplIT`; `reservas/controller/ReservasControllerSoporte`,
`ReservaControllerIT`, `ReservaConcurrenciaIT`.

**Frontend, nuevos.** `api/reservas.js`; `components/ui/CuentaRegresiva.jsx` y su `.module.css`;
`pages/reservas/ResumenReserva.jsx` y su `.module.css`; `pages/reservas/textosReservas.js`; `sesion/retorno.js`.

**Frontend, modificados.** `App.jsx`; `components/ui/EtiquetaEstado.jsx`; `pages/busqueda/FichaEspacio.jsx` y su
`.module.css`; `pages/cuentas/IniciarSesion.jsx`; `sesion/ProveedorSesion.jsx`.

**Docs.** `docs/architecture.md`, `docs/modelo-datos.md`, `docs/frontend.md`, `docs/requerimientos.md` (§8 y §9-8).
