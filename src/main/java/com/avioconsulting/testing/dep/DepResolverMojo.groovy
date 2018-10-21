package com.avioconsulting.testing.dep

import groovy.json.JsonOutput
import org.apache.maven.artifact.Artifact
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.project.MavenProject

@Mojo(name = 'resolve',
        requiresDependencyResolution = ResolutionScope.COMPILE,
        requiresDependencyCollection = ResolutionScope.COMPILE)
class DepResolverMojo extends
        AbstractMojo {
    @Component
    private MavenProject mavenProject

    Map getDependencyMap(Set<Artifact> artifacts) {
        def results = [:]
        def dependencyQueue = [:]
        def nameWithClassifierAndTypeToSimpleMapping = [:]
        artifacts.each { artifact ->
            def ourKey = getKey(artifact)
            def depTrail = artifact.dependencyTrail
            // root parent + ourselves are first and last
            def dependsOnUs = depTrail.size() == 2 ? [] : depTrail[1..-2]
            dependsOnUs.each { dependency ->
                def list = dependencyQueue[dependency] ?: (dependencyQueue[dependency] = [])
                list << ourKey
            }
            def nameWithoutScope = artifact.toString().replace(":${artifact.scope}", '')
            nameWithClassifierAndTypeToSimpleMapping[nameWithoutScope] = ourKey
            results[ourKey] = [
                    groupId     : artifact.groupId,
                    artifactId  : artifact.artifactId,
                    version     : artifact.version,
                    filename    : artifact.file.name,
                    dependencies: []
            ]
        }
        dependencyQueue.each { artifact, deps ->
            def keyToLookup = nameWithClassifierAndTypeToSimpleMapping[artifact]
            def resultForItem = results[keyToLookup]
            resultForItem['dependencies'] = deps
        }
        results
    }

    private static String getKey(Artifact artifact) {
        "${artifact.groupId}:${artifact.artifactId}:${artifact.version}"
    }

    @Override
    void execute() throws MojoExecutionException, MojoFailureException {
        log.info 'Doing stuff'
        def result = getDependencyMap(mavenProject.artifacts)
        def asJson = JsonOutput.prettyPrint(JsonOutput.toJson(result))
        log.info asJson
    }
}
