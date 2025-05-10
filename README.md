# clj-spdx

[![CI](https://github.com/pmonks/clj-spdx/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/pmonks/clj-spdx/actions?query=workflow%3ACI+branch%3Adev) [![Dependencies](https://github.com/pmonks/clj-spdx/actions/workflows/dependencies.yml/badge.svg?branch=dev)](https://github.com/pmonks/clj-spdx/actions?query=workflow%3Adependencies+branch%3Adev) [![Vulnerabilities](https://github.com/pmonks/clj-spdx/actions/workflows/vulnerabilities.yml/badge.svg?branch=dev)](https://pmonks.github.io/clj-spdx/nvd/dependency-check-report.html)
<br/>
[![Latest Version](https://img.shields.io/clojars/v/com.github.pmonks/clj-spdx)](https://clojars.org/com.github.pmonks/clj-spdx/) [![Open Issues](https://img.shields.io/github/issues/pmonks/clj-spdx.svg)](https://github.com/pmonks/clj-spdx/issues) [![License](https://img.shields.io/github/license/pmonks/clj-spdx.svg)](https://github.com/pmonks/clj-spdx/blob/release/LICENSE)

A Clojure wrapper around [`Spdx-Java-Library`](https://github.com/spdx/Spdx-Java-Library), plus some bespoke functionality (e.g. a canonicalising [SPDX expression](https://spdx.github.io/spdx-spec/v3.0.1/annexes/spdx-license-expressions/) parser, regular expressions for matching individual SPDX listed identifiers, etc.).

Note that that library's functionality is being wrapped on-demand by the author based on their needs in other projects, so this wrapper library is not yet comprehensive. Contributions of any kind are warmly welcomed, especially wrapping additional parts of the Java library such as the [SPDX model classes](https://github.com/pmonks/clj-spdx/issues/58)!

Note also that this project has no official relationship with the [SPDX project](https://spdx.dev/) (who maintain `Spdx-Java-Library`), and this work is in no way associated with, or endorsed by, them.

## Installation

`clj-spdx` is available as a Maven artifact from [Clojars](https://clojars.org/com.github.pmonks/clj-spdx).

## API Documentation

[API documentation is available here](https://pmonks.github.io/clj-spdx/), or [here on cljdoc](https://cljdoc.org/d/com.github.pmonks/clj-spdx/).

### A note about Spdx-Java-Library v2

As of v1.0.247, `clj-spdx` uses `Spdx-Java-Library` v2.0, which adds support for [SPDX specification v3.0.1](https://spdx.github.io/spdx-spec/v3.0.1/).  This new version of the Java library is _not_ backwards compatible with the prior version (v1.1.12), and that project's [upgrade document](https://github.com/spdx/Spdx-Java-Library/blob/master/README-V3-UPGRADE.md) is well worth reviewing to understand some of the changes in the Java layer, if you happen to be using it via interop.

While `clj-spdx` managed to hide most of the breaking changes, the following data structure changes were unavoidable:

* [license information maps](https://pmonks.github.io/clj-spdx/spdx.licenses.html#var-id-.3Einfo) no longer contain these keys:
  * `:cross-refs` - merged into `:see-also` (note that the Java library renamed "see also" to "see alsos", however `clj-spdx` preserves the old name)
  * `:text-html` - no longer provided by `Spdx-Java-Library` (and was not included by default by `clj-spdx` anyway)
  * `:header-html` - no longer provided by `Spdx-Java-Library` (and was not included by default by `clj-spdx` anyway)
  * `:header-template` - no longer provided by `Spdx-Java-Library` (and was not included by default by `clj-spdx` anyway)
* [license exception information maps](https://pmonks.github.io/clj-spdx/spdx.exceptions.html#var-id-.3Einfo) no longer contain this key:
  * `:text-html` - no longer provided by `Spdx-Java-Library` (and was not included by default by `clj-spdx` anyway)

## Trying it out

### Clojure CLI

```shell
clj -Sdeps '{:deps {com.github.pmonks/clj-spdx {:mvn/version "RELEASE"}}}'
```

### Leiningen

```shell
lein try com.github.pmonks/clj-spdx
```

### deps-try

```shell
deps-try com.github.pmonks/clj-spdx
```

### Demo

```clojure
(require '[spdx.licenses :as sl])

; This is optional but can be time consuming, so we run it explicitly to force
; population of the local Spdx-Java-Library cache.
(sl/init!)

(sl/ids)
;=> #{"MulanPSL-1.0" "OPUBL-1.0" "CC-BY-SA-1.0" [and many more]

(require '[spdx.exceptions :as se])

(se/ids)
;=> #{"GCC-exception-2.0-note" "Qwt-exception-1.0" [and many more]

(require '[spdx.matching :as sm])

(def apache-20-text (slurp "https://www.apache.org/licenses/LICENSE-2.0.txt"))
(sm/licenses-within-text apache-20-text)
;=> #{"Apache-2.0"}

(require '[spdx.expressions :as sexp])

(sexp/parse "GPL-2.0+ WITH Classpath-exception-2.0 OR Apache-2.0")
;=> [:or
;=>   {:license-id "Apache-2.0"}
;=>   {:license-id "GPL-2.0-or-later" :license-exception-id "Classpath-exception-2.0"}]
```

## Contributor Information

[Contributing Guidelines](https://github.com/pmonks/clj-spdx/blob/release/.github/CONTRIBUTING.md)

[Bug Tracker](https://github.com/pmonks/clj-spdx/issues)

[Code of Conduct](https://github.com/pmonks/clj-spdx/blob/release/.github/CODE_OF_CONDUCT.md)

### Developer Workflow

This project uses the [git-flow branching strategy](https://nvie.com/posts/a-successful-git-branching-model/), and the permanent branches are called `release` and `dev`.  Any changes to the `release` branch are considered a release and auto-deployed (JARs to Clojars, API docs to GitHub Pages, etc.).

For this reason, **all development must occur either in branch `dev`, or (preferably) in temporary branches off of `dev`.**  All PRs from forked repos must also be submitted against `dev`; the `release` branch is **only** updated from `dev` via PRs created by the core development team.  All other changes submitted to `release` will be rejected.

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

Please note that the `release` and `deploy` tasks are restricted to the core development team (and will not function if you run them yourself).

## License

Copyright © 2023 Peter Monks

Distributed under the [Mozilla Public License, version 2.0](https://www.mozilla.org/en-US/MPL/2.0/).

SPDX-License-Identifier: [`MPL-2.0`](https://spdx.org/licenses/MPL-2.0)
