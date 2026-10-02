# CampusCore container image (used for the public demo on Render; works anywhere Docker runs).
# Build:  docker build -t campuscore .
# Run:    docker run -p 8080:8080 -e CMS_DB_URL=... -e CMS_DB_USER=... -e CMS_DB_PASSWORD=... campuscore

# ---- 1. Build the WAR (and run the unit tests) ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -B -q package

# ---- 2. Run it on Tomcat 10.1 ----
FROM tomcat:10.1-jre17-temurin

# Small memory footprint for free hosting tiers; times in UTC (the database connection uses UTC too)
ENV TZ=UTC \
    CATALINA_OPTS="-Xms64m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC -Xss512k" \
    CMS_UPLOAD_DIR=/tmp/cms-uploads

# Only CampusCore: no sample or manager applications. Deployed as ROOT, so it opens at "/".
RUN rm -rf /usr/local/tomcat/webapps/* /usr/local/tomcat/webapps.dist
COPY --from=build /src/target/campuscore.war /usr/local/tomcat/webapps/ROOT.war
COPY tools/demo/demo-proof.png /opt/campuscore/demo-proof.png
COPY docker-entrypoint.sh /usr/local/bin/campuscore-start
RUN chmod +x /usr/local/bin/campuscore-start

EXPOSE 8080
CMD ["campuscore-start"]
