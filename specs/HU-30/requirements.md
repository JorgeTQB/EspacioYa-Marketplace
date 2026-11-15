# HU-30 · Requirements — Límite de intentos compartido entre réplicas

> Fuente: [`docs/historias/HU-30.md`](../../docs/historias/HU-30.md) (CA1–CA5 y STRIDE). Depende de HU-03
> (`comun.limite.LimitadorIntentos` con `superaLimite`, `registrar`, `limpiar`). Historia técnica (épica T): no tiene
> fila con RF en la matriz §7 de `docs/requerimientos.md`; implementa **sin cambiar cifras** RNF-SEG-17 (rate limiting)
> y los pendientes resueltos §9.7 y §9.9 (registro por IP, MFA, catálogo por IP: ventana deslizante).
>
> Glosario:
> - *el limitador* = el bean de Spring `LimitadorIntentos` (API pública sin cambios: `intentar`, `superaLimite`,
>   `registrar`, `limpiar`).
> - *el conteo compartido* = `ConteoIntentosPostgres`, la implementación respaldada por la tabla `intento_limite`.
> - *el conteo en memoria* = `ConteoIntentosMemoria`, el comportamiento actual (`ConcurrentHashMap`) movido tal cual.
> - *una instancia* = un `LimitadorIntentos` con su propio `ConteoIntentosPostgres` y su propio pool de conexiones
>   contra la misma base (simula una réplica en las pruebas).
> - *ahora* = `Instant.now(reloj)` del bean `Clock`, truncado a microsegundos (precisión de `TIMESTAMPTZ`).
> - *vigente* = intento cuyo instante es **estrictamente posterior** a `ahora − ventana`.
> - *la retención de registro* = constante `ConteoIntentosPostgres.RETENCION_REGISTRO` = 1 h (la ventana más larga de
>   RNF-SEG-17).
> - *la huella* = `HuellaCampos.calcular(clave)` (HMAC-SHA256 hex con `espacioya.cifrado.llave-huella`).
> - *la limpieza* = `ConteoIntentosPostgres.eliminarVencidos()`; *el proceso de limpieza* = `ProgramacionLimpiezaIntentos`.

## Conteo compartido entre réplicas

## R1
El sistema DEBE registrar el bean `LimitadorIntentos` de la aplicación sobre el conteo compartido, de modo que cada
intento aceptado o registrado quede como fila de `intento_limite`.

## R2
CUANDO se registran fallos con `registrar(clave)` para la misma clave en dos instancias distintas, el sistema DEBE
devolver `true` en `superaLimite(clave, maximo, ventana)` en cualquiera de las dos en cuanto la suma de intentos
vigentes de ambas alcanza `maximo` (p. ej. 3 en una y 2 en la otra con máximo 5 y 15 min).

## R3
CUANDO se llama a `intentar(clave, maximo, ventana)` en una instancia y la clave ya tiene `maximo` intentos vigentes
aceptados en otra instancia, el sistema DEBE devolver `false`.

## R4
CUANDO se llama a `limpiar(clave)` en una instancia, el sistema DEBE eliminar todos los intentos de esa clave de modo
que `superaLimite` devuelva `false` en todas las instancias.

## R5
CUANDO una instancia limpia la clave `login|<correo>` cuyos fallos registró **solo otra** instancia (lo que hace
`RecuperacionContrasenaService.restablecer` o un login correcto atendido por la otra réplica), el sistema DEBE
devolver `false` en `superaLimite` para esa clave en la instancia que registró los fallos.

## Semántica conservada (ventana deslizante)

## R6
El sistema DEBE contar como vigentes, en `intentar` y en `superaLimite`, solo los intentos cuyo instante es
estrictamente posterior a `ahora − ventana`; un intento exactamente en el borde no cuenta.

## R7
CUANDO llega un intento para una clave cuyos intentos guardados están todos vencidos, el sistema DEBE contarlo desde
cero: `intentar` lo acepta y la clave queda con un solo intento vigente.

## R8
CUANDO `intentar` acepta un intento, el sistema DEBE añadir a la clave el instante *ahora* y descartar de ella los
instantes no vigentes según la ventana recibida.

## R9
SI `intentar` rechaza un intento por haber `maximo` vigentes ENTONCES el sistema NO DEBE registrarlo ni alargar el
bloqueo.

## R10
SI `intentar` recibe `maximo` menor que 1 ENTONCES el sistema DEBE devolver `false` sin escribir en `intento_limite`.

## R11
El sistema DEBE tomar el instante de cada intento y el límite de la ventana del bean `Clock` de Spring, no del reloj
de PostgreSQL.

## R12
El sistema DEBE contar cada clave por separado: los intentos de una clave no cuentan para otra.

## R13
SI `superaLimite` recibe una ventana mayor que la retención de registro ENTONCES el sistema DEBE lanzar
`IllegalArgumentException` sin consultar la base.

## R14
CUANDO se llama a `registrar(clave)`, el sistema DEBE añadir el instante *ahora* y descartar de la clave los instantes
anteriores o iguales a `ahora − retención de registro`.

## R15
El sistema DEBE conservar el constructor público `LimitadorIntentos(Clock)` con el comportamiento actual en memoria y
las firmas de `intentar`, `superaLimite`, `registrar` y `limpiar`, de modo que los servicios y pruebas que lo usan
compilan y pasan sin cambios.

## R16
MIENTRAS la aplicación corre sobre el conteo compartido, el sistema DEBE responder a los límites existentes con los
mismos máximos, ventanas y respuestas de hoy (429 con `ErrorDTO.MENSAJE_DEMASIADOS_INTENTOS` /
`DemasiadosIntentosException`, y la recuperación sin error con `LIMITE_SUPERADO`), incluido el límite por IP del
catálogo en la aplicación.

## Atomicidad entre réplicas (STRIDE S/D)

## R17
MIENTRAS llegan llamadas concurrentes a `intentar(clave, maximo, ventana)` para la misma clave desde dos instancias,
el sistema DEBE aceptar como máximo `maximo` menos los intentos vigentes previos, y aceptar exactamente ese número si
hay al menos tantas llamadas.

## R18
MIENTRAS llegan llamadas concurrentes a `registrar(clave)` para la misma clave desde dos instancias, el sistema DEBE
guardar un instante por cada llamada, sin perder ninguno.

## R19
CUANDO el limitador escribe (`intentar`, `registrar`, `limpiar`), el sistema DEBE confirmar la escritura en una
transacción propia, de modo que el intento sigue contado aunque la transacción del llamador se revierta.

## R20
SI la base de datos falla durante `intentar` ENTONCES el sistema DEBE propagar la excepción y NO DEBE tratar el
intento como aceptado.

## Privacidad de las claves (STRIDE I)

## R21
El sistema DEBE guardar en `intento_limite.clave_hash` la huella de la clave (64 caracteres hexadecimales en
minúscula) y NO DEBE guardar la clave en claro (correo, id de usuario o IP) en ninguna columna.

## R22
CUANDO `intentar` acepta un intento, el sistema DEBE fijar `expira_en` de la clave en el mayor entre su valor anterior
y `ahora + ventana`.

## R23
CUANDO se llama a `registrar(clave)`, el sistema DEBE fijar `expira_en` de la clave en el mayor entre su valor anterior
y `ahora + retención de registro`.

## Limpieza de registros vencidos (CA4)

## R24
CUANDO se ejecuta la limpieza, el sistema DEBE eliminar las filas de `intento_limite` con `expira_en` menor o igual a
*ahora* y conservar las demás.

## R25
SI al ejecutar la limpieza otra réplica tiene la limpieza en curso (bloqueo consultivo de la limpieza tomado)
ENTONCES el sistema DEBE omitir la pasada sin borrar ninguna fila.

## R26
DONDE `espacioya.limite.limpieza.activa` vale `true`, el sistema DEBE registrar el proceso de limpieza, que ejecuta la
limpieza con retraso fijo `espacioya.limite.limpieza.intervalo`.

## R27
MIENTRAS `espacioya.limite.limpieza.activa` vale `false`, el sistema NO DEBE registrar el proceso de limpieza.

## Trazabilidad con la historia

| Origen (docs/historias/HU-30.md) | Cubierto por |
|----------------------------------|--------------|
| CA1: el bloqueo se aplica al máximo sumando ambas réplicas, no el doble | R1, R2, R3, R17 |
| CA2: limpiar en una réplica deja de contar en la otra | R4, R5 |
| CA3: ventana vencida → se cuenta desde cero, igual que hoy | R6, R7, R8, R11 |
| CA4: un proceso programado elimina los vencidos sin duplicar trabajo entre réplicas | R24, R25, R26, R27 |
| CA5: servicios sin cambios de código ni de comportamiento (máximos, ventanas, 429) | R9, R10, R12, R13, R14, R15, R16, R19 |
| (S/D) conteo atómico en la BD, sin carreras entre réplicas | R17, R18, R20 |
| (I) claves con correo no legibles más allá de su ventana; evaluar hash | R21, R22, R23, R24 (decisión D4 de `design.md`) |
| (D) límite por IP del catálogo complementado por regla WAF (infraestructura) | R16 (límite en la aplicación); nota de despliegue en `design.md` § 12, sin código |
