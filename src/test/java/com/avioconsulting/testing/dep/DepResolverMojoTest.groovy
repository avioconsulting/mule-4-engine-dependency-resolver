package com.avioconsulting.testing.dep

import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.DefaultArtifact
import org.junit.Test

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.is
import static org.junit.Assert.assertThat

class DepResolverMojoTest {
    Artifact getArtifact(String artifact,
                         String scope,
                         List<Artifact> dependsOnUs = []) {
        def parent = new DefaultArtifact('some.group',
                                         'ourparent',
                                         '1.0.0',
                                         'compile',
                                         'jar',
                                         'mule-plugin',
                                         null)
        new DefaultArtifact('some.group',
                            artifact,
                            '1.0.0',
                            scope,
                            'jar',
                            'mule-plugin',
                            null).with {
            it.file = new File("/some/path/${artifact}-1.0.0.jar")
            def otherDeps = dependsOnUs.collect { d -> d.toString() }
            // our parent is always first
            otherDeps.add(0, parent.toString())
            // we are always last
            otherDeps.add(it.toString())
            // deps seem to not have scopes
            otherDeps = otherDeps.collect { d ->
                d.replace(':compile', '')
            }
            it.dependencyTrail = otherDeps
            it
        }
    }


    @Test
    void getDependencyMap() {
        // arrange
        def mojo = new DepResolverMojo()
        def artifact1 = getArtifact('artifact1',
                                    'compile',
                                    [])
        def artifact2 = getArtifact('artifact2',
                                    'compile',
                                    [artifact1])
        def artifacts = [
                artifact1,
                artifact2,
                getArtifact('artifact3',
                            'test',
                            [])
        ].toSet()

        // act
        def result = mojo.getDependencyMap(artifacts)

        // assert
        assertThat result,
                   is(equalTo([
                           'some.group:artifact1:1.0.0': new CompleteArtifact('some.group:artifact1:1.0.0',
                                                                              'some.group',
                                                                              'artifact1',
                                                                              '1.0.0',
                                                                              '/some/path/artifact1-1.0.0.jar',
                                                                              'compile',
                                                                              ['some.group:artifact2:1.0.0']),
                           'some.group:artifact2:1.0.0': new CompleteArtifact('some.group:artifact2:1.0.0',
                                                                              'some.group',
                                                                              'artifact2',
                                                                              '1.0.0',
                                                                              '/some/path/artifact2-1.0.0.jar',
                                                                              'compile',
                                                                              [])
                   ]))
    }

    @Test
    void resolveDependencies_only_self() {
        // arrange
        def mojo = new DepResolverMojo()
        def input = [
                'some.group:artifact1:1.0.0': new CompleteArtifact('some.group:artifact1:1.0.0',
                                                                   'some.group',
                                                                   'artifact1',
                                                                   '1.0.0',
                                                                   '/some/path/artifact1-1.0.0.jar',
                                                                   'compile',
                                                                   ['some.group:artifact2:1.0.0']),
                'some.group:artifact2:1.0.0': new CompleteArtifact('some.group:artifact2:1.0.0',
                                                                   'some.group',
                                                                   'artifact2',
                                                                   '1.0.0',
                                                                   '/some/path/artifact2-1.0.0.jar',
                                                                   'compile',
                                                                   [])
        ]

        // act
        def result = mojo.flattenOurDependencyGraph(input,
                                                    ['some.group:artifact2:1.0.0'],
                                                    '/some/path')

        // assert
        assertThat result,
                   is(equalTo([
                           new SimpleArtifact('some.group:artifact2:1.0.0',
                                              'some.group',
                                              'artifact2',
                                              '1.0.0',
                                              'artifact2-1.0.0.jar',
                                              'compile')
                   ]))
    }

    @Test
    void resolveDependencies_some() {
        // arrange
        def mojo = new DepResolverMojo()
        def input = [
                'some.group:artifact1:1.0.0': new CompleteArtifact('some.group:artifact1:1.0.0',
                                                                   'some.group',
                                                                   'artifact1',
                                                                   '1.0.0',
                                                                   '/some/path/artifact1-1.0.0.jar',
                                                                   'compile',
                                                                   ['some.group:artifact2:1.0.0']),
                'some.group:artifact2:1.0.0': new CompleteArtifact('some.group:artifact2:1.0.0',
                                                                   'some.group',
                                                                   'artifact2',
                                                                   '1.0.0',
                                                                   '/some/path/artifact2-1.0.0.jar',
                                                                   'compile',
                                                                   [])
        ]

        // act
        def result = mojo.flattenOurDependencyGraph(input,
                                                    ['some.group:artifact1:1.0.0'],
                                                    '/some/path')

        // assert
        assertThat result,
                   is(equalTo([
                           new SimpleArtifact('some.group:artifact1:1.0.0',
                                              'some.group',
                                              'artifact1',
                                              '1.0.0',
                                              'artifact1-1.0.0.jar',
                                              'compile'),
                           new SimpleArtifact('some.group:artifact2:1.0.0',
                                              'some.group',
                                              'artifact2',
                                              '1.0.0',
                                              'artifact2-1.0.0.jar',
                                              'compile')
                   ]))
    }

    @Test
    void resolveDependencies_dupes() {
        // arrange
        def mojo = new DepResolverMojo()
        def input = [
                'some.group:artifact1:1.0.0': new CompleteArtifact('some.group:artifact1:1.0.0',
                                                                   'some.group',
                                                                   'artifact1',
                                                                   '1.0.0',
                                                                   '/some/path/artifact1-1.0.0.jar',
                                                                   'compile',
                                                                   ['some.group:artifact2:1.0.0',
                                                                    'some.group:artifact3:1.0.0']),
                'some.group:artifact2:1.0.0': new CompleteArtifact('some.group:artifact2:1.0.0',
                                                                   'some.group',
                                                                   'artifact2',
                                                                   '1.0.0',
                                                                   '/some/path/artifact2-1.0.0.jar',
                                                                   'compile',
                                                                   ['some.group:artifact3:1.0.0']),
                'some.group:artifact3:1.0.0': new CompleteArtifact('some.group:artifact3:1.0.0',
                                                                   'some.group',
                                                                   'artifact3',
                                                                   '1.0.0',
                                                                   '/some/path/artifact3-1.0.0.jar',
                                                                   'compile',
                                                                   [])
        ]

        // act
        def result = mojo.flattenOurDependencyGraph(input,
                                                    ['some.group:artifact1:1.0.0',
                                               'some.group:artifact2:1.0.0'],
                                                    '/some/path')

        // assert
        is(equalTo([
                new SimpleArtifact('some.group:artifact1:1.0.0',
                                   'some.group',
                                   'artifact1',
                                   '1.0.0',
                                   'artifact1-1.0.0.jar',
                                   'compile'),
                new SimpleArtifact('some.group:artifact2:1.0.0',
                                   'some.group',
                                   'artifact2',
                                   '1.0.0',
                                   'artifact2-1.0.0.jar',
                                   'compile'),
                new SimpleArtifact('some.group:artifact3:1.0.0',
                                   'some.group',
                                   'artifact3',
                                   '1.0.0',
                                   'artifact3-1.0.0.jar',
                                   'compile')
        ]))
    }
}
