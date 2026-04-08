FROM deegree/deegree3-docker:3.6

# Extract WAR and add workspace config so deegree loads it on startup.
# Removing the WAR prevents Tomcat from re-extracting it over our added files.
RUN cd /usr/local/tomcat/webapps && \
    mkdir deegree-webservices && \
    cd deegree-webservices && \
    jar xf ../deegree-webservices.war && \
    cd .. && \
    rm deegree-webservices.war && \
    mkdir -p deegree-webservices/WEB-INF/conf/services \
             deegree-webservices/WEB-INF/conf/datasources/feature \
             deegree-webservices/WEB-INF/conf/appschemas \
             deegree-webservices/WEB-INF/conf/data

COPY workspace/services/wfs.xml \
     /usr/local/tomcat/webapps/deegree-webservices/WEB-INF/conf/services/wfs.xml
COPY workspace/datasources/feature/memory.xml \
     /usr/local/tomcat/webapps/deegree-webservices/WEB-INF/conf/datasources/feature/memory.xml
COPY workspace/appschemas/testfeature.xsd \
     /usr/local/tomcat/webapps/deegree-webservices/WEB-INF/conf/appschemas/testfeature.xsd
COPY workspace/data/features.gml \
     /usr/local/tomcat/webapps/deegree-webservices/WEB-INF/conf/data/features.gml
