#!/bin/bash
set -e
mvn clean install -DskipTests
#rm -rf temporary_repo
#ARTIFACT_DIR=temporary_repo/com/avioconsulting/mule/depresolver/1.0.0
#mkdir -p $ARTIFACT_DIR
#cp -v target/depresolver-1.0.0.jar $ARTIFACT_DIR
#cp -v pom.xml $ARTIFACT_DIR/depresolver-1.0.0.pom
#mvn -Dmaven.repo.local=temporary_repo
mvn -f src/integration_test/pom.xml clean -Dresolve.outputFile=stuff.json package -DskipTests -e
diff src/integration_test/expected_dependencies.json src/integration_test/target/generated-test-sources/META-INF/stuff.json
