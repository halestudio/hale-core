hale-core
=========

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
4. Add a file watcher (requires file watchers plugin) to run spotless for individual changed files

## License

* The main hale components/libaries are released under the GNU Lesser General Public License (LGPL) v3.0.
* Different licenses may apply to the extensions residing in **ext/**, please see the respective subfolders.
