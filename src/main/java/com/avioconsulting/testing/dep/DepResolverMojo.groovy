package com.avioconsulting.testing.dep

import groovy.json.JsonOutput
import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.DefaultArtifact
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager
import org.apache.maven.artifact.repository.ArtifactRepository
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest
import org.apache.maven.artifact.resolver.ArtifactResolver
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject

@Mojo(name = 'resolve')
class DepResolverMojo extends
        AbstractMojo {
    @Parameter(required = true, defaultValue = 'dependencies.json')
    private String outputJsonFile

    @Parameter(required = false, property = 'resolve.dependency.graph.json.file')
    private String dependencyGraphJsonFile

    @Parameter(required = true,
            property = 'resolve.dependencies.comma.separated',
            defaultValue = 'com.mulesoft.mule.distributions:mule-runtime-impl-bom:${app.runtime},org.mule.distributions:mule-module-embedded-impl:${app.runtime}')
    private String requestedDependenciesCsv

    @Parameter(property = 'resolve.sort.output')
    private boolean sortOutput

    @Component
    private MavenProject mavenProject

    @Parameter(defaultValue = '${localRepository}')
    ArtifactRepository localRepository

    @Component
    private ArtifactHandlerManager artifactHandlerManager

    @Component
    private ArtifactResolver resolver

    private List<String> getRequestedDependencies() {
        this.requestedDependenciesCsv.split(',')
    }

    Map<String, CompleteArtifact> getDependencyMap(Set<Artifact> artifacts) {
        def results = [:]
        def dependencyQueue = [:]
        def nameWithClassifierAndTypeToSimpleMapping = [:]
        artifacts.each { artifact ->
            def ourKey = getKey(artifact)
            def depTrail = artifact.dependencyTrail
            assert depTrail: "Expected dependencyTrail for artifact ${ourKey}"
            // root parent + ourselves are first and last
            def dependsOnUs = depTrail.size() == 2 ? [] : depTrail[1..-2]
            dependsOnUs.each { dependency ->
                def list = dependencyQueue[dependency] ?: (dependencyQueue[dependency] = [])
                list << ourKey
            }
            def nameWithoutScope = artifact.toString().replace(":${artifact.scope}", '')
            nameWithClassifierAndTypeToSimpleMapping[nameWithoutScope] = ourKey
            results[ourKey] = new CompleteArtifact(ourKey,
                                                   artifact.groupId,
                                                   artifact.artifactId,
                                                   artifact.version,
                                                   artifact.file.absolutePath,
                                                   artifact.scope,
                                                   [])
        }
        dependencyQueue.each { artifact,
                               List<String> deps ->
            def keyToLookup = nameWithClassifierAndTypeToSimpleMapping[artifact]
            assert keyToLookup: "Unable to lookup ${artifact}!"
            def resultForItem = results[keyToLookup] as CompleteArtifact
            if (this.sortOutput) {
                deps = deps.sort()
            }
            results[keyToLookup] = new CompleteArtifact(resultForItem.name,
                                                        resultForItem.groupId,
                                                        resultForItem.artifactId,
                                                        resultForItem.version,
                                                        resultForItem.filename,
                                                        resultForItem.scope,
                                                        deps)
        }
        def removeTestScope = results.findAll { key, value ->
            value['scope'] != 'test'
        }
        this.sortOutput ? removeTestScope.sort() : removeTestScope
    }

    private static String getKey(Artifact artifact) {
        "${artifact.groupId}:${artifact.artifactId}:${artifact.version}"
    }

    List<SimpleArtifact> resolveDependencies(Map<String, CompleteArtifact> dependencyGraph,
                                             List<String> desiredDependencies,
                                             String repoPath) {
        def repo = new File(repoPath).toPath()
        desiredDependencies.collect { desiredDependency ->
            def list = flattenDependencies(desiredDependency,
                                           dependencyGraph)
            list.collect { dep ->
                SimpleArtifact.fromComplete(dep,
                                            repo)
            }
        }.flatten().unique()
    }

    private List<CompleteArtifact> flattenDependencies(String key,
                                                       Map<String, CompleteArtifact> dependencyGraph,
                                                       Map<String, CompleteArtifact> totals = [:],
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

    private Set<Artifact> forceDependencyDownload() {
        def handler = this.artifactHandlerManager.getArtifactHandler('jar')
        this.requestedDependencies.collect { dependencyStr ->
            def parts = dependencyStr.split(':')
            def groupId = parts[0]
            def artifactId = parts[1]
            def version = parts[2]
            def artifact = new DefaultArtifact(groupId,
                                               artifactId,
                                               version,
                                               'compile',
                                               'jar',
                                               null,
                                               handler)
            log.info "Forcing download of dependency ${artifact}"
            // TODO: use with
            def request = new ArtifactResolutionRequest()
            request.localRepository = this.localRepository
            request.remoteRepositories = this.mavenProject.remoteArtifactRepositories
            request.artifact = artifact
            // without this, getArtifactResolutionNodes does not return anything
            request.resolveTransitively = true
            def result = this.resolver.resolve(request)
            assert result.success: "We were unable to successfully resolve artifact ${artifact}"
            def resultList = result.artifactResolutionNodes
            assert resultList && resultList.any(): "Expected artifact ${dependencyStr} to be resolved!"
            def dependenciesOfThis = resultList.artifact
            // artifactResolutionNodes does not include ourselves
            dependenciesOfThis << result.originatingArtifact
            return dependenciesOfThis
        }.flatten().toSet()
    }

    private File getOutputFile(String filename) {
        def resourceDir = new File(mavenProject.build.outputDirectory, 'dependency_resources')
        resourceDir.mkdirs()
        new File(resourceDir, filename)
    }

    static private def writePrettyJson(Object input,
                                       File outputFile) {
        def asJson = JsonOutput.prettyPrint(JsonOutput.toJson(input))
        outputFile.write(asJson)
    }

    @Override
    void execute() throws MojoExecutionException, MojoFailureException {
        def artifacts = forceDependencyDownload()
        log.info "Figuring out dependencies for ${this.requestedDependencies}"
        def dependencyGraph = getDependencyMap(artifacts)
        if (this.dependencyGraphJsonFile) {
            def depFile = getOutputFile(dependencyGraphJsonFile)
            log.info "Writing dependency graph to ${depFile}"
            writePrettyJson(dependencyGraph,
                            depFile)

        }
        def resolved = resolveDependencies(dependencyGraph,
                                           this.requestedDependencies,
                                           this.localRepository.basedir)
        def file = getOutputFile(outputJsonFile)
        log.info "Done, now writing JSON to ${file}"
        writePrettyJson(resolved,
                        file)
    }
}
