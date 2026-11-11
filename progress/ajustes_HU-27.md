# Ajustes menores — HU-27

> Implementer, 2026-11-10. Rama `feature/HU-27-reportes-moderacion`. Corrige las dos "Observaciones no bloqueantes"
> de `progress/review_HU-27.md`. Sin cambios de `status`, sin commit/push/PR.

## Archivos tocados

| Archivo | Cambio |
|---------|--------|
| `2_Backend/src/main/java/pe/edu/ulima/espacioya/administracion/controller/ConversionReportes.java` | **Nuevo.** `idReporte(String)` → `Long` o 404 genérico (R27). Delega en `ConversionReclamos.idReclamo`. |
| `2_Backend/src/main/java/pe/edu/ulima/espacioya/administracion/controller/ReporteAdminController.java` | l. 50 y 59: `ConversionReclamos.idReclamo(id)` → `ConversionReportes.idReporte(id)`. |
| `2_Backend/src/test/java/pe/edu/ulima/espacioya/administracion/controller/ConversionReportesTest.java` | **Nuevo.** Unitaria `HU-27 R27`: id bien escrito (incl. `Long.MAX_VALUE`) y mal escrito (null, vacío, 0, -1, letras, decimal, espacio, desborde) → `RecursoNoEncontradoException`. |
| `3_frontend/src/pages/administracion/textosAdministracion.js` | Sección HU-27: funciones `direccionDelEspacio`, `capacidadEnPersonas`, `motivoDe`, `reportadoEl`, `resueltoEl` (mismo estilo que `arrendadorDelEspacio`, `reportesPendientes`). |
| `3_frontend/src/pages/administracion/RevisarReporte.jsx` | Usa esas funciones en lugar de "Dirección:", "Capacidad: … personas", "Motivo:" (x2), "Reportado el", "Resuelto el". Se quita el import de `fechaHora` (ya no se usa aquí). |
| `3_frontend/src/pages/administracion/ReportesAdmin.jsx` | Igual para "Motivo:", "Reportado el", "Resuelto el". Se quita el import de `fechaHora`. |

`ConversionReclamos.java`, `ReclamoController.java` y `ReclamoAdminController.java` (HU-26) **no se tocaron**.

## Decisión del punto 1

`ConversionReportes.idReporte` es una clase propia de HU-27, package-private en `administracion.controller` (igual que
`ConversionReclamos`), que **delega** en `ConversionReclamos.idReclamo`. Motivos:

- El comportamiento ante ids inválidos es idéntico por construcción (misma excepción, mismo 404 genérico), así que
  `ReporteAdminControllerIT` sigue pasando sin cambiar aserciones.
- No se duplica la regla (1-19 dígitos, sin desborde, > 0) ni se cambia o renombra nada de HU-26.
- Extraer un helper neutral habría obligado a tocar `ConversionReclamos` y sus usos en los controladores de reclamos
  (otro dueño), así que se descartó.

## Texto visible del frontend

Idéntico. Las funciones devuelven la misma cadena que antes formaba el JSX (`Dirección: <dir>`, `Capacidad: <n>
personas`, `Motivo: <texto>`, `Reportado el <fechaHora>`, `Resuelto el <fechaHora>`); ante un valor `null` usan `''`,
como hacía React al renderizar `{null}`. Fuera de la lista del reviewer quedan dos literales en `RevisarReporte.jsx`
(`por hora` l. 65 y `Espacio:` l. 83): no se tocaron para no ampliar el alcance.

## Verificación

- `docker info`: OK (servidor 28.5.1).
- `./init.sh` completo: **exit 0**. Sin `[FAIL]` ni `[WARN]`. Arnés, `feature_list.json` (28 historias: 27 done,
  1 in_progress), specs/progress coherentes y 10 reglas de arquitectura en `[OK]`.
- Backend (`./mvnw verify`): unitarias **`Tests run: 906, Failures: 0, Errors: 0, Skipped: 0`** (ahora incluye las
  10 de `ConversionReportesTest`); integración **`Tests run: 1231, Failures: 0, Errors: 0, Skipped: 0`**
  (`ReporteAdminControllerIT`: 21 pruebas, 0 fallos, sin cambiar aserciones).
- Frontend: ESLint sin errores ni advertencias; `npm run build` OK.

## Alineación de `specs/HU-27/design.md` (no editado)

Sí conviene alinearlo (lo hace el spec-author/dueño):

- `specs/HU-27/design.md:37` (DT-14): "Ids de ruta con `ConversionReclamos.idReclamo`" → "Ids de ruta con
  `ConversionReportes.idReporte` (delega en `ConversionReclamos.idReclamo` de HU-26)".
- `specs/HU-27/design.md:684` (tabla de dependencias): `ConversionReclamos.idReclamo` → `ConversionReportes.idReporte`
  (que delega en `ConversionReclamos.idReclamo`).
- Opcional: añadir una fila en la tabla de archivos (cerca de `design.md:76`) para
  `J/administracion/controller/ConversionReportes.java` (N).
