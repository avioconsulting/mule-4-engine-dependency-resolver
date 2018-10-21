package com.avioconsulting.testing.dep

import groovy.json.JsonOutput
import org.apache.maven.artifact.Artifact
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.project.MavenProject

@Mojo(name = 'resolve',
        requiresDependencyResolution = ResolutionScope.TEST,
        requiresDependencyCollection = ResolutionScope.TEST)
class DepResolverMojo extends
        AbstractMojo {
    @Parameter(required = true, defaultValue = 'dependencies.json')
    private File outputJsonFile

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
                    scope       : artifact.scope,
                    dependencies: []
            ]
        }
        dependencyQueue.each { artifact, deps ->
            def keyToLookup = nameWithClassifierAndTypeToSimpleMapping[artifact]
            assert keyToLookup: "Unable to lookup ${artifact}!"
            def resultForItem = results[keyToLookup]
            resultForItem['dependencies'] = deps
        }
        results.findAll { key, value ->
            value['scope'] != 'test'
        }
    }

    private static String getKey(Artifact artifact) {
        "${artifact.groupId}:${artifact.artifactId}:${artifact.version}"
    }

    @Override
    void execute() throws MojoExecutionException, MojoFailureException {
        log.info 'Figuring out dependencies'
        def result = getDependencyMap(mavenProject.artifacts)
        def asJson = JsonOutput.prettyPrint(JsonOutput.toJson(result))
        log.info "Done, now writing JSON to ${outputJsonFile}"
        this.outputJsonFile.write(asJson)
    }
}
