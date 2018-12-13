package com.avioconsulting.testing.dep

import groovy.transform.Immutable
import org.eclipse.aether.artifact.Artifact

import java.nio.file.Path

@Immutable
class SimpleArtifact {
    String groupId, artifactId, version, filename

    static SimpleArtifact fromComplete(Artifact artifact) {
        new SimpleArtifact(artifact.groupId,
                           artifact.artifactId,
                           artifact.version,
                           artifact.file.absolutePath)
    }
}
