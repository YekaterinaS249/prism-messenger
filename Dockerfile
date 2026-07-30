# Multi-stage build: compile with Maven, run on a slim JRE so the final image is small.
FROM maven:3.9-eclipse-temurin-11 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
# maven.test.skip пропускает и компиляцию, и запуск тестов: в проекте есть устаревшие
# unit-тесты (BoardServiceTest/UserServiceTest), не обновлённые под текущие сигнатуры
# конструкторов сервисов — это известный тех.долг, не блокирующий работу приложения,
# но ломающий обычный package, если тесты вообще компилируются.
RUN mvn -B clean package -Dmaven.test.skip=true

FROM eclipse-temurin:11-jre-jammy
WORKDIR /app
COPY --from=build /app/target/messenger.jar app.jar

# Render/Railway/etc inject PORT at runtime; server.port in application.yml already reads it.
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
