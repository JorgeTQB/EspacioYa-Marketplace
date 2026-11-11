# Frontend — Cómo se construyen las pantallas

> Seis personas hacen pantallas en paralelo. Esta guía existe para que todas
> se vean como una sola aplicación y para que ningún agente "invente" estilos.
> Si algo no está aquí ni en el `design.md` de la historia, no se hace.
>
> Estilo de código React: `docs/conventions.md`. Arquitectura general:
> `docs/architecture.md`.

## Principios

1. **El frontend no tiene reglas de negocio.** Muestra lo que devuelve la API;
   nunca calcula montos, roles ni estados (`docs/architecture.md`, principio 6).
2. **Los requirements se escriben sobre la API.** Cada historia describe sus
   pantallas en la sección "Pantallas" de su `design.md`, pero sus `R<n>` se
   verifican con tests del backend. La pantalla se verifica con lint, build y
   la caja negra de la pareja en UAT (`docs/verification.md`).
3. **Un kit propio, sin librerías de componentes.** Nada de MUI, Ant Design,
   Bootstrap, Tailwind ni similares: obligan a aprender su sistema y chocan con
   el kit. Agregar cualquier dependencia al `package.json` se discute antes
   (estado `blocked`).
4. **Un solo origen para los estilos.** Todos los colores, espacios, bordes y
   tamaños de texto salen de `src/styles/tokens.css`.

## Estructura de carpetas

```
3_frontend/src/
├── main.jsx              # Punto de entrada; monta el router
├── index.css             # Estilos base (usa los tokens)
├── styles/
│   └── tokens.css        # ÚNICO archivo con colores literales
├── components/
│   └── ui/               # Kit: Boton, Campo, GrupoCasillas, Alerta, EtiquetaEstado, Tarjeta, Modal, SelectorBloques, Imagen, Galeria, CuentaRegresiva
├── pages/                # Una carpeta por módulo: pages/cuentas/Registro.jsx
├── sesion/               # ProveedorSesion, useSesion, RutaProtegida (HU-03)
└── api/                  # cliente.js, tokenSesion.js + un archivo por módulo: api/cuentas.js
```

- Las páginas se agrupan por el mismo módulo del backend (`cuentas`,
  `espacios`, `busqueda`, `reservas`, `pagos`, `administracion`).
- Cada componente lleva su CSS al lado como **CSS Module**
  (`Boton.jsx` + `Boton.module.css`), así las clases de dos personas no chocan.

## Tokens

`src/styles/tokens.css` define variables CSS. Reglas:

- ❌ Prohibido escribir colores (`#hex`, `rgb()`, `hsl()`) fuera de `tokens.css`.
  `./init.sh` lo revisa.
- Los espacios son múltiplos de 4 px (`--espacio-1` = 4 px … `--espacio-8` = 32 px).
- Radios: `--radio` (8 px) para botones, campos y etiquetas; `--radio-tarjeta`
  (12 px) para tarjetas y modales.
- Una sola tipografía (`--fuente`, la del sistema) y la escala `--texto-xs` a
  `--texto-2xl`.
- Solo modo claro: ninguna historia pide modo oscuro.
- `tokens.css` se importa una sola vez, en `src/main.jsx`, antes de `index.css`.

## Paleta

**Colores base**

| Token | Hex | Dónde se usa |
|-------|-----|--------------|
| `--color-primario` | `#1D4ED8` | Botón principal (Reservar, Pagar, Publicar, Guardar), enlaces, bloque horario seleccionado, pestaña o menú activo, contorno del foco |
| `--color-primario-hover` | `#1E3A8A` | El mismo botón o enlace al pasar el mouse o al presionarlo |
| `--color-primario-suave` | `#DBEAFE` | Fondo de una fila o tarjeta seleccionada, halo del campo con foco, alertas informativas |
| `--color-texto` | `#0F172A` | Títulos, texto principal y montos |
| `--color-texto-secundario` | `#475569` | Etiquetas de campos, fechas, descripciones, texto de ayuda, texto del bloque ocupado |
| `--color-texto-deshabilitado` | `#94A3B8` | Placeholders y controles deshabilitados |
| `--color-fondo` | `#F8FAFC` | Fondo de toda la página |
| `--color-superficie` | `#FFFFFF` | Fondo de tarjetas, formularios, modales y tablas |
| `--color-borde` | `#E2E8F0` | Bordes decorativos: tarjetas, tablas, separadores y modales |
| `--color-borde-control` | `#64748B` | Borde de controles: campos de texto, select, textarea, casillas, radios y botón secundario |
| `--color-gris-suave` | `#F1F5F9` | Fondo de bloques ocupados y filas alternas de tablas |

**Colores del encabezado y la página de inicio** (paleta de la referencia visual; no se usan en otras pantallas)

| Token | Hex | Dónde se usa |
|-------|-----|--------------|
| `--color-marca` / `--color-marca-hover` | `#1565E0` / `#0F4FB8` | Logo, botón "Regístrate", botón "Buscar" del hero, iconos y enlaces del inicio (blanco encima: 5.3:1) |
| `--color-marino` | `#0B2A4A` | Fondo del hero, "Espacio" del logo, títulos de sección del inicio |
| `--color-marca-claro` | `#5AAEF7` | Acento del título del hero sobre marino (6.1:1) |
| `--color-celeste` / `--color-celeste-banda` | `#EEF5FD` / `#E5F3FE` | Fondo de las secciones del inicio y de la banda de ventajas |

**Colores de estado.** Cada uno es un fondo claro y un texto oscuro de la misma
familia; los usan `EtiquetaEstado` y `Alerta`.

| Tipo | Fondo | Texto |
|------|-------|-------|
| Éxito | `--color-exito-fondo` `#DCFCE7` | `--color-exito-texto` `#15803D` |
| Atención | `--color-atencion-fondo` `#FEF3C7` | `--color-atencion-texto` `#B45309` |
| Error | `--color-error-fondo` `#FEE2E2` | `--color-error-texto` `#B91C1C` |
| Neutro | `--color-neutro-fondo` `#F1F5F9` | `--color-neutro-texto` `#475569` |
| Info (solo `Alerta`) | `--color-info-fondo` = primario suave | `--color-info-texto` = primario hover |

**Por qué azul y no verde:** el verde ya significa "confirmada" o "pagada". Si
la marca también fuera verde, un botón y un estado de éxito se confundirían.
El azul además transmite confianza en la pantalla de pago.

**Contraste.** Todo texto sobre su fondo cumple WCAG AA (4.5:1), y todo borde
que permite reconocer un control cumple 3:1 (WCAG 1.4.11):

- Un campo blanco sobre una tarjeta blanca solo se distingue por su borde; por
  eso los controles usan `--color-borde-control` (4.76:1) y no `--color-borde`
  (1.23:1). `--color-borde` queda solo para bordes decorativos, a los que la
  regla no exige 3:1 y que con un gris fuerte harían la interfaz pesada.
- `--color-texto-deshabilitado` (2.6:1 sobre blanco) solo se usa en controles
  deshabilitados, que están exentos, y en placeholders, que nunca reemplazan a
  la etiqueta del campo.

Si agregas un token, comprueba su contraste antes. Alternativa descartada para
el borde de controles: `#94A3B8`, que da 2.56:1 y no llega al mínimo.

## Reglas de uso

- **Un solo botón primario por pantalla**, para la acción principal. Las demás
  acciones usan el botón secundario: fondo `--color-superficie`, borde
  `--color-borde-control` y texto `--color-texto`.
- **Bordes de un campo según su estado:** normal `--color-borde-control`; con
  foco `--color-primario` (6.7:1) más un anillo de 2 px; con error
  `--color-error-texto` (6.5:1). El foco se ve siempre, también al navegar con
  el teclado.
- **El rojo es solo para errores y acciones destructivas.** "Cancelar reserva"
  o "Dar de baja" son botones `peligro`: texto `--color-error-texto` sobre
  fondo blanco. Solo dentro del `Modal` de confirmación el botón pasa a rojo
  relleno (`peligro-confirmar`).
- **Montos** (precio, total, reembolso, comisión): siempre en `--color-texto`
  con `--peso-negrita`. Nunca en verde ni en rojo: esos colores son de los estados.
- Ningún color fuera de `tokens.css`. Los componentes usan siempre
  `var(--color-…)`. Si hace falta un color nuevo, se agrega a `tokens.css` y a
  esta guía.

## Color de cada estado

`EtiquetaEstado` recibe el valor del enum tal como lo devuelve la API y
siempre muestra el **texto** del estado, no solo el color.

| Entidad | Éxito | Atención | Error | Neutro |
|---------|-------|----------|-------|--------|
| Reserva | `CONFIRMADA` | `PENDIENTE_PAGO` (con el contador de 15 min) | — | `CANCELADA`, `EXPIRADA` |
| Pago | `PAGADO` | `PAGADO_TARDIO`, `MONTO_DISTINTO` | `RECHAZADO`, `REEMBOLSO_FALLIDO` | `REEMBOLSADO` |
| Reembolso (HU-16) | — (`REEMBOLSADO` no se muestra en el panel) | `PENDIENTE` ("Sin respuesta de la pasarela") | `FALLIDO` ("Reembolso fallido") | — |
| Espacio | `PUBLICADO` | `PAUSADO` | `OCULTO` (moderación) | `BORRADOR`, `BAJA` |
| Identidad del arrendador | `VERIFICADA` | `EN_REVISION` | `RECHAZADA` | `NO_INICIADA` |
| Cuenta | `ACTIVA`, correo verificado | — | `SUSPENDIDA` | correo pendiente, `ELIMINADA` |
| Liquidación (HU-19) | `PAGADA` ("Pagada") | `GENERADA` ("Por pagar"), `PENDIENTE_CUENTA` ("Pendiente de cuenta") | — | — |
| Reseña (HU-27 en `EtiquetaEstado`) | `VISIBLE` ("Visible") | — | `OCULTA` ("Oculta", moderación) | — |
| Reclamo (HU-26) | — | `ABIERTO` ("Abierto") | — | `RESUELTO_USUARIO` ("Resuelto a favor del usuario"), `RESUELTO_ARRENDADOR` ("Resuelto a favor del arrendador") |
| Reporte (HU-27) | — | `POR_REVISAR` ("Por revisar") | — | `MANTENIDO` ("Contenido mantenido"), `OCULTADO` ("Contenido ocultado") |
| Cuenta bancaria (HU-18) | `DISPONIBLE` ("Disponible para liquidaciones") | `RETENIDA` ("Retenida 72 h") | — | — |

Cancelada y expirada van en neutro, no en error: no son fallos, son finales
normales de una reserva. Los estados que aún no tienen enum (reclamo, cuenta
bancaria) los nombra el spec de su historia, que agrega aquí los que falten.

## Bloques horarios

Son la pantalla central de HU-11 y HU-12 (`SelectorBloques`). Tres estilos fijos:

| Estado del bloque | Estilo |
|-------------------|--------|
| Libre | Fondo blanco, borde y texto `--color-primario` (`--bloque-libre-*`) |
| Seleccionado | Fondo `--color-primario`, texto blanco (`--bloque-seleccionado-*`) |
| Ocupado | Fondo `--color-gris-suave`, texto `--color-texto-secundario` tachado, sin clic (`--bloque-ocupado-*`) |

**Cómo alimentar `SelectorBloques` (HU-08 lo construyó).** El prop `bloques` es
`[{ horaInicio, horaFin, libre, detalle }]`; el componente no llama a la API.

- Desde una lista de horas libres (lo que devuelve `horasLibres`): `horas.map(h => ({ horaInicio: h, horaFin: h + 1, libre: true }))`. Solo se ven los libres.
- Desde bloques con estado (como `GET …/disponibilidad` del dueño, que usa `DisponibilidadEspacio.jsx`): `libre = estado === 'LIBRE'` y `detalle` con un texto de `textosEspacios.js`. En la ficha pública el `detalle` es genérico ("No disponible"), sin decir si está reservado o bloqueado.
- HU-11 y HU-12 (`pages/busqueda/FichaEspacio.jsx`) lo alimentan solo con los libres de `GET /api/espacios/{id}/disponibilidad?fecha=` (`bloquesLibres.map(b => ({ ...b, libre: true }))`); con la lista vacía la pantalla muestra "No hay bloques libres este día. Prueba con otra fecha.". Para quien puede reservar (visitante o rol `USUARIO`) va **con** `onCambiar` y `seleccionados`; para `ARRENDADOR` y `ADMIN`, **sin** `onCambiar` (solo lectura) y sin botón. Que los bloques elegidos sean consecutivos lo valida la pantalla como ayuda y el servidor de verdad, no el componente.
- **Ficha y reserva (HU-12):** la ficha es `/espacios/:id?fecha=AAAA-MM-DD`. Debajo de "Bloques libres" va el único botón primario "Reservar"; "Reservar" crea la reserva sin modal, porque nada se cobra aún. La selección y el error de la reserva se guardan junto a la consulta (`id|fecha`), de modo que cambiar de fecha los descarta sin un efecto. El monto solo se ve en `/reservas/:id`, tras la respuesta del servidor. Si la API responde 400 o 404, `Alerta error` con el mensaje tal cual, selección vacía y bloques recargados.
- Fecha elegible: de hoy a hoy + 55 días en hora de Lima. El servidor responde 400 fuera de esa ventana; el `Campo tipo="date"` acepta `min`/`max` (HU-08) para limitarla en el cliente.

## Kit de componentes (`src/components/ui/`)

Las pantallas solo usan estos componentes para estos elementos. **Si falta uno,
se agrega al kit, no se improvisa en la pantalla**, y se anota en la sección
"Pantallas" del `design.md`.

| Componente | Para qué | Props mínimas |
|------------|----------|---------------|
| `Boton` | Toda acción | `variante` (`primario`, `secundario`, `peligro`, `peligro-confirmar`), `cargando`, `deshabilitado`, `onClick` |
| `Campo` | Toda entrada de formulario, con su etiqueta (`tipo="select"` para listas cerradas, HU-02; `tipo="archivo"` para subir un archivo, HU-05: no controlado, `onChange` recibe el archivo elegido o `null`; `tipo="textarea"` para textos largos, HU-07) | `etiqueta`, `nombre`, `tipo`, `valor`, `onChange`, `error`, `ayuda`, `opciones` (`[{ valor, etiqueta }]`, solo con `select`), `accept` (solo con `archivo`), `filas` (solo con `textarea`, por defecto 4), `min` y `max` (en la rama genérica, p. ej. `tipo="date"`; HU-08), `step` (en la rama genérica, p. ej. `step="0.01"` para un precio con decimales; HU-23) |
| `GrupoCasillas` | Marcar varias opciones de una lista cerrada con una leyenda común. Construido por HU-23 ("Características" en "Editar espacio" y en "Buscar espacios"): `<fieldset>` con `<legend>` y un `Campo tipo="checkbox"` por opción; el error y la ayuda van debajo del grupo con el estilo de `Campo` y el `fieldset` los referencia con `aria-describedby`. `onChange` recibe los valores marcados en el orden de `opciones`. Solo tokens | `leyenda`, `nombre`, `opciones` (`[{ valor, etiqueta }]`), `valores` (arreglo de los marcados), `onChange(nuevosValores)`, `error`, `ayuda` |
| `Alerta` | Mensajes de la API y de éxito | `tipo` (`exito`, `atencion`, `error`, `info`), `children` |
| `EtiquetaEstado` | Estado de una entidad (tabla anterior). Construido por HU-05 con los estados de identidad; cada historia agrega al mapa los de su entidad (HU-07: los de `Espacio`; HU-09: los de `Cuenta`, `ACTIVA`, `SUSPENDIDA` y `ELIMINADA`; HU-12: los de `Reserva`; HU-16: los de `Reembolso`, `FALLIDO` y `PENDIENTE`; HU-18: los de la cuenta bancaria, `DISPONIBLE` y `RETENIDA`; HU-19: los de `Liquidación`, `GENERADA`, `PENDIENTE_CUENTA` y `PAGADA`; HU-26: los de `Reclamo`, `ABIERTO`, `RESUELTO_USUARIO` y `RESUELTO_ARRENDADOR`) | `estado` |
| `Tarjeta` | Agrupar contenido: espacio en resultados (HU-10, "Buscar espacios"), reserva en "Mis reservas" y en "Reservas de mis espacios" (HU-14; en `acciones` HU-15 pone el `Boton peligro` "Cancelar reserva" de las `CONFIRMADA` de Próximas). Construido por HU-07 ("Mis espacios") | `titulo`, `children`, `acciones` (nodo opcional al pie) |
| `Imagen` | Foto pública con proporción 4:3 (`object-fit: cover`, `--radio`, `loading="lazy"`). Sin `src` muestra una caja `--color-gris-suave` con "Sin foto" en `--color-texto-secundario`. Construido por HU-10 (resultados de búsqueda); HU-11 lo reutiliza en la ficha. Para la foto de un borrador, que exige token, sigue `FotoDelEspacio` de HU-07 | `src` (URL o `null`), `alt` |
| `Galeria` | Fotos de un espacio en su ficha pública. Construido por HU-11: la foto elegida en grande con `Imagen` (`alt` = "`<alt>`, foto n de N") y, si hay más de una, una fila de miniaturas; cada una es un `<button type="button" aria-pressed>` con `aria-label` "Ver foto n de N" y la elegida lleva borde `--color-primario`. Empieza en la primera (orden 1). Sin fotos muestra un `Imagen` "Sin foto". No llama a la API | `fotos` (URL ya completas, 0–5, en orden), `alt` (texto base, p. ej. el nombre del espacio) |
| `Modal` | Confirmar acciones irreversibles o importantes (cancelar, suspender, dar de baja, aprobar o rechazar una identidad). Construido por HU-06: `<dialog>` con `showModal()` (foco atrapado; `Esc` llama a `onCerrar`, salvo mientras `cargando`), pie con `Boton secundario` (texto `textoCerrar`, por defecto "Cancelar"; HU-15 usa "Volver" en "¿Cancelar esta reserva?") y el botón de confirmación; fondo oscurecido con `color-mix` sobre `--color-texto` | `abierto`, `titulo`, `onConfirmar`, `onCerrar`, `children`, `textoConfirmar` (por defecto "Confirmar"), `textoCerrar` (por defecto "Cancelar", HU-15), `variante` (`primario` o `peligro-confirmar`), `cargando` |
| `SelectorBloques` | Elegir bloques horarios (HU-11, HU-12) o mostrarlos. Construido por HU-08: cada bloque muestra `HH:00–HH:00`; el ocupado va tachado, sin clic y con su `detalle` como texto. Sin `onCambiar` es **solo lectura** (los bloques no son botones); con `onCambiar`, cada bloque libre es un `<button aria-pressed>` y al pulsarlo llama `onCambiar` con el arreglo nuevo de horas. Lista vacía: no renderiza nada | `bloques` (`[{ horaInicio, horaFin, libre, detalle }]`), `seleccionados` (horas de inicio, por defecto `[]`), `onCambiar` (opcional) |
| `CuentaRegresiva` | Tiempo restante en `mm:ss`, con `role="timer"`. Construido por HU-12 ("Tus bloques están retenidos por:"). Calcula el fin al montarse y compara contra `Date.now()`, para no atrasarse con la pestaña en segundo plano; llama a `alTerminar` una sola vez. Solo avisa: el estado lo decide el servidor al releer. Para reiniciarlo con otros segundos, se le cambia la `key` | `segundos`, `alTerminar` |

**Quién los construye.** Igual que los componentes transversales de `comun`:
los construye la primera historia que los necesita (la de menor sprint y, a
igualdad, menor id) y los demás los reutilizan. El kit base del Sprint 1
(`Boton`, `Campo`, `Alerta`, el router y `api/cliente.js`) lo construye
**HU-01**; HU-02, HU-03 y HU-04 dependen de ella.

## Mensajes y estados de carga

Es justo lo que piden los criterios de aceptación ("veo un mensaje genérico",
"veo los requisitos mínimos"). Siempre igual:

| Situación | Cómo se muestra |
|-----------|-----------------|
| Error de validación de un campo (formato, obligatorio) | Texto `--color-error-texto` debajo del `Campo` (prop `error`) y borde del campo del mismo color. La validación del cliente es solo de ayuda: el servidor siempre revalida |
| Error de la API (cualquier 4xx o 5xx) | `Alerta` de tipo `error` arriba del formulario, con el mensaje del `ErrorDTO` tal cual. Si el `ErrorDTO` trae errores por campo, van además bajo cada `Campo` (su forma la fija el spec que crea el `ManejadorGlobalErrores`) |
| Error de red (sin respuesta) | `Alerta` de error: "No pudimos conectar. Intenta de nuevo." |
| Éxito | `Alerta` de tipo `exito`, o navegar a la pantalla siguiente |
| Operación en curso | `Boton` con `cargando`: deshabilitado hasta la respuesta, para que no se envíe dos veces (reservas, pagos) |
| Lista vacía | Texto secundario que explique qué falta ("Aún no tienes reservas") |

- Los mensajes de seguridad son genéricos y no revelan si un correo o recurso
  existe (RNF-USA-03, RNF-SEG-13). El frontend no los reescribe más específicos.
- Nunca se muestran códigos HTTP, stack traces ni ids internos.
- Textos en español, tratando al usuario de "tú".

## Rutas y llamadas a la API

- **Router:** `react-router-dom`, que agrega HU-01. Rutas en español,
  minúsculas y con guiones: `/registro`, `/espacios/:id`, `/mis-reservas`.
- **Cliente HTTP:** un solo `src/api/cliente.js` (sobre `fetch`) que pone la
  URL base (`import.meta.env.VITE_API_URL`), el JSON y convierte las
  respuestas de error en el `ErrorDTO`. Para descargar un archivo con el token
  (p. ej. la foto de un borrador, que un `<img src>` no puede pedir con el
  encabezado `Authorization`) está `peticionArchivo(ruta)`, que devuelve un
  `Blob` con el mismo manejo de errores (HU-07). Cada módulo tiene su archivo
  (`api/reservas.js`) con una función por endpoint. Los componentes nunca
  llaman a `fetch` directamente.
- **Cómo viaja la sesión (HU-03):** token opaco en el encabezado
  `Authorization: Bearer <token>`, que `cliente.js` agrega solo si hay token.
  El token se guarda en `sessionStorage` (`api/tokenSesion.js`, clave
  `espacioya.token`), nunca en `localStorage` ni en cookies. Si una solicitud
  con token recibe 401, `cliente.js` borra el token y avisa a
  `alPerderSesion`; `ProveedorSesion` lleva entonces a `/iniciar-sesion` con el
  mensaje. El frontend no tiene temporizador de inactividad: decide el backend.
- **Buscar espacios con filtros (HU-23, `pages/busqueda/BuscarEspacios.jsx`):** además de tipo, zona y fecha (HU-10),
  `Campo tipo="number"` "Precio máximo por hora (S/)" (`step="0.01"`), "Capacidad mínima (personas)" y `GrupoCasillas`
  "Características" (`CARACTERISTICAS` de `pages/espacios/textosEspacios.js`). Los filtros viven en la URL
  (`precioMaximo`, `capacidadMinima` y `caracteristica` repetido, leído con `getAll`); "Buscar" omite los vacíos y
  "Anterior"/"Siguiente" copian la URL y solo cambian `pagina`. `buscarEspacios` agrega un `caracteristica` por valor.
  Con la página 1 vacía y algún filtro en la URL se muestra `SIN_RESULTADOS_CON_FILTROS`, `SUGERENCIA_QUITAR_FILTRO` y
  el `Boton secundario` "Quitar filtros", que deja solo `tipo`, `zona`, `fecha` y `pagina=1` (CA3). Errores por campo
  bajo cada filtro. "Editar espacio" (HU-07) suma el `GrupoCasillas` "Características" tras "Descripción".
- **Inicio de sesión en dos pasos (HU-17, `pages/cuentas/IniciarSesion.jsx`):** si `POST /api/sesiones` responde
  `{ desafio, paso }` (202) en vez de `token`, la pantalla pasa al paso `codigo` (`PasoCodigoMfa.jsx`:
  `verificarMfa`) o, para el admin sin MFA, a `configuracion` (`ConfiguracionMfa.jsx`: `configurarMfaEnInicio`,
  clave en grupos de 4 y enlace `otpauth://`, sin QR) y luego a `codigos` (`CodigosRespaldo.jsx`: los 5 códigos una
  sola vez; `entrar` recién tras "Ya los guardé, continuar"). El desafío vive **solo en el estado de React**, nunca en
  `sessionStorage`; las dos llamadas van con `autenticada: false`, así que su 401 (verificación expirada) vuelve al paso
  de la contraseña con el mensaje y no cuenta como sesión perdida. Un código incorrecto es 400 bajo el `Campo`.
- **`/panel/cuenta-bancaria` (HU-18, `pages/pagos/CuentaBancaria.jsx`, con `RutaProtegida`):** "Cuenta bancaria" (enlace
  desde `Panel` solo para `ARRENDADOR`; otro rol vuelve a `/panel`). Lee `obtenerCuentaBancaria()`: con cuenta, `Tarjeta` con
  banco, número enmascarado, titular, `EtiquetaEstado` (`DISPONIBLE`/`RETENIDA`) y la fecha de retención; sin cuenta, "Aún
  no tienes una cuenta bancaria registrada." El formulario (`Campo` de banco, número, casilla de titularidad y contraseña;
  `Boton` "Guardar cuenta") llama a `guardarCuentaBancaria`; con cuenta ya registrada muestra `Alerta atencion` sobre el
  correo y la retención de 72 h; con la identidad sin verificar, `Alerta atencion` con enlace a `/panel/identidad` y el botón
  deshabilitado. Errores por campo bajo su `Campo`; 400 sin campo, 429 y 5xx en `Alerta error`. La contraseña se vacía tras
  cada respuesta y el número solo vive en el estado del formulario (se limpia al guardar).
- **`/panel/seguridad` (HU-17, `pages/cuentas/SeguridadCuenta.jsx`, con `RutaProtegida`):** "Seguridad de la cuenta"
  (enlace desde `Panel` para los tres roles). Lee `obtenerMfa()`: inactivo → "Activar verificación en dos pasos"
  (`iniciarConfiguracionMfa`, `ConfiguracionMfa.jsx`, `activarMfa`, `CodigosRespaldo.jsx`); activo → `Alerta exito` y
  "Te quedan N códigos de respaldo."; si la API dice `desactivable`, `Boton peligro` "Desactivar" con `Modal`
  `peligro-confirmar` (contraseña y código); si no (admin), "es obligatoria". No agrega componentes al kit, estados ni
  colores.
- **`/panel/perfil` (HU-20, `pages/cuentas/Perfil.jsx`, con `RutaProtegida`):** "Mi perfil" (enlace desde `Panel` para
  `USUARIO` y `ARRENDADOR`; un `ADMIN` vuelve a `/panel`). Cuatro `Tarjeta`: "Datos personales" (`actualizarDatosPerfil`, sin
  contraseña; el formulario toma los valores de la respuesta), "Foto" (`obtenerFotoPerfil` con `peticionArchivo` →
  `URL.createObjectURL`, revocado al reemplazarla o salir; sin foto, `Imagen` "Sin foto"; `subirFotoPerfil` y
  `quitarFotoPerfil`), "Correo" (`Alerta info` con el pendiente; `solicitarCambioCorreo`) y, solo `ARRENDADOR`, "Datos de
  facturación" (`EtiquetaEstado` de identidad, `actualizarFacturacion`; con `VERIFICADA`, `Alerta atencion` de vuelta a
  revisión; con `EN_REVISION`, documento y razón social como texto y solo la dirección como `Campo`; tras guardar llama a
  `refrescar()` de `useSesion` para que el aviso del panel cambie). El correo y la facturación piden la contraseña en un
  `Modal primario` "Confirma con tu contraseña": un error de `contrasena` o sin campo (429, en revisión, documento no
  disponible) se queda en el modal; uno de otro campo lo cierra y se ve bajo su `Campo`. La contraseña vive solo en el
  estado del modal y se vacía tras cada respuesta. No agrega componentes al kit, estados ni colores.
  **HU-22** agrega al final una quinta `Tarjeta`, "Mis datos personales" (`pages/cuentas/DatosPersonales.jsx`, no es del
  kit), con `Boton secundario` "Descargar mis datos" y `Boton peligro` "Eliminar mi cuenta". Descargar abre el `Modal
  primario` "Confirma con tu contraseña" (`exportarMisDatos`, con `peticion`): con 200 arma un `Blob` JSON y lo descarga con
  un `<a download="espacioya-mis-datos-<fecha>.json">` temporal (fecha de Lima de `generadaEn`, `URL.createObjectURL`
  revocado al terminar) y muestra `Alerta exito`. Eliminar abre el `Modal` `peligro-confirmar` "¿Eliminar tu cuenta?" con
  `Alerta atencion` y la contraseña (`eliminarMiCuenta`): con 200, `borrarToken()`, `salir()` y `navegar('/iniciar-sesion',
  { replace: true, state: { mensaje, tipo: 'exito' } })` (el token se borra antes, así el 401 siguiente no avisa de sesión
  perdida). Un 400 con `reservas`, `liquidaciones` o `pagos` cierra el modal y lista los mensajes en un `Alerta atencion`
  "Antes de eliminar tu cuenta, resuelve esto:" con enlaces (`reservas` → `/mis-reservas` o `/panel/reservas` según el rol;
  `liquidaciones` y `pagos` → `/panel/liquidaciones`); `contrasena`, 429 o sin campo se quedan en el modal. El JSON solo vive
  en memoria. No agrega componentes al kit, estados ni colores.
  **HU-25** agrega la `Tarjeta` "Recordatorios" (`pages/cuentas/Recordatorios.jsx`, no es del kit) entre "Correo" y "Datos de facturación": al montar llama a `obtenerPreferenciasCorreo` (mientras tanto "Cargando…"), un `Campo tipo="checkbox"` "Recibir un recordatorio por correo 24 h antes de cada reserva" con la `ayuda` fija "Los correos de pago y de cancelación se envían siempre." (CA3) y `Boton secundario` "Guardar" (`cargando`; el único primario de la pantalla es "Guardar datos") que llama a `actualizarPreferenciasCorreo({ recordatoriosActivos })`. Con 200, `Alerta exito` "Guardamos tu preferencia de recordatorios." y la casilla toma el valor de la respuesta; un 400 de `recordatoriosActivos` va bajo el `Campo`; otro error (incluido sin red) en `Alerta error` con el mensaje tal cual. No agrega componentes al kit, estados ni colores.
- **`/confirmar-correo` (HU-20, `pages/cuentas/ConfirmarCorreo.jsx`, pública):** como `/restablecer-contrasena`, lee el
  token del `#token=…` y lo quita de la URL con `navegar(pathname, { replace: true })`. No confirma al cargar (D19): un
  único `Boton primario` "Confirmar mi nuevo correo" llama a `confirmarCambioCorreo` (`autenticada: false`). 200 → `Alerta
  exito` con el mensaje de la API y enlace a "Iniciar sesión" (o "Ir a mi perfil" con sesión); 400 o sin token → `Alerta
  error` y "Ir a mi perfil".
- **`ProveedorSesion.refrescar()` (HU-20):** vuelve a leer `obtenerSesionActual()` y actualiza `{ rol, aviso }`; si falla,
  deja la sesión como estaba. Se expone junto a `entrar` y `salir`.
- **Rutas con sesión:** se envuelven en `<RutaProtegida>`; el estado
  `{ rol, aviso }` se lee con `useSesion()`. El rol solo elige qué mostrar.
- **Volver tras el login (HU-12):** se navega a `/iniciar-sesion` con
  `state: { retorno, mensaje, tipo }`, y `IniciarSesion.jsx` va a `state.retorno` o, si
  no hay o no es válido, a `/panel`, en sus **dos** salidas (`<Navigate>` y `navegar`).
  `sesion/retorno.js` (`retornoValido`) solo acepta rutas con una sola barra inicial; lo
  usan `IniciarSesion` y `ProveedorSesion`. Va en el `state` de React
  Router y no en la URL, para que un enlace no pueda forjar el destino. La ficha arma
  `retorno` con la fecha en uso (`/espacios/${id}?fecha=${fecha}`), no copiando
  `location`. `ProveedorSesion` agrega `retorno` (`pathname + search` de
  `window.location`) cuando la sesión caduca (401 con token), en cualquier ruta salvo
  el propio login. `RutaProtegida` aún no guarda el retorno. Los bloques elegidos no
  se conservan al volver.
- **`/reservas/:id` (HU-12, `pages/reservas/ResumenReserva.jsx`, con `RutaProtegida`):**
  resumen con `EtiquetaEstado`, fecha, horario, precio, monto y `CuentaRegresiva`. Al
  llegar a cero relee con `obtenerReserva(id)` y el servidor decide.
- **Pagar (HU-13, en la misma pantalla):** el único primario es "Pagar", solo si la API dice `pagoDisponible`. Va a `urlPago`
  con `window.location.assign`, en la misma pestaña, porque el token vive en `sessionStorage`. La pasarela vuelve a
  `/reservas/:id?pago=exito|fallo`: ese parámetro **solo elige el mensaje** y nunca confirma nada. Con `?pago=exito` y la
  reserva sin confirmar, relee cada 3 s, hasta 10 veces, sin "Se acabó el tiempo" ni "Pagar": quien pagó no debe pagar otra
  vez.
  HU-13 no muestra el estado del pago con `EtiquetaEstado`; HU-28 agregó al mapa `PAGADO` y `REEMBOLSADO` (los dos que ve el usuario).
- **`/reservas/:id/pago-simulado` (HU-13 D9, `pages/pagos/PagoSimulado.jsx`, con `RutaProtegida`):** con la pasarela simulada,
  "Pagar" lleva aquí en lugar de a un checkout. Hace de página de la pasarela: no tiene ningún campo, y sobre todo lo demás va
  siempre la `Alerta atencion` que dice que es una simulación, que no se cobra nada ni se usa ninguna tarjeta. Tiene
  **"Aprobar pago simulado"** como único primario y **"Rechazar pago simulado"** como secundario, y vuelve con `replace` a
  `/reservas/:id?pago=exito|fallo`. Sin pago disponible no ofrece botones. **Solo el backend la habilita:** la pantalla se
  decide con `GET …/pago/simulacion`, sin ninguna variable `VITE_`; con `mercadopago` la ruta existe en el build, pero la API
  responde 404 y no muestra título, datos ni botones. No agrega componentes, estados ni colores.

- **`/mis-reservas` y `/panel/reservas` (HU-14, `pages/reservas/MisReservas.jsx` y `ReservasDeMisEspacios.jsx`, con
  `RutaProtegida`):** "Mis reservas" (enlace desde `Panel` para `USUARIO`) y "Reservas de mis espacios" (enlace junto a
  "Mis espacios" para `ARRENDADOR`). Cada una tiene dos secciones, "Próximas" y "Pasadas", con
  `pages/reservas/SeccionReservas.jsx` (no es del kit): pide la página 1 al montarse y, mientras la API diga `hayMas`,
  muestra un `Boton secundario` "Ver más" que agrega la página siguiente sin repetir ids. El corte, el orden y el estado
  los decide el servidor. Cada reserva es una `Tarjeta` con `EtiquetaEstado`, fecha, horario y monto (y "Reservado por"
  para el arrendador). "Ver reserva" → `/reservas/:id` va en **todas** las del usuario, en cualquier estado (ahí están
  "Pagar" y el aviso de pago no aplicado de HU-13); "Ver espacio" va a `/espacios/:id` (usuario) o `/panel/espacios/:id`
  (arrendador). Lista vacía: texto secundario; error (p. ej. 403 por otro rol): `Alerta error` con el mensaje tal cual.
- **Reseñar una reserva (HU-24, en `/mis-reservas`):** `MisReservas` pide `listarResenasPendientes()` al montarse y con cada
  `recarga`, y guarda los ids en un `Set`; si la consulta falla, la lista funciona igual, sin el botón. En "Pasadas", solo las
  reservas de ese `Set` llevan `pages/reservas/ResenarReserva.jsx` (no es del kit): `Boton secundario` "Calificar" y `Modal`
  `primario` "Califica tu reserva", con `Campo select` "Calificación" (de 5 a 1) y `Campo textarea` "Comentario". "Publicar reseña"
  llama a `crearResena`; un `select` vacío viaja como `null` y responde el servidor. Con 201 se cierra el modal, `Alerta exito` "Tu
  reseña ya está publicada." y `recarga` sube. Los errores de `calificacion` y `comentario` quedan bajo su `Campo`; otro rechazo (400
  o 404) cierra el modal y va en `Alerta error` tal cual bajo el botón. No agrega componentes al kit, estados ni colores.
- **Reseñas en la ficha (HU-24, `/espacios/:id`):** debajo de la descripción, "4.3 de 5 · 12 reseñas" o "Aún sin reseñas.", con el
  promedio y el total de la API (texto, sin estrellas). Al final de la pantalla, después de "Reservar",
  `pages/busqueda/ResenasEspacio.jsx` (no es del kit, `key={espacio.id}`): `listarResenasDeEspacio` sin token, una `Tarjeta` por reseña
  con título "n de 5", comentario (`white-space: pre-line`) y fecha; "Ver más" (`Boton secundario`) mientras `hayMas`, sin repetir
  ids; vacía, "Este espacio aún no tiene reseñas."; error, `Alerta error` tal cual. Nunca `dangerouslySetInnerHTML`. `VISIBLE` no se
  muestra: la ficha solo recibe reseñas visibles. El único primario sigue siendo "Reservar".
- **Cancelar una reserva (HU-15, en las mismas dos pantallas):** solo en las `CONFIRMADA` de "Próximas", el `Boton peligro`
  "Cancelar reserva" de `pages/reservas/CancelarReserva.jsx` (no es del kit) pide la vista previa (`obtenerCancelacion` u
  `obtenerCancelacionDeMiEspacio`) y abre el `Modal` `peligro-confirmar` "¿Cancelar esta reserva?" con espacio, fecha,
  horario, monto pagado y la línea del reembolso elegida por la `politica` de la API (el monto, en negrita, es el de la API).
  "Sí, cancelar reserva" envía `montoReembolsoVisto` = el `montoReembolso` de la vista previa, sin tocarlo. Si la API dice
  que el monto cambió, `Alerta atencion` dentro del modal y nueva vista previa; otro error, `Alerta error` bajo la tarjeta.
  Tras cancelar, `Alerta exito` y un contador `recarga` como `key` de las dos secciones: la reserva pasa a Pasadas como
  "Cancelada" (usuario) o desaparece (arrendador). No agrega componentes al kit, estados ni colores.
- **`/panel/espacios/:id` — pausar, reanudar y dar de baja (HU-21, `pages/espacios/EditarEspacio.jsx`):** con el
  espacio `PAUSADO`, `Alerta info` "Este espacio está pausado…"; con `BAJA`, `Alerta info` "Este espacio está dado de
  baja…" y "Guardar cambios", "Subir foto" y "Quitar foto" `deshabilitado` (igual en `OCULTO`). Con `PUBLICADO` o
  `PAUSADO`, debajo de las fotos, el bloque "Estado de la publicación" (`pages/espacios/EstadoPublicacion.jsx`, no es del
  kit; solo botones `secundario` o `peligro`, porque el primario de la pantalla es "Guardar cambios"): "Pausar" (`Modal`
  `primario` → `pausarEspacio`), "Reanudar" sin modal (`reanudarEspacio`; un error va en `Alerta error` tal cual, con
  "Verificar mi identidad" si `sesion.aviso`) y `Boton peligro` "Dar de baja" (`Modal` `peligro-confirmar` →
  `darDeBajaEspacio`). Cada éxito actualiza el espacio y muestra `Alerta exito` arriba. Si la baja responde 400 con un
  error en el campo `reservas`, `Alerta atencion` con ese mensaje y "Reservas afectadas"
  (`pages/espacios/ReservasAfectadas.jsx`, no es del kit): `SeccionReservas` de "proximas" con
  `listarReservasDeMisEspacios({ grupo, pagina, espacioId })` y, en cada `CONFIRMADA`,
  `pages/reservas/TarjetaReservaDeMiEspacio.jsx` (extraída de `ReservasDeMisEspacios`) con `CancelarReserva` (HU-15,
  100 %); tras cada cancelación, `Alerta exito` y recarga. Luego el arrendador vuelve a pulsar "Dar de baja". Las rutas
  del admin no tienen pantalla. No agrega componentes al kit, estados ni colores.
- **`/admin/reembolsos` (HU-16, `pages/pagos/ReembolsosPorAtender.jsx`, con `RutaProtegida`):** "Reembolsos por atender"
  (enlace desde `Panel` para `ADMIN`); otro rol vuelve a `/panel` con `Navigate` (la API igual responde 403). Lista lo que
  devuelve `GET /api/admin/reembolsos` (`listarReembolsosPorAtender`): una `Tarjeta` por reembolso con "Reserva #N",
  `EtiquetaEstado`, monto en negrita (`formatoPrecio`), origen, intentos y fechas (`fechaHora`). Cada tarjeta tiene un
  `Boton secundario` "Reintentar" (hay varios: ninguno es primario) que abre el `Modal` `primario` "¿Reintentar el
  reembolso?"; al confirmar, `reintentarReembolso(id)` con `cargando`. 200 `REEMBOLSADO` → `Alerta exito` "Reembolso de
  S/ X realizado."; 200 `FALLIDO` → `Alerta error` "La pasarela volvió a fallar…"; 400/404 u otro error → `Alerta error`
  con el mensaje tal cual. En los tres casos se cierra el modal y se recarga la lista. No agrega componentes al kit ni
  colores; agrega los estados de la fila Reembolso.
- **`/admin/liquidaciones` y `/admin/liquidaciones/:id` (HU-19, `pages/pagos/Liquidaciones.jsx` y
  `DetalleLiquidacion.jsx`, con `RutaProtegida`):** "Liquidaciones" (enlace desde `Panel` para `ADMIN`); otro rol vuelve a
  `/panel` (la API igual responde 403). La lista tiene el único `Boton primario` "Generar liquidación" con `Modal` `primario`
  (`generarLiquidaciones`): con resultado `Alerta exito` "Se generaron N liquidaciones." ("Se generó 1 liquidación."), vacía
  `Alerta info` "No hay reservas por liquidar.", error `Alerta error` tal cual; luego recarga las dos secciones "Por pagar" y
  "Pagadas" (`pages/pagos/SeccionLiquidaciones.jsx`, no es del kit, como `SeccionReservas`; "Ver más" mientras `hayMas`). Cada
  liquidación es una `Tarjeta` con el nombre del arrendador, `EtiquetaEstado`, periodo, bruto, "Comisión (10 %)", neto, número
  de reservas, el aviso de simulada y los avisos del arrendador en texto, y "Ver detalle". El detalle muestra los avisos y la
  simulada en `Alerta atencion`, la "Cuenta de destino" (completa solo si la API la trae, con "Cópialo para transferir. Esta
  consulta queda registrada."; si no, enmascarada con su `EtiquetaEstado` y, si está retenida, la fecha; sin cuenta, `Alerta
  atencion`), las reservas con `pages/pagos/LineasLiquidacion.jsx` (no es del kit) y, solo si `pagable`, el `Boton primario`
  "Marcar como pagada" con `Modal` y un `Campo` "Número de operación" (`marcarLiquidacionPagada`, sin espacios extremos; el
  error de campo va bajo el `Campo`, otro error cierra el modal con `Alerta error` y recarga). No agrega componentes al kit
  ni colores.
- **`/panel/liquidaciones` (HU-19, `pages/pagos/MisLiquidaciones.jsx`, con `RutaProtegida`):** "Mis liquidaciones" (enlace
  desde `Panel` para `ARRENDADOR`, junto a "Cuenta bancaria"); otro rol vuelve a `/panel`. `listarMisLiquidaciones` de 20 en
  20 con "Ver más". Cada liquidación es una `Tarjeta` "Liquidación del <periodo>" con `EtiquetaEstado`, bruto, comisión,
  neto, sus reservas y: `PAGADA` → "Pagada el … · Operación N.º … · <banco> <cuenta enmascarada>"; `PENDIENTE_CUENTA` →
  `Alerta atencion` con enlace a `/panel/cuenta-bancaria`; `GENERADA` → "En proceso de pago.". Sin totales acumulados (el
  panel de ingresos está fuera del alcance).
- **`/mis-pagos` (HU-28, `pages/pagos/MisPagos.jsx`, con `RutaProtegida`):** "Mis pagos" (enlace desde `Panel` para `USUARIO`);
  otro rol vuelve a `/panel`. Reutiliza `SeccionLiquidaciones` con un `cargar` que renombra `transacciones` a `liquidaciones`.
  Cada pago es una `Tarjeta` "Pago #id" con `EtiquetaEstado` (`PAGADO` "Pagado" éxito, `REEMBOLSADO` "Reembolsado" neutro),
  fecha, espacio, monto, "Reembolsado: S/ X" si la API trae `montoReembolsado` y "Pago simulado" si `simulada`. `Boton
  secundario` "Descargar comprobante": `descargarComprobante(id)` (`peticionArchivo`, Blob con el token) y un `<a download>`
  temporal; el error se muestra en `Alerta error` bajo la tarjeta. Lista vacía: "Aún no tienes pagos.". Sin reglas en el
  cliente. No agrega componentes al kit ni colores.
- **Reclamos (HU-26, `specs/HU-26/design.md` § 12).** En `/mis-reservas`, las reservas de `listarReclamables()` llevan
  `pages/reservas/ReportarProblema.jsx` (no es del kit): `Boton secundario` "Reportar un problema" y `Modal primario` con
  `Campo select` "Motivo" y `Campo textarea` "Descripción" (`abrirReclamo`); con 201, `Alerta exito` con enlace "Ver
  reclamo". `/reclamos` (`pages/administracion/MisReclamos.jsx`, "Mis reclamos" del `USUARIO` o "Reclamos de mis
  reservas" del `ARRENDADOR`; un `ADMIN` vuelve a `/panel`) lista de 10 en 10 con "Ver más". `/reclamos/:id`
  (`DetalleReclamo.jsx`) muestra el reclamo, la resolución, el monto solo si la API lo trae, el hilo
  (`HiloReclamo.jsx`, no es del kit, "Tú" para el papel propio, nunca HTML) y, si `puedeEscribir`, el `Campo textarea`
  "Tu mensaje" con el único primario "Enviar mensaje". `/admin/reclamos` (`ReclamosAdmin.jsx`) reutiliza
  `SeccionLiquidaciones` para "Abiertos" y "Resueltos". `/admin/reclamos/:id` (`ResolverReclamo.jsx`) muestra nombres
  ("Sin nombre" si faltan), monto pagado y el hilo; con `ABIERTO`, el formulario "A favor de" / "Reembolso" / "Monto a
  reembolsar (S/)" / "Motivo de la resolución" y el primario "Resolver" con `Modal primario` que repite la decisión con
  el monto de la API o el escrito. El impedimento `reclamos` de HU-22 enlaza a `/reclamos`. No agrega componentes al kit
  ni colores; agrega los estados de la fila Reclamo.
- **Reportes y moderación (HU-27, `specs/HU-27/design.md` § 12).** En `/espacios/:id`, para `USUARIO` y `ARRENDADOR`
  (un visitante o un `ADMIN` no lo ven), `pages/busqueda/ReportarContenido.jsx` (no es del kit) bajo la política de
  cancelación ("Reportar publicación") y en `acciones` de cada reseña ("Reportar reseña"): `Boton secundario` y `Modal
  primario` con `Campo select` "Motivo" (`reportarContenido`); con 201, `Alerta exito` y el botón queda "Reportado"; el
  error de `motivo` se queda en el modal y cualquier otro rechazo va bajo el botón. `/admin/reportes` (`ReportesAdmin.jsx`)
  reutiliza `SeccionLiquidaciones` para "Por revisar" y "Resueltos". `/admin/reportes/:id` (`RevisarReporte.jsx`) muestra
  el reporte, el contenido en su estado actual (espacio con "Arrendador: <nombre>" o reseña) y, con `POR_REVISAR`, el
  formulario "Decisión" (la opción "Ocultar por fraude y cancelar sus reservas" solo para espacios) / "Motivo de la
  decisión" con el primario "Registrar decisión"; el `Modal` es `peligro-confirmar` al ocultar y `primario` al mantener.
  Enlace "Reportes" en el `Panel` del `ADMIN`. No agrega componentes al kit ni colores; agrega los estados de las filas
  Reseña y Reporte.

## Accesibilidad mínima

- Todo `Campo` tiene `<label>` asociada; nada de placeholders como etiqueta.
- El foco es visible: contorno `--color-primario` (`:focus-visible` en
  `index.css`). El halo `--color-primario-suave` puede acompañarlo, pero solo
  no basta, porque sobre blanco casi no se ve.
- El color nunca es la única señal: estados y errores siempre llevan texto.
- Contraste AA en todo par texto/fondo (garantizado si solo usas tokens).

## Sección "Pantallas" del `design.md`

Toda historia con interfaz la incluye (ver `docs/specs.md`). Por cada pantalla:

- Ruta y archivo (`pages/<modulo>/<Pantalla>.jsx`).
- Componentes del kit que usa y si agrega alguno nuevo.
- Funciones de `api/<modulo>.js` que llama y qué endpoint hay detrás.
- Cómo muestra cada CA visible (mensaje, estado, navegación), según la tabla de
  [Mensajes y estados de carga](#mensajes-y-estados-de-carga).

## Qué NO hacer

- No escribir colores, tamaños de letra ni espacios sueltos: usa los tokens.
- No usar estilos en línea (`style={{…}}`) para dar apariencia.
- No crear un botón, campo o alerta propio dentro de una pantalla.
- No instalar librerías de componentes ni de CSS.
- No calcular en el cliente nada que decida el servidor (montos, estados, roles).
- No guardar datos personales ni tokens en `localStorage`: el token de sesión va
  solo en `sessionStorage` (HU-03).
