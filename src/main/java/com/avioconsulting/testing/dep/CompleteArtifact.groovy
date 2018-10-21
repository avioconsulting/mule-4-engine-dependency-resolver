package com.avioconsulting.testing.dep

import groovy.transform.Immutable

@Immutable
class CompleteArtifact {
    String name, groupId, artifactId, version, filename, scope
    List<String> dependencies
}
