| | | | |
|---:|:---:|:---:|:---:|
| [**main**](https://github.com/pmonks/clj-spdx/tree/main) | [![CI](https://github.com/pmonks/clj-spdx/workflows/CI/badge.svg?branch=main)](https://github.com/pmonks/clj-spdx/actions?query=workflow%3ACI+branch%3Amain) | [![Dependencies](https://github.com/pmonks/clj-spdx/workflows/dependencies/badge.svg?branch=main)](https://github.com/pmonks/clj-spdx/actions?query=workflow%3Adependencies+branch%3Amain) | [![Vulnerabilities](https://github.com/pmonks/clj-spdx/workflows/vulnerabilities/badge.svg?branch=main)](https://pmonks.github.io/pbr/nvd/dependency-check-report.html) |
| [**dev**](https://github.com/pmonks/clj-spdx/tree/dev)  | [![CI](https://github.com/pmonks/clj-spdx/workflows/CI/badge.svg?branch=dev)](https://github.com/pmonks/clj-spdx/actions?query=workflow%3ACI+branch%3Adev) | [![Dependencies](https://github.com/pmonks/clj-spdx/workflows/dependencies/badge.svg?branch=dev)](https://github.com/pmonks/clj-spdx/actions?query=workflow%3Adependencies+branch%3Adev) | [![Vulnerabilities](https://github.com/pmonks/clj-spdx/workflows/vulnerabilities/badge.svg?branch=dev)](https://github.com/pmonks/clj-spdx/actions?query=workflow%3Avulnerabilities+branch%3Adev) |

[![Latest Version](https://img.shields.io/clojars/v/com.github.pmonks/clj-spdx)](https://clojars.org/com.github.pmonks/clj-spdx/) [![Open Issues](https://img.shields.io/github/issues/pmonks/clj-spdx.svg)](https://github.com/pmonks/clj-spdx/issues) [![License](https://img.shields.io/github/license/pmonks/clj-spdx.svg)](https://github.com/pmonks/clj-spdx/blob/main/LICENSE)


# clj-spdx

A Clojure wrapper around [`Spdx-Java-Library`](https://github.com/spdx/Spdx-Java-Library).  Note that that library's functionality is being wrapped on-demand by the author based on their needs in other projects, so it is almost certain that this library is not yet comprehensive. Contributions of wrappings of additional parts of the Java library are warmly welcomed!

Note also that the author has no official relationship with the [SPDX project](https://spdx.dev/) (who maintain `Spdx-Java-Library`), and this work is in no way associated with, or endorsed by, them.

## Installation

`clj-spdx` is available as a Maven artifact from [Clojars](https://clojars.org/com.github.pmonks/clj-spdx).

### Trying it Out

#### Clojure CLI

```shell
$ clj -Sdeps '{:deps {com.github.pmonks/clj-spdx {:mvn/version "#.#.#"}}}'  # Where #.#.# is replaced with an actual version number (see badge above)
```

#### Leiningen

```shell
$ lein try com.github.pmonks/clj-spdx
```

### API Documentation

[API documentation is available here](https://pmonks.github.io/clj-spdx/).

## Contributor Information

[Contributing Guidelines](https://github.com/pmonks/clj-spdx/blob/main/.github/CONTRIBUTING.md)

[Bug Tracker](https://github.com/pmonks/clj-spdx/issues)

[Code of Conduct](https://github.com/pmonks/clj-spdx/blob/main/.github/CODE_OF_CONDUCT.md)

### Developer Workflow

This project uses the [git-flow branching strategy](https://nvie.com/posts/a-successful-git-branching-model/), with the caveat that the permanent branches are called `main` and `dev`, and any changes to the `main` branch are considered a release and auto-deployed (JARs to Clojars, API docs to GitHub Pages, etc.).

For this reason, **all development must occur either in branch `dev`, or (preferably) in temporary branches off of `dev`.**  All PRs from forked repos must also be submitted against `dev`; the `main` branch is **only** updated from `dev` via PRs created by the core development team.  All other changes submitted to `main` will be rejected.

### Build Tasks

`clj-spdx` uses [`tools.build`](https://clojure.org/guides/tools_build). You can get a list of available tasks by running:

```
clojure -A:deps -T:build help/doc
```

Of particular interest are:

* `clojure -T:build test` - run the unit tests
* `clojure -T:build lint` - run the linters (clj-kondo and eastwood)
* `clojure -T:build ci` - run the full CI suite (check for outdated dependencies, run the unit tests, run the linters)
* `clojure -T:build install` - build the JAR and install it locally (e.g. so you can test it with downstream code)

Please note that the `deploy` task is restricted to the core development team (and will not function if you run it yourself).

## License

Copyright © 2023 Peter Monks

Distributed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

SPDX-License-Identifier: [Apache-2.0](https://spdx.org/licenses/Apache-2.0)
