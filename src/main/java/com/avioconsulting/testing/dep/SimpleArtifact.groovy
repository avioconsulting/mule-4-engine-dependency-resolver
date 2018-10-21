package com.avioconsulting.testing.dep

import groovy.transform.Immutable

import java.nio.file.Path

@Immutable
class SimpleArtifact {
    String name, groupId, artifactId, version, filenameRelativeToRepo, scope

    static SimpleArtifact fromComplete(CompleteArtifact artifact,
                                       Path repoDirectory) {
        def path = repoDirectory.relativize(new File(artifact.filename).toPath()).toString()
        new SimpleArtifact(artifact.name,
                           artifact.groupId,
                           artifact.artifactId,
                           artifact.version,
                           path,
                           artifact.scope)
    }
}
