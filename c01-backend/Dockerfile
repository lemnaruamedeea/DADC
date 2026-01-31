FROM critoma/linux-u20-dev-security-ism:latest AS builder
WORKDIR /build
RUN apt-get update -qq && apt-get install -y -qq maven openjdk-17-jdk > /dev/null && rm -rf /var/lib/apt/lists/*
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
COPY c01-backend/ ./
RUN mvn package -DskipTests -q

FROM critoma/linux-u20-dev-security-ism:latest
RUN apt-get update -qq && apt-get install -y -qq openjdk-17-jre-headless && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder /build/target/c01-backend-1.0-all.jar ./app.jar
EXPOSE 7000
CMD ["java", "-Dport=7000", "-jar", "app.jar"]
