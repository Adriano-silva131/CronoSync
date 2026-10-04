# CronoSync

[![CI](https://github.com/Adriano-silva131/CronoSync/actions/workflows/ci.yml/badge.svg)](https://github.com/Adriano-silva131/CronoSync/actions/workflows/ci.yml)

*[Read in English](README.md)*

Cronômetro, timer e Pomodoro que **vários aparelhos veem e controlam ao mesmo tempo**. Inicie o
timer no celular, pause no computador: todos os aparelhos da mesma sala mostram o mesmo tempo, em
tempo real.

- **Cronômetro** com voltas · **Timer** com alarme · **Pomodoro** (foco / pausa curta / pausa longa,
  personalizável, a próxima fase começa sozinha)
- **Salas** compartilhadas por um código de 8 caracteres; sem sala, tudo funciona offline num aparelho
- Apps para **Android**, **Linux** e **Windows**, e um servidor próprio

## Como funciona

```
 Android ─┐                         ┌─ PostgreSQL (as salas sobrevivem a reinícios)
 Desktop ─┼── WebSocket (wss://) ── Servidor Ktor
 Desktop ─┘                         └─ relógio do servidor = a única fonte do tempo
```

- O estado da sala é guardado como *"começou em X + tempo acumulado"* no **relógio do servidor**,
  nunca como um número que vai sendo atualizado. Cada aparelho estima a diferença do seu relógio
  para o do servidor, então todas as telas mostram o mesmo tempo sem ninguém mandar "tiques".
- Toda mudança é um comando com **versão esperada**: se dois aparelhos tocarem no mesmo instante, o
  servidor aplica um e recusa o desatualizado, e o aparelho que perdeu desfaz a sua previsão.
- O toque vale **na hora** no aparelho que tocou (previsão otimista) e depois é confirmado pelo
  servidor.

## Estrutura do projeto

| Módulo | O que tem nele |
|---|---|
| `shared` | Kotlin Multiplatform: domínio (cronômetro, timer, Pomodoro), protocolo e cliente de sincronização, ViewModels |
| `sharedUi` | Telas em Compose Multiplatform compartilhadas entre Android e desktop |
| `androidApp` | App Android: alarme em tela cheia, notificações, permissões |
| `desktopApp` | App desktop (Linux/Windows): bandeja do sistema, notificações, instaladores |
| `server` | Servidor Ktor: salas por WebSocket, persistência em PostgreSQL (migrações com Flyway) |
| `deploy/homologacao` | Deploy de homologação: Docker Compose, nginx e script de deploy |

Arquitetura: MVVM com fluxo de dados unidirecional, interface em Jetpack/Compose Multiplatform e
Koin para injeção de dependências. As regras de negócio ficam em `shared/commonMain`, sem
dependências de plataforma.

**Tecnologias:** Kotlin 2.4 · Compose Multiplatform 1.12 · Ktor 3.6 · Koin 4.2 · PostgreSQL 16 ·
Flyway · kotlin-test, kotlinx-coroutines-test, Turbine, Testcontainers.

## Rodando localmente

Requisitos: JDK 17+, Android SDK (Android Studio), Docker.

```bash
scripts/local-test.sh        # banco + servidor + app desktop (+ instala no celular conectado por USB)
scripts/server.sh            # só banco + servidor, no Docker, em segundo plano
./gradlew :desktopApp:run    # app desktop apontando para o servidor local
```

A versão de debug do Android conecta no servidor do seu computador pelo Wi-Fi (o endereço é
detectado no build). Em modo de desenvolvimento há uma página de teste em `http://localhost:8080/dev/`.

## Testes

```bash
./gradlew :shared:jvmTest :server:test :desktopApp:test
```

Os testes com PostgreSQL usam Testcontainers e precisam do Docker rodando.

## Download

Os instaladores do Windows (`.msi`) e do Linux (`.deb`, `.rpm`) ficam na página de [Releases](https://github.com/Adriano-silva131/CronoSync/releases).
Para publicar uma versão nova, envie uma tag de versão; o workflow `Release` testa, gera os instaladores e
cria a Release:

```bash
git tag v1.0.3 && git push origin v1.0.3
```

## Gerando os apps

| Alvo | Comando |
|---|---|
| Android (homologação) | `./gradlew :androidApp:assembleHomologacao` |
| Linux `.deb` | `./gradlew :desktopApp:packageDeb` |
| Windows `.msi` (num Windows) | `gradlew.bat :desktopApp:packageMsi` |

Acrescente `-Pcronosync.environment=homologacao` nos builds do desktop para apontar para o servidor
de homologação. O deploy do servidor está descrito em
[`deploy/homologacao/README.md`](deploy/homologacao/README.md).

## Segurança

- Salas não são criadas sob demanda: um aparelho só entra num código que o servidor gerou.
- Limites por IP na criação de salas, nas tentativas de conexão e nas conexões simultâneas, além de
  um limite de comandos por conexão e um tamanho máximo de mensagem.
- Atrás de um proxy reverso, o IP real só é lido do endereço que o próprio proxy acrescenta
  (`CRONOSYNC_BEHIND_PROXY`), então um cabeçalho `X-Forwarded-For` forjado não escapa dos limites.
- Fora do modo de desenvolvimento, o servidor se recusa a subir sem as credenciais do banco, e o
  contêiner roda com um usuário sem privilégios.

## Licença

[MIT](LICENSE)
