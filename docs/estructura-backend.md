# Estructura de carpetas del backend — propuesta

> **Estado: propuesta para discutir en equipo.** Se basa en `AGENTS.md`, `arquitectura-propuesta.md`, `docs/requerimientos.md` y las 28 historias (último commit revisado: `56825f6`).
> Pendientes que afectan esta estructura: nombre del proyecto (paquete raíz), proveedor de la pasarela (nombre del adaptador) y confirmación de PostgreSQL.

## 1. Raíz del backend

```
backend/                      ← o "3_Backend/" si se sigue la numeración de 4_Infraestructure
├── pom.xml                   Spring Web, Data JPA, Security, Validation, Flyway, PostgreSQL,
│                             Bucket4j (rate limit), OpenPDF (comprobantes), ArchUnit, Testcontainers
├── Dockerfile                para ECS Fargate
├── .env.example              PASARELA_*, DB_*, AWS_*, JWT_*, CIFRADO_LLAVE… (sin valores reales)
├── scripts/
│   └── crear-admin.sql       cuentas admin solo por script interno (§4 E)
└── src/
    ├── main/java/pe/edu/ulima/espacioya/      ← "espacioya" mientras se confirma el nombre
    ├── main/resources/
    └── test/java/pe/edu/ulima/espacioya/
```

## 2. Paquetes (`src/main/java/pe/edu/ulima/espacioya/`)

```
EspacioYaApplication.java               @EnableScheduling

comun/                                  transversal; NO depende de ningún módulo
├── seguridad/      SecurityConfig, FiltroAutenticacion, UsuarioSesion (principal),
│                   UsuarioActual (identidad SIEMPRE desde la sesión), IpCliente,
│                   ValidadorSesion            ← interfaz; la implementa cuentas
├── auditoria/      Auditoria (entity), AuditoriaRepository, AuditoriaService
├── error/          ManejadorGlobalErrores (@RestControllerAdvice), ErrorDTO,
│                   ReglaNegocioException, RecursoNoEncontradoException, AccesoDenegadoException
├── ratelimit/      @LimitarTasa, InterceptorLimiteTasa (límites de RNF-SEG-17)
├── correo/         HU-25: Factory Method + Adapter
│   ├── ProveedorCorreo                 Target
│   ├── SesCorreoAdapter                UAT (AWS SES)
│   ├── ConsolaCorreoAdapter            dev (imprime el correo)
│   ├── CorreoService                   lo usan todos los módulos
│   └── fabrica/    FabricaCorreo, CorreoRecordatorio, CorreoPagoConfirmado, CorreoCancelacion…
├── almacenamiento/ AlmacenamientoArchivos (interfaz), S3AlmacenamientoAdapter, LocalAlmacenamientoAdapter,
│                   ValidadorArchivo (tipo real y tamaño), LimpiadorMetadatosImagen
├── cifrado/        CifradorCampos, CampoCifradoConverter (AttributeConverter de JPA)
└── config/         Reloj (bean Clock: pruebas de 15 min, 48 h, 72 h), propiedades tipadas

cuentas/                                HU-01 a 05, 17, 20, 22
├── controller/     RegistroController, SesionController, RecuperacionController,
│                   VerificacionCorreoController, DocumentoIdentidadController,
│                   MfaController, PerfilController, DatosPersonalesController
├── dto/            RegistroUsuarioDTO, RegistroArrendadorDTO, LoginDTO, PerfilDTO…
├── service/        RegistroService, AutenticacionService, RecuperacionContrasenaService,
│   │               IdentidadArrendadorService, MfaService, PerfilService, DatosPersonalesService,
│   │               CuentaService (pública para otros módulos), ValidadorSesionImpl
│   └── puertos/    ParticipanteDatosPersonales   ← HU-22; lo implementan reservas, pagos y busqueda
├── domain/         Usuario, Rol, EstadoCuenta, PerfilArrendador, EstadoIdentidad,
│                   DocumentoIdentidad, Token, TipoToken, Sesion, ConfiguracionMfa,
│                   CodigoRespaldo, PoliticaContrasena
└── repository/     UsuarioRepository, PerfilArrendadorRepository, TokenRepository, SesionRepository…

espacios/                               HU-07, 08, 21
├── controller/     EspacioController, HorarioController, BloqueoController
├── dto/
├── service/        EspacioService, DisponibilidadService (horario − bloqueos − reservas;
│   │               lo reutilizan HU-10, 11 y 12)
│   └── puertos/    ReservasDelEspacio          ← bloques ocupados, ¿hay pagadas en la fecha?,
│                                                  cancelar futuras (HU-21); lo implementa reservas
├── domain/         Espacio (publicar(), pausar(), darDeBaja(), ocultar()), TipoEspacio,
│                   EstadoEspacio, FotoEspacio, HorarioSemanal, BloqueoFecha, Caracteristica
└── repository/

busqueda/                               HU-10, 11, 23, 24
├── controller/     BusquedaController, FichaEspacioController, ResenaController
├── dto/            CriteriosBusquedaDTO (lista blanca), ResultadoBusquedaDTO, FichaEspacioDTO
├── service/        BusquedaService, FichaEspacioService, ResenaService
│   └── filtros/    HU-23 Decorator: BusquedaEspacios, BusquedaBase, FiltroBusqueda (abstracto),
│                   FiltroPrecioMaximo, FiltroCapacidadMinima, FiltroCaracteristica
├── domain/         Resena, EstadoResena
└── repository/     ResenaRepository

reservas/                               HU-12, 14, 15, 25
├── controller/     ReservaController, CancelacionController, ReservasArrendadorController
├── dto/            CrearReservaDTO, ReservaDTO, SimulacionCancelacionDTO
├── service/        ReservaService (pública), MisReservasService, CancelacionService,
│   │               ExpiracionReservasTarea (@Scheduled, RF-D-02),
│   │               RecordatorioReservasTarea (@Scheduled, RF-D-10)
│   ├── politica/   HU-15 Strategy: PoliticaReembolso, ReembolsoUsuarioConAnticipacion,
│   │               ReembolsoUsuarioSinAnticipacion, ReembolsoCancelaArrendador, SelectorPoliticaReembolso
│   ├── iterador/   HU-14 Iterator: ReservasDelUsuario, IteradorReservas (próximas / pasadas)
│   └── puertos/    ReservasPorCobrarImpl, ReservasLiquidablesImpl, ReservasDelEspacioImpl,
│                   DatosPersonalesReservas     ← implementaciones de interfaces de otros módulos
├── domain/         Reserva (confirmar(), expirar(), cancelar()), EstadoReserva, ReservaBloque, ActorCancelacion
└── repository/     ReservaRepository, ReservaBloqueRepository

pagos/                                  HU-13, 16, 18, 19, 28
├── controller/     CheckoutController, WebhookPasarelaController (sin sesión, con firma),
│                   ReembolsoAdminController, CuentaBancariaController,
│                   LiquidacionController, HistorialPagosController
├── dto/            CheckoutDTO, TransaccionDTO (solo ultimos4), LiquidacionDTO, CuentaBancariaDTO (enmascarada)
├── pasarela/       HU-13 Adapter + Factory Method
│   ├── PasarelaPago                    Target: crearCheckout(), verificarWebhook(), reembolsar()
│   ├── <Proveedor>PasarelaAdapter      traduce la API de la pasarela (sandbox)
│   ├── PasarelaSimulada                dev y pruebas, sin red
│   ├── FabricaPasarelaPago             creador abstracto: crearPasarela()
│   ├── FabricaPasarelaSandbox / FabricaPasarelaSimulada   creadores concretos (por perfil)
│   └── EventoPasarela, ResultadoCheckout, FirmaInvalidaException
├── service/        CheckoutFacade (HU-13: reserva vigente → monto → checkout),
│   │               WebhookPagoService (firma + idempotencia), PagoService (pública),
│   │               ReembolsoService, CuentaBancariaService, LiquidacionService,
│   │               HistorialPagosService, ComprobantePdfService
│   ├── comando/    HU-16 Command: SolicitudReembolso (ejecutar(), idempotente, reintentable)
│   └── puertos/    ReservasPorCobrar, ReservasLiquidables, ReclamosAbiertos,
│                   DatosPersonalesPagos (impl. del puerto de cuentas)
├── domain/         Pago, EstadoPago, Reembolso, EstadoReembolso, OrigenReembolso,
│                   CuentaBancaria, Liquidacion (calcula la comisión del 10 %), LiquidacionDetalle, EstadoLiquidacion
└── repository/

administracion/                         HU-06, 09, 26, 27
├── controller/     RevisionIdentidadController, CuentasAdminController,
│                   ReclamoController, ReporteController
├── dto/
├── service/        RevisionIdentidadService, SuspensionService, ReclamoService, ModeracionService
│   └── puertos/    ReclamosAbiertosImpl       ← RN-12: excluir de la liquidación
├── domain/         RevisionIdentidad, DecisionRevision, Reclamo, EstadoReclamo,
│                   MensajeReclamo, Reporte, TipoObjetivoReporte, EstadoReporte
└── repository/
```

## 3. La decisión clave: no hay ciclos entre módulos

Con las historias tal como están escritas aparecen **dependencias circulares**, y la regla 5 de `AGENTS.md` las prohíbe:

- `reservas → pagos` para el reembolso (HU-15), pero el webhook en `pagos` tiene que confirmar la reserva (HU-13).
- `reservas → espacios` para el precio y la disponibilidad, pero `espacios` necesita las reservas para calcular los bloques libres (HU-08) y para la baja (HU-21).
- `administracion → pagos` para el reembolso de un reclamo, pero la liquidación necesita saber qué reclamos están abiertos (RN-12).
- Todos dependen de `cuentas`, pero HU-22 necesita los datos de todos para exportar y eliminar.

**Solución (DIP):** el módulo de abajo define una interfaz en `service/puertos/` y el de arriba la implementa. Así las dependencias van en un solo sentido:

```
comun ← cuentas ← espacios ← reservas → pagos → cuentas
                     ↑          ↑
                 busqueda ──────┘        administracion → (todos)
```

| Interfaz (la define) | La implementa | Para |
| --- | --- | --- |
| `comun.ValidadorSesion` | cuentas | Que el filtro de seguridad revise si la sesión está revocada |
| `cuentas.ParticipanteDatosPersonales` | reservas, pagos, busqueda | HU-22: exportar, verificar si hay impedimentos y anonimizar. Se inyecta como `List<>`, así que agregar un módulo no cambia nada (OCP) |
| `espacios.ReservasDelEspacio` | reservas | HU-08 y HU-21 |
| `pagos.ReservasPorCobrar` | reservas | HU-13: obtener la reserva vigente con su monto y confirmarla |
| `pagos.ReservasLiquidables` | reservas | HU-19 |
| `pagos.ReclamosAbiertos` | administracion | RN-12 |

## 4. Recursos y pruebas

```
src/main/resources/
├── application.yml            solo ${VARIABLES}; nada de secretos
├── application-dev.yml        PasarelaSimulada, correo a consola, archivos locales
├── application-uat.yml        pasarela sandbox, SES, S3, RDS
├── db/migration/              Flyway, numerado por sprint para no chocar entre 6 personas:
│   ├── V1_1__usuarios_y_tokens.sql
│   ├── V2_1__espacios_y_horarios.sql
│   ├── V3_1__reservas.sql     (índice único parcial del bloque activo)
│   ├── V4_1__pagos_y_reembolsos.sql
│   └── V5_1__cuenta_bancaria_y_liquidacion.sql …
└── templates/correo/          recordatorio.html, pago-confirmado.html, cancelacion.html…

src/test/java/pe/edu/ulima/espacioya/
├── ArquitecturaTest.java      ArchUnit: controller ↛ repository; sin ciclos entre módulos;
│                              domain ↛ spring-web; comun ↛ módulos; nadie usa repositorios de otro módulo
├── <modulo>/service/…Test     unitarias con Mockito: al menos una por CA,
│                              p. ej. @DisplayName("HU-13 CA3: pago rechazado…")
└── <modulo>/…IT               integración con Testcontainers PostgreSQL (webhook, doble reserva)
```

`ArquitecturaTest` es lo que hace cumplir las reglas 1, 2 y 5 de `AGENTS.md` sin depender de que todos las recuerden. También es buena evidencia para el informe.

## 5. Desvíos del `AGENTS.md` que hay que acordar en equipo

1. **`pagos/pasarela/`** es un sexto subpaquete, además de los cinco que fija la regla 4. `arquitectura-propuesta.md` §4.2 permite "adaptadores en el módulo", pero si se prefiere no desviarse, puede ir en `pagos/service/pasarela/`. Lo mismo pasa con los subpaquetes dentro de `service/` (`puertos`, `politica`, `filtros`, `comando`, `iterador`).
2. **Las interfaces en `puertos/`** no aparecen en la arquitectura propuesta y cambian cómo se comunican los módulos. Conviene agregarlas al §4.2 de `arquitectura-propuesta.md` y al informe.
3. **Solo los servicios públicos llevan interfaz** (`CuentaService`, `ReservaService`, `PagoService`). Los internos son clases concretas, para no crear interfaces por crear (YAGNI).

## 6. Fundamentos de diseño

- **DIP (inversión de dependencias):** base de las interfaces en `puertos/`. Los módulos de negocio dependen de abstracciones, no de otros módulos concretos.
- **Factory Method:** el creador concreto (una subclase) decide qué producto crea. Por eso se proponen `FabricaPasarelaSandbox` y `FabricaPasarelaSimulada`, y no un `@Bean` con un `if`, que se parecería más a una Simple Factory.
- **Adapter y DAO:** la pasarela, el correo y el almacenamiento van detrás de interfaces propias. Spring Data JPA cumple el rol de DAO en toda la persistencia.
- **Núcleo separado de la API:** se propone un solo módulo Maven con ArchUnit, no `nucleo.jar` + `api.jar`. Con un solo cliente web, separar en varios artefactos sería complejidad innecesaria, y ArchUnit ya garantiza que el núcleo no dependa de la web.
