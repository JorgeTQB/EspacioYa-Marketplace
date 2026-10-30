# Implementación HU-10 · Búsqueda por tipo, zona y fecha

- **Dueño:** Sebastian Garate · **Rama:** `feature/HU-10-busqueda-espacios` · **Fecha:** 2026-10-29 · **Agente:** implementer
- **Spec:** `specs/HU-10/` (aprobado por el dueño el 2026-10-29; D1–D12 tal cual). Tasks T1–T20 marcadas `[x]`.
- **Verificación:** `./init.sh` completo (unitarias, IT con Docker, lint y build del frontend, colores y secretos): ver
  "Resultado de init.sh" al final.

## Trazabilidad

Clases de prueba (`2_Backend/src/test/java/pe/edu/ulima/espacioya/`): `busqueda/controller/BusquedaControllerIT` (BIT),
`busqueda/controller/LimiteCatalogoIT` (LIT), `busqueda/controller/LimiteCatalogoTest` (LT),
`busqueda/service/BusquedaServiceTest` (BST), `busqueda/service/BusquedaBaseTest` (BBT),
`comun/limite/ReglaLimiteTest` (RLT), `comun/limite/FiltroLimiteIntentosTest` (FLT),
`espacios/service/DisponibilidadServiceImplTest` (DT), `espacios/service/DisponibilidadServiceImplIT` (DIT),
`espacios/service/CatalogoEspaciosServiceImplTest` (CT), `espacios/controller/ZonaControllerIT` (ZIT).

- R1 → `BusquedaControllerIT.buscaPorTipoYZona`
- R2 → `BusquedaControllerIT.buscaPorTipoYZona`, `BusquedaBaseTest.filtraConservandoElOrden`, `CatalogoEspaciosServiceImplTest.publicadosConTipoInvalido`
- R3 → `BusquedaControllerIT.excluyeNoPublicados`
- R4 → `BusquedaControllerIT.excluyeArrendadoresNoActivos`, `CatalogoEspaciosServiceImplTest.sinExcluidosUsaLaConsultaSimple`, `CatalogoEspaciosServiceImplTest.conExcluidosUsaLaConsultaConExclusion`
- R5 → `BusquedaControllerIT.excluyeSinHorarioEseDia`, `DisponibilidadServiceImplTest.excluyeSinHorarioYBloqueado`
- R6 → `BusquedaControllerIT.excluyeFechaBloqueada`, `DisponibilidadServiceImplTest.excluyeSinHorarioYBloqueado`
- R7 → `BusquedaControllerIT.excluyeTodoReservado`, `DisponibilidadServiceImplTest.excluyeTodoReservado`
- R8 → `BusquedaControllerIT.hoyExcluyeHorasEmpezadas`, `DisponibilidadServiceImplTest.hoyExcluyeHorasEmpezadas`
- R9 → `BusquedaControllerIT.paginaTodoElCatalogo`
- R10 → `BusquedaControllerIT.paginaTodoElCatalogo`, `BusquedaControllerIT.empatePorId`, `BusquedaServiceTest.ordenYFotosDeLaPagina`, `BusquedaBaseTest.filtraConservandoElOrden`
- R11 → `BusquedaControllerIT.sinResultados`, `BusquedaServiceTest.sinDisponibles`, `BusquedaBaseTest.sinCandidatosNoCalculaDisponibilidad`
- R12 → `BusquedaControllerIT.tokenInvalidoComoAnonimo`
- R13 → `ZonaControllerIT.sinSesion`, `ZonaControllerIT.tokenInvalido`
- R14 → `BusquedaControllerIT.buscaPorTipoYZona` (`containsOnlyKeys`), `BusquedaServiceTest.ordenYFotosDeLaPagina`
- R15 → `BusquedaControllerIT.buscaPorTipoYZona`, `CatalogoEspaciosServiceImplTest.fotosPrincipales`
- R16 → `BusquedaControllerIT.sinFotoPrincipal`, `BusquedaServiceTest.ordenYFotosDeLaPagina`, `CatalogoEspaciosServiceImplTest.fotosPrincipales`
- R17 → `BusquedaControllerIT.paginaTodoElCatalogo`, `BusquedaServiceTest.fotosSoloDeLaPaginaPedida`
- R18 → `BusquedaServiceTest.paginasDeVeinte`, `BusquedaControllerIT.paginaTodoElCatalogo`
- R19 → `BusquedaServiceTest.sinPaginaEsLaPrimera`, `BusquedaControllerIT.paginaPorDefectoYPosterior`
- R20 → `BusquedaServiceTest.paginasDeVeinte`, `BusquedaServiceTest.hayMasEnElBorde`, `BusquedaControllerIT.paginaTodoElCatalogo`
- R21 → `BusquedaServiceTest.paginaPosteriorALaUltima`, `BusquedaControllerIT.paginaPorDefectoYPosterior`
- R22 → `BusquedaControllerIT.ignoraParametrosDeTamano`
- R23 → `BusquedaServiceTest.tipoInvalido`, `BusquedaControllerIT.tipoInvalido`, `CatalogoEspaciosServiceImplTest.tiposValidos`
- R24 → `BusquedaServiceTest.zonaInvalida`, `BusquedaControllerIT.zonaInvalida`, `CatalogoEspaciosServiceImplTest.zonasValidas`
- R25 → `BusquedaServiceTest.fechaMalEscrita`, `BusquedaServiceTest.textosDeFechaComoHu08`, `BusquedaControllerIT.fechaInvalida`
- R26 → `BusquedaServiceTest.fechaFueraDeVentana`, `BusquedaServiceTest.bordesDeLaVentana`, `BusquedaServiceTest.textosDeFechaComoHu08`, `BusquedaControllerIT.fechaFueraDeVentana`
- R27 → `BusquedaServiceTest.paginaInvalida`, `BusquedaControllerIT.paginaInvalida`
- R28 → `BusquedaServiceTest.variosErroresEnOrden`, `BusquedaControllerIT.variosErrores`
- R29 → `BusquedaServiceTest.invalidaNoBusca` (más `verifyNoInteractions(busqueda)` en los tests de R23–R27)
- R30 → `DisponibilidadServiceImplIT.conBloquesLibresEquivaleAHorasLibres`, `DisponibilidadServiceImplTest.excluyeNoVisibles`
- R31 → `DisponibilidadServiceImplTest.loteConsultaUnaVez`
- R32 → `DisponibilidadServiceImplTest.loteVacioOFueraDeVentana`
- R33 → `LimiteCatalogoIT.cuartaResponde429`, `FiltroLimiteIntentosTest.cupoAgotadoResponde429`
- R34 → `LimiteCatalogoIT.vuelveAAceptarTrasLaVentana`
- R35 → `LimiteCatalogoIT.otraIpSigue`
- R36 → `LimiteCatalogoIT.rechazadasNoCuentan`, `FiltroLimiteIntentosTest.rechazadasNoCuentan`
- R37 → `LimiteCatalogoIT.invalidasCuentan`
- R38 → `LimiteCatalogoIT.zonasYFotosSinLimite`, `LimiteCatalogoIT.zonasYFotosNoConsumen`
- R39 → `BusquedaControllerIT.cupoPorDefecto`
- R40 → `LimiteCatalogoTest.maximoInvalido`, `LimiteCatalogoTest.ventanaInvalida`
- R41 → `LimiteCatalogoTest.reglasCompartenGrupo`, `LimiteCatalogoTest.reglaDeLaBusqueda`, `BusquedaControllerIT.cupoPorDefecto`
- R42 → `ReglaLimiteTest.patronConVariable`, `ReglaLimiteTest.patronConComodin`
- R43 → `ReglaLimiteTest.literalSoloASuRuta`, `ReglaLimiteTest.aplicaASinCambios` (HU-02, intacto), `FiltroLimiteIntentosTest.rutaSinReglaNoConsume`
- R44 → `ReglaLimiteTest.rutaCodificadaAplica`, `ReglaLimiteTest.escapeMalFormadoNoAplica`, `LimiteCatalogoIT.rutaCodificadaCuenta`
- R45 → `ReglaLimiteTest.patronInvalidoLanza`
- R46 → `FiltroLimiteIntentosTest.dosReglasDelMismoGrupoDescuentanUnIntento`, `FiltroLimiteIntentosTest.gruposDistintosCuentanPorSeparado`
- R47 → `LimiteCatalogoIT.registroSigueLimitado` (y `ArrendadorControllerIT`/`UsuarioControllerIT` de HU-01/HU-02 sin cambios, verdes)

## Reemplazo de un test anterior (T13)

`ZonaControllerIT.sinSesion` cambió de "HU-07 R43: 401" a "HU-10 R13: 200 con las 50 zonas": por D2 `GET /api/zonas`
es público y **reemplaza HU-07 R43 para `/api/zonas`** (el resto de HU-07 R43 sigue igual). Es la única prueba de
HU-01…HU-09 modificada; se agregó `ZonaControllerIT.tokenInvalido`.

## Archivos

Backend `2_Backend/src/main/java/pe/edu/ulima/espacioya/`:
- M `comun/limite/ReglaLimite.java` (record → final class con `PathPattern`), `comun/limite/FiltroLimiteIntentos.java`
  (un intento por grupo), `comun/seguridad/ConfiguracionSeguridad.java` (`permitAll` `GET /api/espacios`, `GET /api/zonas`).
- M `espacios/repository/{EspacioRepository,HorarioSemanalRepository,BloqueoFechaRepository,FotoEspacioRepository}.java`,
  `espacios/service/{DisponibilidadService,DisponibilidadServiceImpl}.java`, `espacios/service/puertos/ReservasDelEspacio.java`.
- N `espacios/service/{CatalogoEspaciosService,CatalogoEspaciosServiceImpl,EspacioPublicado}.java`.
- N `busqueda/controller/{BusquedaController,LimiteCatalogo,ReglasLimiteBusqueda}.java`,
  `busqueda/dto/{ResultadoBusquedaDTO,ResultadoEspacioDTO}.java`,
  `busqueda/service/{BusquedaService,BusquedaEspacios,BusquedaBase,CriteriosBusqueda}.java`.
- N `2_Backend/src/main/resources/db/migration/V3_2__indices_busqueda.sql` (V3_2 libre en `origin/develop` al crearla).
- M `2_Backend/src/main/resources/application.properties`, `2_Backend/.env.example`.

Tests `2_Backend/src/test/java/pe/edu/ulima/espacioya/`:
- N `busqueda/controller/{BusquedaControllerIT,LimiteCatalogoIT,LimiteCatalogoTest}.java`,
  `busqueda/service/{BusquedaServiceTest,BusquedaBaseTest}.java`, `comun/limite/FiltroLimiteIntentosTest.java`,
  `espacios/service/CatalogoEspaciosServiceImplTest.java`.
- M `comun/limite/ReglaLimiteTest.java` (3 tests de HU-02 intactos), `espacios/service/DisponibilidadServiceImplTest.java`,
  `espacios/service/DisponibilidadServiceImplIT.java`, `espacios/controller/ZonaControllerIT.java`.

Frontend `3_frontend/src/`:
- N `components/ui/Imagen.jsx` + `Imagen.module.css`, `api/busqueda.js`, `pages/busqueda/BuscarEspacios.jsx` +
  `BuscarEspacios.module.css`, `pages/busqueda/textosBusqueda.js`.
- M `App.jsx` (ruta pública `/buscar`; enlace "Buscar espacios" en `/` y en el encabezado, con y sin sesión).

Docs: M `docs/architecture.md` (tabla de puertos: `bloquesReservadosDelDia`; `conBloquesLibres` hecho;
`CatalogoEspaciosService`; `ReglaLimite` con patrones y grupo `catalogo`; la nota existente para HU-12 no se duplicó),
`docs/frontend.md` (fila `Imagen`, `Tarjeta` en resultados, árbol del kit), `docs/modelo-datos.md` (índice
`ix_espacio_busqueda`). Progreso: `specs/HU-10/tasks.md`, `progress/HU-10.md`.

## Desviaciones menores del spec (sin cambiar requirements ni decisiones)

1. **Ejemplo de R8 en design § 13** ("hoy a las 10:00 con franja 8–11 → incluido"): con `CalculoDisponibilidad` de
   HU-08, a las 10:00 la hora 10 ya empezó (`fecha.atTime(10,0)` no es posterior a ahora), así que 8–11 no tiene
   bloques libres. Los tests usan 8–11 → excluido y 8–12 → incluido. Es la misma regla R8; no se tocó código de HU-08.
2. **`.env.example`** (T9 decía "las dos variables, vacías"): van **comentadas** con su valor por defecto. Con
   `LIMITE_CATALOGO_MAXIMO=` vacía, `${LIMITE_CATALOGO_MAXIMO:120}` resuelve a "" (el valor por defecto solo aplica si
   la variable no existe) y el backend no arrancaría por conversión de tipo. El comentario lo explica.
3. **R12** se prueba con un Bearer inexistente; revocado y vencido siguen el mismo camino
   (`ValidadorSesion.validar` vacío → anónimo) sin código nuevo.

## Notas para el reviewer y el dueño

- HU-11 y HU-23 solo agregan un `@Bean` con `limiteCatalogo.regla("GET", "/api/espacios/{id}")` (u otra ruta) para
  compartir el cupo; HU-23 envuelve `BusquedaEspacios` (Decorator) sin cambiar `BusquedaService`.
- Aviso a HU-12 (Eduardo Saldaña): ya estaba en `docs/architecture.md`; se recomienda sobrescribir
  `bloquesReservadosDelDia` con una sola consulta.
- Al unir a `develop`, si otra historia del sprint 3 tomó `V3_2`, renombrar `V3_2__indices_busqueda.sql` al siguiente
  libre (`docs/conventions.md`).
- Tareas no completadas: ninguna.

## Resultado de init.sh

2026-10-29, `./init.sh` completo, exit 0, "[OK] Entorno listo": unitarias 472 y de integración 322, todas verdes
(`./mvnw verify` con Testcontainers); ESLint sin errores ni advertencias; `npm run build` correcto; sin colores fuera
de `tokens.css`, sin secretos trackeados y con las comprobaciones de arquitectura en verde.
