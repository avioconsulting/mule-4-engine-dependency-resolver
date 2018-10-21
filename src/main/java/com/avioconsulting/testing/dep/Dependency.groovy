package com.avioconsulting.testing.dep

import groovy.transform.Immutable

@Immutable
class Dependency {
    String name, artifactId, groupId, version, filename, scope
    List<String> dependencies

    static Dependency parse(String name,
                            Map stuff) {
        new Dependency(name,
                       stuff['artifactId'],
                       stuff['groupId'],
                       stuff['version'],
                       stuff['filename'],
                       stuff['scope'],
                       stuff['dependencies'])
    }
}
