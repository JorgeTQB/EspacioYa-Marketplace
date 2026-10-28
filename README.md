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
| `5_Test/`            | Pruebas (vacía por ahora; las pruebas del backend viven en `2_Backend/src/test/`) |
| `docs/`              | Historias, requerimientos, arquitectura y convenciones |

Si trabajas con un agente de IA, el punto de entrada es [`AGENTS.md`](AGENTS.md).

## Cómo levantar el proyecto para probarlo

Necesitas JDK 25, Node 20.19 o superior y Docker Desktop encendido. En
Windows, corre los comandos desde Git Bash. Cada pieza va en su propia
terminal, desde la raíz del repo.

### 1. Base de datos (PostgreSQL 17 en Docker)

```bash
docker compose -f 1_Database/compose.yaml up -d
```

Levanta una base vacía en `localhost:5432` (base, usuario y contraseña:
`espacioya`). Las tablas las crea el backend con Flyway al arrancar.

### 2. Backend (Spring Boot, puerto 8080)

**Solo la primera vez: crea tus llaves de cifrado.** El backend cifra datos
sensibles (desde la HU-02) y no arranca sin dos llaves: `CIFRADO_LLAVE` y
`CIFRADO_LLAVE_HUELLA`. Si faltan, el log muestra
`Falta CIFRADO_LLAVE o no es una llave de 32 bytes en base64`. Cada
desarrollador genera las suyas: tu base local es solo tuya, así que no
necesitas las llaves de nadie más. Desde Git Bash:

```bash
echo "export CIFRADO_LLAVE=$(openssl rand -base64 32)" >> ~/.bashrc
echo "export CIFRADO_LLAVE_HUELLA=$(openssl rand -base64 32)" >> ~/.bashrc
source ~/.bashrc
```

Así quedan guardadas en `~/.bashrc` y cada terminal nueva de Git Bash ya las
trae. No las cambies después: lo que ya se cifró en tu base no se podría leer.
Si las pierdes, borra tu base (`down -v`, paso 4) y genera otras. Nunca las
subas al repo.

Luego, cada vez:

```bash
cd 2_Backend
./mvnw spring-boot:run
```

Aparte de las llaves, en local no necesitas `.env`: `application.properties`
ya apunta a la base del paso 1 y permite peticiones desde
`http://localhost:5173`. Está listo cuando el log dice
`Started EspacioYaApplication`.

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

## Cómo trabajar una historia con Claude Code

El repositorio trae un arnés de **Spec Driven Development**: Claude no
programa una historia sin que su dueño apruebe antes un spec.

| Pieza | Archivos |
|-------|----------|
| El repositorio es el sistema | `AGENTS.md`, `init.sh`, `feature_list.json`, `specs/`, `progress/`, `docs/` |
| Orquestación con subagentes | `.claude/agents/`: `orquestador`, `spec_author`, `implementer`, `reviewer` |
| Spec Driven Development | `docs/specs.md`: EARS y aprobación del dueño en `spec_ready` |
| Supervisión | `CHECKPOINTS.md`, hook de `.claude/settings.json`, pruebas en `2_Backend/src/test/` |

### Antes de empezar

Necesitas JDK 25, Node 20.19 o superior y Docker Desktop encendido. En
Windows, corre los comandos desde Git Bash.

```bash
./init.sh            # verificación completa; debe terminar en verde
./init.sh --rapido   # sin pruebas de integración ni build, para empezar rápido
```

### Paso a paso

1. Abre Claude Code en la raíz del repo (`claude`). `CLAUDE.md` lo obliga a
   actuar como **orquestador**: coordina subagentes y no edita código.
2. Dile quién eres, con el nombre que aparece en `owner` de
   `feature_list.json`: **«Soy Jorge Quispe, trabaja mi siguiente historia»**
   (o «trabaja HU-03»).
3. **Fase spec.** El `spec_author` escribe
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
| `specs/HU-XX/requirements.md` | spec_author | Requirements en EARS (`R1`, `R2`…) y trazabilidad con los CA y STRIDE |
| `specs/HU-XX/design.md` | spec_author | Endpoints, clases, migraciones, diagramas y alternativa descartada |
| `specs/HU-XX/tasks.md` | spec_author | Checklist; el implementer la marca `[x]` |
| `progress/HU-XX.md` | orquestador e implementer | Estado vivo de la historia, aprobación y bloqueos |
| `progress/explore_HU-XX.md` | explorador | Código existente que la historia puede reutilizar |
| `progress/impl_HU-XX.md` | implementer | Archivos tocados y mapa `R<n> → test` |
| `progress/review_HU-XX.md` | reviewer | Veredicto contra `docs/`, el spec y `CHECKPOINTS.md` |
| `feature_list.json` | orquestador, spec_author e implementer | Estado: `pending` → `spec_ready` → `in_progress` → `done` |
| `progress/history.md` | implementer | Resumen de cada sesión cerrada (solo se agrega) |

Los subagentes no devuelven su trabajo por el chat: lo dejan en estos
archivos y queda versionado en git.
