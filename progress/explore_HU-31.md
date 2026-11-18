# Exploración HU-31 (orquestador)

Pregunta: ¿dónde se decide hoy el fallo simulado del reembolso y de dónde puede salir el número de intento persistido?

Archivos analizados (en `2_Backend/src/main/java/pe/edu/ulima/espacioya/pagos/`):

- `service/pasarela/PasarelaSimulada.java:50` — `llamadasDeReembolso` (`ConcurrentHashMap<String, AtomicInteger>`),
  y `reembolsar` (líneas ~116-128): cuenta llamadas por `claveIdempotencia` y falla si `llamada <= fallosReembolso`
  (propiedad `espacioya.pagos.simulada.fallos-reembolso`, `application.properties:109`, variable
  `PAGOS_SIMULADA_FALLOS_REEMBOLSO`, por defecto 0). Javadoc líneas 30-32 lo describe como contador en memoria.
- `service/pasarela/SolicitudReembolsoPasarela.java:9` — `record (idTransaccion, monto, claveIdempotencia)`; la clave es
  la misma en todos los intentos (D6).
- `service/pasarela/MercadoPagoAdapter.java:153` — envía `claveIdempotencia` como encabezado de idempotencia; es el
  otro implementador del puerto `PasarelaPago`.
- `domain/Reembolso.java:49,108,124,162` — campo `intentos`, se incrementa al empezar un intento (`intentos++`),
  `claveIdempotencia()`, `getIntentos()`.
- `service/reembolso/IntentoReembolso.java:7` — javadoc: "`numero` es el valor de `intentos` tras sumar 1".
- `service/reembolso/RegistroReembolsos.java:58-59` — arma el intento con `reembolso.getIntentos()` y
  `reembolso.claveIdempotencia()`.
- `service/reembolso/SolicitudReembolso.java:49` — construye `SolicitudReembolsoPasarela` con
  `intento.claveIdempotencia()`.
- `repository/ReembolsoRepository.java:21` — serializa los intentos de un mismo reembolso (HU-16 DT-5).

Hallazgo: el número de intento ya está persistido y llega hasta `SolicitudReembolso`; basta con propagarlo en
`SolicitudReembolsoPasarela` y que `PasarelaSimulada` decida con él. Las pruebas existentes de `PasarelaSimulada` y de
reembolsos (buscar `fallos-reembolso`, `PasarelaSimuladaTest`, `*Reembolso*Test`, `*Reembolso*IT`) deben revisarse.
