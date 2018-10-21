package com.avioconsulting.testing.dep

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

    Map getDependencyMap(Set<Artifact> artifacts,
                         Map results = [:]) {
        artifacts.each { artifact ->
            results[withoutScope(artifact)] = [
                    groupId   : artifact.groupId,
                    artifactId: artifact.artifactId,
                    version   : artifact.version,
                    filename  : artifact.file
            ]
        }
        results
//        artifacts.each { a ->
//            println "artifact ${a.artifactId} file ${a.file} - deps - ${a.getDependencyTrail()} - tostring ${a.toString()} scope - ${a.scope}"
//        }
    }

    private static String withoutScope(Artifact artifact) {
        "${artifact.groupId}:${artifact.artifactId}:${artifact.version}"
    }

    @Override
    void execute() throws MojoExecutionException, MojoFailureException {
        log.info 'Doing stuff'
        getDependencyMap(mavenProject.artifacts)
    }
}
