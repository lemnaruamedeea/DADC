FROM critoma/linux-u20-dev-security-ism:latest AS builder
WORKDIR /build
RUN apt-get update -qq && apt-get install -y -qq maven openjdk-17-jdk > /dev/null && rm -rf /var/lib/apt/lists/*
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
COPY pom.xml ./
COPY zoom-rmi-api ./zoom-rmi-api/
COPY c04-rmi-server ./c04-rmi-server/
COPY c05-rmi-server ./c05-rmi-server/
COPY c03-mdb-rmi-client ./c03-mdb-rmi-client/
RUN mvn -pl zoom-rmi-api,c03-mdb-rmi-client -am install -DskipTests -q

FROM critoma/linux-u20-dev-security-ism:latest
WORKDIR /opt
ARG TOMEE_VERSION=10.1.3
ENV TOMEE_HOME=/opt/tomee
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

RUN apt-get update -qq && apt-get install -y -qq --no-install-recommends \
    openjdk-17-jre-headless curl unzip ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL -o /opt/tomee.zip \
    "https://archive.apache.org/dist/tomee/tomee-${TOMEE_VERSION}/apache-tomee-${TOMEE_VERSION}-plume.zip" \
    && unzip -q /opt/tomee.zip -d /opt \
    && rm -f /opt/tomee.zip \
    && ln -s /opt/apache-tomee-plume-${TOMEE_VERSION} ${TOMEE_HOME} \
    && chmod -R a+rx /opt/apache-tomee-plume-${TOMEE_VERSION}/bin

RUN rm -rf ${TOMEE_HOME}/webapps/docs ${TOMEE_HOME}/webapps/manager \
    ${TOMEE_HOME}/webapps/host-manager ${TOMEE_HOME}/webapps/ROOT

COPY c03-mdb-rmi-client/tomee/tomee.xml ${TOMEE_HOME}/conf/tomee.xml
COPY c03-mdb-rmi-client/start.sh /start.sh
RUN sed -i 's/\r$//' /start.sh && chmod +x /start.sh

COPY --from=builder /build/c03-mdb-rmi-client/target/c03-mdb.war ${TOMEE_HOME}/webapps/c03.war

EXPOSE 8080
ENV JAVA_OPTS="-Dc04.host=c04 -Dc05.host=c05 -Dc06.url=http://c06:3000 -Dc01.url=http://c01:7000"
CMD ["/bin/bash", "/start.sh"]
