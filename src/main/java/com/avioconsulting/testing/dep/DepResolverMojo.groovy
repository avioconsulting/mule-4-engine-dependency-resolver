package com.avioconsulting.testing.dep

import groovy.json.JsonOutput
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.graph.DependencyNode
import org.eclipse.aether.util.artifact.JavaScopes

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

    @Component
    private RepositorySystem repositorySystem

    @Parameter(defaultValue = '${repositorySystemSession}',
            readonly = true)
    private RepositorySystemSession repoSession

    private List<String> getRequestedDependencies() {
        this.requestedDependenciesCsv.split(',')
    }

    Map<String, CompleteArtifact> getDependencyMap(List<DependencyNode> artifacts,
                                                   Map<String, CompleteArtifact> results = [:]) {
        artifacts.each { node ->
            def artifact = node.artifact
            def ourKey = getKey(artifact)
            getDependencyMap(node.children,
                             results)
            def dependencyKeys = node.children.collect { childNode ->
                getKey(childNode.artifact)
            }
            results[ourKey] = new CompleteArtifact(ourKey,
                                                   artifact.groupId,
                                                   artifact.artifactId,
                                                   artifact.version,
                                                   // TODO: fix the file path problem
                                                   'somefilepath',
                                                   'compile',
                                                   dependencyKeys)
        }
        this.sortOutput ? results.sort() : results
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

    private List<DependencyNode> collectDependencies() {
        this.requestedDependencies.collect { dependencyStr ->
            def artifact = new DefaultArtifact(dependencyStr)
            log.info "Forcing download of dependency ${artifact}"
            def collectRequest = new CollectRequest()
            collectRequest.setRoot(new Dependency(artifact,
                                                  JavaScopes.COMPILE))
            // TODO: setRepositories??
            def collectResult = repositorySystem.collectDependencies(repoSession,
                                                                     collectRequest)
            collectResult.root
        }
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
        def dependencyNodes = collectDependencies()
        log.info "Figuring out dependencies for ${this.requestedDependencies}"
        def dependencyGraph = getDependencyMap(dependencyNodes)
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
