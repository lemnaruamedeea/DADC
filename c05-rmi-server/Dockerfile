FROM critoma/linux-u20-dev-security-ism:latest AS builder
WORKDIR /build
RUN apt-get update -qq && apt-get install -y -qq maven openjdk-17-jdk > /dev/null && rm -rf /var/lib/apt/lists/*
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
COPY pom.xml /build/
COPY zoom-rmi-api /build/zoom-rmi-api/
COPY c04-rmi-server /build/c04-rmi-server/
COPY c05-rmi-server /build/c05-rmi-server/
COPY c03-mdb-rmi-client /build/c03-mdb-rmi-client/
RUN mvn -f /build/pom.xml -pl zoom-rmi-api,c05-rmi-server -am install -DskipTests -q

FROM critoma/linux-u20-dev-security-ism:latest
WORKDIR /opt
RUN apt-get update -qq && apt-get install -y -qq curl unzip openjdk-17-jre-headless ca-certificates > /dev/null && rm -rf /var/lib/apt/lists/*
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ARG TOMEE_VERSION=10.1.3
ENV TOMEE_HOME=/opt/tomee
RUN curl -fsSL -o /opt/tomee.zip \
    "https://archive.apache.org/dist/tomee/tomee-${TOMEE_VERSION}/apache-tomee-${TOMEE_VERSION}-webprofile.zip" \
    && unzip -q /opt/tomee.zip -d /opt \
    && rm -f /opt/tomee.zip \
    && ln -s "/opt/apache-tomee-webprofile-${TOMEE_VERSION}" "${TOMEE_HOME}" \
    && chmod -R a+rx "${TOMEE_HOME}/bin"
COPY --from=builder /build/c05-rmi-server/target/c05-rmi.war /opt/tomee/webapps/
EXPOSE 8080 1099
ENV CATALINA_HOME=/opt/tomee
ENV JAVA_OPTS="-Djava.rmi.server.hostname=c05"
CMD ["/opt/tomee/bin/catalina.sh", "run"]
