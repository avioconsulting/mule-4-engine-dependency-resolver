package com.avioconsulting.testing.dep

import groovy.transform.Immutable

@Immutable
class CompleteArtifact {
    String name, groupId, artifactId, version, filename, scope
    List<String> dependencies

    static CompleteArtifact parse(String name,
                                  Map stuff) {
        new CompleteArtifact(name,
                             stuff['groupId'],
                             stuff['artifactId'],
                             stuff['version'],
                             stuff['filename'],
                             stuff['scope'],
                             stuff['dependencies'])
    }
}
