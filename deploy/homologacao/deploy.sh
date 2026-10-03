#!/usr/bin/env bash
#
# Publica o servidor CronoSync na VPS de homologação (https://homologacao.focussync.com.br).
# Roda no SEU computador: compila aqui e envia por SSH só o resultado.
#
#   deploy/homologacao/deploy.sh          compila, envia e (re)sobe o servidor na VPS
#   deploy/homologacao/deploy.sh logs     log do servidor na VPS, ao vivo (Ctrl+C só sai do log)
#   deploy/homologacao/deploy.sh status   contêineres rodando na VPS
#
# Antes da primeira vez: crie deploy/homologacao/deploy.env (copie o deploy.env.example).
#
# Por que compilar aqui e não na VPS: o módulo :shared também é uma biblioteca Android, e compilar
# exigiria o Android SDK lá. O rsync envia só os arquivos que mudaram (normalmente, poucos KB).
set -euo pipefail
cd "$(dirname "$0")/../.."

CONFIG=deploy/homologacao/deploy.env
[[ -f "$CONFIG" ]] || { echo "Falta $CONFIG (copie deploy/homologacao/deploy.env.example)."; exit 1; }
# shellcheck source=/dev/null
source "$CONFIG"
: "${CRONOSYNC_SSH:?defina CRONOSYNC_SSH em $CONFIG}"
REMOTE_DIR="${CRONOSYNC_REMOTE_DIR:-cronosync-homologacao}"
PUBLIC_URL="https://homologacao.focussync.com.br"

# Uma conexão SSH só, reaproveitada por todos os comandos (ControlMaster). O firewall da VPS
# (ufw "LIMIT") bloqueia por alguns segundos quem abre mais de 6 conexões em 30 s.
SSH=(ssh -o ControlMaster=auto -o "ControlPath=$HOME/.ssh/cronosync-%C" -o ControlPersist=60)
remote() { "${SSH[@]}" "$CRONOSYNC_SSH" "cd '$REMOTE_DIR' && $*"; }
# "docker compose" (plugin) ou "docker-compose" (programa separado): usa o que a VPS tiver.
COMPOSE='$(docker compose version >/dev/null 2>&1 && echo "docker compose" || echo docker-compose)'

case "${1:-up}" in
  logs) remote "$COMPOSE" logs -f --tail 100 server; exit ;;
  status) remote "$COMPOSE" ps; exit ;;
  up) ;;
  *) echo "Uso: deploy/homologacao/deploy.sh [up|logs|status]"; exit 1 ;;
esac

echo "==> Compilando o servidor..."
./gradlew -q :server:installDist

echo "==> Enviando para $CRONOSYNC_SSH:~/$REMOTE_DIR ..."
"${SSH[@]}" "$CRONOSYNC_SSH" "mkdir -p '$REMOTE_DIR/server/build'"
# --delete: um .jar que saiu do projeto também sai da VPS.
rsync -az -e "${SSH[*]}" --delete server/build/install/ "$CRONOSYNC_SSH:$REMOTE_DIR/server/build/install/"
rsync -az -e "${SSH[*]}" server/Dockerfile "$CRONOSYNC_SSH:$REMOTE_DIR/server/Dockerfile"
rsync -az -e "${SSH[*]}" deploy/homologacao/docker-compose.yml "$CRONOSYNC_SSH:$REMOTE_DIR/docker-compose.yml"

# Senha do banco: sorteada NA VPS na primeira vez e guardada só lá (o arquivo só o dono lê).
remote 'test -f .env || (umask 077 && echo "POSTGRES_PASSWORD=$(openssl rand -hex 24)" > .env && echo "    senha do banco criada em ~/'"$REMOTE_DIR"'/.env")'

echo "==> Subindo banco + servidor na VPS..."
# --build: gera a imagem com o que acabou de chegar; --wait: só volta quando estiver de pé.
remote "$COMPOSE" up -d --build --wait

# -f: um erro (ex.: 502 do nginx) conta como falha, não como "respondeu".
if curl -fs --max-time 10 -o /dev/null "$PUBLIC_URL/"; then
  echo "    pronto: $PUBLIC_URL"
else
  echo "    o servidor subiu na VPS, mas $PUBLIC_URL não respondeu (DNS, nginx ou certificado?)."
  echo "    log: deploy/homologacao/deploy.sh logs"
fi
