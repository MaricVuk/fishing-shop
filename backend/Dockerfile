FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package \
    && cp target/fishing-shop-*-SNAPSHOT.jar /build/app.jar

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 --no-create-home shop
WORKDIR /app
COPY --from=build --chown=shop:shop /build/app.jar app.jar
USER shop
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
