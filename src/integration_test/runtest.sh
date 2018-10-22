#!/bin/bash
set -e
mvn clean install -DskipTests
mvn com.avioconsulting.mule:depresolver:1.0.0:resolve -Dresolve.dependencies=org.apache.maven:maven-core:3.5.4 -Dresolve.dependency.graph.json.file=dependency_graph.json
