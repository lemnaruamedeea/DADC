FROM critoma/linux-u20-dev-security-ism:latest AS metricsbuilder
WORKDIR /build
RUN apt-get update -qq && apt-get install -y -qq maven openjdk-17-jdk > /dev/null && rm -rf /var/lib/apt/lists/*
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
COPY c02-metrics/ ./c02-metrics/
RUN mvn -f c02-metrics/pom.xml package -DskipTests -q

FROM critoma/linux-u20-dev-security-ism:latest
WORKDIR /opt

ENV LANG=C.UTF-8 LC_ALL=C.UTF-8
ENV ACTIVEMQ_VERSION=5.18.3
ENV ACTIVEMQ_HOME=/opt/activemq
ARG TOMEE_VERSION=10.1.3
ENV TOMEE_HOME=/opt/tomee
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

RUN apt-get update -qq && apt-get install -y -qq --no-install-recommends \
    openjdk-17-jre-headless wget tar ca-certificates unzip curl \
    && rm -rf /var/lib/apt/lists/*

RUN wget -q "https://archive.apache.org/dist/activemq/${ACTIVEMQ_VERSION}/apache-activemq-${ACTIVEMQ_VERSION}-bin.tar.gz" -O /opt/activemq.tgz \
    && tar -xzf /opt/activemq.tgz -C /opt \
    && mv "/opt/apache-activemq-${ACTIVEMQ_VERSION}" "${ACTIVEMQ_HOME}" \
    && rm -f /opt/activemq.tgz

RUN curl -fsSL -o /opt/tomee.zip \
    "https://archive.apache.org/dist/tomee/tomee-${TOMEE_VERSION}/apache-tomee-${TOMEE_VERSION}-plume.zip" \
    && unzip -q /opt/tomee.zip -d /opt \
    && rm -f /opt/tomee.zip \
    && ln -sf "/opt/apache-tomee-plume-${TOMEE_VERSION}" "${TOMEE_HOME}" \
    && chmod -R a+rx "${TOMEE_HOME}/bin"

COPY c02-jms-broker/activemq.xml ${ACTIVEMQ_HOME}/conf/activemq.xml
COPY c02-jms-broker/start.sh /start.sh
RUN sed -i 's/\r$//' /start.sh && chmod +x /start.sh
COPY --from=metricsbuilder /build/c02-metrics/target/metrics.war ${TOMEE_HOME}/webapps/

EXPOSE 61616 8080
CMD ["/bin/bash", "/start.sh"]
