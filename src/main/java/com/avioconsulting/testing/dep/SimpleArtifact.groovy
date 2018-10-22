package com.avioconsulting.testing.dep

import groovy.transform.Immutable
import org.eclipse.aether.artifact.Artifact

import java.nio.file.Path

@Immutable
class SimpleArtifact {
    String groupId, artifactId, version, filenameRelativeToRepo

    static SimpleArtifact fromComplete(Artifact artifact,
                                       File repoDirectory) {
        def path = repoDirectory.toPath().relativize(artifact.file.toPath()).toString()
        new SimpleArtifact(artifact.groupId,
                           artifact.artifactId,
                           artifact.version,
                           path)
    }
}
