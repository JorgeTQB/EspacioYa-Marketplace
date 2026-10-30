# Review — HU-10

**Veredicto:** APPROVED

Rama `feature/HU-10-busqueda-espacios`, cambios sin commitear. Revisado el 2026-10-29 contra `specs/HU-10/`,
`docs/historias/HU-10.md`, `docs/requerimientos.md` §9.9, `docs/architecture.md`, `docs/conventions.md`,
`docs/frontend.md` y `CHECKPOINTS.md`.

## Verificación ejecutada

`./init.sh` completo (sin `--rapido`): **exit 0**, "[OK] Entorno listo". Backend `./mvnw verify`: 472 unitarias y
322 de integración (Testcontainers), 0 fallos y 0 errores. ESLint sin advertencias, `npm run build` correcto,
sin colores fuera de `tokens.css`, sin secretos y comprobaciones de arquitectura en verde.

## Trazabilidad requirements ↔ tests
Todos los tests llevan `@DisplayName("HU-10 R<n>: …")` y comprueban un resultado concreto (código HTTP, campos del
DTO, ids exactos u orden, `verify`/`verifyNoInteractions`).

- R1: [x] `BusquedaControllerIT.buscaPorTipoYZona` (200 sin sesión, claves del DTO)
- R2: [x] `BusquedaControllerIT.buscaPorTipoYZona` (excluye otro tipo y otra zona), `BusquedaBaseTest.filtraConservandoElOrden`
- R3: [x] `BusquedaControllerIT.excluyeNoPublicados` (BORRADOR, PAUSADO, BAJA, OCULTO)
- R4: [x] `BusquedaControllerIT.excluyeArrendadoresNoActivos` (SUSPENDIDA y ELIMINADA), `CatalogoEspaciosServiceImplTest.sinExcluidosUsaLaConsultaSimple` / `conExcluidosUsaLaConsultaConExclusion`
- R5: [x] `BusquedaControllerIT.excluyeSinHorarioEseDia`, `DisponibilidadServiceImplTest.excluyeSinHorarioYBloqueado`
- R6: [x] `BusquedaControllerIT.excluyeFechaBloqueada`, `DisponibilidadServiceImplTest.excluyeSinHorarioYBloqueado`
- R7: [x] `BusquedaControllerIT.excluyeTodoReservado`, `DisponibilidadServiceImplTest.excluyeTodoReservado`
- R8: [x] `BusquedaControllerIT.hoyExcluyeHorasEmpezadas`, `DisponibilidadServiceImplTest.hoyExcluyeHorasEmpezadas`
- R9: [x] `BusquedaControllerIT.paginaTodoElCatalogo` (45 ids únicos en 3 páginas)
- R10: [x] `BusquedaControllerIT.paginaTodoElCatalogo`, `BusquedaControllerIT.empatePorId`, `BusquedaServiceTest.ordenYFotosDeLaPagina`
- R11: [x] `BusquedaControllerIT.sinResultados`, `BusquedaServiceTest.sinDisponibles`, `BusquedaBaseTest.sinCandidatosNoCalculaDisponibilidad`
- R12: [x] `BusquedaControllerIT.tokenInvalidoComoAnonimo` (Bearer inexistente; revocado y vencido siguen el mismo camino, desviación 3 documentada en `progress/impl_HU-10.md`)
- R13: [x] `ZonaControllerIT.sinSesion` (50 zonas, solo `codigo`/`nombre`, orden por collator es-PE), `ZonaControllerIT.tokenInvalido`
- R14: [x] `BusquedaControllerIT.buscaPorTipoYZona` (`containsOnlyKeys` de los seis campos)
- R15: [x] `BusquedaControllerIT.buscaPorTipoYZona` (`/api/fotos/<uuid de orden 1>`), `CatalogoEspaciosServiceImplTest.fotosPrincipales`
- R16: [x] `BusquedaControllerIT.sinFotoPrincipal`, `CatalogoEspaciosServiceImplTest.fotosPrincipales`
- R17: [x] `BusquedaControllerIT.paginaTodoElCatalogo`, `BusquedaServiceTest.fotosSoloDeLaPaginaPedida`
- R18: [x] `BusquedaServiceTest.paginasDeVeinte`, `BusquedaControllerIT.paginaTodoElCatalogo`
- R19: [x] `BusquedaServiceTest.sinPaginaEsLaPrimera`, `BusquedaControllerIT.paginaPorDefectoYPosterior`
- R20: [x] `BusquedaServiceTest.paginasDeVeinte`, `BusquedaServiceTest.hayMasEnElBorde` (40 exactos)
- R21: [x] `BusquedaServiceTest.paginaPosteriorALaUltima`, `BusquedaControllerIT.paginaPorDefectoYPosterior`
- R22: [x] `BusquedaControllerIT.ignoraParametrosDeTamano`
- R23: [x] `BusquedaServiceTest.tipoInvalido`, `BusquedaControllerIT.tipoInvalido` (incluye `cancha` en minúsculas), `CatalogoEspaciosServiceImplTest.tiposValidos`
- R24: [x] `BusquedaServiceTest.zonaInvalida`, `BusquedaControllerIT.zonaInvalida`, `CatalogoEspaciosServiceImplTest.zonasValidas`
- R25: [x] `BusquedaServiceTest.fechaMalEscrita`, `BusquedaControllerIT.fechaInvalida` (`2026-02-30`, `manana`, ausente), `BusquedaServiceTest.textosDeFechaComoHu08`
- R26: [x] `BusquedaServiceTest.fechaFueraDeVentana`, `BusquedaServiceTest.bordesDeLaVentana`, `BusquedaControllerIT.fechaFueraDeVentana` (hoy−1 y hoy+56 → 400, hoy+55 → 200)
- R27: [x] `BusquedaServiceTest.paginaInvalida`, `BusquedaControllerIT.paginaInvalida` (`0`, `-1`, `1.5`, `abc`, vacío, `99999999999`)
- R28: [x] `BusquedaServiceTest.variosErroresEnOrden`, `BusquedaControllerIT.variosErrores` (4 errores en orden)
- R29: [x] `BusquedaServiceTest.invalidaNoBusca` (`verifyNoInteractions`)
- R30: [x] `DisponibilidadServiceImplIT.conBloquesLibresEquivaleAHorasLibres` (equivalencia contra `horasLibres` en PostgreSQL), `DisponibilidadServiceImplTest.excluyeNoVisibles`
- R31: [x] `DisponibilidadServiceImplTest.loteConsultaUnaVez` (`times(1)`)
- R32: [x] `DisponibilidadServiceImplTest.loteVacioOFueraDeVentana`
- R33: [x] `LimiteCatalogoIT.cuartaResponde429`, `FiltroLimiteIntentosTest.cupoAgotadoResponde429` (cuerpo JSON exacto, cadena no invocada)
- R34: [x] `LimiteCatalogoIT.vuelveAAceptarTrasLaVentana`
- R35: [x] `LimiteCatalogoIT.otraIpSigue`
- R36: [x] `LimiteCatalogoIT.rechazadasNoCuentan`, `FiltroLimiteIntentosTest.rechazadasNoCuentan`
- R37: [x] `LimiteCatalogoIT.invalidasCuentan`
- R38: [x] `LimiteCatalogoIT.zonasYFotosSinLimite`, `LimiteCatalogoIT.zonasYFotosNoConsumen`
- R39: [x] `BusquedaControllerIT.cupoPorDefecto` (120 y PT1M sin propiedades)
- R40: [x] `LimiteCatalogoTest.maximoInvalido`, `LimiteCatalogoTest.ventanaInvalida`
- R41: [x] `LimiteCatalogoTest.reglasCompartenGrupo`, `LimiteCatalogoTest.reglaDeLaBusqueda`, `BusquedaControllerIT.cupoPorDefecto`
- R42: [x] `ReglaLimiteTest.patronConVariable`, `ReglaLimiteTest.patronConComodin`
- R43: [x] `ReglaLimiteTest.literalSoloASuRuta`, `FiltroLimiteIntentosTest.rutaSinReglaNoConsume`, y los 3 tests HU-02 de `ReglaLimiteTest` sin cambios
- R44: [x] `ReglaLimiteTest.rutaCodificadaAplica`, `ReglaLimiteTest.escapeMalFormadoNoAplica`, `LimiteCatalogoIT.rutaCodificadaCuenta` (por HTTP)
- R45: [x] `ReglaLimiteTest.patronInvalidoLanza`
- R46: [x] `FiltroLimiteIntentosTest.dosReglasDelMismoGrupoDescuentanUnIntento`, `FiltroLimiteIntentosTest.gruposDistintosCuentanPorSeparado`
- R47: [x] `LimiteCatalogoIT.registroSigueLimitado` (6.º `POST /api/usuarios` → 429, `POST /api/arrendadores` comparte grupo, otra IP sigue), además de las IT de HU-01/HU-02 sin cambios y en verde

### Rate limiting existente (HU-01, HU-02, HU-03, HU-04) con el cambio a patrones
- HU-01/HU-02 (`cuentas/controller/ReglasLimiteCuentas`): sus dos `ReglaLimite` son literales. Con `PathPattern`, un
  literal solo coincide consigo mismo (R43), y la API pública de la clase no cambió: constructores de 4 y 5
  argumentos, accesores `metodo()`…`grupo()`, `aplicaA` y `clave` (`ReglaLimite.java:22-70`). Los 3 tests HU-02 de
  `ReglaLimiteTest` siguen intactos; `LimiteCatalogoIT.registroSigueLimitado` y las IT de registro pasan. El cambio
  además cierra la evasión `/api/usu%61rios` (`ReglaLimiteTest.rutaCodificadaAplica`).
- HU-03/HU-04 (`InicioSesionService`, `RecuperacionContrasenaService`) usan `LimitadorIntentos` directamente, no
  `ReglaLimite` ni el filtro; `LimitadorIntentos` no se modificó. Sus tests (unitarios e IT) están en verde.
- `FiltroLimiteIntentos.java:39-46`: el `Set` de grupos solo evita el doble descuento dentro del mismo grupo; las
  reglas de grupos distintos siguen contando por separado (`gruposDistintosCuentanPorSeparado`).

## Trazabilidad con la historia
- CA1 (publicados de no suspendidos con al menos un bloque libre): [x] R1–R9, R12, R13, R23–R26, R28, R30–R32
- CA2 (nombre, foto principal, precio, zona; páginas de 20): [x] R10, R14–R21, R27; pantalla en design § 12
- CA3 (sin resultados: mensaje y sugerencia): [x] R11; textos en `textosBusqueda.js` y `BuscarEspacios.jsx:136-141`
- (I/D) scraping, rate limiting por IP: [x] R33–R47; implementado en `LimiteCatalogo`, `ReglasLimiteBusqueda`,
  `ReglaLimite` y `FiltroLimiteIntentos`; cifra decidida por el dueño (§9.9)
- (I/D) scraping, máximo 20 por página: [x] R17, R22, R29; `BusquedaService.TAMANO_PAGINA = 20`, sin parámetro de tamaño
- (I) riesgo aceptado, catálogo público sin datos del arrendador: [x] R12, R13, R14; `ResultadoEspacioDTO` con seis
  campos, sin dirección, descripción ni `arrendadorId`

## Tasks completas
- T1: [x]
- T2: [x]
- T3: [x]
- T4: [x]
- T5: [x]
- T6: [x]
- T7: [x]
- T8: [x]
- T9: [x] (desviación justificada: las variables van comentadas en `.env.example`, no vacías; ver `progress/impl_HU-10.md`)
- T10: [x]
- T11: [x]
- T12: [x]
- T13: [x] (el reemplazo de HU-07 R43 para `/api/zonas` está anotado en `progress/impl_HU-10.md`; decisión D2 del dueño)
- T14: [x]
- T15: [x]
- T16: [x]
- T17: [x]
- T18: [x]
- T19: [x]
- T20: [x]

## Checkpoints
- C1 (arnés completo): [x] los cinco puntos; `./init.sh` completo con exit 0.
- C2:
  - Una `in_progress` por dueño: [x]
  - `in_progress` con `progress/HU-10.md`: [x]
  - Historias `done` con tests verdes: n/a (HU-10 no está `done`; las 9 `done` siguen en verde)
  - Aprobación del dueño anotada: [x] `progress/HU-10.md` ("Spec aprobado por: Sebastian Garate, 2026-10-29")
  - `progress/HU-10.md` solo de la sesión activa: [x]
- C3:
  - Paquetes de módulo: [x] (`busqueda`, `espacios`, `comun`)
  - Ningún controller importa un repository: [x]
  - Sin `@Autowired`, Lombok ni comodines: [x]
  - Sin prints de depuración: [x]
  - Módulos vía interfaz de service o puerto: [x] `busqueda` usa solo `CatalogoEspaciosService` y `DisponibilidadService`
    (interfaces públicas, nuevas o ampliadas, registradas en `docs/architecture.md`); `espacios` usa `CuentaService`.
    `BusquedaService` importa `espacios.domain.VentanaReserva`, que `docs/architecture.md` (tabla "Disponibilidad")
    pide expresamente reutilizar para validar la fecha de la búsqueda.
  - DTO `record`, sin entidades ni campos sensibles: [x]
  - Identidad de la sesión, montos y estados en el servidor: [x] (ruta pública; el filtro de visibilidad está en el servidor)
  - Migración `V3_2__indices_busqueda.sql`, ninguna subida editada: [x] (`V3_2` libre en `origin/develop`)
  - Adapters para integraciones externas: n/a
  - Nada fuera del alcance: [x] (sin mapa, favoritos ni destacados)
  - Sin secretos: [x]
  - Frontend sin colores fuera de tokens ni librerías: [x]
  - Frontend con el kit, mensajes según `docs/frontend.md` y API solo desde `src/api/`: [x] `Campo`, `Boton` (un solo
    primario, "Buscar"; "Anterior" y "Siguiente" secundarios), `Alerta`, `Tarjeta` e `Imagen` (nueva, documentada en el
    kit); errores por campo bajo `Campo` y `Alerta error` con el `ErrorDTO`; lista vacía con texto secundario; monto en
    `--color-texto` negrita; sin `style={{…}}`; llamadas en `api/busqueda.js` y `api/espacios.js`. Coincide con
    design § 12.
- C4:
  - `<Clase>Test` / `<Clase>IT` con `@Import(TestcontainersConfiguration.class)`: [x]
  - Sin H2: [x]
  - Plazos con `Clock` fijo: [x] (`RelojControlable` en IT, `Clock` fijo en unitarias; ventana del cupo y "hoy" en Lima)
  - IT para endpoints y migración: [x] (`BusquedaControllerIT`, `LimiteCatalogoIT`, `ZonaControllerIT`,
    `DisponibilidadServiceImplIT`); concurrencia: n/a
  - Cada test comprueba un resultado concreto, incluido el error: [x]
  - `./mvnw verify` > 0 tests y verde: [x] (472 + 322)
  - `npm run lint` y `build`: [x]
- C5:
  - Sin archivos sospechosos sin trackear: [x] (solo código, specs y progress de HU-10)
  - `progress/history.md` con entrada de la última sesión: n/a hasta el cierre (hay entradas del orquestador del
    2026-10-29; la de cierre de sesión la agrega el orquestador al terminar)
  - `progress/HU-10.md` vuelto a la plantilla: n/a (la historia aún no se cierra)
  - Estado en `feature_list.json`: [x] `in_progress` (pasar a `done` lo decide el dueño tras esta revisión)
  - Rama `feature/HU-10-busqueda-espacios`: [x]
- C6:
  - Carpeta `specs/HU-10/` con los tres archivos: [x]
  - EARS y tabla de trazabilidad completa: [x]
  - `design.md` con endpoints, clases, migración, secuencias, alternativas (A1–A8) y pantallas: [x] (no es ★)
  - Tasks todas `[x]`: [x]
  - Cada `R<n>` con test documentado en `progress/impl_HU-10.md`: [x]
  - Ningún pendiente de §9 resuelto sin el dueño: [x] (§9.9 resuelto por Sebastian Garate y registrado)

## Observaciones (no bloquean)
1. `3_frontend/src/App.jsx:87-90`: la clase `estilos.inicio` pasó del enlace "Crea tu cuenta" al nuevo "Buscar
   espacios"; "Crea tu cuenta" queda en un `<p>` sin clase. Revisar el espaciado de la portada en UAT.
2. Al unir a `develop`, confirmar que `V3_2` sigue libre; si otra historia del sprint 3 lo tomó, renombrar la
   migración (`docs/conventions.md`).
3. El ejemplo de design § 13 para R8 ("10:00 con 8–11 → incluido") no coincide con `CalculoDisponibilidad`; el
   implementer lo probó con 8–11 excluido y 8–12 incluido (misma regla R8). Conviene corregir ese ejemplo si se
   vuelve a editar el design.

## Cambios requeridos (si aplica)
Ninguno.
