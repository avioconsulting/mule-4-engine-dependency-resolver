#!/bin/bash
set -e
mvn clean install -DskipTests
#rm -rf temporary_repo
#ARTIFACT_DIR=temporary_repo/com/avioconsulting/mule/depresolver/1.0.0
#mkdir -p $ARTIFACT_DIR
#cp -v target/depresolver-1.0.0.jar $ARTIFACT_DIR
#cp -v pom.xml $ARTIFACT_DIR/depresolver-1.0.0.pom
#mvn -Dmaven.repo.local=temporary_repo
mvn com.avioconsulting.mule:dependency-resolver-maven-plugin:1.0.0:resolve -Dresolve.dependencies.comma.separated=org.apache.maven:maven-core:3.5.4  -Dresolve.sort.output=true -Dresolve.outputFile=target/stuff.json
diff src/integration_test/dependencies.json target/stuff.json
