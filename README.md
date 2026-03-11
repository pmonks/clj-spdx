<img alt="clj-spdx logo: a stylised black and white variation on the Clojure logo with the letters SPDX embossed on it" align="right" width="15%" src="https://raw.githubusercontent.com/pmonks/clj-spdx/dev/clj-spdx-logo.png">

# clj-spdx

[![CI](https://github.com/pmonks/clj-spdx/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/pmonks/clj-spdx/actions?query=workflow%3ACI+branch%3Adev)
[![Dependencies](https://github.com/pmonks/clj-spdx/actions/workflows/dependencies.yml/badge.svg?branch=dev)](https://github.com/pmonks/clj-spdx/actions?query=workflow%3Adependencies+branch%3Adev)
[![Vulnerabilities](https://github.com/pmonks/clj-spdx/actions/workflows/vulnerabilities.yml/badge.svg?branch=dev)](https://pmonks.github.io/clj-spdx/nvd/dependency-check-report.html)
<br/>
[![Latest Version](https://img.shields.io/clojars/v/com.github.pmonks/clj-spdx)](https://clojars.org/com.github.pmonks/clj-spdx/)
[![Open Issues](https://img.shields.io/github/issues/pmonks/clj-spdx.svg)](https://github.com/pmonks/clj-spdx/issues)
[![License](https://img.shields.io/github/license/pmonks/clj-spdx.svg)](https://github.com/pmonks/clj-spdx/blob/release/LICENSE)
![Maintained](https://badges.ws/badge/?label=maintained&value=yes,+at+author's+discretion)

A Clojure wrapper around [`Spdx-Java-Library`](https://github.com/spdx/Spdx-Java-Library), plus some bespoke functionality (e.g. a canonicalising [SPDX expression](https://spdx.github.io/spdx-spec/v3.0.2/annexes/spdx-license-expressions/) parser, regular expressions for matching individual SPDX listed identifiers and refs, etc.).

Note that that library's functionality is being wrapped on demand by the author based on their needs in other projects, so this wrapper library is not yet comprehensive. Contributions of any kind are warmly welcomed, especially wrapping additional parts of the Java library such as the [SPDX model](https://github.com/pmonks/clj-spdx/issues/58)!

Note also that this project has no official relationship with the [SPDX project](https://spdx.dev/) (who maintain `Spdx-Java-Library`), and this work is in no way associated with, or endorsed by, them.

## Installation

`clj-spdx` is available as a Maven artifact from [Clojars](https://clojars.org/com.github.pmonks/clj-spdx).

## API Documentation

[API documentation is available here](https://pmonks.github.io/clj-spdx/), or [here on cljdoc](https://cljdoc.org/d/com.github.pmonks/clj-spdx/).  I'm also active on [the Clojure Discord server](https://discord.gg/discljord) if you'd like to chat.

### A note about SPDX license list assets

`Spdx-Java-Library` has two ways of obtaining the data files that comprise the SPDX license list:

1. Using a pre-packaged copy of the files stored inside the JAR (which may not be the latest version)
2. Downloading the latest version of the files from the internet, and caching them locally (as of SPDX license list v3.28.0 this comprises approximately 800 files totaling around 25MB)

This is controlled via the [`org.spdx.useJARLicenseInfoOnly` JVM property](https://github.com/spdx/Spdx-Java-Library?tab=readme-ov-file#configuration-options), which defaults to `false` (i.e. method 2 is the default).  The challenge is that  `Spdx-Java-Library` seems to be [suspiciously slow](https://github.com/spdx/Spdx-Java-Library/issues/394) at downloading these assets, and while `clj-spdx` does its best to workaround those costs (by parallelising the downloads), they remain substantial.

By default `Spdx-Java-Library` will only retrieve these assets on demand, as required by calling code, which has the benefit of amortising the download cost.  However certain functions (especially those in the `spdx.matching` namespace) require all assets, so if you're using those functions you may notice a substantial pause (up to several minutes) the first time they're called.  Subsequent calls will be faster since `Spdx-Java-Library` makes use of a persistent local cache of the downloaded files, and that cache is checked for staleness infrequently (once per day, by default, but also configurable).

Because of this substantial cost, `clj-spdx` provides callers with the option to "force initialise" `Spdx-Java-Library` up front, which doesn't solve the performance problem but does at least make it deterministic; these are the various `init!` functions.  **Calling these `init!` functions is completely optional**, and if you're not performing matching it's better to _not_ call them and instead rely on `Spdx-Java-Library`'s default behaviour.

If you are performing matching and find the download cost (whether on demand or forced up front using `init!`) is unacceptable, currently the only alternative is to use method 1 (load the files stored in the `Spdx-Java-Library` JAR), and just accept that your code will be limited to whatever version of the SPDX license list is packaged in the current release of `Spdx-Java-Library`.

### A note about Spdx-Java-Library v2

From v1.0.247 onward, `clj-spdx` uses `Spdx-Java-Library` v2.x, which adds support for [SPDX specification v3.x](https://spdx.github.io/spdx-spec/v3.0.2/).  This new version of the Java library is _not_ backwards compatible with the earlier version v1.x versions, and that project's [upgrade document](https://github.com/spdx/Spdx-Java-Library/blob/master/README-V3-UPGRADE.md) is well worth reviewing to understand some of the changes in the Java layer, if you happen to be using it via interop.

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
;; A taste of the spdx.identifiers namespace

(require '[spdx.identifiers :as si])

(si/version)
;=> "3.28.0"

(si/ids)
;=> #{"MulanPSL-1.0" "OPUBL-1.0" "CC-BY-SA-1.0" [and many many more]

(si/listed-id? "Apache-2.0")
;=> true

(si/listed-id? "Classpath-exception-2.0")
;=> true

(si/canonicalise-id "aPaChE-2.0")
;=> "Apache-2.0"

(si/canonicalise-id "CLASSPATH-EXCEPTION-2.0")
;=> "Classpath-exception-2.0"

(si/id-type "Apache-2.0")
;=> :license-id

(si/id-type "Classpath-exception-2.0")
;=> :exception-id

(si/id-type "LicenseRef-foo")
;=> :license-ref

(si/id-type "AdditionRef-foo")
;=> :addition-ref

(si/id->info "Apache-2.0")
;=> {:id "Apache-2.0" :name "Apache License 2.0" :see-also
;=>  ("https://www.apache.org/licenses/LICENSE-2.0"
;=>   "https://opensource.org/licenses/Apache-2.0"
;=>   "https://opensource.org/license/apache-2-0")
;=>  :fsf-libre? true :osi-approved? true :type :license-id}

(si/id->info "Classpath-exception-2.0")
;=> {:id "Classpath-exception-2.0" :name "Classpath exception 2.0" :see-also
;=>  ("http://www.gnu.org/software/classpath/license.html"
;=>   "https://fedoraproject.org/wiki/Licensing/GPL_Classpath_Exception")
;=>  :type :exception-id}

; spdx.licenses and spdx.exceptions provide finer-grained type-specific fns for
; SPDX licenses and exceptions


;; A taste of the spdx.matching namespace

(require '[spdx.matching :as sm])

(def apache-20-text (slurp "https://www.apache.org/licenses/LICENSE-2.0.txt"))

(sm/text-is-license? apache-20-text "Apache-2.0")
;=> true

(def mit-text (slurp "https://mit-license.org/license.txt"))

; This is optional, but forces Spdx-Java-Library to fully populate its local
; cache, which some clj-spdx functions (including licenses-within-text) require.
; Note that Spdx-Java-Library is slow at populating its local cache, and this
; call can take a minute or more the first time it's run.
(sm/init!)

; Matching can also be time consuming, since it has to evaluate every SPDX
; matching template (all 811 of them, as of SPDX license list v3.28.0) against
; the provided text.  See https://github.com/spdx/Spdx-Java-Library/issues/341
; for one suggestion for speeding this up.
(sm/licenses-within-text (str apache-20-text "\n\n" mit-text))
;=> #{"Apache-2.0" "MIT"}


;; A taste of the spdx.expressions namespace

(require '[spdx.expressions :as sx])

(sx/parse "GPL-2.0+ WITH Classpath-exception-2.0 OR Apache-2.0")
;=> [:or
;=>   {:license-id "Apache-2.0"}
;=>   {:license-id "GPL-2.0-or-later" :license-exception-id "Classpath-exception-2.0"}]

(sx/parse "DocumentRef-foo:LicenseRef-bar with DocumentRef-foo:AdditionRef-bar")
;=> {:document-ref "foo"          :license-ref "bar"
;=>  :addition-document-ref "foo" :addition-ref "bar"}

(sx/parse "none and mit")
;=> [:and {:license-id "MIT"} {:special-form :none}]

(sx/canonicalise "mit and apache-2.0 or ecos-2.0+")
;=> "GPL-2.0-or-later WITH eCos-exception-2.0 OR (Apache-2.0 AND MIT)"


;; A taste of the spdx.regexes namespace

(require '[spdx.regexes :as sr])

(sr/id-seq "the quick brown apache-2.0 jumps over the lazy mit.")
;=> ("Apache-2.0" "MIT")
; Note that the ids returned by this fn are canonicalised

; Using some of the regexes directly (with help from rencg)

(require '[rencg.api :as ncg])

(ncg/re-matches (sr/ids-re) "Apache-2.0")
;=> {:start 0, :end 10, :match "Apache-2.0", "Identifier" "Apache-2.0"}

(ncg/re-find (sr/ids-re) "some initial text GPL-3.0 some final text")
;=> {:start 18, :end 25, :match "GPL-3.0", "Identifier" "GPL-3.0"}

; NOTE: ids are not canonicalised by the regexes...
(ncg/re-seq (sr/ids-re) "initial text mpl-2.0 more text LicenseRef-foo even more text classpath-exception-2.0 final text")
;=> ({:start 13 :end 20 :match "mpl-2.0" "Identifier" "mpl-2.0"}
;=>  {:start 31 :end 45 :match "LicenseRef-foo" "LicenseRef" "foo" "Identifier" "LicenseRef-foo"}
;=>  {:start 61 :end 84 :match "classpath-exception-2.0" "Identifier" "classpath-exception-2.0"})

; ...but they are by the id-seq-* fns, which also provide identifier type information
(sr/id-seq-matches "initial text mpl-2.0 more text LicenseRef-foo even more text classpath-exception-2.0 final text")
;=> ({:start 13 :end 20 :match "mpl-2.0" :identifier "MPL-2.0" :type :license-id}
;=>  {:start 31 :end 45 :match "LicenseRef-foo" :identifier "LicenseRef-foo" :type :license-ref :license-ref "foo"}
;=>  {:start 61 :end 84 :match "classpath-exception-2.0" :identifier "Classpath-exception-2.0" :type :exception-id})
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
