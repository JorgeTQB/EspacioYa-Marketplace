# Exploración HU-22 · Derechos sobre datos personales

- **Dueño:** Kevin Chan · **Módulo:** `cuentas` · **Rama:** `feature/HU-22-derechos-datos-personales` (desde `develop`)
- **Fecha:** 2026-11-04 · **Explorador:** general-purpose (solo lectura)

Rutas cortas: `M/` = `2_Backend/src/main/java/pe/edu/ulima/espacioya/`, `T/` = `2_Backend/src/test/java/pe/edu/ulima/espacioya/`,
`DB/` = `2_Backend/src/main/resources/db/migration/`, `F/` = `3_frontend/src/`.

## 0. Qué pide la historia y qué dicen los docs

**Archivos leídos:** `docs/historias/HU-22.md`, `docs/requerimientos.md` (RF-A-23..25, RNF-SEG-03/12/16/17, RNF-CUM-03/04, RN-17,
§9 pendiente 6), `docs/modelo-datos.md`, `docs/architecture.md` (puertos, §Cuenta bancaria, §Perfil, §Liquidación, §Mis reservas,
estados), `docs/conventions.md` (§Tests), `docs/frontend.md` (§perfil).

- CA1: exportar JSON con perfil, reservas y pagos tras confirmar contraseña. CA2: sin reservas futuras ni liquidaciones
  pendientes → desactivar cuenta, anonimizar datos personales, conservar registros contables "el plazo legal". CA3: con
  impedimentos → decir qué resolver. STRIDE: (S) reautenticación; (I) solo datos de la sesión; (R) auditar exportación y eliminación.
- RF-A-23, RF-A-24, RF-A-25; RN-17 ("Para eliminar la cuenta no debe haber reservas futuras ni liquidaciones pendientes");
  RNF-SEG-03 (reautenticación también para exportar/eliminar); RNF-SEG-12 (auditar "exportación y eliminación"); RNF-CUM-04.
- **Ya decidido en `docs/architecture.md`:**
  - Tabla de puertos (línea 134): **`cuentas.ParticipanteDatosPersonales`**, la implementan `reservas`, `pagos` y `busqueda`, para
    "exportar, verificar impedimentos y anonimizar". Se inyecta como `List<>` (OCP). **No existe aún en código.**
  - Línea 165-166: `CuentaService.confirmarContrasena(usuario, contrasena)`: "HU-20 y HU-22 pueden reutilizarlo".
  - Línea 178: **HU-22 anonimiza la fila de `cuenta_bancaria`** (sin cascada desde `usuario`).
  - Línea 210-212: **HU-22 exporta `correo_pendiente` y si hay foto; al anonimizar borra `correo_pendiente`, `foto_ruta`/`foto_tipo_mime`
    y el archivo de `ArchivosPrivados`, anula los tokens `CAMBIO_CORREO`, y puede reutilizar `ReautenticacionPerfil` (o su patrón).**
  - Línea 462-464: **HU-22 conecta `MisLiquidacionesService.tieneLiquidacionesPendientes` al puerto**; "los pagos `PAGADO` aún no
    liquidados no cuentan (si deben bloquear, lo decide su spec)"; `liquidacion` no tiene cascada.
  - Línea 489: `ReservaConHorario.esProxima(ahora, ahoraLocal)`: "HU-21 y HU-22 pueden reutilizarla para reservas futuras".
  - Estados: `usuario.estado` ya admite `ELIMINADA` (línea 596).
- **Pendiente abierto** `docs/requerimientos.md` §9 punto 6: "Plazo legal de conservación contable (HU-22 CA2): falta el plazo concreto."

## 1. HU-01: registro con consentimiento

**Archivos:** `DB/V1_1__usuarios.sql`, `DB/V1_2__auditoria.sql`, `M/cuentas/domain/Usuario.java`, `M/cuentas/domain/EstadoUsuario.java`,
`M/cuentas/service/RegistroService.java`, `M/cuentas/service/PoliticaPrivacidad.java`, `M/cuentas/controller/UsuarioController.java`,
`M/cuentas/controller/PoliticaPrivacidadController.java`, `M/cuentas/dto/RegistroUsuarioDTO.java`, `application.properties`.

- Migraciones **Flyway** en `DB/` (`V<sprint>_<n>__nombre.sql`; última en `develop`: `V6_1__perfil_usuario.sql`, así que HU-22
  (Sprint 6) usaría **`V6_2`** si necesita migración).
- Tabla `usuario` (`V1_1`): `id`, `correo` (`uq_usuario_correo`, `ck_usuario_correo_normalizado` = minúsculas sin espacios),
  `hash_contrasena NOT NULL`, `rol` (`USUARIO|ARRENDADOR|ADMIN`), `correo_verificado`, `estado` (`ck_usuario_estado` con
  `ACTIVA|SUSPENDIDA|ELIMINADA`), `version_politica NOT NULL`, `fecha_consentimiento NOT NULL`. Luego `V1_3` añade `nombre`,
  `telefono` (`ck_usuario_telefono` `^9[0-9]{8}$` o nulo) y `V6_1` añade `correo_pendiente`, `foto_ruta`, `foto_tipo_mime` con
  `ck_usuario_correo_pendiente` y `ck_usuario_foto`.
- **Consentimiento:** `Usuario.registrar(...)` guarda `versionPolitica` + `fechaConsentimiento = Instant.now(reloj)`. La **IP** del
  consentimiento NO está en `usuario`: solo en `auditoria` (`RegistroService.ACCION_REGISTRO = "REGISTRO_USUARIO"`, `ip`,
  `detalle = "versionPolitica=<v>"`). La versión vigente sale de `espacioya.privacidad.version-vigente=2026-10`
  (`PoliticaPrivacidad.versionVigente()/esVigente()`), expuesta en `GET /api/politicas-privacidad/vigente`.
- Endpoints: `POST /api/usuarios` (registro, 202 genérico), `POST /api/arrendadores` (HU-02); límite 5 registros/IP/hora
  (`M/cuentas/controller/ReglasLimiteCuentas.java`, grupo `registro`).
- `Usuario` no tiene hoy métodos de eliminación/anonimización; sí `suspender()`/`reactivar()`, `esGestionablePorAdmin()` (excluye
  `ELIMINADA`), `motivoRechazoInicioSesion()` (rechaza estado ≠ `ACTIVA`), `quitarFoto()`, `cambiarContrasena(hash)`.
- `auditoria` (`V1_2`): `usuario_id` FK a `usuario` **sin cascada**, `accion`, `entidad`, `entidad_id`, `ip`, `fecha`, `detalle`.

## 2. HU-20: edición de perfil (rectificación)

**Archivos:** `M/cuentas/controller/PerfilController.java`, `FacturacionController.java`, `ConfirmacionCorreoController.java`,
`M/cuentas/service/PerfilService.java`, `CambioCorreoService.java`, `FacturacionService.java`, `ReautenticacionPerfil.java`,
`M/cuentas/dto/PerfilDTO.java`, `DatosPerfilDTO.java`, `FacturacionDTO.java`, `ActualizacionFacturacionDTO.java`,
`SolicitudCambioCorreoDTO.java`, `F/pages/cuentas/Perfil.jsx`.

- Rutas (todas `no-store`, la cuenta sale de `@AuthenticationPrincipal UsuarioAutenticado`, sin id en la URL):
  `GET/PUT /api/usuarios/actual/perfil`, `GET/PUT/DELETE /api/usuarios/actual/foto`, `POST /api/usuarios/actual/cambios-correo`,
  `POST /api/cambios-correo/confirmaciones` (pública), `PUT /api/arrendadores/actual/facturacion`.
- Editable: nombre y teléfono (`DatosPerfilDTO`, `@Size(max=100)`, `@Pattern("^$|9[0-9]{8}")`; arrendador obligatorios), foto
  (JPG/PNG ≤5 MiB, cifrada en `ArchivosPrivados` carpeta `perfiles`), correo (vía `correo_pendiente` + token `CAMBIO_CORREO`
  30 min, con contraseña, 3/hora), facturación del arrendador (tipo/número de documento, razón social, dirección; con contraseña;
  puede volver la identidad a `EN_REVISION`).
- `PerfilDTO(rol, nombre, telefono, correo, correoPendiente, tieneFoto, facturacion)`; `FacturacionDTO` con número **enmascarado**
  (`PerfilArrendador.numeroEnmascarado()`), razón social y dirección descifradas, `estadoIdentidad`. Útil como base del bloque
  "perfil" de la exportación (pero ojo: la exportación de acceso debería decidir si da el documento completo o enmascarado).
- `PerfilService.exigirTitular(usuario)` (paquete, `static`): solo `USUARIO`/`ARRENDADOR`, si no `AccesoDenegadoException` (403).
  HU-22 puede reutilizar esta regla (ADMIN no exporta ni se elimina).
- **`ReautenticacionPerfil.exigir(usuario, contrasena, Operacion, ip)`** (`@Component`): 429 si superó 5 fallos/15 min con clave
  `perfil|<id>`; contraseña vacía → 400 `contrasena` (`MENSAJE_CONTRASENA`); incorrecta → `limitador.registrar`, audita
  `PERFIL_CONTRASENA_FALLIDA` con `detalle="operacion=<OP>"` y 400 `MENSAJE_CONTRASENA_INCORRECTA`; correcta → limpia contador.
  Se llama **fuera de transacción**. `enum Operacion { CORREO, FACTURACION }` → HU-22 tendría que **agregar** `EXPORTACION` y
  `ELIMINACION` (o crear su propia clase con el mismo patrón). Contraseña incorrecta es 400 y no 401 porque `F/api/cliente.js`
  borra el token ante 401 con token.
- Patrones de transacción en `PerfilService`: `TransactionTemplate` + `UsuarioRepository.findParaActualizar(id)` (PESSIMISTIC_WRITE)
  y `DespuesDelCommit.ejecutar(() -> eliminarSinFallar(clave))` para borrar archivos tras el commit.
- `CambioCorreoService`: `TokenRepository.anularPendientes(usuarioId, TipoToken, ahora)` ya existe (útil para anular
  `CAMBIO_CORREO` y `RECUPERACION`).

## 3. HU-19: liquidaciones y datos contables a retener

**Archivos:** `DB/V5_2__cuenta_bancaria.sql`, `DB/V5_3__liquidacion.sql`, `DB/V4_1__pago.sql`, `DB/V4_3__reembolso.sql`,
`M/pagos/domain/{Liquidacion,LiquidacionDetalle,CuentaBancaria,EstadoLiquidacion,Pago,Reembolso}.java`,
`M/pagos/service/MisLiquidacionesService.java`, `M/pagos/repository/{LiquidacionRepository,CuentaBancariaRepository,PagoRepository}.java`.

- `liquidacion` (`V5_3`): `arrendador_id` FK a `perfil_arrendador(usuario_id)` **sin cascada** ("dato contable, HU-22 anonimiza"),
  montos, `estado` `PENDIENTE_CUENTA|GENERADA|PAGADA`, `generada_por`/`pagada_por` FK a `usuario` (admin), `numero_operacion`,
  `banco_destino`, `cuenta_destino` enmascarada `****1234`. `ck_liquidacion_pagada` exige esas columnas en `PAGADA` → **no se pueden
  anular**; son contables y no contienen nombre/documento.
- `liquidacion_detalle`: FK a `liquidacion`, `reserva`, `pago` sin cascadas. → **impide borrar** `reserva`, `pago`, `perfil_arrendador`.
- `cuenta_bancaria` (`V5_2`): `arrendador_id` FK a `perfil_arrendador` **sin cascada**, `numero` cifrado `v1:`, `titular` (razón social
  en claro), `banco`. CHECKs: `ck_cuenta_bancaria_banco` (≥2 caracteres), `ck_cuenta_bancaria_titular` (no vacío), `numero NOT NULL`
  → la anonimización debe poner valores de relleno válidos o borrar la fila (borrar es posible: nada la referencia; la liquidación
  pagada ya guardó banco y cuenta enmascarada).
- `pago` (`V4_1`): `reserva_id` sin cascada, `ultimos4`, `monto`, `estado`, `id_transaccion_pasarela`. `reembolso` (`V4_3`): `pago_id`
  sin cascada. Son registros contables → conservar.
- **Impedimento listo:** `MisLiquidacionesService.tieneLiquidacionesPendientes(arrendadorId)` → `existsByArrendadorIdAndEstadoIn(id,
  EstadoLiquidacion.POR_PAGAR)` (`GENERADA` o `PENDIENTE_CUENTA`), sin sesión ni rol, `readOnly`. Javadoc: "lo conectará HU-22 a
  `cuentas.ParticipanteDatosPersonales`".
- No hay hoy consulta de "pagos `PAGADO` sin liquidar de un arrendador" por arrendador (solo `PagoRepository.pagadosSinLiquidar(bool)`
  global) ni de "pagos de las reservas de un usuario" (solo por `reservaId`: `findFirstByReservaIdOrderByCreadoEnDescIdDesc`,
  `findByReservaIdAndEstado`). Para exportar pagos habrá que agregar un método por lote de `reservaId`s.
- `MisLiquidacionesService.listar` + `VistaLiquidaciones.misLiquidaciones` dan `MiLiquidacionDTO` del arrendador (exportable).

## 4. Otros datos personales a cubrir (exportación / anonimización)

**Archivos:** todas las migraciones de `DB/`, `M/cuentas/domain/{PerfilArrendador,DocumentoIdentidad,Sesion,Token,Mfa,CodigoRespaldo,DesafioMfa}.java`,
`M/reservas/domain/Reserva.java`, `M/reservas/repository/ReservaDAO.java`, `M/espacios/domain/Espacio.java`.

| Tabla | Dato personal | FK a usuario / cascada | Para HU-22 |
|---|---|---|---|
| `usuario` | correo, nombre, teléfono, `correo_pendiente`, foto (`foto_ruta` → archivo cifrado `perfiles/<UUID>`), hash, consentimiento | — | Exportar; anonimizar: `correo` debe seguir único, normalizado y `NOT NULL` (placeholder p. ej. por id), `hash_contrasena NOT NULL`, nombre/teléfono/correo_pendiente/foto a nulo, `estado=ELIMINADA`. ¿conservar `version_politica`/`fecha_consentimiento`? |
| `perfil_arrendador` (`V1_3`) | `numero_documento` (cifrado), `hash_documento` (HMAC, **UNIQUE NOT NULL**), `razon_social`, `direccion_fiscal` (cifradas), `motivo_rechazo` | PK=FK `usuario` ON DELETE CASCADE | No se puede borrar: lo referencian `liquidacion`, `cuenta_bancaria`, `espacio` (cascada), `documento_identidad`, `revision_identidad`. Anonimizar con relleno; `hash_documento` necesita un valor único distinto (y liberar el documento para un nuevo registro) |
| `documento_identidad` (`V2_1`) | archivos cifrados `identidad/<UUID>` en `ArchivosPrivados` | FK `perfil_arrendador` cascada | Borrar filas **y archivos** (`ArchivosPrivados.eliminar`, tras commit). Repo: `DocumentoIdentidadRepository.findByArrendadorIdOrderByTipoAsc` |
| `revision_identidad` (`V2_3`) | `motivo` (texto del admin) | cascada desde perfil | Historial admin; decidir |
| `suspension_cuenta` (`V3_1`) | `motivo` | cascada | Historial admin; decidir |
| `sesion` (`V1_4`) | hash de token | cascada | Revocar todas: `SesionRepository.revocarTodas(usuarioId)` (lo usa `CuentaServiceImpl.suspenderCuenta`, línea ~194). `SesionService.validar` ya rechaza cuentas no `ACTIVA` |
| `token` (`V1_5`, `V6_1`) | hash | cascada | `TokenRepository.anularPendientes(usuarioId, tipo, ahora)` para `RECUPERACION` y `CAMBIO_CORREO` |
| `mfa`, `codigo_respaldo`, `desafio_mfa` (`V5_1`) | secreto TOTP cifrado, huellas | cascada | Borrar o desactivar (repos `MfaRepository`, `CodigoRespaldoRepository`, `DesafioMfaRepository`). Exportar solo "MFA activo sí/no" |
| `reserva` (`V3_3`) | `usuario_id` (sin cascada), espacio, fecha/horas, montos, cancelación | sin cascada | Exportar (vía `ReservaDAO.delUsuario` → `ReservaConHorario`); conservar (contable). `reserva_bloque` cascada desde reserva |
| `pago`, `reembolso`, `checkout_pago` | `ultimos4`, montos, ids pasarela, `url_pago` | vía `reserva` | Exportar pagos del usuario; conservar |
| `espacio`, `foto_espacio`, `horario_semanal`, `bloqueo_fecha` (`V2_2`, `V2_4`) | nombre, dirección del espacio (no del titular), fotos públicas | `espacio.arrendador_id` cascada desde perfil | Decidir en spec: ¿espacios del arrendador eliminado pasan a `BAJA`? Hoy ya quedan ocultos porque `arrendadoresNoActivos()`/`cuentaActiva()` excluyen cuentas no `ACTIVA` |
| `cuenta_bancaria` (`V5_2`) | número cifrado, titular, banco | sin cascada | Anonimizar o borrar (ver §3) |
| `liquidacion`, `liquidacion_detalle` | montos, cuenta enmascarada | sin cascada | Conservar (contable), exportar las del arrendador |
| `auditoria` (`V1_2`) | `ip`, `detalle` (p. ej. `nuevo=<correo enmascarado>`) | FK **sin cascada** | Conservar (STRIDE R); decidir si entra a la exportación (probablemente no) |
| `resena`, `reclamo`, `mensaje_reclamo`, `reporte` | — | — | **No existen aún** (HU-24, HU-26, HU-27). El puerto con `List<>` permite que esas historias se sumen después |

- Reglas de "reserva futura": `ReservaConHorario.esProxima(ahora, ahoraLocal)` (estado leído `PENDIENTE_PAGO` vigente o `CONFIRMADA` y
  fin posterior a la hora de Lima). Para el **usuario**: `ReservaDAO.delUsuario(usuarioId)`. Para el **arrendador**: reservas de
  sus espacios con `CatalogoEspaciosService.nombresDelArrendador(arrendadorId)` + `ReservaDAO.confirmadasDeEspacios(ids)` (solo
  `CONFIRMADA`) o el puerto `espacios.ReservasDelEspacio.bloquesReservados(espacioId, desde, hasta)` + `BloqueReservado.terminaDespuesDe`
  (patrón de HU-21, ventana `VentanaReserva`; incluye `PENDIENTE_PAGO` vigente).
- Reembolsos `PENDIENTE`/`FALLIDO` del usuario (`reembolso`, índice `ix_reembolso_por_atender`) no están en RN-17; ver Riesgos.

## 5. Componentes transversales reutilizables (`comun` y `cuentas`)

**Archivos:** `M/comun/auditoria/*`, `M/comun/limite/*`, `M/comun/error/*`, `M/comun/seguridad/*`, `M/comun/transaccion/DespuesDelCommit.java`,
`M/comun/web/IpCliente.java`, `M/comun/almacenamiento/ArchivosPrivados.java`, `M/comun/cifrado/*`, `M/comun/configuracion/*`,
`M/cuentas/service/NotificadorCorreo.java`, `M/reservas/service/ProgramacionExpiracion.java`.

- **Auditoría:** `AuditoriaService.registrar(new EventoAuditoria(usuarioId, accion, entidad, entidadId, ip, detalle))`, `@Transactional`
  (se une a la del llamador o abre una). Convención de acciones `UPPER_SNAKE` como constantes `ACCION_*` en el servicio
  (p. ej. `PERFIL_ACTUALIZADO`); detalle sin valores personales (solo nombres de campos).
- **Rate limiting:** `LimitadorIntentos` (en memoria, `intentar/superaLimite/registrar/limpiar(clave)`), y por ruta/IP
  `ReglaLimite(metodo, patron, maximo, ventana, grupo)` como `@Bean` (aplicada por `FiltroLimiteIntentos`). RNF-SEG-17 no fija un
  límite para exportar/eliminar más allá de la contraseña.
- **Errores** (`ManejadorGlobalErrores`): `ReglaNegocioException(campo, mensaje)` → 400; `ValidacionException(List<ErrorCampoDTO>)` →
  400 con lista de errores por campo (sirve para CA3: varios impedimentos); `AccesoDenegadoException` → 403;
  `RecursoNoEncontradoException` → 404; `DemasiadosIntentosException` → 429; `CredencialesInvalidasException` → 401 (no usar aquí);
  genérico 500.
- **Usuario autenticado:** `record UsuarioAutenticado(Long usuarioId, Long sesionId, String rol)` vía
  `@AuthenticationPrincipal` (lo pone `FiltroAutenticacionSesion`; sesión validada por `ValidadorSesion` → `SesionService.validar`,
  que rechaza con 401 cuentas no `ACTIVA`). IP: `IpCliente.de(HttpServletRequest)`.
- **Re-autenticación:** `CuentaService.confirmarContrasena(usuario, contrasena)` + `ReautenticacionPerfil` (ver §2).
- **Correo:** `NotificadorCorreo` (interfaz; implementación `NotificadorCorreoSimulado`). No hay método de "cuenta eliminada"; si
  la spec lo quiere, agrega uno (y HU-25 lo implementa en el adaptador real). Enviar tras commit con `DespuesDelCommit.ejecutar`.
- **Archivos:** `ArchivosPrivados.leer/eliminar(clave)` (foto de perfil y documentos de identidad).
- **Cifrado:** `CifradorCampos` (`v1:` AES-GCM) y `HuellaCampos` (HMAC) para valores de relleno de `perfil_arrendador`.
- **Reloj/zona:** bean `Clock` (UTC) y `ZonaHoraria.hoy/ahora(reloj)` en `America/Lima`.
- **Jobs programados:** solo `reservas/service/ProgramacionExpiracion` (`@EnableScheduling`, `@Scheduled(fixedDelayString=...)`,
  activable por propiedad). No hay job de purga/retención; ninguno es necesario para CA1–CA3.
- **Puertos/servicios entre módulos:** dirección `comun ← cuentas ← espacios ← reservas → pagos → cuentas`. `cuentas` no puede
  llamar a `reservas`/`pagos`: por eso el puerto `ParticipanteDatosPersonales` en `cuentas` implementado por `reservas` y `pagos`
  (ejemplo de puerto en otro módulo: `M/espacios/service/puertos/ReservasDelEspacio.java` + `M/reservas/service/impl/ReservasDelEspacioImpl.java`;
  `ObjectProvider` o `List<>` inyectada).

## 6. Frontend

**Archivos:** `F/App.jsx`, `F/api/cliente.js`, `F/api/cuentas.js`, `F/api/reservas.js`, `F/api/pagos.js`, `F/api/tokenSesion.js`,
`F/sesion/ProveedorSesion.jsx`, `F/pages/cuentas/Perfil.jsx`, `F/pages/cuentas/textosCuentas.js`, `F/pages/cuentas/erroresCampo.js`,
`F/components/ui/*`, `docs/frontend.md`.

- Kit `F/components/ui/`: `Alerta` (`tipo` info/exito/atencion/error), `Boton` (`variante` primario/secundario/peligro…, `cargando`,
  `deshabilitado`, `tipo`), `Campo`, `CuentaRegresiva`, `EtiquetaEstado` (ya tiene `ELIMINADA` de `Cuenta`), `Galeria`, `Imagen`,
  `Modal` (`variante` por defecto `primario`; HU-17 usa `peligro-confirmar`), `SelectorBloques`, `Tarjeta` (`titulo`).
- API: `peticion(ruta, {metodo, cuerpo, autenticada})` (JSON) y `peticionArchivo(ruta)` (GET con token → `blob`). Para descargar el
  JSON tras contraseña hace falta un **POST** que devuelva el archivo: `peticionArchivo` solo hace GET sin cuerpo → habría que
  extenderlo o usar `peticion` y armar el `Blob` en el cliente. Funciones de perfil en `F/api/cuentas.js`: `obtenerPerfil`,
  `actualizarDatosPerfil`, `subirFotoPerfil`, `obtenerFotoPerfil`, `quitarFotoPerfil`, `solicitarCambioCorreo`, `confirmarCambioCorreo`,
  `actualizarFacturacion`, `cerrarSesion`.
- Rutas: `/panel/perfil` (`Perfil.jsx`, `RutaProtegida`, USUARIO/ARRENDADOR; ADMIN vuelve a `/panel`) con 4 `Tarjeta` y el
  `Modal primario` "Confirma con tu contraseña" (patrón: error de `contrasena`/429 se queda en el modal). `/panel/seguridad`
  (`SeguridadCuenta.jsx`). **Encaje natural:** una nueva `Tarjeta` "Mis datos personales" en `Perfil.jsx` (o subruta
  `/panel/perfil/datos`) con "Descargar mis datos" y `Boton peligro` "Eliminar mi cuenta" + `Modal` de contraseña; tras eliminar:
  `borrarToken()` + `salir()` de `useSesion` y navegar a `/`.
- Textos en `textosCuentas.js`; errores por campo con `erroresPorCampo` (`erroresCampo.js`). Regla `docs/frontend.md` 394: no
  guardar datos personales ni tokens en `localStorage`.
- **No hay framework de tests frontend** (`3_frontend/package.json` solo tiene `dev`, `build`, `lint`, `preview`): la verificación
  frontend es ESLint + prueba manual.

## 7. Convenciones de tests

**Archivos:** `docs/conventions.md` §Tests, `T/cuentas/service/ReautenticacionPerfilTest.java`, `T/cuentas/controller/PerfilSoporte.java`,
`T/cuentas/controller/PerfilControllerIT.java`, `T/TestcontainersConfiguration.java`, `T/RelojControlable.java`, `T/pagos/controller/PagosSoporte.java`.

- JUnit 5 + Mockito + AssertJ (unitarias, `<Clase>Test`, `./mvnw test`, sin Docker); `@SpringBootTest` + `MockMvc` + Testcontainers
  PostgreSQL 17 (`<Clase>IT`, `./mvnw verify`, failsafe); mismo paquete que la clase; `@DisplayName("HU-XX Rn: ...")`; reloj `Clock`
  inyectado; una prueba por CA y por control STRIDE.
- **Unitaria (ejemplo):** `ReautenticacionPerfilTest` — `@ExtendWith(MockitoExtension.class)`, `@Mock CuentaService`, `@Mock AuditoriaService`,
  `LimitadorIntentos` real con `Clock.fixed(AHORA, UTC)`, `UsuarioAutenticado TITULAR = new UsuarioAutenticado(7L, 70L, "ARRENDADOR")`,
  `assertThatThrownBy(...).isInstanceOfSatisfying(ReglaNegocioException.class, e -> assertThat(e.getCampo())...)`.
- **Integración (ejemplo):** `PerfilControllerIT extends PerfilSoporte` con `@SpringBootTest`, `@AutoConfigureMockMvc(print = MockMvcPrint.NONE)`,
  `@Import({TestcontainersConfiguration.class, PerfilSoporte.Dobles.class})`; `PerfilSoporte` usa `JdbcTemplate` para sembrar/leer filas
  (`filaUsuario(id)`, `filaPerfil(id)`), `RelojControlable`, doble `@Primary` de `NotificadorCorreo`, una IP distinta por test (10.20.x.x)
  y adelanta el reloj para no arrastrar contadores; `limpiar` borra en orden FK (`token`, `sesion`, `auditoria`, `documento_identidad`,
  `mfa`, `perfil_arrendador`, `usuario`). Para HU-22 con reservas/pagos/liquidaciones, ver el orden de `PagosSoporte.limpiar`
  (`liquidacion_detalle`, `liquidacion`, `reembolso`, `pago`, `reserva`, `bloqueo_fecha`, `espacio`, `sesion`, `auditoria`,
  `cuenta_bancaria`, `perfil_arrendador`, `usuario`). Dobles de puertos deben ir con `@Primary` (nota HU-12 en architecture.md).
- Hay también IT de migración (`T/cuentas/controller/PerfilUsuarioMigracionIT.java`) si HU-22 agrega `V6_2`.

## Riesgos / huecos

1. **Puerto inexistente:** `cuentas.ParticipanteDatosPersonales` está en `architecture.md` pero no en código. Hay que definir su
   contrato (exportar sección, impedimentos, anonimizar) e implementarlo en `reservas` y `pagos`. `busqueda` figura como implementador
   pero hoy **no guarda datos personales**: probablemente no lo implementa (justificar en design).
2. **Plazo legal contable sin definir** (`requerimientos.md` §9.6). La spec debe decidir (o dejar como pregunta al PO) si se conserva
   indefinidamente o se documenta un plazo sin job de purga. No hay job de retención.
3. **Restricciones que impiden anonimizar "a nulo":** `usuario.correo` UNIQUE/NOT NULL/normalizado, `hash_contrasena NOT NULL`,
   `perfil_arrendador.numero_documento/razon_social/direccion_fiscal/hash_documento NOT NULL` (+ `hash_documento` UNIQUE),
   `cuenta_bancaria.banco/titular/numero` con CHECKs. Hace falta placeholders únicos por id o una migración `V6_2` que relaje
   restricciones (p. ej. permitir nulos cuando `estado = 'ELIMINADA'`). Decidir también si se libera el correo/documento para
   un nuevo registro.
4. **Exportación de pagos:** no hay consulta de pagos por lote de reservas del usuario; `pagos` no conoce reservas del usuario
   (solo `ReservasPorCobrar`/`ReservasLiquidables`). La sección "pagos" probablemente se arma en `reservas` vía `PagoService` o en
   `pagos` con los ids de reserva → definir quién la compone sin ciclo.
5. **Impedimentos no cubiertos por RN-17:** pagos `PAGADO` aún no liquidados del arrendador (architecture.md 463 lo deja a la spec),
   reembolsos `PENDIENTE`/`FALLIDO` del usuario, `PAGADO_TARDIO`/`MONTO_DISTINTO` por devolver a mano, reservas `PENDIENTE_PAGO`
   vigentes. Definir cuáles bloquean y con qué mensaje (CA3).
6. **Reservas futuras del arrendador:** `ReservaDAO.confirmadasDeEspacios` solo trae `CONFIRMADA`; el puerto `ReservasDelEspacio`
   incluye `PENDIENTE_PAGO` vigente. Elegir uno y coherente con HU-21.
7. **Espacios del arrendador eliminado:** quedan ocultos (filtro de cuenta no `ACTIVA`) pero conservan estado; ¿pasarlos a `BAJA`?
   Fotos públicas de espacios en almacenamiento sin cifrar.
8. **Sesión tras eliminar:** revocar todas (`SesionRepository.revocarTodas`) y el frontend debe limpiar token sin pasar por el
   flujo de "sesión perdida" (el 401 siguiente lo dispararía).
9. **Concurrencia:** eliminar mientras se crea una reserva o se genera una liquidación. Orden de bloqueos existente:
   `usuario → espacio → reserva` (HU-12 D4, `CuentaService.bloquearCuenta`), `usuario → perfil_arrendador`, `usuario → cuenta_bancaria`,
   `pg_advisory_xact_lock(190019)` de generación de liquidaciones. La verificación de impedimentos debe hacerse con la fila de `usuario`
   bloqueada para que no entre una reserva entre el chequeo y la anonimización.
10. **Exportación como archivo:** sin endpoint de descarga JSON con `Content-Disposition`; `peticionArchivo` es solo GET. Si la
    contraseña va en el cuerpo, el endpoint es POST → extender el cliente. Respuesta `no-store`.
11. **Reautenticación:** `ReautenticacionPerfil.Operacion` solo tiene `CORREO`/`FACTURACION`; agregar operaciones (comparten el
    cupo `perfil|<id>`) o justificar un cupo propio. RNF-SEG-17 no fija límite específico de exportación/eliminación.
12. **Correo de confirmación de eliminación:** no existe método en `NotificadorCorreo`; la historia no lo pide explícitamente.
13. **Auditoría y consentimiento:** las filas de `auditoria` (con IP) se conservan por (R); la IP del consentimiento solo vive ahí.
    Decidir si la exportación incluye `version_politica`, `fecha_consentimiento` y la IP del registro.
14. **MFA y admin:** borrar `mfa`/`codigo_respaldo`/`desafio_mfa`; ADMIN no debe poder exportar/eliminar (403, como HU-20 D7).
15. **Sin tests frontend:** la pantalla solo se verifica con ESLint y manualmente.
16. **Reseñas/reclamos/reportes** (HU-24/26/27) aún no existen: el puerto con `List<>` debe permitir que se sumen sin cambiar `cuentas`.
