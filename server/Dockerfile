# Imagem do servidor CronoSync. Não compila nada aqui dentro: o módulo :shared também é uma
# biblioteca Android, e compilar no Docker exigiria o Android SDK na imagem. Por isso o Gradle
# compila no computador (:server:installDist) e esta imagem só copia o resultado pronto.
#
# Não rode "docker build" direto: use scripts/server.sh, que compila antes.
FROM eclipse-temurin:21-jre

# Roda com um usuário sem privilégios, não como root: se alguém explorasse uma falha no servidor,
# não ganharia poder de administrador dentro do contêiner.
RUN useradd --system --no-create-home --uid 10001 cronosync

COPY build/install/server /app

USER cronosync
EXPOSE 8080
CMD ["/app/bin/server"]
