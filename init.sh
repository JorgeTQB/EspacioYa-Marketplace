#!/usr/bin/env bash
# init.sh — Verificación e inicialización del entorno
#
# Este script lo ejecuta el agente al COMENZAR una sesión y antes de
# declarar cualquier historia como `done`. Si falla, la sesión no debe avanzar.
#
#   ./init.sh            verificación completa (obligatoria antes de `done`)
#   ./init.sh --rapido   sin pruebas de integración ni build del frontend;
#                        sirve para empezar una sesión o redactar un spec,
#                        NUNCA para cerrar una historia
#   ./init.sh --todo     corre TODAS las pruebas unitarias (se combina con
#                        --rapido)
#
# Pruebas unitarias (*Test): por defecto solo las de las clases creadas o
# modificadas en la rama respecto de `develop` (o de BASE_PRUEBAS), más los
# cambios sin commit. Si cambia pom.xml o src/*/resources, corren todas.
# Pruebas de integración (*IT): en el modo completo corren SIEMPRE todas.
#
# Salida esperada: códigos de salida claros y bloques marcados con [OK]/[FAIL].
# En Windows se ejecuta desde Git Bash.

set -u
cd "$(dirname "$0")" || exit 1

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m'

ok()    { printf "${GREEN}[OK]${NC}    %s\n" "$1"; }
warn()  { printf "${YELLOW}[WARN]${NC}  %s\n" "$1"; }
fail()  { printf "${RED}[FAIL]${NC}  %s\n" "$1"; }

RAPIDO=0
TODO=0
for arg in "$@"; do
  case "$arg" in
    --rapido) RAPIDO=1 ;;
    --todo)   TODO=1 ;;
    *) printf "Opción desconocida: %s (usa --rapido y/o --todo)\n" "$arg"; exit 2 ;;
  esac
done

EXIT_CODE=0
BACKEND=2_Backend
FRONTEND=3_frontend
JAVA_MAIN="$BACKEND/src/main/java/pe/edu/ulima/espacioya"
LOG_DIR=$(mktemp -d)

# Candado: dos init.sh a la vez escriben en el mismo 2_Backend/target y se
# pisan los reportes de pruebas. Si hay otro en curso, se espera (máx. 30 min).
# Un candado de más de 60 min se considera abandonado. El hook Stop
# (.claude/hooks/verificar-cierre.sh) no corre si el candado existe.
LOCK=.init.lock
lock_vigente() { [ -f "$LOCK" ] && [ -z "$(find "$LOCK" -mmin +60 2>/dev/null)" ]; }
if lock_vigente; then
  warn "Hay otro ./init.sh en curso; espero a que termine (máx. 30 min)..."
  ESPERA=0
  while lock_vigente && [ "$ESPERA" -lt 1800 ]; do sleep 5; ESPERA=$((ESPERA + 5)); done
fi
echo $$ > "$LOCK"
trap 'rm -rf "$LOG_DIR"; [ "$(cat "$LOCK" 2>/dev/null)" = "$$" ] && rm -f "$LOCK"' EXIT

echo "── 1. Verificando entorno ─────────────────────────────"

# Java 25 (lo fija 2_Backend/pom.xml)
if ! command -v java >/dev/null 2>&1; then
  fail "java no está instalado (se requiere JDK 25)"
  exit 1
fi
JAVA_MAJOR=$(java -XshowSettings:properties -version 2>&1 | sed -n 's/.*java\.specification\.version = //p' | tr -d '\r')
if [ "${JAVA_MAJOR:-0}" -lt 25 ] 2>/dev/null; then
  fail "Se requiere Java >= 25 (encontrado: ${JAVA_MAJOR:-desconocido})"
  exit 1
fi
ok "java -> $JAVA_MAJOR"

if [ ! -f "$BACKEND/mvnw" ]; then
  fail "Falta el Maven Wrapper ($BACKEND/mvnw)"
  exit 1
fi
ok "Maven Wrapper presente"

# Node (frontend con Vite 8 y validación de este script)
if ! command -v node >/dev/null 2>&1 || ! command -v npm >/dev/null 2>&1; then
  fail "node y npm son obligatorios (Node >= 20.19)"
  exit 1
fi
if ! node -e 'const [a,b]=process.versions.node.split(".").map(Number); process.exit(a>20||(a===20&&b>=19)?0:1)'; then
  fail "Se requiere Node >= 20.19 (encontrado: $(node --version))"
  exit 1
fi
ok "node -> $(node --version)"

# Docker (Testcontainers para las pruebas *IT)
DOCKER_OK=0
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  DOCKER_OK=1
  ok "Docker encendido"
elif [ $RAPIDO -eq 1 ]; then
  warn "Docker no responde (en modo rápido no se necesita)"
else
  fail "Docker no responde: enciende Docker Desktop (lo usan las pruebas *IT)"
  EXIT_CODE=1
fi

# Llaves que exige ./mvnw spring-boot:run (README, "2. Backend"). Las pruebas traen las suyas en
# src/test/resources, así que solo se avisa. Mismo criterio que LlaveCifrado; nunca se muestran.
declare -A LLAVES=()
LLAVES_MAL=""
for v in CIFRADO_LLAVE CIFRADO_LLAVE_HUELLA ALMACENAMIENTO_LLAVE_FIRMA; do
  valor=$(printf '%s' "${!v:-}" | tr -d '[:space:]')
  if [[ "$valor" =~ ^[A-Za-z0-9+/]{43}=?$ ]]; then
    LLAVES[$v]=${valor%=}
  else
    LLAVES_MAL="$LLAVES_MAL $v"
  fi
done
if [ -n "$LLAVES_MAL" ]; then
  warn "Faltan o no son de 32 bytes en base64:$LLAVES_MAL. El backend no arrancará; ver README, \"2. Backend\""
elif [ "${LLAVES[CIFRADO_LLAVE]}" = "${LLAVES[CIFRADO_LLAVE_HUELLA]}" ] \
  || [ "${LLAVES[ALMACENAMIENTO_LLAVE_FIRMA]}" = "${LLAVES[CIFRADO_LLAVE]}" ] \
  || [ "${LLAVES[ALMACENAMIENTO_LLAVE_FIRMA]}" = "${LLAVES[CIFRADO_LLAVE_HUELLA]}" ]; then
  warn "Las llaves del backend deben ser distintas entre sí. El backend no arrancará; ver README, \"2. Backend\""
else
  ok "Llaves del backend definidas (CIFRADO_LLAVE, CIFRADO_LLAVE_HUELLA, ALMACENAMIENTO_LLAVE_FIRMA)"
fi

echo ""
echo "── 2. Verificando archivos base del arnés ──────────────"

for f in AGENTS.md CHECKPOINTS.md feature_list.json \
         progress/_plantilla.md progress/history.md \
         docs/architecture.md docs/conventions.md docs/specs.md docs/verification.md \
         docs/requerimientos.md docs/modelo-datos.md docs/frontend.md \
         3_frontend/src/styles/tokens.css \
         .claude/agents/orquestador.md .claude/agents/spec-author.md \
         .claude/agents/implementer.md .claude/agents/reviewer.md; do
  if [ ! -f "$f" ]; then
    fail "Falta archivo base: $f"
    EXIT_CODE=1
  else
    ok "Existe $f"
  fi
done

echo ""
echo "── 3. Validando feature_list.json, progress y specs ───"

node - <<'JS'
const fs = require("fs");
const path = require("path");

const errores = [];
const avisos = [];

function archivosJava(dir) {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(e => {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) return archivosJava(p);
    return e.name.endsWith(".java") ? [p] : [];
  });
}

// R<n> cubiertos por @DisplayName("HU-XX R1, R3: ...") en las pruebas del backend
const cubiertos = {};
for (const f of archivosJava("2_Backend/src/test/java")) {
  const txt = fs.readFileSync(f, "utf8");
  for (const m of txt.matchAll(/@DisplayName\(\s*"(HU-\d{2})((?:[\s,y]+R\d+)+)\s*:/g)) {
    cubiertos[m[1]] ??= new Set();
    for (const r of m[2].match(/R\d+/g)) cubiertos[m[1]].add(r);
  }
}

let data;
try {
  data = JSON.parse(fs.readFileSync("feature_list.json", "utf8"));
} catch (e) {
  console.log(`[FAIL]  feature_list.json no es JSON válido: ${e.message}`);
  process.exit(1);
}

const validos = new Set(data.rules?.valid_status ?? []);
const historias = data.features ?? [];
const porId = new Map(historias.map(h => [h.id, h]));
const enCursoPorDueno = {};

if (porId.size !== historias.length) errores.push("Hay ids de historia repetidos");

for (const h of historias) {
  const id = h.id;
  if (!/^HU-\d{2}$/.test(id ?? "")) { errores.push(`Id inválido: ${id}`); continue; }
  if (!validos.has(h.status)) errores.push(`${id}: estado inválido "${h.status}"`);
  if (!h.owner) errores.push(`${id}: no tiene dueño (owner)`);
  if (!fs.existsSync(`docs/historias/${id}.md`)) errores.push(`${id}: falta docs/historias/${id}.md`);
  for (const d of h.depends_on ?? []) {
    if (!porId.has(d)) errores.push(`${id}: depende de ${d}, que no existe`);
  }

  if (h.status === "in_progress") {
    (enCursoPorDueno[h.owner] ??= []).push(id);
    const sinTerminar = (h.depends_on ?? []).filter(d => porId.get(d)?.status !== "done");
    if (sinTerminar.length) avisos.push(`${id} está in_progress con dependencias sin terminar: ${sinTerminar.join(", ")}`);
  }
  if (["in_progress", "blocked"].includes(h.status) && !fs.existsSync(`progress/${id}.md`)) {
    errores.push(`${id} en ${h.status} sin progress/${id}.md`);
  }

  if (!h.sdd || !["spec_ready", "in_progress", "done"].includes(h.status)) continue;

  const dir = `specs/${id}`;
  const faltan = ["requirements.md", "design.md", "tasks.md"].filter(f => !fs.existsSync(`${dir}/${f}`));
  if (faltan.length) { errores.push(`${id} en ${h.status} sin ${faltan.map(f => `${dir}/${f}`).join(", ")}`); continue; }

  if (h.status === "done") {
    const tasks = fs.readFileSync(`${dir}/tasks.md`, "utf8");
    const abiertas = (tasks.match(/^\s*- \[ \]/gm) ?? []).length;
    if (abiertas) errores.push(`${id} está done con ${abiertas} task(s) sin marcar en ${dir}/tasks.md`);
  }

  if (h.status === "in_progress" || h.status === "done") {
    const reqs = fs.readFileSync(`${dir}/requirements.md`, "utf8");
    const rs = [...reqs.matchAll(/^##\s+(R\d+)\b/gm)].map(m => m[1]);
    const sinTest = rs.filter(r => !cubiertos[id]?.has(r));
    if (sinTest.length) {
      const msg = `${id}: sin @DisplayName("${id} R<n>: ...") para ${sinTest.join(", ")}`;
      (h.status === "done" ? errores : avisos).push(msg);
    }
  }
}

for (const [dueno, ids] of Object.entries(enCursoPorDueno)) {
  if (ids.length > 1) errores.push(`${dueno} tiene ${ids.length} historias in_progress (${ids.join(", ")}); máximo 1 por dueño`);
}

for (const a of avisos) console.log(`[WARN]  ${a}`);
for (const e of errores) console.log(`[FAIL]  ${e}`);
if (errores.length) process.exit(1);

const conteo = {};
for (const h of historias) conteo[h.status] = (conteo[h.status] ?? 0) + 1;
const resumen = Object.entries(conteo).map(([s, n]) => `${n} ${s}`).join(", ");
console.log(`[OK]    feature_list.json válido (${historias.length} historias: ${resumen})`);
console.log("[OK]    Specs, progress y tasks coherentes con el estado de cada historia");
JS

if [ $? -ne 0 ]; then EXIT_CODE=1; fi

echo ""
echo "── 4. Reglas de arquitectura y convenciones ────────────"

# Reporta un fallo si el comando de búsqueda encuentra algo.
regla() {
  local descripcion="$1"; shift
  local hallazgos
  hallazgos=$("$@" 2>/dev/null)
  if [ -n "$hallazgos" ]; then
    fail "$descripcion"
    printf '%s\n' "$hallazgos" | head -10 | sed 's/^/          /'
    EXIT_CODE=1
  else
    ok "Sin ${descripcion,}"
  fi
}

if [ -d "$JAVA_MAIN" ]; then
  modulos_raros=$(find "$JAVA_MAIN" -mindepth 1 -maxdepth 1 -type d \
    | sed 's#.*/##' | grep -vxE 'cuentas|espacios|busqueda|reservas|pagos|administracion|comun')
  if [ -n "$modulos_raros" ]; then
    fail "Paquetes fuera de los módulos de docs/architecture.md: $(echo $modulos_raros)"
    EXIT_CODE=1
  else
    ok "Paquetes Java dentro de los módulos previstos"
  fi

  controladores=$(find "$JAVA_MAIN" -type d -name controller)
  if [ -n "$controladores" ]; then
    regla "Controladores que importan repositorios" \
      grep -rlE '^import pe\.edu\.ulima\.espacioya\.[a-z]+\.repository\.' --include='*.java' $controladores
  fi
  regla "@Autowired en src/main (usar inyección por constructor)" \
    grep -rln '@Autowired' --include='*.java' "$BACKEND/src/main"
fi

if [ -d "$BACKEND/src" ]; then
  regla "Lombok en el backend" \
    grep -rlE 'lombok' --include='*.java' --include='pom.xml' "$BACKEND/src" "$BACKEND/pom.xml"
  regla "Imports con comodín" \
    grep -rnE '^import [a-zA-Z0-9_.]+\.\*;' --include='*.java' "$BACKEND/src"
  regla "Prints de depuración en el backend" \
    grep -rnE 'System\.(out|err)\.print|\.printStackTrace\(' --include='*.java' "$BACKEND/src"
fi

if [ -d "$FRONTEND/src" ]; then
  regla "console.log en el frontend" \
    grep -rn 'console\.log' --include='*.js' --include='*.jsx' "$FRONTEND/src"
  regla "Colores fuera de src/styles/tokens.css (docs/frontend.md)" \
    grep -rnE '#[0-9a-fA-F]{3,8}\b|\b(rgba?|hsla?)\(' --include='*.css' --include='*.js' --include='*.jsx' \
    --exclude='tokens.css' "$FRONTEND/src"
fi

if [ -f "$FRONTEND/package.json" ]; then
  regla "Librerías de componentes o de CSS en el frontend (docs/frontend.md)" \
    grep -nE '"(@mui/[a-z-]+|antd|bootstrap|react-bootstrap|tailwindcss|@chakra-ui/[a-z-]+|styled-components|@emotion/[a-z-]+)"' \
    "$FRONTEND/package.json"
fi

regla "Secretos trackeados en git" \
  sh -c "git ls-files | grep -E '(^|/)\.env(\..+)?$|\.(pem|key|p12|jks)$|application-local\.' | grep -v '\.env\.example$'"

echo ""
echo "── 5. Ejecutando tests del backend ─────────────────────"

# 5a. Qué pruebas unitarias correr: las de las clases tocadas en la rama.
#     FILTRO_TEST vacío = todas; "NingunaUnitaria" = ninguna (solo compila).
FILTRO_TEST=""
if [ $TODO -eq 1 ]; then
  ok "Unitarias: todas (--todo)"
else
  BASE=""
  for rama in "${BASE_PRUEBAS:-develop}" origin/develop; do
    if git rev-parse --verify -q "$rama^{commit}" >/dev/null 2>&1; then
      BASE=$(git merge-base HEAD "$rama" 2>/dev/null) && break
    fi
  done
  if [ -z "$BASE" ]; then
    warn "No se encontró la rama base (${BASE_PRUEBAS:-develop}); se corren todas las unitarias"
  else
    { git -c core.quotepath=off diff --name-only "$BASE" HEAD 2>/dev/null
      git -c core.quotepath=off diff --name-only HEAD 2>/dev/null
      git -c core.quotepath=off ls-files --others --exclude-standard
    } | sort -u > "$LOG_DIR/cambios.txt"

    # Imprime una línea: "TODAS <motivo>", "NINGUNA" o "LISTA A,B,C".
    SELECCION=$(node - "$LOG_DIR/cambios.txt" <<'JS'
const fs = require("fs");
const path = require("path");

const cambios = fs.readFileSync(process.argv[2], "utf8").split(/\r?\n/)
  .filter(f => f.startsWith("2_Backend/"));

// Cambios que pueden afectar a cualquier prueba: corren todas. Las migraciones
// (resources/db/) no cuentan: las unitarias no usan BD y las *IT corren todas.
const global = cambios.find(f =>
  f === "2_Backend/pom.xml" ||
  f.startsWith("2_Backend/.mvn/") ||
  (/^2_Backend\/src\/(main|test)\/resources\//.test(f) && !f.startsWith("2_Backend/src/main/resources/db/")));
if (global) { console.log(`TODAS cambió ${global}`); process.exit(0); }

// Clases Java creadas, modificadas o borradas (main o test).
const clases = [...new Set(cambios
  .filter(f => /^2_Backend\/src\/(main|test)\/java\/.*\.java$/.test(f))
  .map(f => path.basename(f, ".java")))];
if (!clases.length) { console.log("NINGUNA"); process.exit(0); }

function archivosJava(dir) {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(e => {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) return archivosJava(p);
    return e.name.endsWith(".java") ? [p] : [];
  });
}

// Una unitaria corre si ella misma cambió o si menciona una clase cambiada.
const patron = new RegExp(`\\b(${clases.join("|")})\\b`);
const elegidas = archivosJava("2_Backend/src/test/java")
  .filter(f => f.endsWith("Test.java"))
  .filter(f => clases.includes(path.basename(f, ".java")) || patron.test(fs.readFileSync(f, "utf8")))
  .map(f => path.basename(f, ".java"))
  .sort();
console.log(elegidas.length ? `LISTA ${elegidas.join(",")}` : "NINGUNA");
JS
)
    case "$SELECCION" in
      TODAS*)
        ok "Unitarias: todas (${SELECCION#TODAS })" ;;
      LISTA*)
        FILTRO_TEST=${SELECCION#LISTA }
        ok "Unitarias de las clases tocadas ($(printf '%s' "$FILTRO_TEST" | awk -F, '{print NF}')):"
        printf '%s\n' "$FILTRO_TEST" | tr ',' '\n' | sed 's/^/          /' ;;
      NINGUNA)
        FILTRO_TEST=NingunaUnitaria
        ok "Unitarias: ninguna (no hay clases del backend tocadas con pruebas *Test)" ;;
      *)
        warn "No se pudo calcular la selección; se corren todas las unitarias" ;;
    esac
  fi
fi

MAVEN_ARGS=()
if [ -n "$FILTRO_TEST" ]; then
  MAVEN_ARGS=("-Dtest=$FILTRO_TEST" -Dsurefire.failIfNoSpecifiedTests=false)
fi

# 5b. Objetivo: test (solo unitarias) o verify (unitarias + TODAS las *IT).
if [ $RAPIDO -eq 1 ]; then
  OBJETIVO=test
  warn "Modo rápido: sin pruebas de integración (./mvnw test)"
elif [ $DOCKER_OK -eq 1 ]; then
  OBJETIVO=verify
  ok "Integración: todas las pruebas *IT (./mvnw verify)"
else
  OBJETIVO=""
  fail "Sin Docker no se pueden correr las pruebas *IT (./mvnw verify)"
  EXIT_CODE=1
fi

if [ -n "$OBJETIVO" ]; then
  if (cd "$BACKEND" && ./mvnw -B -ntp "$OBJETIVO" "${MAVEN_ARGS[@]}") > "$LOG_DIR/maven.log" 2>&1; then
    grep -E 'Tests run: [0-9]+, Failures' "$LOG_DIR/maven.log" | grep -v ' -- in ' | sed 's/^\[[A-Z]*\] */          /'
    if grep -qE 'Tests run: [1-9]' "$LOG_DIR/maven.log"; then
      ok "Todos los tests del backend pasan (./mvnw $OBJETIVO)"
    elif [ $RAPIDO -eq 1 ] && [ "$FILTRO_TEST" = "NingunaUnitaria" ]; then
      ok "El backend compila; no había unitarias que correr"
    elif [ $RAPIDO -eq 1 ]; then
      warn "./mvnw test no encontró pruebas unitarias todavía"
    else
      fail "./mvnw $OBJETIVO no ejecutó ningún test"
      EXIT_CODE=1
    fi
  else
    grep -E 'Tests run:|FAIL|ERROR' "$LOG_DIR/maven.log" | head -30
    echo "          ... últimas líneas:"
    tail -20 "$LOG_DIR/maven.log"
    fail "Hay tests rotos o el backend no compila (./mvnw $OBJETIVO)"
    EXIT_CODE=1
  fi
fi

echo ""
echo "── 6. Verificando el frontend ──────────────────────────"

if [ ! -d "$FRONTEND/node_modules" ]; then
  echo "          Instalando dependencias (npm ci)..."
  if ! (cd "$FRONTEND" && npm ci --no-audit --no-fund) > "$LOG_DIR/npm-ci.log" 2>&1; then
    tail -20 "$LOG_DIR/npm-ci.log"
    fail "npm ci falló en $FRONTEND"
    EXIT_CODE=1
  fi
fi

if [ -d "$FRONTEND/node_modules" ]; then
  if (cd "$FRONTEND" && npm run lint -- --max-warnings=0) > "$LOG_DIR/lint.log" 2>&1; then
    ok "ESLint sin errores ni advertencias"
  else
    tail -30 "$LOG_DIR/lint.log"
    fail "ESLint encontró problemas (npm run lint)"
    EXIT_CODE=1
  fi

  if [ $RAPIDO -eq 1 ]; then
    warn "Modo rápido: se omite npm run build"
  elif (cd "$FRONTEND" && npm run build) > "$LOG_DIR/build.log" 2>&1; then
    ok "El frontend compila (npm run build)"
  else
    tail -30 "$LOG_DIR/build.log"
    fail "El frontend no compila (npm run build)"
    EXIT_CODE=1
  fi
fi

echo ""
echo "── 7. Resumen ──────────────────────────────────────────"

if [ $EXIT_CODE -ne 0 ]; then
  fail "Entorno NO está listo. Resuelve los errores antes de avanzar."
elif [ $RAPIDO -eq 1 ]; then
  ok "Entorno listo para trabajar."
  warn "Fue una verificación rápida: corre ./init.sh completo antes de marcar una historia como done."
else
  ok "Entorno listo. Puedes empezar a trabajar."
fi

exit $EXIT_CODE
