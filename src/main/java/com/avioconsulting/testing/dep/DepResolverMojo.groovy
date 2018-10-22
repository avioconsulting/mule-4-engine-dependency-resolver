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
import org.eclipse.aether.resolution.DependencyRequest
import org.eclipse.aether.util.artifact.JavaScopes
import org.eclipse.aether.util.filter.DependencyFilterUtils

@Mojo(name = 'resolve')
class DepResolverMojo extends
        AbstractMojo {
    @Parameter(required = true, defaultValue = 'dependencies.json')
    private String outputJsonFile

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

    List<SimpleArtifact> getDependencyList(List<DependencyNode> artifacts,
                                           File repoDirectory) {
        def results = artifacts.collect { node ->
            def artifact = node.artifact
            def file = artifact.file
            assert file: "No filename looked up for ${artifact}"
            def us = SimpleArtifact.fromComplete(artifact,
                                                 getKey(artifact),
                                                 repoDirectory)
            def kids = getDependencyList(node.children,
                                         repoDirectory)
            kids << us
        }.flatten() as List<SimpleArtifact>
        this.sortOutput ? results.sort { artifact -> artifact.name } : results
    }

    private static String getKey(Artifact artifact) {
        "${artifact.groupId}:${artifact.artifactId}:${artifact.version}"
    }

    private List<DependencyNode> collectDependencies() {
        this.requestedDependencies.collect { dependencyStr ->
            def artifact = new DefaultArtifact(dependencyStr)
            log.info "Resolving dependency ${artifact}"
            def collectRequest = new CollectRequest()
            collectRequest.setRoot(new Dependency(artifact,
                                                  JavaScopes.COMPILE))
            collectRequest.setRepositories(mavenProject.remoteProjectRepositories)
            def dependencyRequest = new DependencyRequest(collectRequest,
                                                          DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE,
                                                                                                JavaScopes.RUNTIME))
            def result = repositorySystem.resolveDependencies(repoSession,
                                                              dependencyRequest)
            result.root
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
        log.info "Figuring out dependencies for ${this.requestedDependencies}"
        def dependencyNodes = collectDependencies()
        log.info 'Getting list'
        def list = getDependencyList(dependencyNodes,
                                     repoSession.localRepository.basedir)
        def file = getOutputFile(outputJsonFile)
        log.info "Done, now writing JSON to ${file}"
        writePrettyJson(list,
                        file)
    }
}
