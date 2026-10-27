#!/usr/bin/env bash
# Hook Stop: corre ./init.sh --rapido cuando Claude termina de responder.
# En verde no dice nada; en rojo muestra los [FAIL] como aviso al usuario.
# No bloquea: la verificación completa (./init.sh) sigue siendo obligatoria
# antes de marcar una historia como done.

RAIZ="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
LOG="${TMPDIR:-/tmp}/espacioya_init.log"

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
