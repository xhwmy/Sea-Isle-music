# Third party components

The project does not grant a separate license to these components. Their upstream licenses apply.

- AndroidX Media3 1.4.1: playback, HTTP data sources and bounded audio cache. [Source and Apache 2.0 license](https://github.com/androidx/media/tree/1.4.1).
- JUnit 4.13.2: host and instrumentation test support. Its Eclipse Public License 1.0 is included in `app/libs/junit-4.13.2.jar` as `LICENSE-junit.txt`. [Upstream](https://github.com/junit-team/junit4).
- Hamcrest Core 1.3: test assertions. Its BSD license is included in `app/libs/hamcrest-core-1.3.jar` as `LICENSE.txt`. [Upstream](https://github.com/hamcrest/JavaHamcrest).
- Gradle Wrapper 8.2.1: build bootstrap, distributed under Apache 2.0. [Upstream](https://github.com/gradle/gradle/tree/v8.2.1).

Gradle resolves additional transitive AndroidX, Google and annotation dependencies from Google Maven and Maven Central. Their corresponding artifact metadata describes each dependency's license. Signing keys, music and private server data are not included.
