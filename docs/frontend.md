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
│   └── ui/               # Kit: Boton, Campo, Alerta, EtiquetaEstado, Tarjeta, Modal, SelectorBloques
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
| Pago | `PAGADO` | — | `RECHAZADO`, `REEMBOLSO_FALLIDO` | `REEMBOLSADO` |
| Espacio | `PUBLICADO` | `PAUSADO` | `OCULTO` (moderación) | `BORRADOR`, `BAJA` |
| Identidad del arrendador | `VERIFICADA` | `EN_REVISION` | `RECHAZADA` | `NO_INICIADA` |
| Cuenta | `ACTIVA`, correo verificado | — | `SUSPENDIDA` | correo pendiente, `ELIMINADA` |
| Liquidación | `PAGADA` | `PENDIENTE_CUENTA`, `GENERADA` | — | — |
| Reseña | `VISIBLE` | — | `OCULTA` (moderación) | — |
| Reclamo | — | abierto | — | — |
| Cuenta bancaria | — | retenida 72 h | — | — |

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
- HU-11 lo usa **sin** `onCambiar` (solo lectura) o con `onCambiar` si la selección empieza en la ficha; HU-12 lo usa **con** `onCambiar` y `seleccionados`. Que los bloques elegidos sean consecutivos (HU-12) lo valida la pantalla y el servidor, no el componente.
- Fecha elegible: de hoy a hoy + 55 días en hora de Lima. El servidor responde 400 fuera de esa ventana; el `Campo tipo="date"` acepta `min`/`max` (HU-08) para limitarla en el cliente.

## Kit de componentes (`src/components/ui/`)

Las pantallas solo usan estos componentes para estos elementos. **Si falta uno,
se agrega al kit, no se improvisa en la pantalla**, y se anota en la sección
"Pantallas" del `design.md`.

| Componente | Para qué | Props mínimas |
|------------|----------|---------------|
| `Boton` | Toda acción | `variante` (`primario`, `secundario`, `peligro`, `peligro-confirmar`), `cargando`, `deshabilitado`, `onClick` |
| `Campo` | Toda entrada de formulario, con su etiqueta (`tipo="select"` para listas cerradas, HU-02; `tipo="archivo"` para subir un archivo, HU-05: no controlado, `onChange` recibe el archivo elegido o `null`; `tipo="textarea"` para textos largos, HU-07) | `etiqueta`, `nombre`, `tipo`, `valor`, `onChange`, `error`, `ayuda`, `opciones` (`[{ valor, etiqueta }]`, solo con `select`), `accept` (solo con `archivo`), `filas` (solo con `textarea`, por defecto 4), `min` y `max` (en la rama genérica, p. ej. `tipo="date"`; HU-08) |
| `Alerta` | Mensajes de la API y de éxito | `tipo` (`exito`, `atencion`, `error`, `info`), `children` |
| `EtiquetaEstado` | Estado de una entidad (tabla anterior). Construido por HU-05 con los estados de identidad; cada historia agrega al mapa los de su entidad (HU-07: los de `Espacio`; HU-09: los de `Cuenta`, `ACTIVA`, `SUSPENDIDA` y `ELIMINADA`) | `estado` |
| `Tarjeta` | Agrupar contenido: espacio en resultados, reserva en "Mis reservas". Construido por HU-07 ("Mis espacios") | `titulo`, `children`, `acciones` (nodo opcional al pie) |
| `Modal` | Confirmar acciones irreversibles o importantes (cancelar, suspender, dar de baja, aprobar o rechazar una identidad). Construido por HU-06: `<dialog>` con `showModal()` (foco atrapado; `Esc` llama a `onCerrar`, salvo mientras `cargando`), pie con `Boton secundario` "Cancelar" y el botón de confirmación; fondo oscurecido con `color-mix` sobre `--color-texto` | `abierto`, `titulo`, `onConfirmar`, `onCerrar`, `children`, `textoConfirmar` (por defecto "Confirmar"), `variante` (`primario` o `peligro-confirmar`), `cargando` |
| `SelectorBloques` | Elegir bloques horarios (HU-11, HU-12) o mostrarlos. Construido por HU-08: cada bloque muestra `HH:00–HH:00`; el ocupado va tachado, sin clic y con su `detalle` como texto. Sin `onCambiar` es **solo lectura** (los bloques no son botones); con `onCambiar`, cada bloque libre es un `<button aria-pressed>` y al pulsarlo llama `onCambiar` con el arreglo nuevo de horas. Lista vacía: no renderiza nada | `bloques` (`[{ horaInicio, horaFin, libre, detalle }]`), `seleccionados` (horas de inicio, por defecto `[]`), `onCambiar` (opcional) |

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
- **Rutas con sesión:** se envuelven en `<RutaProtegida>`; el estado
  `{ rol, aviso }` se lee con `useSesion()`. El rol solo elige qué mostrar.

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
