package com.avioconsulting.testing.dep

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.util.FileUtils

import java.security.MessageDigest

@Mojo(name = 'preserveRepository')
class RepositoryPreserverMojo extends AbstractMojo {
    @Component
    private MavenProject mavenProject

    @Override
    void execute() throws MojoExecutionException, MojoFailureException {
        // aka target
        def buildDirectory = mavenProject.build.directory
        def classLoaderModelFile = new File(buildDirectory,
                                            'META-INF/mule-artifact/classloader-model.json')
        assert classLoaderModelFile.exists(): "Expected ${classLoaderModelFile} to exist already (mvn test-compile)"
        def digest = MessageDigest.getInstance('SHA-256')
        digest.update(classLoaderModelFile.bytes)
        def hashAsBase64 = Base64.encoder.encodeToString(digest.digest())
        def sourceRepositoryDirectory = new File(buildDirectory,
                                                 'repository')
        def targetRepositoryDirectory = new File(buildDirectory,
                                                 "repository-${hashAsBase64}")
        log.info "Copying ${sourceRepositoryDirectory} to ${targetRepositoryDirectory} in case Studio wipes it out"
        FileUtils.copyDirectoryStructure(sourceRepositoryDirectory,
                                         targetRepositoryDirectory)
    }
}
