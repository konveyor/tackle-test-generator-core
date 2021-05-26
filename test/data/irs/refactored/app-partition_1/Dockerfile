# Build Image
FROM maven:3.6-slim as build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:resolve

COPY . .
RUN mvn package

# Production Image
FROM websphere-liberty
COPY --from=build /app/target/app-partition_1.war /config/dropins/app.war
