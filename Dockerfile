# =============================
#   STAGE 1 — BUILDER
# =============================
FROM eclipse-temurin:17-jdk AS builder

# Define o diretório de trabalho
WORKDIR /build

# Copia os arquivos de configuração primeiro (para melhor cache)
COPY mvnw pom.xml ./
COPY .mvn .mvn

# Copia o código-fonte
COPY src src

# Instala o jar da fonte
RUN ./mvnw install:install-file \
         -Dfile=src/main/resources/reports/fair_font.jar \
         -DgroupId=local.jasperFontOverrides \
         -DartifactId=local.jasperFontOverrides \
         -Dversion=1.0 \
         -Dpackaging=jar

# Compila e empacota o projeto
RUN ./mvnw clean package -DskipTests

# =============================
#   STAGE 2 — FINAL (opcional)
# =============================
# Se você quiser um container que apenas **roda** o JAR:
FROM eclipse-temurin:17-jre AS app

WORKDIR /build

# Copia o jar do builder
COPY --from=builder /build/target/compile_jasper-*.jar /build/app.jar

# Copia os arquivos jasper
COPY --from=builder /build/src/main/resources /build/resources
