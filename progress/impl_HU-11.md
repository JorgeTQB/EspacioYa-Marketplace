# Implementación — HU-11 · Detalle del espacio

- **Dueña:** Adriana Callupe · **Rama:** `feature/HU-11-detalle-espacio` · **Fecha:** 2026-10-30 · **Agente:** implementer
- **Spec:** `specs/HU-11/` (aprobado el 2026-10-30 con D1–D8). Tasks T1–T13 marcadas `[x]`.
- **Estado:** `in_progress`, pendiente del reviewer. Sin commit, push ni PR.

## Archivos

Prefijos: `B/` = `2_Backend/src/main/java/pe/edu/ulima/espacioya/`, `T/` = `2_Backend/src/test/java/pe/edu/ulima/espacioya/`,
`F/` = `3_frontend/src/`.

### Backend
| Archivo | | Task |
|---------|---|------|
| `B/espacios/service/FichaPublicada.java` | N | T1 |
| `B/espacios/service/CatalogoEspaciosService.java` | M (`publicado`, `esVisible`) | T1 |
| `B/espacios/service/CatalogoEspaciosServiceImpl.java` | M (implementación + `visible` privado) | T1 |
| `B/comun/seguridad/ConfiguracionSeguridad.java` | M (dos `permitAll`) | T2 |
| `B/busqueda/dto/FichaPublicaDTO.java`, `BloqueLibreDTO.java`, `DisponibilidadPublicaDTO.java` | N | T3 |
| `B/busqueda/service/FichaService.java` | N | T4 |
| `B/busqueda/controller/ReglasLimiteFicha.java`, `FichaController.java` | N | T5 |
| `T/espacios/service/CatalogoEspaciosServiceImplTest.java` | M (10 casos nuevos; los 6 de HU-10 intactos) | T1 |
| `T/busqueda/service/FichaServiceTest.java` | N (38 casos) | T4 |
| `T/busqueda/controller/ReglasLimiteFichaTest.java` | N (3) | T5 |
| `T/busqueda/controller/FichaControllerIT.java` | N (23, IP 10.12.x.x) | T6 |
| `T/busqueda/controller/LimiteFichaIT.java` | N (6, IP 10.13.x.x; reutiliza `LimiteCatalogoIT.Dobles` para compartir contexto) | T7 |

Sin cambios en `DisponibilidadService*`, `EspacioService`, repositorios, código de HU-10 (`BusquedaService`,
`ReglasLimiteBusqueda`, …), `ManejadorGlobalErrores` ni `application.properties`. Sin migración ni auditoría.

### Frontend
| Archivo | | Task |
|---------|---|------|
| `F/components/ui/Galeria.jsx` + `Galeria.module.css` | N (kit) | T8 |
| `F/api/busqueda.js` | M (`obtenerEspacio`, `consultarBloquesLibres`, `autenticada: false`) | T9 |
| `F/pages/busqueda/textosFicha.js` | N | T9 |
| `F/pages/busqueda/FichaEspacio.jsx` + `FichaEspacio.module.css` | N | T10 |
| `F/App.jsx` | M (ruta pública `/espacios/:id`) | T11 |
| `F/pages/busqueda/BuscarEspacios.jsx` | M (solo `Link` "Ver espacio" en `acciones` de cada `Tarjeta`) | T11 |

`SelectorBloques` sin cambios (solo lectura, sin `onCambiar`).

### Docs
- `docs/architecture.md`: `CatalogoEspaciosService.publicado/esVisible`; rate limiting con las dos rutas de la ficha;
  § Disponibilidad, HU-11 "hecho". Ningún puerto nuevo.
- `docs/frontend.md`: `Galeria` en la lista de carpetas y en la tabla del kit; `SelectorBloques` en la ficha de HU-11 y
  contrato para HU-12 (design § 13). Ningún estado nuevo.

## Trazabilidad

| R | Tests (`@DisplayName("HU-11 R<n>: …")`) |
|---|------------------------------------------|
| R1 | `FichaControllerIT.fichaDeUnEspacioVisible`, `FichaServiceTest.fichaDeUnEspacioVisible`, `FichaServiceTest.aceptaElIdMaximo` |
| R2 | `FichaControllerIT.fichaConExactamenteSusCampos` |
| R3 | `FichaControllerIT.fichaDeUnEspacioVisible` |
| R4 | `FichaControllerIT.fichaConNombreDeZona` |
| R5 | `FichaControllerIT.fotosEnOrden` |
| R6 | `FichaControllerIT.sinFotos` |
| R7 | `FichaControllerIT.minimizacion` |
| R8 | `FichaControllerIT.bearerInvalidoComoAnonimo` |
| R9 | `FichaControllerIT.noPublicadosResponden404`, `FichaServiceTest.fichaNoVisible` |
| R10 | `FichaControllerIT.arrendadorNoActivoResponde404` |
| R11 | `FichaControllerIT.idMalEscritoResponde404`, `FichaServiceTest.fichaConIdMalEscrito` |
| R12 | `FichaControllerIT.cuerposIguales` |
| R13 | `FichaControllerIT.bloquesLibresDelDia`, `FichaServiceTest.bloquesLibresDelDia`, `FichaServiceTest.bordesDeLaVentana` |
| R14 | `FichaControllerIT.bloquesLibresDelDia`, `FichaServiceTest.bloquesLibresDelDia` |
| R15 | `FichaControllerIT.fechaBloqueada`, `FichaServiceTest.sinHorasLibres` |
| R16 | `FichaControllerIT.excluyeReservadas` |
| R17 | `FichaControllerIT.hoyExcluyeLasHorasEmpezadas` |
| R18 | `FichaControllerIT.diaSinHorario`, `FichaServiceTest.sinHorasLibres` |
| R19 | `FichaControllerIT.disponibilidadMinima` |
| R20 | `FichaControllerIT.disponibilidadNoVisibleResponde404`, `FichaServiceTest.disponibilidadDeEspacioNoVisible`, `FichaServiceTest.disponibilidadConIdMalEscrito` |
| R21 | `FichaControllerIT.fechaInvalida`, `FichaServiceTest.fechaInvalida` |
| R22 | `FichaControllerIT.fechaFueraDeVentana`, `FichaServiceTest.fechaFueraDeVentana` |
| R23 | `FichaControllerIT.noVisibleYFechaInvalidaResponde404`, `FichaServiceTest.noVisibleYFechaInvalida` |
| R24 | `FichaControllerIT.sinCache` |
| R25 | `FichaControllerIT.sinAuditoria` |
| R26 | `LimiteFichaIT.fichaConCupoAgotado` |
| R27 | `LimiteFichaIT.disponibilidadConCupoAgotado` |
| R28 | `LimiteFichaIT.cupoCompartidoConLaBusqueda`, `LimiteFichaIT.fichasAgotanLaBusqueda`, `ReglasLimiteFichaTest.compartenElCupoConLaBusqueda` |
| R29 | `LimiteFichaIT.noEncontradasCuentan`, `LimiteFichaIT.invalidasCuentan` |
| R30 | `ReglasLimiteFichaTest.reglaDeLaFicha`, `ReglasLimiteFichaTest.reglaDeLaDisponibilidad` |
| R31 | `CatalogoEspaciosServiceImplTest.publicadoDevuelveLaFicha`, `CatalogoEspaciosServiceImplTest.publicadoSinFotos` |
| R32 | `CatalogoEspaciosServiceImplTest.idNulo`, `.inexistente`, `.noPublicado` (BORRADOR, PAUSADO, BAJA, OCULTO), `.arrendadorNoActivo` |
| R33 | `CatalogoEspaciosServiceImplTest.idNulo`, `.inexistente`, `.noPublicado`, `.arrendadorNoActivo`, `.esVisibleSiPublicadoYActivo` |

Pantalla (CA1 política, CA2 presentación, D1–D8 visibles): lint + build en verde; se verifica en UAT (caja negra).

## Verificación — `./init.sh` completo (2026-10-30)

- Entorno, docs, `feature_list.json`/specs/progress coherentes, reglas de arquitectura (sin colores fuera de tokens,
  sin librerías, sin secretos): `[OK]`.
- Backend `./mvnw verify`: **523 unitarias** (472 previas + 51 de HU-11), 0 fallos; **351 IT**, 0 fallos.
- Frontend: ESLint sin errores ni advertencias; `npm run build` compila.
- Resultado: `[OK] Entorno listo.`

## Tasks no completadas

Ninguna.

## Observaciones para el reviewer

- `FichaEspacio.jsx` usa `h1` para el nombre del espacio, como pide design § 12; el encabezado de `App.jsx` también
  usa `h1` para la marca (las demás pantallas usan `h2`). No se cambió para no desviarse del spec.
- `@ParameterizedTest` (junit-jupiter-params, incluido en `spring-boot-starter-test`) se usa por primera vez en el
  repo, en `FichaServiceTest` y `CatalogoEspaciosServiceImplTest`; no se agregó ninguna dependencia.
- `LimiteFichaIT` importa `LimiteCatalogoIT.Dobles` (mismo reloj y propiedades) para compartir el contexto de Spring,
  como indica DT-15; solo cambia el rango de IP.

## Vuelta 1

Cambios requeridos por `progress/review_HU-11.md` (vuelta 1 de 2), aplicados sin tocar las observaciones no
bloqueantes:

1. `docs/historias/HU-11.md:13`: revertida la línea `aaprobado` con `git restore`; `git diff HEAD docs/historias/HU-11.md`
   queda vacío.
2. `2_Backend/src/test/java/pe/edu/ulima/espacioya/busqueda/controller/FichaControllerIT.java`: `@Primary` en el
   `@Bean reservasFalsas()` de `Dobles`, igual que `EspaciosControllerSoporte.java:108`, para que la IT no rompa con
   `NoUniqueBeanDefinitionException` cuando HU-12 registre su `ReservasDelEspacio` real.

Verificación:
- `FichaControllerIT` solo: 23 tests, 0 fallos, 0 errores.
- `./init.sh` completo: exit 0; 523 unitarias y 351 IT sin fallos; ESLint limpio; `npm run build` compila;
  `[OK] Entorno listo.`

El mapa de trazabilidad R1–R33 no cambia.
