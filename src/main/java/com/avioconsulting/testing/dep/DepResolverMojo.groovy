package com.avioconsulting.testing.dep

import groovy.json.JsonOutput
import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.repository.ArtifactRepository
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

    @Parameter(required = true)
    private List<String> requestedDependencies

    @Component
    private MavenProject mavenProject

    @Parameter(defaultValue = '${localRepository}')
    ArtifactRepository repository

    Map<String, Dependency> getDependencyMap(Set<Artifact> artifacts) {
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
                    filename    : artifact.file.absolutePath,
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
        }.collectEntries { key, value ->
            [
                    key,
                    Dependency.parse(key,
                                     value)
            ]
        }
    }

    private static String getKey(Artifact artifact) {
        "${artifact.groupId}:${artifact.artifactId}:${artifact.version}"
    }

    List<String> resolveDependencies(Map<String, Dependency> dependencyGraph,
                                     List<String> desiredDependencies,
                                     String repoPath) {
        def repo = new File(repoPath).toPath()
        desiredDependencies.collect { desiredDependency ->
            def list = flattenDependencies(desiredDependency,
                                           dependencyGraph)
            list.collect { dep ->
                repo.relativize(new File(dep.filename).toPath()).toString()
            }
        }.flatten().unique()
    }

    private List<Dependency> flattenDependencies(String key,
                                                 Map<String, Dependency> dependencyGraph,
                                                 Map<String, Dependency> totals = [:],
                                                 boolean recurse = false) {
        def root = dependencyGraph[key]
        assert root: "Unable to find expected key ${key} in ${dependencyGraph}"
        totals[key] = root
        root.dependencies.each { depKey ->
            if (!totals.containsKey(depKey)) {
                flattenDependencies(depKey,
                                    dependencyGraph,
                                    totals,
                                    true)
            }
        }
        recurse ? null : totals.values().toList()
    }

    @Override
    void execute() throws MojoExecutionException, MojoFailureException {
        log.info "Figuring out dependencies ${repository.basedir}"
        def result = getDependencyMap(mavenProject.artifacts)
        def asJson = JsonOutput.prettyPrint(JsonOutput.toJson(result))
        log.info "Done, now writing JSON to ${outputJsonFile}"
        this.outputJsonFile.write(asJson)
    }
}
