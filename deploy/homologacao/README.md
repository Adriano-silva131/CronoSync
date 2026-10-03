# Homologação — homologacao.focussync.com.br

Servidor de testes publicado na internet, para testar os apps fora da rede de casa.

```
App ──wss://homologacao.focussync.com.br──▶ nginx (VPS, HTTPS) ──▶ 127.0.0.1:8095 CronoSync (Docker) ──▶ PostgreSQL (Docker)
```

| Arquivo | Para quê |
|---|---|
| `docker-compose.yml` | Banco + servidor na VPS (sem portas abertas para a internet) |
| `nginx/homologacao.focussync.com.br.conf` | Site do nginx: HTTPS, WebSocket e IP real dos aparelhos |
| `deploy.sh` | Roda no seu computador: compila, envia por SSH e sobe na VPS |
| `deploy.env.example` | Modelo do `deploy.env` (endereço SSH da VPS) |

## Primeira instalação (uma vez só)

1. **DNS** — no painel do domínio, crie um registro **A**: nome `homologacao`, valor = IP da VPS.
   Confira com `dig +short homologacao.focussync.com.br` (pode levar alguns minutos).

2. **nginx + certificado** — na VPS:
   ```bash
   sudo apt update && sudo apt install -y nginx certbot python3-certbot-nginx
   # copie o arquivo do site (do seu PC: scp deploy/homologacao/nginx/*.conf usuario@vps:)
   sudo cp homologacao.focussync.com.br.conf /etc/nginx/sites-available/
   sudo ln -s /etc/nginx/sites-available/homologacao.focussync.com.br.conf /etc/nginx/sites-enabled/
   sudo nginx -t && sudo systemctl reload nginx
   sudo certbot --nginx -d homologacao.focussync.com.br   # gera o HTTPS e renova sozinho
   ```

3. **Firewall** — só as portas do nginx (e o SSH) abertas:
   ```bash
   sudo ufw allow OpenSSH && sudo ufw allow 'Nginx Full' && sudo ufw enable
   ```
   A porta 8095 **não** é aberta: só o nginx, na própria VPS, fala com o servidor.

4. **Deploy** — no seu computador:
   ```bash
   cp deploy/homologacao/deploy.env.example deploy/homologacao/deploy.env   # e ajuste o CRONOSYNC_SSH
   deploy/homologacao/deploy.sh
   ```

## Depois disso

- Mudou algo no servidor? `deploy/homologacao/deploy.sh` de novo.
- Log ao vivo: `deploy/homologacao/deploy.sh logs` · contêineres: `deploy/homologacao/deploy.sh status`.
- Apps presos à homologação:
  - Android: `./gradlew :androidApp:assembleHomologacao` → instala como **CronoSync Homolog**, ao lado da versão de testes.
  - Desktop, só rodar: `./gradlew :desktopApp:run -Pcronosync.environment=homologacao`.
  - Linux (.deb): `./gradlew :desktopApp:packageDeb -Pcronosync.environment=homologacao` → instala como
    **CronoSync-Homolog** (`/opt/cronosync-homologacao`), ao lado da versão normal.
  - Windows (.msi, gerado num Windows): `gradlew.bat :desktopApp:packageMsi -Pcronosync.environment=homologacao`.
- A senha do banco fica só na VPS, em `~/cronosync-homologacao/.env` (criada no primeiro deploy).
