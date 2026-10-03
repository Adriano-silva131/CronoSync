#!/usr/bin/env bash
#
# Servidor CronoSync no Docker, em segundo plano (junto com o banco do docker-compose.yml).
#
#   scripts/server.sh          compila o servidor e (re)sobe o contêiner — use depois de mudar
#                              algo em server/ ou shared/
#   scripts/server.sh logs     mostra o log do servidor ao vivo (Ctrl+C só sai do log)
#   scripts/server.sh stop     para o servidor (o banco continua no ar)
#
# O contêiner volta sozinho quando o computador reinicia, até você rodar "stop".
# Página de teste: http://localhost:8080/dev/
set -euo pipefail
cd "$(dirname "$0")/.."

PORT="${CRONOSYNC_PORT:-8080}"

case "${1:-up}" in
  logs) exec docker compose logs -f server ;;
  stop) exec docker compose stop server ;;
  up) ;;
  *) echo "Uso: scripts/server.sh [up|logs|stop]"; exit 1 ;;
esac

echo "==> Compilando o servidor..."
./gradlew -q :server:installDist

# --build: gera a imagem de novo com o que acabou de ser compilado e troca o contêiner antigo.
echo "==> Subindo banco + servidor no Docker..."
docker compose up -d --build --wait

for _ in $(seq 1 60); do
  curl -s --max-time 1 -o /dev/null "http://localhost:$PORT/" && break
  sleep 0.5
done
if curl -s --max-time 1 -o /dev/null "http://localhost:$PORT/"; then
  echo "    servidor pronto em localhost:$PORT · página de teste: http://localhost:$PORT/dev/"
else
  echo "O servidor não respondeu. Veja o log: scripts/server.sh logs"
  exit 1
fi
