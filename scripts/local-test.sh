#!/usr/bin/env bash
#
# Ambiente de testes local do CronoSync: tudo conectado ao servidor em localhost:8080.
#
#   scripts/local-test.sh            servidor + app desktop (+ instala no celular, se estiver no USB)
#   scripts/local-test.sh --no-desktop    só servidor (+ celular)
#
# O celular só precisa do cabo (ou da depuração por Wi-Fi) para INSTALAR: a versão de testes
# conecta no servidor deste computador pelo Wi-Fi (mesma rede). Ctrl+C encerra tudo.
# Página web de teste: http://localhost:8080/dev/ (no celular: http://<IP deste PC>:8080/dev/)
set -euo pipefail
cd "$(dirname "$0")/.."

RUN_DESKTOP=true
[[ "${1:-}" == "--no-desktop" ]] && RUN_DESKTOP=false

ADB="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
PHONE=""
if [[ -x "$ADB" ]]; then
  PHONE=$("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')
fi

# O servidor do Docker (scripts/server.sh) roda código já compilado e ocuparia a porta 8080:
# este script roda o servidor fora do Docker, com o código atual, então para o de lá.
if [[ -n "$(docker compose ps -q --status running server 2>/dev/null)" ]]; then
  echo "==> Parando o servidor do Docker (para religar depois: scripts/server.sh)..."
  docker compose stop server >/dev/null
fi

# Outro servidor na porta 8080 (ex.: um ":server:run" antigo) responderia no lugar do novo, que nem
# conseguiria subir — e os testes rodariam contra código velho sem ninguém perceber.
if curl -s --max-time 1 -o /dev/null http://localhost:8080/; then
  echo "A porta 8080 já está em uso (provavelmente um servidor do CronoSync rodando)."
  echo "Pare esse servidor (Stop no Android Studio ou Ctrl+C no terminal dele) e rode de novo."
  exit 1
fi

# Banco das salas (docker-compose.yml). --wait: só segue quando o Postgres aceitar conexões.
# Ele continua rodando depois do Ctrl+C: as salas sobrevivem entre uma sessão de testes e outra.
echo "==> Subindo o banco de dados (PostgreSQL no Docker, porta 5434)..."
docker compose up -d --wait postgres >/dev/null || { echo "Não foi possível subir o banco. O Docker está rodando?"; exit 1; }

echo "==> Compilando servidor, desktop${PHONE:+ e Android}..."
TASKS=(:server:installDist :desktopApp:classes)
[[ -n "$PHONE" ]] && TASKS+=(:androidApp:assembleDebug)
./gradlew -q "${TASKS[@]}"

# Servidor a partir da cópia instalada (build/install): recompilar o projeto com o servidor
# no ar não troca as classes por baixo dele. O modo de desenvolvimento habilita a página /dev/.
echo "==> Subindo o servidor em localhost:8080..."
SERVER_OPTS="-Dio.ktor.development=true" server/build/install/server/bin/server &
SERVER_PID=$!
PIDS=("$SERVER_PID")
cleanup() {
  echo
  echo "==> Encerrando..."
  kill "${PIDS[@]}" 2>/dev/null || true
  wait 2>/dev/null || true
}
trap cleanup EXIT INT TERM

for _ in $(seq 1 60); do
  curl -s --max-time 1 -o /dev/null http://localhost:8080/ && break
  sleep 0.5
done
curl -s --max-time 1 -o /dev/null http://localhost:8080/ || { echo "O servidor não subiu."; exit 1; }
echo "    servidor pronto · página de teste: http://localhost:8080/dev/"

if [[ -n "$PHONE" ]]; then
  SERVER_ADDRESS=$(grep -o 'LOCAL_SERVER = "[^"]*"' androidApp/build/generated/source/buildConfig/debug/com/adriano/cronosync/BuildConfig.java 2>/dev/null | cut -d'"' -f2)
  echo "==> Celular $PHONE: instalando (confirme na tela do celular se ele pedir)..."
  echo "    o app vai conectar em $SERVER_ADDRESS pelo Wi-Fi — pode tirar o cabo depois de instalar"
  "$ADB" -s "$PHONE" install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk >/dev/null \
    && "$ADB" -s "$PHONE" shell am start -n com.adriano.cronosync/.MainActivity >/dev/null \
    && echo "    app instalado e aberto no celular" \
    || echo "    não foi possível instalar (a instalação pelo USB foi recusada no celular?)"
else
  echo "==> Nenhum celular conectado pelo USB (pulando o Android)."
fi

if $RUN_DESKTOP; then
  echo "==> Abrindo o app desktop (versão de testes)..."
  ./gradlew -q :desktopApp:run &
  PIDS+=("$!")
fi

echo "==> Tudo no ar. Ctrl+C para encerrar."
wait "$SERVER_PID"
