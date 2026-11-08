#!/usr/bin/env bash
# Hook Stop: corre ./init.sh --rapido cuando Claude termina de responder.
# En verde no dice nada; en rojo muestra los [FAIL] como aviso al usuario.
# No bloquea: la verificación completa (./init.sh) sigue siendo obligatoria
# antes de marcar una historia como done.

RAIZ="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
LOG="${TMPDIR:-/tmp}/espacioya_init.log"

# Si ya hay un ./init.sh en curso (p. ej. el completo de un subagente), no se
# lanza otro: los dos escribirían en el mismo 2_Backend/target y se pisarían
# los reportes de pruebas. init.sh crea .init.lock mientras corre; uno de más
# de 60 min se considera abandonado.
LOCK="$RAIZ/.init.lock"
if [ -f "$LOCK" ] && [ -z "$(find "$LOCK" -mmin +60 2>/dev/null)" ]; then
  exit 0
fi

if "$RAIZ/init.sh" --rapido > "$LOG" 2>&1; then
  exit 0
fi

node -e '
const log = process.argv[1];
const fallos = require("fs").readFileSync(log, "utf8")
  .replace(/\x1b\[[0-9;]*m/g, "")
  .split("\n").filter(l => l.includes("[FAIL]")).slice(0, 8).join("\n");
console.log(JSON.stringify({
  systemMessage: `[harness] init.sh --rapido FALLÓ (log: ${log}):\n${fallos}`,
}));
' "$LOG"
exit 0
