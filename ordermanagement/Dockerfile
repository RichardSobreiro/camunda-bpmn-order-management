FROM camunda/camunda-bpm-platform:latest
COPY ./target/*.jar /usr/local/tomcat/webapps/camunda-engine-app.jar
ENTRYPOINT ["java","-jar","/usr/local/tomcat/webapps/camunda-engine-app.jar"]