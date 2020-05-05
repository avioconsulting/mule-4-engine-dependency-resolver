# 1.0.5
* Now that testing framework has decoupled more from Maven, preserve the repository directory with a new goal since Studio wipes it out w/ a file edit

# 1.0.3
* Allow Mule patches to be supplied

# 1.0.2
* Further insulate the testing framework from Maven by just including the dependency paths from this plugin. We can do that because the file we generate is always generated.

# 1.0.1
* Fix issue with Studio 7 tooling instance

# 1.0.0
* Initial release
