# Exploración HU-24 · Reseñas y calificaciones

> Informe del explorador para `spec-author`. Solo hechos del repo (rama `develop`, 2026-11-06). Las contradicciones se
> señalan en § 8 sin resolverlas: las decide el spec o el dueño (Ariel Enriquez).

Abreviaturas: `J/` = `2_Backend/src/main/java/pe/edu/ulima/espacioya/`, `T/` = `2_Backend/src/test/java/pe/edu/ulima/espacioya/`,
`F/` = `3_frontend/src/`.

## Preguntas

1. Reservas (HU-12/13/14): entidad, estados, cómo saber que está confirmada y que "su fecha ya pasó", servicio/DAO/iterador
   de "Mis reservas", `ReservaDTO` y si se puede ampliar sin romper a otros.
2. Acceso entre módulos (regla de `docs/architecture.md`) y vía de `busqueda`/`espacios` hacia reservas y viceversa.
3. Ficha pública: endpoint, DTO, pantalla; si la búsqueda (HU-10/23) debe mostrar el promedio.
4. Sanitización, auditoría, rate limiting, autenticación y errores reutilizables en `comun`.
5. Última migración Flyway y siguiente número; si `docs/modelo-datos.md` define la tabla.
6. Frontend: kit, `src/api/`, rutas, "Mis reservas" como punto de entrada.
7. Qué impone HU-27 a HU-24.

## Archivos analizados

Docs: `docs/historias/HU-24.md`, `HU-27.md`, `docs/requerimientos.md`, `docs/architecture.md`, `docs/modelo-datos.md`,
`docs/frontend.md`, `docs/conventions.md`, `specs/HU-14/design.md` (§ 11.3-11.4), `specs/HU-11/design.md` (DT-16, § 13),
`specs/HU-22/design.md` (DT-2), `specs/HU-23/design.md` (DT-12), `progress/HU-24.md`, `feature_list.json`.

Backend: `J/reservas/**` (`Reserva`, `ReservaConHorario`, `EstadoReserva`, `GrupoReservas`, `HistorialReservas`, `ReservaDTO`,
`ConsultaReservasService`, `ConversionReservas`, `ReservaDAO`, `ReservaService`, `ReservaServiceImpl`, `ReservaController`,
`ReservaRepository`, `ReservaBloqueRepository`, `HorarioReserva`, `ReservasLiquidablesImpl`, `DatosPersonalesReservasImpl`),
`J/busqueda/**` (`FichaController`, `FichaService`, `FichaPublicaDTO`, `ResultadoEspacioDTO`, `BusquedaService`,
`LimiteCatalogo`, `ReglasLimiteFicha`), `J/comun/**` (auditoría, límite, `ConfiguracionSeguridad`, `ManejadorGlobalErrores`,
`ReglaNegocioException`, `UsuarioAutenticado`), `J/cuentas/service/puertos/*`, `J/cuentas/service/CuentaService.java`,
`J/espacios/service/CatalogoEspaciosService.java`, migraciones `V1_2`, `V3_3`, `V7_1`, `application.properties`, `pom.xml`.

Frontend: `F/App.jsx`, `F/api/reservas.js`, `F/api/busqueda.js`, `F/api/cliente.js`, `F/pages/reservas/MisReservas.jsx`,
`SeccionReservas.jsx`, `textosReservas.js`, `F/pages/busqueda/FichaEspacio.jsx`, `textosFicha.js`, `F/components/ui/Campo.jsx`,
`EtiquetaEstado.jsx`.

Pruebas que fijan contratos: ver § 1 y § 3 y C1, C3, C8.

## 1. Reservas

**Entidad y estados.** `J/reservas/domain/Reserva.java`; `EstadoReserva` = `PENDIENTE_PAGO, CONFIRMADA, EXPIRADA, CANCELADA`.
Campos: `id, usuarioId, espacioId, estado, precioHoraAplicado, montoTotal, creadaEn, expiraEn, canceladaPor, canceladaEn,
montoReembolso`. Tablas `reserva` y `reserva_bloque` (`V3_3`, `V4_2`).

**Confirmada.** Regla del repo: quien lee usa `reserva.estadoEn(ahora)`, nunca `getEstado()` (`architecture.md` l. 361). Una
`CONFIRMADA` se lee siempre `CONFIRMADA` (l. 523-524). Una cancelada queda `CANCELADA`, aunque haya sido liquidable.

**"Ya pasó".** No hay un método "terminada" propio; lo más cercano:

- `ReservaConHorario(reserva, fecha, horaInicio, horaFin)` (`J/reservas/domain/ReservaConHorario.java`): `fin()` (con
  `horaFin = 24`, 00:00 del día siguiente), `instanteDeFin()` en `America/Lima` y `esProxima(ahora, ahoraLocal)`. Pasadas =
  negación de `esProxima`, que incluye `EXPIRADA` y `CANCELADA` con fecha futura (no sirve como "pasada reseñable" por sí sola).
- HU-19 y HU-26 usan `instanteDeFin` como referencia (`architecture.md` l. 491-492: reclamos solo si `ahora < fin + 48 h`).
- `comun/configuracion/ZonaHoraria`: `hoy(reloj)`, `ahora(reloj)`, `LIMA`. El `Clock` va en UTC (l. 282-284).
- Horario: `ReservaBloqueRepository.horarios(ids)` agrupa **todas** las filas de `reserva_bloque`, activas o no.

**Sin carrera con la cancelación.** `Reserva.motivoNoCancelable` da `YA_INICIADA` si `ahora >= inicio`: una `CONFIRMADA` ya
terminada no puede pasar a `CANCELADA`. El webhook tampoco toca reservas terminadas (solo confirma vigentes).

**"Mis reservas".**

| Pieza | Ruta |
|-------|------|
| `GET /api/reservas?grupo=proximas\|pasadas&pagina=n` (rol `USUARIO`) | `J/reservas/controller/ReservaController.java` → `J/reservas/service/ConsultaReservasService.java#misReservas` (clase concreta, sin interfaz) |
| DAO ★ | `J/reservas/repository/ReservaDAO.java` (`delUsuario(usuarioId)`, `confirmadasDeEspacios(ids)`; exige dueño en la firma, sin buscar por id) + `ReservaDAOJpa` |
| Iterator ★ | `J/reservas/domain/HistorialReservas.java`, `IteradorReservas`, `Pagina` (10 por página, `hayMas`) |
| Conversión | `J/reservas/service/ConversionReservas.java#aDto`: única fuente de `ReservaDTO` (HU-12 y HU-14) |

Una solicitud fija un solo `ahora` y lo usa para clasificar y para el estado (l. 521).

**`ReservaDTO`** (`J/reservas/dto/ReservaDTO.java`): 11 campos `id, espacioId, espacioNombre, estado, fecha, horaInicio, horaFin,
precioHora, montoTotal, expiraEn, segundosRestantes`. Lo devuelven `POST /api/reservas`, `GET /api/reservas/{id}` y la lista.
`architecture.md` l. 531 y `specs/HU-14/design.md` l. 497 permiten ampliarlo con campos nuevos. **No es gratis:** ver C1 y C2.

## 2. Acceso entre módulos

Regla (`architecture.md` principio 5, l. 58-67, y "Puertos entre módulos", l. 118-149): entre módulos solo la interfaz del
`service` del otro; nunca su repositorio. Si el módulo de abajo necesita algo del de arriba, define una interfaz en
`service/puertos/` y el de arriba la implementa. Un puerto nuevo se justifica en `design.md` y se agrega a la tabla.

```
comun ← cuentas ← espacios ← reservas → pagos → cuentas
                     ↑          ↑
                 busqueda ──────┘        administracion → (todos)
```

- **`busqueda → reservas`: permitido por el diagrama, sin puerto.** Hoy `J/busqueda` no importa nada de `reservas`, `pagos`,
  `administracion` ni `cuentas`, y nadie importa `busqueda`.
- **`reservas → busqueda`: no permitido** (haría ciclo). Si `reservas` necesitara saber de reseñas, haría falta un puerto en
  `reservas/service/puertos/`, carpeta que **no existe** hoy.
- **`ReservaService`** (`J/reservas/service/ReservaService.java`) es la interfaz pública: `crear` y `consultar(usuario, id)`.
  `consultar` ya filtra por dueño (`findByIdAndUsuarioId`), responde 404 para id mal escrito, inexistente o ajeno, lee con
  `estadoEn` y devuelve `espacioId`, `estado`, `fecha`, `horaInicio`, `horaFin`. **No comprueba el rol.** Es una vía existente
  sin cambios; no da el instante de fin (se puede derivar de `fecha`/`horaFin` o agregar un método).
- `ConsultaReservasService` y `ReservaDAO` no son interfaces públicas entre módulos.
- Precedente de método de sistema sin sesión: `ReservasLiquidablesImpl` (HU-19), que no usa el DAO porque recibe ids.
- **`busqueda → espacios`:** `CatalogoEspaciosService`: `publicado(id)`, `esVisible(id)`, `nombres(ids)` (sin filtro de
  visibilidad), `arrendadoresDe(ids)`.
- **`cuentas`:** `CuentaService.nombres(ids)` da solo nombres no vacíos, sin correo ni teléfono; los faltantes se suplen con un
  texto por defecto (`ConsultaReservasService.NOMBRE_USUARIO_POR_DEFECTO = "Usuario de EspacioYa"`). `cuentaActiva(id)`.
  Ver C8 sobre la flecha `busqueda → cuentas`.
- **`busqueda` hoy solo tiene `controller`, `dto`, `service`.** HU-24 creará los primeros `domain` y `repository` del módulo.

## 3. Ficha pública y búsqueda

**Ficha.** `GET /api/espacios/{id}` en `J/busqueda/controller/FichaController.java` → `J/busqueda/service/FichaService.java#ficha`
→ `CatalogoEspaciosService.publicado`. DTO `J/busqueda/dto/FichaPublicaDTO.java` (10 campos), con fábrica `de(FichaPublicada)`.
Todo lo no visible (id malo, inexistente, no publicado, arrendador no activo) es el mismo 404.

- `specs/HU-11/design.md` DT-16 y l. 429: HU-24 **agrega campos** a `FichaPublicaDTO` (promedio y reseñas), y las secciones nuevas
  van después de la descripción. "Sin romperlo".
- Pantalla: `F/pages/busqueda/FichaEspacio.jsx`, ruta `/espacios/:id`, pública. Llama a `obtenerEspacio` (sin token). Orden hoy:
  galería, datos (precio, dirección, capacidad, descripción, política), bloques libres. Un solo primario: "Reservar".
- Pruebas que fijan las claves: `T/busqueda/controller/FichaControllerIT.java:267` (`containsOnlyKeys`) y
  `T/busqueda/service/FichaServiceTest.java:72` (constructor del record).
- Seguridad: `GET /api/espacios/*` y `/api/espacios/*/disponibilidad` son `permitAll`; "cualquier otra subruta exige sesión"
  (`J/comun/seguridad/ConfiguracionSeguridad.java:66-68`). Una subruta pública nueva (p. ej. lista paginada de reseñas) debe
  agregarse allí **y** registrar su regla en el grupo `catalogo` (un patrón `/{id}` no cubre subrutas, l. 279).

**Búsqueda.** HU-10 y HU-23 no piden promedio. `grep` de "promedio" y "calificación" en `docs/` y `specs/`: solo RF-C-08, RN-13,
HU-24 CA1 y `specs/HU-11` hablan de él, y siempre en la **ficha**. `ResultadoEspacioDTO` tiene seis campos fijados por HU-10 R14,
HU-23 R18 y DT-12; `T/busqueda/controller/BusquedaControllerIT.java:234` y `:616` los fijan con `containsOnlyKeys`. Ningún requisito
pide promedio en resultados, ni ordenar o filtrar por calificación.

## 4. Reutilizables de `comun`

**Sanitización: no existe.** `grep` de sanitiz, jsoup, owasp, HtmlUtils, escapeHtml en `2_Backend/src/main`: 0 resultados. `pom.xml` no
trae jsoup ni OWASP. En `F/` no hay `dangerouslySetInnerHTML` ni `innerHTML`: React escapa al renderizar. No figura en el principio
9 de `architecture.md` ni en `requerimientos.md` § 8. HU-24 sería la primera. Lo más parecido hoy: `FichaEspacioDTO.recortar` (`strip`).
`org.springframework.web.util.HtmlUtils` está en el classpath por `spring-web`, sin dependencia nueva. Ver C5.

**Auditoría.** `J/comun/auditoria/AuditoriaService.java#registrar(EventoAuditoria)` (`@Transactional`, se une a la del caso de uso).
`EventoAuditoria(usuarioId, accion, entidad, entidadId, ip, detalle)`. `accion VARCHAR(60)` sin `CHECK`. IP con
`J/comun/web/IpCliente.de(peticion)` en el controlador. Ejemplo: `ReservaServiceImpl.auditar` (`RESERVA_CREADA`, detalle
`clave=valor;…`). Sin datos personales en el detalle.

**Rate limiting.** Por IP, en un filtro antes del controlador:

- `J/comun/limite/ReglaLimite.java` (`metodo, ruta/patrón, maximo, ventana, grupo`) como `@Bean`; `FiltroLimiteIntentos` las toma todas.
- Se declaran en cada módulo: `J/cuentas/controller/ReglasLimiteCuentas.java`, `J/busqueda/controller/ReglasLimiteBusqueda.java`,
  `ReglasLimiteFicha.java`, con `LimiteCatalogo.regla(metodo, patron)` (grupo `catalogo`, 120/min por IP,
  `espacioya.limite.catalogo.*` en `application.properties:67-68`).
- Por usuario, en el servicio: `J/comun/limite/LimitadorIntentos.java` (`intentar(clave, maximo, ventana)`), en memoria.
- RNF-SEG-17 **no** lista un límite de reseñas; HU-24 no tiene control (D). `requerimientos.md` § 8 tampoco la nombra entre quienes
  reutilizan el limitador. La regla `reservas` única + ventana de 14 días acotan por sí mismas.

**Autenticación.** Identidad con `@AuthenticationPrincipal UsuarioAutenticado` (`usuarioId, sesionId, rol`), nunca del cuerpo o la URL.
Toda ruta no listada exige sesión. Rol: patrón `exigirRol` de `ConsultaReservasService` (`AccesoDenegadoException` → 403, antes de
validar). Solo `USUARIO` reserva; solo él tendría reservas que reseñar. `J/reservas/service/ConversionReservas.idReserva` es la regla
del id de reservas (público, pero de otro módulo; `FichaService.idValido` es privada).

**Errores** (`J/comun/error/`, `ManejadorGlobalErrores`):

| Excepción | Respuesta |
|-----------|-----------|
| `ReglaNegocioException(mensaje)` / `(campo, mensaje)` | 400 sin o con campo |
| `ValidacionException(errores)` | 400 con varios `ErrorCampoDTO` |
| `MethodArgumentNotValidException` (`@Valid`) | 400 por campo |
| `HttpMessageNotReadableException` | 400 genérico |
| `AccesoDenegadoException` | 403 |
| `RecursoNoEncontradoException` | 404 genérico |
| `DemasiadosIntentosException` | 429 |
| `DataIntegrityViolationException` | **sin manejador**: cae en 500. Patrón a imitar para la doble reseña: `ReservaServiceImpl.esBloqueYaRetenido` (traduce por nombre de restricción, con `TransactionTemplate` por fuera, l. 93-100, 169-172) |

Deserializadores estrictos reutilizables como ejemplo: `J/reservas/dto/CrearReservaDTO.java` (`SoloIdEntero`, `SoloTexto`).
Correos: HU-24 no pide ninguno.

## 5. Migraciones y modelo

- **Última:** `V7_1__caracteristicas.sql` (HU-23). **Siguiente libre del sprint 7: `V7_2`.** Formato `V<sprint>_<n>__<descripcion>.sql`
  (`docs/conventions.md` l. 81-103). HU-24 es Sprint 7; comprobar `develop` antes de numerar, porque otra historia puede tomarlo.
- `docs/modelo-datos.md` l. 36 **ya define** `resena`: id, reserva_id (único), usuario_id, espacio_id, calificacion (1–5), comentario,
  estado (VISIBLE, OCULTA). l. 58: `resena.reserva_id` único y `CHECK` 1–5. l. 74: FK en todas las relaciones. l. 103: `RESERVA ||--o| RESENA`.
- Falta en el modelo: `creado_en`, longitud y obligatoriedad de `comentario`, cascadas, índices. Ver C6. Referencia: `reserva.usuario_id`
  y `espacio_id` son FK sin cascada (`V3_3`).
- Precedente: IT que borran `reserva` deberán borrar `resena` antes si la FK no tiene cascada (`PagosSoporte.limpiar` y similares).

## 6. Frontend

**Kit** (`F/components/ui/`): `Alerta, Boton, Campo, CuentaRegresiva, EtiquetaEstado, Galeria, GrupoCasillas, Imagen, Modal, SelectorBloques, Tarjeta`.

- `Campo`: `tipo="textarea"` (prop `filas`) para el comentario; `tipo="select"` con `opciones` sirve para 1–5. **No** tiene `maxLength`
  ni radios; `min`/`max` solo en la rama genérica.
- `Modal` (`primario`), `Alerta`, `Boton`, `Tarjeta` (`acciones` al pie) cubren el formulario y la lista.
- **No hay** componente de estrellas o calificación. Regla: si falta, "se agrega al kit" y se anota en "Pantallas" (`frontend.md` l. 177).
  Solo tokens; el color nunca es la única señal (la calificación lleva texto).
- `EtiquetaEstado`: no tiene `VISIBLE` ni `OCULTA`; `frontend.md` l. 148 ya los define (éxito, error). Se agregan si se muestran.

**API.** `F/api/cliente.js`: `peticion(ruta, { metodo, cuerpo, autenticada })`. `F/api/reservas.js`: `listarMisReservas`, `obtenerReserva`.
`F/api/busqueda.js`: `obtenerEspacio`, `consultarBloquesLibres` (`autenticada: false`). HU-24 va en `busqueda.js` por el módulo.

**Rutas** (`F/App.jsx`): `/espacios/:id` pública; `/mis-reservas` y `/reservas/:id` con `RutaProtegida`.

**Punto de entrada.** `F/pages/reservas/MisReservas.jsx`, sección "Pasadas": `tarjetaDeReserva(reserva, cancelar = null)` ya acepta un
nodo de acción en `acciones`; "Próximas" lo usa con `tarjetaProxima`, "Pasadas" lo llama sin acción. Precedente directo: un
`CancelarReserva` en la tarjeta y el contador `recarga` como `key` de `SeccionReservas` tras la acción, con `Alerta exito`. El
frontend no tiene reglas de negocio: **qué reserva se puede reseñar debe venir del servidor** (`frontend.md` principio 1), y hoy
`ReservaDTO` no lo dice. Ver C1 y C2. Los textos van en `textosReservas.js` y `textosFicha.js`.

Ojo: en "Mis reservas", "Ver espacio" da 404 si el espacio ya no es visible.

**Datos personales (HU-22), obligación de HU-24.** `architecture.md` l. 134, l. 597-598 y `specs/HU-22/design.md` DT-2: `busqueda`
implementa `J/cuentas/service/puertos/ParticipanteDatosPersonales.java` al crear `resena` y **decide si se exporta y cómo se anonimiza**.
`exportar` (sección de nombre único), `impedimentos` (`MANDATORY`, puede devolver vacío), `anonimizar` (`MANDATORY`). Bean con `@Order`: ya
existen `@Order(1)` reservas y `@Order(2)` pagos. Debe corregir la fila de la tabla de puertos. Si agrega una sección fija, ver C8b.

## 7. HU-27

`docs/historias/HU-27.md` (Kevin Chan, depende de HU-24; `pending`). Lo que HU-24 debe prever:

1. **`estado` VISIBLE/OCULTA** desde la migración (`modelo-datos.md` l. 36). Ficha y promedio deben contar solo `VISIBLE`, aunque HU-24 no
   oculte nada: si no, HU-27 tendría que tocar esas consultas.
2. **El id de la reseña es público.** `reporte.objetivo_id` con `tipo_objetivo = RESENA` (l. 38); CA1 reporta una reseña desde la ficha,
   así que el DTO público debe incluir `id`.
3. **Interfaz pública en `busqueda`.** `administracion → (todos)` solo por `service`: HU-27 necesitará leer y ocultar una reseña. Hoy no hay
   interfaz pública en `busqueda`. Se puede dejar a HU-27, que la crea, o prever una fila.
4. **Una reseña oculta sigue contando como "ya reseñada"** si el único es sobre `reserva_id` sin filtrar por estado (`architecture.md`
   principio 7, `modelo-datos.md` l. 58). No hay requisito sobre re-reseñar tras ocultar.
5. **Auditoría:** HU-27 registra reportes y acciones (R); no afecta a HU-24.
6. `frontend.md` l. 148 ya reserva el estado `OCULTA`.

HU-27 no dice si el autor ve su reseña oculta, ni si se le avisa. CA3 solo cubre publicaciones.

## 8. Contradicciones y huecos (sin resolver)

**C1. "`ReservaDTO` se puede ampliar sin romper" frente a las pruebas.** `architecture.md` l. 531 y `specs/HU-14/design.md` l. 497 lo permiten,
pero estas pruebas fijan los 11 campos y fallarían: `T/reservas/service/impl/ReservaServiceImplTest.java:143, :416, :439`
(`containsExactly`, HU-12 R41), `T/reservas/service/ConsultaReservasServiceTest.java:179`, `T/reservas/controller/MisReservasControllerIT.java:241`
(`hasSize(11)`), `T/reservas/controller/ReservaControllerIT.java:89` (`containsExactlyInAnyOrder`) y, por nombre, `:581`. Además, `ConversionReservas.aDto`
es la única fuente y la usan `crear` y `consultar`, donde un campo de reseña no aplica.

**C2. Ampliar `ReservaDTO` frente a la dirección de dependencias.** La tabla `resena` vive en `busqueda` (`architecture.md` l. 52; `feature_list.json`
`module: busqueda`), pero `reservas` no puede llamar a `busqueda`. Si `ReservaDTO` debe decir "ya reseñada" o "reseñable", hace falta un puerto
nuevo en `reservas/service/puertos/`, carpeta inexistente. La alternativa es una ruta propia de `busqueda` que el frontend combine con la lista,
pero `frontend.md` prohíbe reglas en el cliente. Ninguna doc elige.

**C3. HU-11 y HU-23 sobre `FichaPublicaDTO`.** `specs/HU-11/design.md` DT-16 y l. 429 dicen que HU-23 agrega características; `specs/HU-23/design.md`
DT-12 dice que no cambia, y el código no las tiene. Solo importa para no asumir que la ficha ya creció.

**C4. Dueño desactualizado.** `specs/HU-14/design.md` l. 497 nombra a Adriana Callupe; `feature_list.json`, `docs/historias/HU-24.md` y
`progress/HU-24.md` a Ariel Enriquez (cambio local sin commit, según `progress/HU-24.md`). Estado en `feature_list.json`: `pending`.

**C5. Sanitización sin definir.** HU-24 (T) y RNF-SEG-10 piden "texto sanitizado contra inyección de scripts", pero ninguna doc dice si es al
guardar (limpiar o rechazar) o al mostrar, ni existe el componente. React ya escapa. Escapar al guardar con `HtmlUtils` dejaría entidades en la
exportación de HU-22 y en la ficha renderizada. Una dependencia nueva (jsoup) no está discutida.

**C6. Modelo incompleto.** `resena` no trae `creado_en` (necesaria para ordenar la ficha; `reserva` y otras la tienen), longitud ni obligatoriedad de
`comentario`, ni cascadas. HU-24 dice "calificarlo de 1 a 5 y dejar un comentario"; RF-C-08 "calificar de 1 a 5 y comentar": no aclara si el
comentario es opcional. `modelo-datos.md` exige justificar y actualizar el archivo si el `design.md` lo cambia (l. 5-7).

**C7. "14 días siguientes" a qué, y "su fecha ya pasó".** HU-24 CA1 dice "cuya fecha ya pasó"; RF-C-08 "confirmada ya pasada"; RN-13 "dentro de los
14 días siguientes". Nadie fija la referencia: `fecha` calendario, `horaFin` o `instanteDeFin` (el de HU-19/HU-26), ni si son 14 × 24 h o días de
Lima, ni el borde inclusivo. Con "fecha", una reserva de hoy ya terminada no es reseñable; con `fin`, sí.

**C8. Flecha `busqueda → cuentas`.** El diagrama (`architecture.md` l. 126-128) no la muestra, pero el puerto `ParticipanteDatosPersonales` la implica
(l. 134, "busqueda lo sumará") y mostrar el nombre del autor requeriría `CuentaService.nombres`. Además, l. 217: "No se lee `usuario` desde
`busqueda`" (se refiere a la tabla). Ninguna doc dice qué se muestra del autor en la ficha **pública**: nombre, nombre por defecto o nada. RNF-SEG-16
solo regula al arrendador y la relación arrendador-usuario. Una cuenta `ELIMINADA` tiene `nombre` nulo, y `CuentaService.nombres` la omite.

**C8b.** Agregar una sección fija a la exportación rompe `T/cuentas/controller/DatosPersonalesControllerIT.java:116, :138, :157, :162`
(`containsExactly("reservas", "pagos")`, detalle de auditoría `secciones=…`). `DatosPersonalesServiceTest` usa dobles.

**C9. CA2 por espacio, regla por reserva.** CA2: "no tengo una reserva pasada en ese espacio". RF-C-09 igual. RF-C-08, RN-13 y el modelo: por **reserva**.
No se define la forma de la ruta (`POST` con `reservaId`, o bajo `/api/reservas/{id}/…` o `/api/espacios/{id}/…`), ni qué código dar (400 o 404) a una
reserva ajena frente a una propia no reseñable. Las rutas de reservas responden 404 genérico para lo ajeno.

**C10. Espacio ya no visible.** Ningún CA dice si se puede reseñar una reserva de un espacio `PAUSADO`, `BAJA`, `OCULTO` o de arrendador suspendido.
La ficha da 404, así que no se vería; al volver a `PUBLICADO` reaparecería. `ReservaService.consultar` no filtra visibilidad.

**C11. Reserva con reembolso.** HU-26 (`pending`) puede reembolsar por reclamo sin cancelar. Nada dice si esa reserva `CONFIRMADA` sigue siendo reseñable.

**C12. Sin paginación ni orden definidos.** Ninguna doc dice cuántas reseñas lleva la ficha, en qué orden, si van en `FichaPublicaDTO` (HU-11 DT-16) o en
ruta paginada aparte, ni cómo se calcula el promedio (decimales, valor sin reseñas). `modelo-datos.md` no guarda promedio: se calcularía al consultar.

**C13. Fuera de alcance confirmado.** Respuesta del arrendador a reseñas (`architecture.md` l. 736). Sin edición ni borrado por el autor en ningún CA.
HU-24 no es ★: sin patrón exigido.
