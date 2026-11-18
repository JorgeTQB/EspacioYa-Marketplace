# EspacioYa — Marketplace de alquiler de espacios

Marketplace web para alquilar espacios deportivos y para eventos (canchas,
estadios, auditorios) con pago en línea.

- **Roles:** Usuario (arrendatario), Arrendador y Administrador.
- **Alcance:** 28 historias de usuario en 2 releases.
  - **Release 1 (MVP transaccional):** publicar → buscar → reservar → pagar →
    cancelar con reembolso.
  - **Release 2 (confianza y operación):** MFA, cobro del arrendador, perfil y
    datos personales, filtros, reseñas, reclamos y moderación.
- **Contexto:** Proyecto Final de Ingeniería de Software II (Universidad de
  Lima, 2026-2), con Scrum.

## Estructura

| Carpeta              | Contenido |
|----------------------|-----------|
| `1_Database/`        | PostgreSQL 17 local (`compose.yaml`) |
| `2_Backend/`         | API REST en Spring Boot (Java) |
| `3_frontend/`        | SPA en React + Vite |
| `4_Infraestructure/` | Arquitectura de despliegue en AWS (`arquitectura-aws.html`) |
| `docs/`              | Historias, requerimientos, arquitectura y convenciones |

Si trabajas con un agente de IA, el punto de entrada es [`AGENTS.md`](AGENTS.md).

## Cómo levantar el proyecto para probarlo

Necesitas JDK 25, Node 20.19 o superior y Docker Desktop encendido. Cada
pieza va en su propia terminal, desde la raíz del repo.

> **Todos los comandos de este README asumen Git Bash** (viene con
> [Git for Windows](https://git-scm.com/download/win)). No funcionan en
> PowerShell ni en CMD: usan `export`, `read -s`, `~/.bashrc` y `./mvnw`. En
> Linux o macOS sirven tal cual en cualquier terminal bash.

### 1. Base de datos (PostgreSQL 17 en Docker)

```bash
docker compose -f 1_Database/compose.yaml up -d
```

Levanta una base vacía en `localhost:5432` (base, usuario y contraseña:
`espacioya`). Las tablas las crea el backend con Flyway al arrancar.

### 2. Backend (Spring Boot, puerto 8080)

**Solo la primera vez: crea tus llaves.** El backend no arranca sin tres
llaves de 32 bytes en base64, todas distintas:

| Variable | Para qué | Desde |
|---|---|---|
| `CIFRADO_LLAVE` | Cifra datos sensibles en la base | HU-02 |
| `CIFRADO_LLAVE_HUELLA` | Huella para buscar datos cifrados | HU-02 |
| `ALMACENAMIENTO_LLAVE_FIRMA` | Firma las URL de 5 min de documentos y fotos | HU-05 |

Si falta alguna, el log termina con
`Falta <VARIABLE> o no es una llave de 32 bytes en base64`. Cada
desarrollador genera las suyas: tu base local es solo tuya, así que no
necesitas las llaves de nadie más. Desde Git Bash:

```bash
echo "export CIFRADO_LLAVE=$(openssl rand -base64 32)" >> ~/.bashrc
echo "export CIFRADO_LLAVE_HUELLA=$(openssl rand -base64 32)" >> ~/.bashrc
echo "export ALMACENAMIENTO_LLAVE_FIRMA=$(openssl rand -base64 32)" >> ~/.bashrc
echo 'export MSYS2_ENV_CONV_EXCL="CIFRADO_LLAVE;CIFRADO_LLAVE_HUELLA;ALMACENAMIENTO_LLAVE_FIRMA"' >> ~/.bashrc
source ~/.bashrc
```

La cuarta línea es obligatoria en Git Bash: sin ella, una llave que empieza
con `/` (pasa 1 de cada 64 veces) le llega a Java convertida en ruta
(`C:/Program Files/Git/...`) y el backend no arranca con el error
`Falta <VARIABLE> o no es una llave...`, aunque la llave esté bien. Si tus llaves ya estaban en `~/.bashrc`, agrega solo
esa línea.

Así quedan guardadas en `~/.bashrc` y cada terminal nueva de Git Bash ya las
trae. Corre cada línea **una sola vez**: si una variable aparece dos veces en
`~/.bashrc`, vale la última. Para ver cuáles tienes sin mostrar los valores:
`grep -o '^export [A-Z_]*' ~/.bashrc`.

Si ya tenías las dos de cifrado y el backend dejó de arrancar al traer la
HU-05, agrega solo la tercera línea. No cambies las de cifrado: lo que ya se
cifró en tu base no se podría leer. Si las pierdes, borra tu base (`down -v`,
paso 4) y genera otras. La de firma sí puedes regenerarla cuando quieras: solo
invalida las URL de archivos emitidas en los últimos 5 minutos. Nunca subas
ninguna al repo.

**Solo la primera vez: elige la pasarela (HU-13).** Sin `PAGOS_PASARELA` el
backend no arranca: cada entorno debe elegirla de forma explícita. En local usa
la simulada. Mercado Pago todavía no está conectado (D8):

```bash
echo "export PAGOS_PASARELA=simulada" >> ~/.bashrc
source ~/.bashrc
```

Si el log dice que falta un bean de tipo `PasarelaPago`, es que falta
`PAGOS_PASARELA` o tiene un valor distinto de `mercadopago` o `simulada`.

Luego, cada vez:

```bash
cd 2_Backend
./mvnw spring-boot:run
```

Aparte de esas variables, en local no necesitas `.env`:
`application.properties` ya apunta a la base del paso 1 y permite peticiones
desde `http://localhost:5173`. Está listo cuando el log dice
`Started EspacioYaApplication`.

**Cuenta de administrador (HU-06).** No hay registro de admins por la API ni
admins en las migraciones: se crean con un comando interno del backend, que
aplica la política de contraseña, guarda el hash bcrypt, audita
`CREACION_ADMIN` y termina. Con la base levantada, desde la raíz del repo:

```bash
read -r -p "Correo del admin: " ADMIN_CORREO
read -r -s -p "Contraseña del admin: " ADMIN_CONTRASENA; echo
ADMIN_CREAR=true ADMIN_CORREO="$ADMIN_CORREO" ADMIN_CONTRASENA="$ADMIN_CONTRASENA"   ./2_Backend/mvnw -f 2_Backend/pom.xml spring-boot:run -Dspring-boot.run.arguments=--server.port=0
unset ADMIN_CORREO ADMIN_CONTRASENA
```

`read -s` evita que la contraseña quede en el historial de la terminal. El log
dice `Cuenta admin creada` y la aplicación se cierra. Si la contraseña no
cumple la política, el correo no tiene forma de correo o ya existe una cuenta
con ese correo, el comando falla nombrando la variable (nunca su valor) y no
crea ni modifica nada. En UAT se corre igual, como tarea única del contenedor.

**Verificación en dos pasos del admin (HU-17).** En su primer inicio de sesión
el admin creado con el comando anterior debe configurar MFA: tras la
contraseña, la pantalla le muestra una clave (y un enlace `otpauth://` para
abrirla en el celular) que agrega en su app de autenticación (Google o
Microsoft Authenticator, etc.); con el primer código de 6 dígitos entra y
recibe **5 códigos de respaldo**, que se muestran una sola vez. Desde entonces
cada inicio de sesión pide un código de la app o uno de respaldo. El admin no
puede desactivar MFA.

*Si un admin pierde su app y sus 5 códigos de respaldo* (no tiene recuperación
por correo), quien opera la base borra su configuración; sus códigos caen en
cascada y sus sesiones dejan de valer mientras no tenga MFA activo:

```sql
DELETE FROM mfa WHERE usuario_id = (SELECT id FROM usuario WHERE correo = 'admin@ejemplo.pe' AND rol = 'ADMIN');
```

En su siguiente inicio de sesión vuelve a configurar MFA como la primera vez.

**Panel admin e IPs permitidas (HU-06).** Las rutas `/api/admin/**` y el inicio
de sesión del admin solo funcionan desde las IPs de `ADMIN_IPS_PERMITIDAS` (por
defecto `127.0.0.1,::1`, es decir, tu máquina). Desde HU-17 este control
**complementa** a MFA, no lo reemplaza; para aceptar cualquier IP (solo MFA),
`ADMIN_IPS_PERMITIDAS=0.0.0.0/0,::/0`. En UAT:

| Variable | Valor | Si se deja vacía |
|---|---|---|
| `ADMIN_IPS_PERMITIDAS` | IPs o rangos CIDR de quienes administran, separados por comas (p. ej. `190.12.34.56,10.20.0.0/16`) | Nadie entra al panel |
| `PROXIES_CONFIANZA` | Expresión regular con las IPs internas del balanceador (la red de la VPC), p. ej. `10\.0\.\d{1,3}\.\d{1,3}` | La IP del cliente es la de la conexión: detrás del balanceador sería la del balanceador |

El backend solo cree al encabezado `X-Forwarded-For` si la conexión viene de
`PROXIES_CONFIANZA`; así un cliente no puede hacerse pasar por una IP
permitida. Un valor de `ADMIN_IPS_PERMITIDAS` mal escrito impide el arranque
con un error que nombra la variable.

### 3. Frontend (React + Vite, puerto 5173)

La primera vez, crea el `.env` del frontend con la URL de la API:

```bash
cd 3_frontend
cp .env.example .env   # deja VITE_API_URL=http://localhost:8080
npm install
```

Luego, cada vez:

```bash
npm run dev
```

Abre <http://localhost:5173>. Sin el `.env`, el frontend no encuentra la API.

### 4. Apagar

`Ctrl+C` en las terminales del backend y del frontend, y luego:

```bash
docker compose -f 1_Database/compose.yaml down      # conserva los datos
docker compose -f 1_Database/compose.yaml down -v   # borra los datos
```

### Probar cada historia a mano

Además de las pruebas automáticas (`./init.sh`), cada historia `done` se
puede probar así. Agrega aquí la tuya al terminarla.

#### HU-01 — Registro de usuario con consentimiento

Entra a <http://localhost:5173/registro>.

| Prueba | Qué haces | Qué debes ver |
|--------|-----------|---------------|
| CA1 — registro exitoso | Correo nuevo, contraseña válida (p. ej. `clave1234`), marca la casilla de privacidad | «Te enviamos un correo de verificación…». No llega ningún correo: el envío es simulado, y en el log del backend aparece `Envío de correo de verificación simulado` |
| CA2 — sin consentimiento | Igual, pero sin marcar la casilla | Error bajo la casilla; no se crea la cuenta |
| CA3 — contraseña débil | `abc`, `abcdefgh` (sin dígito), `12345678` (sin letra) o igual al correo | Los requisitos mínimos de contraseña |
| CA3 — correo repetido | Registra otra vez un correo ya usado | El mismo mensaje que CA1, sin revelar que la cuenta existe |
| (D) — límite por IP | Envía el formulario 6 veces en menos de una hora | El 6.º intento muestra un error de demasiados intentos (429). Reinicia el backend para limpiar el contador |
| Privacidad | Entra a <http://localhost:5173/privacidad> | La política con su versión (`2026-10`) |

Para comprobar lo que quedó en la base de datos:

```bash
docker exec -it espacioya-postgres psql -U espacioya -d espacioya \
  -c "SELECT id, correo, rol, estado, correo_verificado, version_politica, fecha_consentimiento FROM usuario;" \
  -c "SELECT accion, usuario_id, ip, fecha FROM auditoria ORDER BY fecha;"
```

Debes ver la cuenta con rol `USUARIO`, estado `ACTIVA`, `correo_verificado = t`
y la versión de la política aceptada. En `auditoria` debe aparecer una fila por
cada registro exitoso y por cada intento con un correo ya existente. La contraseña nunca se guarda en claro: solo su hash bcrypt.

Si prefieres probar la API sin el frontend:

```bash
curl -i http://localhost:8080/api/politicas-privacidad/vigente
curl -i -X POST http://localhost:8080/api/usuarios \
  -H "Content-Type: application/json" \
  -d '{"correo":"prueba@ejemplo.com","contrasena":"clave1234","aceptaPrivacidad":true,"versionPolitica":"2026-10"}'
```

#### HU-13 — Pago con tarjeta

**Con la pasarela simulada** (`PAGOS_PASARELA=simulada`, sin cuentas, sin
terminal y sin internet). Reserva un espacio y pulsa **Pagar**: se abre la
pantalla de pago simulado, marcada como simulación. No pide tarjeta y no cobra
nada.

| Prueba | Qué haces | Qué debes ver |
|--------|-----------|---------------|
| CA1 — pago aprobado | **Aprobar pago simulado** | La reserva queda **Confirmada**, con «Tu reserva está confirmada…». En el log, `Envío de comprobante de pago simulado`: el correo aún no se envía (HU-25) |
| CA3 — pago rechazado | **Rechazar pago simulado** | «Tu pago no se aprobó…» y sigue **Pagar**, que vuelve a la misma pantalla |
| D4 — tres rechazos | Rechaza 3 veces | «Alcanzaste el máximo de intentos…», sin **Pagar** |
| D2 — pago tardío | Deja la pantalla abierta hasta que la reserva venza y pulsa **Aprobar** | «Recibimos un pago que no pudimos aplicar a esta reserva…». La reserva no se confirma |
| CA4 — sin pago disponible | Abre la pantalla en el último minuto o con la reserva expirada | «Este pago simulado ya no está disponible.», sin botones |

Un pago con otro monto (`MONTO_DISTINTO`) no se puede producir desde la pantalla:
el cliente no envía el monto. Esa regla la prueba `WebhookPagoIT`.

**Opcional, solo en local: simular por `curl`.** Define `PAGOS_SECRETO_WEBHOOK`
con cualquier valor y reinicia el backend. Primero pulsa **Pagar**, porque esta
ruta toma el monto de la memoria: tras reiniciar el backend responde `503` hasta
que se pulse **Pagar** en una reserva nueva. La pantalla no tiene ese límite.

```bash
RESERVA=7   # el número de la URL /reservas/7
curl -i -X POST "http://localhost:8080/api/pagos/webhook?type=payment&data.id=sim-$RESERVA-APROBADO-1" \
  -H "x-signature: $PAGOS_SECRETO_WEBHOOK"
```

Con otro `x-signature`, o sin secreto configurado, responde `401` sin cuerpo y
nada cambia.

**En UAT**, hasta conectar Mercado Pago (D7 revisada, D8). No hay pasarela
certificada: se demuestran el flujo y las reglas de la aplicación, no la
integración real.

| Variable | Valor en UAT |
|----------|--------------|
| `PAGOS_PASARELA` | `simulada` |
| `PAGOS_SECRETO_WEBHOOK` | **Vacía:** la ruta pública del webhook rechaza todo pago y la pantalla es la única entrada. Si la defines, quien la conozca puede aprobar pagos de cualquier reserva con `curl` |
| `PAGOS_LLAVE_SECRETA`, `PAGOS_URL_NOTIFICACION` | Sin definir |

**Con Mercado Pago de prueba (no conectado todavía).** `MercadoPagoAdapter` está
listo, pero solo se ha probado contra un doble: la firma real sigue sin
comprobar. Hace falta una URL HTTPS pública, porque Mercado Pago no admite
`localhost`: un túnel hacia el puerto 8080, o UAT. Con `FRONTEND_URL` sin HTTPS el
comprador no vuelve solo a la reserva. Se paga con el comprador de prueba y usa
credenciales de prueba. `read -s` evita que queden en el historial:

```bash
read -r -s -p "Llave secreta: " PAGOS_LLAVE_SECRETA; echo
read -r -s -p "Firma secreta: " PAGOS_SECRETO_WEBHOOK; echo
export PAGOS_LLAVE_SECRETA PAGOS_SECRETO_WEBHOOK
export PAGOS_PASARELA=mercadopago
export PAGOS_URL_NOTIFICACION=https://<tu-túnel>/api/pagos/webhook
```

La guía completa está en `progress/impl_HU-13.md`.

## Cómo trabajar una historia con Claude Code

El repositorio trae un arnés de **Spec Driven Development**: Claude no
programa una historia sin que su dueño apruebe antes un spec.

| Pieza | Archivos |
|-------|----------|
| El repositorio es el sistema | `AGENTS.md`, `init.sh`, `feature_list.json`, `specs/`, `progress/`, `docs/` |
| Orquestación con subagentes | `.claude/agents/`: `orquestador`, `spec-author`, `implementer`, `reviewer` |
| Spec Driven Development | `docs/specs.md`: EARS y aprobación del dueño en `spec_ready` |
| Supervisión | `CHECKPOINTS.md`, hook de `.claude/settings.json`, pruebas en `2_Backend/src/test/` |

### Antes de empezar

Necesitas JDK 25, Node 20.19 o superior y Docker Desktop encendido. En
Windows, corre los comandos desde Git Bash.

```bash
./init.sh            # verificación completa; debe terminar en verde
./init.sh --rapido   # sin pruebas de integración ni build, para empezar rápido
./init.sh --todo     # corre todas las unitarias, no solo las de lo que cambió
```

Las pruebas unitarias (`*Test`) corren solo para las clases creadas o
modificadas en la rama respecto de `develop` (otra base con
`BASE_PRUEBAS=<rama>`): las que se llaman igual que la clase o la mencionan.
Si cambia `pom.xml` o un archivo de configuración en `src/*/resources`, corren
todas. Las pruebas de integración (`*IT`) corren siempre todas en el modo
completo.

### Paso a paso

1. Abre Claude Code en la raíz del repo (`claude`). `CLAUDE.md` lo obliga a
   actuar como **orquestador**: coordina subagentes y no edita código.
2. Dile quién eres, con el nombre que aparece en `owner` de
   `feature_list.json`: **«Soy Jorge Quispe, trabaja mi siguiente historia»**
   (o «trabaja HU-03»).
3. **Fase spec.** El `spec-author` escribe
   `specs/HU-XX/{requirements,design,tasks}.md` y la historia pasa a
   `spec_ready`. Claude se detiene.
4. Lee los tres archivos y responde **«aprobado»** o pide cambios. Solo vale
   la aprobación del dueño de la historia.
5. **Fase código.** El `implementer` sigue las tasks marcándolas `[x]` y el
   `reviewer` comprueba que cada `R<n>` tenga su test y que `./init.sh` esté en
   verde. Si el reviewer aprueba, la historia queda `done`.
6. Revisa los cambios, haz commit en tu rama `feature/HU-XX-descripcion-corta`
   y abre el PR hacia `develop`. Claude no hace commit ni push.

Si algo queda `blocked`, la razón y la pregunta para ti están en
`progress/HU-XX.md`.

### Dónde queda cada cosa

| Archivo | Lo escribe | Contiene |
|---------|------------|----------|
| `specs/HU-XX/requirements.md` | spec-author | Requirements en EARS (`R1`, `R2`…) y trazabilidad con los CA y STRIDE |
| `specs/HU-XX/design.md` | spec-author | Endpoints, clases, migraciones, diagramas y alternativa descartada |
| `specs/HU-XX/tasks.md` | spec-author | Checklist; el implementer la marca `[x]` |
| `progress/HU-XX.md` | orquestador e implementer | Estado vivo de la historia, aprobación y bloqueos |
| `progress/explore_HU-XX.md` | explorador | Código existente que la historia puede reutilizar |
| `progress/impl_HU-XX.md` | implementer | Archivos tocados y mapa `R<n> → test` |
| `progress/review_HU-XX.md` | reviewer | Veredicto contra `docs/`, el spec y `CHECKPOINTS.md` |
| `feature_list.json` | orquestador, spec-author e implementer | Estado: `pending` → `spec_ready` → `in_progress` → `done` |
| `progress/history.md` | implementer | Resumen de cada sesión cerrada (solo se agrega) |

Los subagentes no devuelven su trabajo por el chat: lo dejan en estos
archivos y queda versionado en git.
