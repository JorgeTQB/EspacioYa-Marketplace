# Rediseño de la página de inicio (`/`) — sdd: false

Cambio visual pedido por el usuario a partir de una imagen de referencia (1536×1024). No es una historia: sin spec ni
cambios en `feature_list.json` ni en `2_Backend/`. Nada commiteado.

## Archivos

**Creados**
- `3_frontend/src/pages/inicio/Inicio.jsx`: la página (hero + buscador, tarjetas por tipo, banda de ventajas).
- `3_frontend/src/pages/inicio/Inicio.module.css`
- `3_frontend/src/pages/inicio/textosInicio.js`: textos, tarjetas de tipo y ventajas (convención `textos*.js`).
- `3_frontend/src/pages/inicio/iconos.jsx`: iconos SVG inline de trazo (`aria-hidden`), sin dependencias nuevas.
- `3_frontend/src/assets/inicio/{hero,canchas,estadios,auditorios,decorativa}.webp` (≈ 81 KB en total).

**Modificados**
- `3_frontend/src/App.jsx`: "/" ahora es `<Inicio />`; encabezado nuevo (ver abajo); `main` y la alerta de cierre van
  dentro de `.contenido` salvo en "/".
- `3_frontend/src/App.module.css`: estilos del encabezado y clase `.contenido` (max 1120 px + 16 px de relleno).
- `3_frontend/src/index.css`: `#root` ya no limita el ancho ni pone relleno (lo hace `.contenido`), para que el
  encabezado y el hero ocupen todo el ancho. El resto de pantallas conserva su ancho de 1120 px; la única diferencia es
  16 px más de aire entre el encabezado y el contenido.
- `3_frontend/src/styles/tokens.css`: tokens nuevos `--color-marca` (#1565E0), `--color-marca-hover` (#0F4FB8),
  `--color-marino` (#0B2A4A), `--color-marca-claro` (#5AAEF7), `--color-celeste` (#EEF5FD), `--color-celeste-banda`
  (#E5F3FE), `--texto-3xl` (52 px, solo el título del hero) y `--ancho-amplio` (1380 px). Contrastes comprobados:
  blanco/marca 5.3:1, marca-claro/marino 6.1:1. No se tocó `--color-primario`: el resto de la app sigue igual.
- `docs/frontend.md`: tabla de los tokens nuevos (la guía exige documentar todo color nuevo).
- `progress/history.md`: una entrada.

## Decisiones

- **Encabezado:** logo (pin SVG + "Espacio" marino + "Ya" azul) → "/"; "Explorar espacios" → `/buscar`; lupa →
  `/buscar` (`aria-label` "Buscar espacios"). Sin sesión: "Iniciar sesión" y botón pastilla "Regístrate" → `/registro`.
  Con sesión: "Mi panel" y `Boton secundario` "Cerrar sesión" con la misma lógica y el mismo manejo de errores (401 =
  cerrar igual; otro error = `Alerta`). Se mantiene el `<h1>` del logo, del que dependen las demás pantallas (usan `h2`).
- **"Cómo funciona"** → `/#como-funciona`, la banda "Alquila fácil, rápido y seguro". Como React Router no desplaza al
  ancla, `Inicio` hace `scrollIntoView()` al cambiar `hash`/`key` (funciona desde otra página y al repetir el clic). Se
  quitó `behavior: 'smooth'` porque en la prueba no desplazaba.
- **"Nosotros" se omitió:** no hay página ni sección "sobre nosotros", y apuntarlo a la misma banda duplicaba "Cómo
  funciona".
- **Tipos reales:** `TIPOS_ESPACIO` tiene `CANCHA`, `ESTADIO` y `AUDITORIO`. Las tarjetas son "Canchas deportivas",
  "Estadios" (no "Estadios y polideportivos": no existe tipo polideportivo) y "Auditorios", y llevan a
  `/buscar?tipo=<VALOR>`. "Ver todos los espacios" → `/buscar`.
- **Buscador del hero:** usa `Campo` y `Boton` del kit (sin campos propios), solo reestilizados desde
  `Inicio.module.css`. "¿Dónde?" es un `select` de zonas (`listarZonas`, código de zona), no texto libre, porque
  `/buscar` filtra por código de zona. "¿Cuándo?" es `date` con `min`/`max` de `ventanaBusqueda()` y por defecto hoy.
  "Tipo de espacio" usa `TIPOS_ESPACIO`. Al enviar navega a `/buscar` solo con los parámetros con valor: con los tres,
  `/buscar` busca de inmediato; con menos, solo rellena su formulario. Si `listarZonas` falla, la lista queda vacía en
  silencio y `/buscar` mostrará el error.
- **CTA de arrendador:** "¿Tienes un espacio? Publícalo →" en la banda, hacia `/registro-arrendador`. "¿Ya tienes
  cuenta? Inicia sesión" y "Crea tu cuenta" del inicio anterior quedan cubiertos por el encabezado.
- **Imágenes:** `assets/hero.png` es el gráfico por defecto de Vite (13 KB), no sirve. Se recortaron con Pillow las
  fotos de la propia imagen de referencia (script temporal en el scratchpad, nada descargado): estadio del hero (solo
  la parte derecha, sin texto encima, 636×387), las tres fotos de las tarjetas (≈ 440×118, sin el icono superpuesto) y
  la zapatilla decorativa (214×174, sin el texto de "Soporte 24/7").

## Responsive (comprobado en navegador, Vite dev)

- 1440 px y 1024 px: sin scroll horizontal; la banda pasa a 2 columnas de ventajas y oculta la imagen decorativa por
  debajo de 1280 px.
- 375 px: encabezado en una línea (se ocultan "Explorar espacios" y "Cómo funciona"; queda la lupa), buscador apilado
  con el botón a todo el ancho, tarjetas y ventajas en 1 columna, `scrollWidth` = 375. Con sesión ("Mi panel" + "Cerrar
  sesión") las acciones pueden bajar a una segunda línea, sin desbordar.
- Probado: el buscador navega a `/buscar?fecha=…&tipo=ESTADIO` y `/buscar` rellena el tipo; la tarjeta Auditorios va a
  `/buscar?tipo=AUDITORIO`; "Cómo funciona" desplaza a la banda; `/iniciar-sesion` conserva su ancho centrado.

## Verificación

- `npm --prefix 3_frontend run lint`: OK, sin avisos.
- `npm --prefix 3_frontend run build`: OK.
- Regla de colores de `init.sh` (hex/rgb fuera de `tokens.css`): sin coincidencias en `src/` (solo los `.svg` de Vite,
  que la regla no revisa). No se ejecutó `./init.sh` completo.
- Backend apagado durante la prueba: no se vio el desplegable de zonas con datos.

## Diferencias con la referencia

- La foto del hero es un recorte de 636 px de ancho estirado hasta ~980 px: se ve algo blanda en pantallas grandes.
  Las fotos de las tarjetas también tienen poca resolución (recortes de ~118 px de alto mostrados a 3:1).
- Tipografía del sistema (`--fuente`) en lugar de la redondeada de la imagen; el peso máximo es `--peso-negrita` (600).
- "¿Dónde?" es una lista de zonas, no un campo de texto "Ciudad, distrito o zona"; los `select` dicen "Elige una
  opción" (texto fijo de `Campo`) en lugar de "Todos los espacios", y la fecha muestra hoy, no "Selecciona fechas".
- "Estadios" en lugar de "Estadios y polideportivos"; sin el enlace "Nosotros".
- Los campos del buscador no llevan borde propio (como en la imagen): el contorno de foco global sigue visible, pero
  se aparta de la regla de `docs/frontend.md` sobre el borde de controles (3:1).
- La banda agrega el enlace "¿Tienes un espacio? Publícalo", que la imagen no tiene.
- Los textos de ventajas ("Pagos seguros", "Espacios verificados", "Soporte 24/7") se copiaron de la imagen: son
  promesas comerciales que el dueño del producto debería confirmar (en particular el soporte 24/7).
- Algunos espaciados del inicio usan px fijos (56/40/48 px) fuera de la escala `--espacio-*`.
