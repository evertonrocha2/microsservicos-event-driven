# Build multi-stage: compila o monorepo e roda apenas o jar deste servico.
#
# Os poms de TODOS os modulos sao copiados porque o pom pai os declara em
# <modules>, e o Maven precisa conseguir ler o reator inteiro. Ja o codigo-fonte
# copiado e so o necessario, e o -pl limita a compilacao a este servico.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY shared-contracts/pom.xml      shared-contracts/
COPY order-service/pom.xml         order-service/
COPY payment-service/pom.xml       payment-service/
COPY inventory-service/pom.xml     inventory-service/
COPY notification-service/pom.xml  notification-service/

COPY shared-contracts/src shared-contracts/src
COPY order-service/src order-service/src

RUN mvn -q -B -pl shared-contracts,order-service -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/order-service/target/order-service-1.0.0.jar app.jar
EXPOSE 9081
ENTRYPOINT ["java", "-jar", "app.jar"]
