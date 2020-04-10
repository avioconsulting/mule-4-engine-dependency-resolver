package com.avioconsulting.testing.dep

import groovy.json.JsonOutput
import org.apache.maven.model.Resource
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.graph.DependencyNode
import org.eclipse.aether.resolution.DependencyRequest
import org.eclipse.aether.util.artifact.JavaScopes
import org.eclipse.aether.util.filter.DependencyFilterUtils

@Mojo(name = 'resolve')
class MuleEngineDependencyResolverMojo extends AbstractMojo {
    @Parameter(required = true,
            defaultValue = 'mule4_dependencies.json',
            property = 'resolve.outputFile')
    private String engineOutputJsonFilename

    @Parameter(required = true,
            defaultValue = 'dw_dependencies.json',
            property = 'resolve.dw.outputFile')
    private String dataWeaveOutputJsonFileName

    @Parameter(required = true,
            property = 'resolve.dependencies.comma.separated',
            defaultValue = 'com.mulesoft.mule.distributions:mule-runtime-impl-bom:${app.runtime},org.mule.distributions:mule-module-embedded-impl:${app.runtime}')
    private List<String> requestedDependencies

    @Parameter(property = 'resolve.patches.comma.separated')
    private List<String> mulePatches

    @Parameter(property = 'resolve.sort.output')
    private boolean sortOutput

    @Component
    private MavenProject mavenProject

    @Component
    private RepositorySystem repositorySystem

    @Parameter(defaultValue = '${repositorySystemSession}',
            readonly = true)
    private RepositorySystemSession repoSession

    List<SimpleArtifact> getDependencyList(List<DependencyNode> artifacts) {
        def results = artifacts.collect { node ->
            def artifact = node.artifact
            def file = artifact.file
            assert file: "No filename looked up for ${artifact}"
            def us = SimpleArtifact.fromComplete(artifact)
            def kids = getDependencyList(node.children)
            kids << us
        }.flatten() as List<SimpleArtifact>
        this.sortOutput ? results.sort { artifact -> "${artifact.groupId}:${artifact.artifactId}" } : results
    }

    private List<DependencyNode> collectDependencies(List<String> dependencies) {
        dependencies.collect { dependencyStr ->
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

    static private def writePrettyJson(Object input,
                                       File outputFile) {
        def asJson = JsonOutput.prettyPrint(JsonOutput.toJson(input))
        outputFile.write(asJson)
    }

    @Override
    void execute() throws MojoExecutionException, MojoFailureException {
        log.info "Figuring out dependencies for ${this.requestedDependencies}"
        def dependencyNodes = collectDependencies(this.requestedDependencies)
        if (this.mulePatches) {
            log.info "Adding patches ${this.mulePatches}"
            dependencyNodes.addAll(collectDependencies(this.mulePatches))
        }
        log.info 'Getting list'
        def list = getDependencyList(dependencyNodes)
        def outputJsonFile = new File(engineOutputJsonFilename)
        if (!outputJsonFile.absolute) {
            def path = new File(mavenProject.build.directory,
                                'generated-test-sources/META-INF')
            outputJsonFile = new File(path,
                                      engineOutputJsonFilename)
            def resource = new Resource()
            resource.setDirectory(path.absolutePath)
            resource.addInclude(engineOutputJsonFilename)
            log.info "Added test resource ${resource}"
            mavenProject.addTestResource(resource)
        }
        outputJsonFile.parentFile.mkdirs()
        log.info "Done, now writing JSON to ${outputJsonFile}"
        writePrettyJson(list,
                        outputJsonFile)
    }
}
