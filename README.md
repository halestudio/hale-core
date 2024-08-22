hale-core
=========

[![publish](https://github.com/halestudio/hale-core/actions/workflows/publish.yml/badge.svg)](https://github.com/halestudio/hale-core/actions/workflows/publish.yml)
[![allure](https://img.shields.io/badge/Allure-test%20report-blue.svg)](https://halestudio.github.io/hale-core/)

Core libraries of [hale»studio](https://github.com/halestudio/hale).

Previously part of hale»studio these libraries have been moved to this repository with version 6 of hale.

## Code formatting

In hale-core [spotless](https://github.com/diffplug/spotless) is used to make sure certain code formatting rules are met.
The rules are based on the original Eclipse formatting used in hale»studio.

To check if the rules are met run:

```shell
./gradlew spotlessCheck
```

To automatically format the code run:

```shell
./gradlew spotlessApply
```

or

```shell
./spotless.sh
```

### Integration in IntelliJ

There are at least the following options to integrate the formatting in IntelliJ.

1. Manually run the Gradle task `spotlessApply` in the root project from the UI to format all files
2. Automatically run `spotlessApply` before building (right click on Gradle task in UI, select respective option)
3. Add the call to Gradle as external tool and assign a key binding (Settings -> Tools -> External tools; Settings -> Keymap)
4. Add a file watcher (requires file watchers plugin) to run spotless for individual changed files **(recommended)**


## Allure test framework

Unit tests can make use of features from the [Allure test framework](https://allurereport.org/).

This allows for adding additional information to tests that can be inspected in the Allure test report.
You can for instance structure your tests into different steps, add file attachments or link to issues.

Please see the documentation on how to use the Allure functionality in tests, for example in [JUnit 4](https://allurereport.org/docs/junit4-reference/) and [JUnit 5](https://allurereport.org/docs/junit5-reference/).

The current report for the `master` branch can be inspected [here](https://halestudio.github.io/hale-core/).

If you want to show a report for locally run tests proceed as follows:

1. Run desired tests with Gradle (e.g. `./gradlew test`)
2. Serve aggregated report over all projects (`./gradlew :allure:allureAggregateServe`)

**Note:** Please do not changes `allure.properties` files manually, they are automatically generated for each project.

### Set labels per project

You can set specific Allure test labels in the build configuration of a project.
Just extend the hale build related settings in the project's `build.gradle` file similar to here:

```
hale {
  allure {
    epic = '<epic>'
    feature = '<feature>'
    story = '<story>'
  }
}
```

### Links to issues

You can generally add custom links, but also specifically links to issues.

The following link types are supported:

| Link type | Target                   | Usage (Annotation)                         |
| --------- | ------------------------ | ------------------------------------------ |
| issue     | GitHub issue             | `@Issue("12")`                             |
| hale      | hale studio GitHub issue | `@Link(value = "1136", type = "hale")`     |
| JIRA      | wetransform JIRA         | `@Link(value = "ING-4128", type = "JIRA")` |


## License

* The main hale components/libaries are released under the GNU Lesser General Public License (LGPL) v3.0.
* Different licenses may apply to the extensions residing in **ext/**, please see the respective subfolders.
