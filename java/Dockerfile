FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml ./

RUN mvn -q -B dependency:go-offline

COPY src ./src

RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:17-jre AS runtime

WORKDIR /app

RUN addgroup --system confident && adduser --system --ingroup confident confident

COPY --from=build /app/target/confident-agent-*-all.jar /app/confident-agent.jar
RUN chown -R confident:confident /app

USER confident

CMD ["java", "-jar", "confident-agent.jar"]
