# Corrección acotada del spec HU-27 (hallazgo 1 de review_HU-27.md)

Archivo: `specs/HU-27/design.md`, § 8 "Migración `V8_2__reportes.sql`", restricción `ck_reporte_decision`.

Antes (líneas 623-626):

```sql
    CONSTRAINT ck_reporte_decision CHECK (
        (estado = 'POR_REVISAR' AND admin_id IS NULL AND motivo_decision IS NULL AND resuelto_en IS NULL)
        OR (estado IN ('MANTENIDO', 'OCULTADO') AND admin_id IS NOT NULL
            AND char_length(btrim(motivo_decision)) >= 1 AND resuelto_en >= creado_en)),
```

Después (líneas 623-628, idéntico a `V8_2__reportes.sql` líneas 25-30):

```sql
    -- Los IS NOT NULL explícitos evitan que un NULL vuelva la expresión NULL y el CHECK la deje pasar (R57).
    CONSTRAINT ck_reporte_decision CHECK (
        (estado = 'POR_REVISAR' AND admin_id IS NULL AND motivo_decision IS NULL AND resuelto_en IS NULL)
        OR (estado IN ('MANTENIDO', 'OCULTADO') AND admin_id IS NOT NULL
            AND motivo_decision IS NOT NULL AND char_length(btrim(motivo_decision)) >= 1
            AND resuelto_en IS NOT NULL AND resuelto_en >= creado_en)),
```

- No hay prosa en design.md que describa esta restricción aparte del bloque; el comentario SQL añadido (copiado de la
  migración) es la frase que menciona los IS NOT NULL explícitos.
- Sin cambios en requirements.md, tasks.md, ningún R, la migración, el código ni el status de feature_list.json.

## Alineación con `ConversionReportes.idReporte` (ver `progress/ajustes_HU-27.md`)

Archivo: `specs/HU-27/design.md`.

- DT-14 (línea 37): "Ids de ruta con `ConversionReclamos.idReclamo`" pasa a "Ids de ruta con
  `ConversionReportes.idReporte` (delega en `ConversionReclamos.idReclamo` de HU-26; ...)".
- Tabla de archivos de `administracion` (tras la fila de `ReporteAdminController.java`, línea 77): nueva fila
  `J/administracion/controller/ConversionReportes.java` | N | `idReporte(String)` package-private; delega en
  `ConversionReclamos.idReclamo` de HU-26 (DT-14).
- Tabla de dependencias reutilizadas (línea ~685): `ConversionReportes.idReporte` (delega en
  `ConversionReclamos.idReclamo`).
- Sin cambios en requirements.md, tasks.md, ningún R, el código ni el status de feature_list.json.
