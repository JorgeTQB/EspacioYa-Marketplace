# Review — HU-11

**Veredicto:** APPROVED (vuelta 1; la vuelta 0 fue CHANGES_REQUESTED, ver abajo)

> Revisado el 2026-10-30 sobre la rama `feature/HU-11-detalle-espacio` (cambios sin commitear; `develop` = `HEAD` =
> `bdec034`). La implementación cumple el spec, la arquitectura y la seguridad, y `./init.sh` está en verde. Se
> rechaza solo por dos cambios pequeños (ver "Cambios requeridos"): una edición sobrante en la historia y un doble de
> prueba sin `@Primary` que va a fallar cuando llegue HU-12. Con esos dos arreglos se puede aprobar sin otra ronda
> de revisión del código.

## `./init.sh` completo (sin `--rapido`)
- Exit 0, `[OK] Entorno listo`.
- Backend `./mvnw verify`: **523 unitarias** (472 previas + 51 de HU-11) y **351 IT**, 0 fallos, 0 errores, 0 omitidas.
- Reglas de arquitectura (paquetes, sin repositorio en controladores, sin `@Autowired`, sin Lombok, sin comodines,
  sin prints, sin colores fuera de tokens, sin librerías de UI, sin secretos): todas `[OK]`.
- Frontend: ESLint sin errores ni advertencias; `npm run build` compila.

## Trazabilidad requirements ↔ tests
- R1: [x] `FichaControllerIT.fichaDeUnEspacioVisible` (200 sin sesión, valores), `FichaServiceTest.fichaDeUnEspacioVisible` (DTO igual campo a campo), `FichaServiceTest.aceptaElIdMaximo`
- R2: [x] `FichaControllerIT.fichaConExactamenteSusCampos` (`containsOnlyKeys` de los 10 campos)
- R3: [x] `FichaControllerIT.fichaDeUnEspacioVisible` (nombre, tipo, dirección, zona, capacidad, precio y descripción contra la fila insertada)
- R4: [x] `FichaControllerIT.fichaConNombreDeZona` (`zonaNombre` = "Los Olivos")
- R5: [x] `FichaControllerIT.fotosEnOrden` (insertadas 3, 1, 2 → salen 1, 2, 3 como `/api/fotos/<UUID>`); `CatalogoEspaciosServiceImplTest.publicadoDevuelveLaFicha`
- R6: [x] `FichaControllerIT.sinFotos`; `CatalogoEspaciosServiceImplTest.publicadoSinFotos`
- R7: [x] `FichaControllerIT.minimizacion` (el cuerpo no contiene correo, teléfono, nombre, documento, razón social, dirección fiscal, `espacios/`, `arrendadorId`, `estado`, `creadoEn`, `actualizadoEn`)
- R8: [x] `FichaControllerIT.bearerInvalidoComoAnonimo` (las dos rutas responden 200 con Bearer inexistente)
- R9: [x] `FichaControllerIT.noPublicadosResponden404` (inexistente, BORRADOR, PAUSADO, BAJA, OCULTO); `FichaServiceTest.fichaNoVisible`
- R10: [x] `FichaControllerIT.arrendadorNoActivoResponde404` (SUSPENDIDA y ELIMINADA)
- R11: [x] `FichaControllerIT.idMalEscritoResponde404` (`abc`, `0`, `-1`, `1.5`, `007a`, 20 dígitos); `FichaServiceTest.fichaConIdMalEscrito` (15 casos, incluido `9223372036854775808`, con `verifyNoInteractions(catalogo, disponibilidad)`)
- R12: [x] `FichaControllerIT.cuerposIguales` (7 cuerpos 404 de ficha y disponibilidad, iguales byte a byte)
- R13: [x] `FichaControllerIT.bloquesLibresDelDia`, `FichaServiceTest.bloquesLibresDelDia`, `FichaServiceTest.bordesDeLaVentana`
- R14: [x] `FichaControllerIT.bloquesLibresDelDia` (franjas 14–16 y 8–10 → 8, 9, 14, 15 en orden con `horaFin = horaInicio + 1`)
- R15: [x] `FichaControllerIT.fechaBloqueada`
- R16: [x] `FichaControllerIT.excluyeReservadas` (hora 9 reservada en el doble → `[8, 10]`)
- R17: [x] `FichaControllerIT.hoyExcluyeLasHorasEmpezadas` (10:00 Lima, franja 8–12 → `[11]`)
- R18: [x] `FichaControllerIT.diaSinHorario`
- R19: [x] `FichaControllerIT.disponibilidadMinima` (`containsOnlyKeys` de respuesta y bloque; sin `RESERVADO`/`BLOQUEADO`/`PASADO`/`estado`)
- R20: [x] `FichaControllerIT.disponibilidadNoVisibleResponde404`; `FichaServiceTest.disponibilidadDeEspacioNoVisible` y `.disponibilidadConIdMalEscrito` (`verifyNoInteractions(disponibilidad)`)
- R21: [x] `FichaControllerIT.fechaInvalida` (ausente, vacía, `2026-02-30`, `02/11/2026`, `manana`: 400 con un único error en `fecha`); `FichaServiceTest.fechaInvalida`
- R22: [x] `FichaControllerIT.fechaFueraDeVentana` (ayer y hoy+56 → 400; hoy+55 → 200); `FichaServiceTest.fechaFueraDeVentana`
- R23: [x] `FichaControllerIT.noVisibleYFechaInvalidaResponde404`; `FichaServiceTest.noVisibleYFechaInvalida`
- R24: [x] `FichaControllerIT.sinCache` (`Cache-Control` contiene `no-store` en las dos rutas)
- R25: [x] `FichaControllerIT.sinAuditoria` (`count(*)` de `auditoria` igual tras 200, 404 y 400)
- R26: [x] `LimiteFichaIT.fichaConCupoAgotado` (4.ª → 429 con `MENSAJE_DEMASIADOS_INTENTOS`, sin cuerpo de ficha)
- R27: [x] `LimiteFichaIT.disponibilidadConCupoAgotado`
- R28: [x] `LimiteFichaIT.cupoCompartidoConLaBusqueda`, `LimiteFichaIT.fichasAgotanLaBusqueda`, `ReglasLimiteFichaTest.compartenElCupoConLaBusqueda`
- R29: [x] `LimiteFichaIT.noEncontradasCuentan` (tres 404), `LimiteFichaIT.invalidasCuentan` (dos 400 y un 404)
- R30: [x] `ReglasLimiteFichaTest.reglaDeLaFicha`, `ReglasLimiteFichaTest.reglaDeLaDisponibilidad` (método, patrón, grupo `catalogo`, `aplicaA` positivo y negativo)
- R31: [x] `CatalogoEspaciosServiceImplTest.publicadoDevuelveLaFicha` (record completo con `zonaNombre` y URL en orden), `.publicadoSinFotos`
- R32: [x] `CatalogoEspaciosServiceImplTest.idNulo`, `.inexistente`, `.noPublicado` (parametrizado por cada estado ≠ PUBLICADO), `.arrendadorNoActivo`
- R33: [x] los mismos casos de R32 (`esVisible` = `false`) y `.esVisibleSiPublicadoYActivo` (`true`, sin leer fotos ni zonas)

Todos los tests comprueban un resultado concreto (código HTTP, cuerpo, campos, orden, `verifyNoInteractions`).
El mapa de `progress/impl_HU-11.md:49-85` coincide con los `@DisplayName` reales.

## Trazabilidad con la historia
- CA1 (fotos, descripción, dirección, capacidad, precio por hora): [x] R1–R6, R31; pantalla en `3_frontend/src/pages/busqueda/FichaEspacio.jsx:108-129`
- CA1 (política de cancelación 48 h): [x] D5, texto estático `3_frontend/src/pages/busqueda/textosFicha.js:3-7` (las tres reglas de RN-06), mostrado en `FichaEspacio.jsx:122-127`
- CA1 (solo visibles; resto 404 genérico): [x] R9–R12, R32, R33
- CA2 (elijo/cambio la fecha y veo los bloques libres): [x] R13–R18, R20–R23; `FichaEspacio.jsx:54-67` recarga solo los bloques y descarta respuestas viejas (`activo`), `FichaEspacio.jsx:74-76` refleja la fecha en la URL con `replace`
- (I) No se muestran teléfono, correo ni documentos del arrendador: [x] R2, R7, R12, R19. Implementado: `FichaPublicada` no transporta `arrendadorId` (`2_Backend/src/main/java/pe/edu/ulima/espacioya/espacios/service/FichaPublicada.java:12-13`); `FichaPublicaDTO` con 10 campos (`busqueda/dto/FichaPublicaDTO.java:12-13`); la pantalla no muestra nada del arrendador
- (D) Rate limiting compartido con HU-10: [x] R26–R30. Implementado: `2_Backend/src/main/java/pe/edu/ulima/espacioya/busqueda/controller/ReglasLimiteFicha.java:18-26`, dos `@Bean` con `LimiteCatalogo.regla("GET", …)`

## Tasks completas
- T1: [x] `CatalogoEspaciosService.java:26-33`, `CatalogoEspaciosServiceImpl.java:84-108`, `FichaPublicada.java`
- T2: [x] `ConfiguracionSeguridad.java:60-62`
- T3: [x] `FichaPublicaDTO`, `BloqueLibreDTO`, `DisponibilidadPublicaDTO` (records)
- T4: [x] `FichaService.java` (regex `[0-9]{1,19}` + `parseLong` + `> 0`, 404 antes que 400, `VentanaReserva`, constantes de `BusquedaService`)
- T5: [x] `ReglasLimiteFicha.java`, `FichaController.java` (`@PathVariable String id`, `@RequestParam(required = false) String fecha`)
- T6: [x] `FichaControllerIT` (23 tests, IP 10.12.x.x)
- T7: [x] `LimiteFichaIT` (6 tests, IP 10.13.x.x)
- T8: [x] `Galeria.jsx` + `.module.css` (solo tokens, `aria-pressed`, `aria-label`, borde `--color-primario`)
- T9: [x] `api/busqueda.js:9-17` (`autenticada: false`, `encodeURIComponent`), `textosFicha.js`
- T10: [x] `FichaEspacio.jsx` + `.module.css`
- T11: [x] `App.jsx:105-106` (ruta pública), `BuscarEspacios.jsx:150-155` (solo el `Link` "Ver espacio" en `acciones`)
- T12: [x] `docs/architecture.md:172-175, 183-186, 210-216`; `docs/frontend.md:34, 161-162, 179`
- T13: [x] `./init.sh` verde (verificado por el reviewer) y `progress/impl_HU-11.md`

## Diseño, decisiones y contrato
- DT-1 a DT-16: [x] el código coincide. En particular DT-5 (id como texto), DT-6 (un solo `RecursoNoEncontradoException`), DT-7 (orden 404 → 400 → `horasLibres`, `FichaService.java:50-66`), DT-9 (no-store probado, sin código), DT-10 (sin migración ni auditoría), DT-12 (`permitAll` solo GET).
- D1–D8: [x] D1 solo libres con `SelectorBloques` sin `onCambiar` (`FichaEspacio.jsx:149-151`) y `SIN_BLOQUES`; D2 sin selección ni botón primario; D3 nada del arrendador; D4 `Galeria` nueva; D5 política RN-06; D6 `?fecha=` o `ventanaBusqueda().hoy` (`FichaEspacio.jsx:33-34`); D7 `nombreTipo · zonaNombre` (`FichaEspacio.jsx:111`); D8 404 con `Alerta` y `Link` a `/buscar` (`FichaEspacio.jsx:78-88`).
- Contrato para HU-12 (design § 13): [x] ruta `/espacios/:id?fecha=` pública; `obtenerEspacio(id)` y `consultarBloquesLibres(id, fecha)` con la firma acordada; `SelectorBloques` intacto y listo para `seleccionados`/`onCambiar`; DTO ampliables; documentado en `docs/frontend.md:162`.
- `docs/frontend.md`: [x] solo tokens, CSS Modules, sin estilos en línea, ningún botón primario, error 404/429/red con `Alerta` y error de fecha bajo el `Campo` según design § 12, llamadas solo vía `src/api/`.

## Sin regresiones
- [x] `DisponibilidadService*`, `SelectorBloques.jsx`, `BusquedaService`, `ReglasLimiteBusqueda`, `ManejadorGlobalErrores`, `application.properties` y migraciones: sin cambios (`git diff --stat`).
- [x] `CatalogoEspaciosService` y su impl solo agregan métodos (`+10`/`+28` líneas, 0 borradas en lógica); `ConfiguracionSeguridad` solo agrega 3 líneas antes de `anyRequest().authenticated()`.
- [x] Único test previo tocado: `CatalogoEspaciosServiceImplTest`, solo con casos añadidos (diff sin líneas borradas); los 472 unitarios previos y las IT de HU-01…HU-10 siguen verdes.

## Seguridad
- [x] Minimización (RNF-SEG-16): ver (I) arriba.
- [x] 404 indistinguible para inexistente, no publicado, arrendador no activo e id no numérico, en las dos rutas (R12 lo prueba byte a byte).
- [x] Rate limiting en las dos rutas nuevas, en el grupo `catalogo`; cuentan también 404 y 400.
- [x] `permitAll` solo para `GET /api/espacios/*` y `GET /api/espacios/*/disponibilidad` (`ConfiguracionSeguridad.java:61-62`); no hay otras rutas bajo `/api/espacios/**` y cualquier otro método o subruta sigue `authenticated()`.

## Checkpoints
- C1 (arnés completo, `./init.sh` exit 0): [x]
- C2 una `in_progress` por dueño / `progress/HU-11.md` existe: [x]
- C2 historias `done` con tests verdes: n/a (HU-11 aún no es `done`)
- C2 aprobación de la dueña anotada: [x] `progress/HU-11.md:14, 45`
- C2 `progress/HU-11.md` solo con la sesión activa: [x]
- C3 paquetes, controladores sin repositorios, sin `@Autowired`/Lombok/comodines/prints: [x]
- C3 dependencias entre módulos solo vía service o puerto: [x] `busqueda` usa `CatalogoEspaciosService` y `DisponibilidadService`; ningún puerto nuevo
- C3 DTO `record`, sin entidades ni campos sensibles: [x]
- C3 identidad de la sesión, montos y estados en el servidor: [x] (rutas públicas; el cliente no decide nada)
- C3 migraciones: n/a (sin migración)
- C3 adapters para integraciones externas: n/a
- C3 nada fuera del alcance: [x] (galería de ≤ 5 fotos, sin mapa ni video)
- C3 sin secretos, sin colores fuera de tokens ni librerías de UI: [x]
- C3 frontend con el kit, mensajes según `docs/frontend.md`, API solo desde `src/api/`: [x]
- C4 nombres `*Test`/`*IT` con `@Import(TestcontainersConfiguration.class)`: [x]
- C4 sin H2: [x]
- C4 `Clock` fijo: [x] (`Clock.fixed` en `FichaServiceTest`, `RelojControlable` en las IT)
- C4 IT para endpoints: [x]; concurrencia: n/a
- C4 resultados concretos, incluido el camino de error: [x]
- C4 `mvnw verify` > 0 tests y verde; lint y build: [x]
- C5 sin archivos sueltos sospechosos: [x]
- C5 `progress/history.md` con entrada de la sesión: n/a hasta el cierre (lo hace el orquestador al pasar a `done`)
- C5 `progress/HU-11.md` vuelto a plantilla: n/a (historia no terminada)
- C5 estado en `feature_list.json`: [x] `in_progress`
- C5 rama `feature/HU-11-detalle-espacio`: [x]
- C6 carpeta `specs/HU-11/` completa: [x]
- C6 EARS y tabla de trazabilidad que cubre CA1, CA2, (I) y (D): [x] `specs/HU-11/requirements.md:205-224`
- C6 `design.md` con endpoints, clases, secuencias y alternativas: [x]; patrón ★: n/a (HU-11 no es ★)
- C6 todas las tasks `[x]`: [x]
- C6 cada R<n> con test documentado en `impl_HU-11.md`: [x]
- C6 ningún R<n> resuelve un pendiente §9 sin decisión del dueño: [x] (§9.9 resuelto por Sebastian Garate; D1–D8 aprobados por la dueña)

## Cambios requeridos
1. **`docs/historias/HU-11.md:13`**: alguien reemplazó la línea en blanco bajo "**Criterios de aceptación:**" por el
   texto `aaprobado`. No forma parte de la historia ni de ninguna task, ensucia la fuente de verdad de los CA y
   entraría a `develop` con el PR. La aprobación ya consta donde corresponde (`progress/HU-11.md:14`). Revertir esa
   línea a vacía (`git diff docs/historias/HU-11.md` debe quedar sin cambios). Al ser un `docs/`, lo puede hacer el
   orquestador por pedido de la dueña; no hace falta volver al implementer.
2. **`2_Backend/src/test/java/pe/edu/ulima/espacioya/busqueda/controller/FichaControllerIT.java:103-106`**: el doble
   `ReservasFalsas` se registra como `@Bean` **sin `@Primary`**. `DisponibilidadServiceImpl` lo resuelve con
   `ObjectProvider<ReservasDelEspacio>` (`DisponibilidadServiceImpl.java:42`); cuando HU-12 agregue su implementación
   real habrá dos candidatos y `getIfAvailable` lanzará `NoUniqueBeanDefinitionException`, rompiendo R13–R19 de esta
   IT. `docs/architecture.md:225-227` documenta justo esa trampa y el precedente
   (`EspaciosControllerSoporte.java:108`, `@Primary`). Agregar `@Primary` al `@Bean reservasFalsas()` y volver a
   correr `FichaControllerIT`.

## Observaciones (no bloquean)
- `3_frontend/src/pages/busqueda/FichaEspacio.jsx:34`: si se entra sin `?fecha=`, la fecha inicial (hoy) no se
  escribe en la URL hasta que el usuario la cambia. D6 solo pide reflejar la fecha *elegida*, así que cumple; pero
  HU-12, para "vuelvo a la ficha", debe armar la URL de retorno con la `fecha` en uso y no copiar `location` tal cual
  (anotarlo en el contrato con Eduardo Saldaña).
- `FichaEspacio.jsx:34`: con `?fecha=` vacío, `fecha` es `""` (no hoy) y la pantalla muestra el 400 "Elige una fecha
  válida." bajo el campo. Es aceptable (el servidor manda), solo se señala.
- `FichaEspacio.jsx:110`: `h1` para el nombre del espacio, como pide design § 12, mientras `App.jsx` ya usa `h1` para
  la marca (lo señala el implementer en `progress/impl_HU-11.md:103-104`). Revisar la jerarquía de encabezados en UAT.
- `FichaService` y `CatalogoEspaciosServiceImplTest` estrenan `@ParameterizedTest` en el repo; viene en
  `spring-boot-starter-test`, sin dependencias nuevas.

## Vuelta 1

**Veredicto:** APPROVED

Revisado el 2026-10-30 sobre la misma rama (cambios sin commitear). Según lo acordado en la vuelta 0, solo se
verifican los dos cambios pedidos, que no haya otros y `./init.sh` completo.

1. Cambio 1, `docs/historias/HU-11.md`: [x] `git diff docs/historias/HU-11.md` vacío; el archivo ya no figura en
   `git status`.
2. Cambio 2, `@Primary`: [x] `2_Backend/src/test/java/pe/edu/ulima/espacioya/busqueda/controller/FichaControllerIT.java:103-107`
   (`@Bean @Primary ReservasFalsas reservasFalsas()`), con `import org.springframework.context.annotation.Primary;`
   en la línea 31.
3. Sin otros cambios: [x] `git status --short` lista los mismos archivos que en la vuelta 0 salvo
   `docs/historias/HU-11.md`, que ya no aparece. Los únicos archivos con fecha de modificación posterior a la
   revisión anterior son los dos de arriba, `progress/impl_HU-11.md` (sección "Vuelta 1") y `progress/HU-11.md`
   (entrada de bitácora). Ningún archivo de `2_Backend/src/main/`, `3_frontend/src/`, `docs/` ni `specs/` cambió.
   Limitación: `FichaControllerIT.java` no está versionado y no hay copia previa para compararlo línea a línea. El
   recuento de tests no varía (23 en la IT, 351 IT en total) y el resto del código es el mismo que se aprobó en la
   vuelta 0.
4. `./init.sh` completo (sin `--rapido`): [x] exit 0, `[OK] Entorno listo`. Backend: 523 unitarias y 351 IT con
   0 fallos, 0 errores y 0 omitidas. Todas las reglas de arquitectura en `[OK]`. ESLint limpio y `npm run build`
   compila.

Trazabilidad R1–R33, CA1, CA2, (I), (D), tasks T1–T13 y checkpoints: sin cambios respecto de la vuelta 0, todos en
`[x]` o `n/a`. No quedan cambios requeridos. Las observaciones no bloqueantes de la vuelta 0 siguen vigentes para
UAT y HU-12.
