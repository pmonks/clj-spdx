#!/usr/bin/env bash

# Ensure any non-zero exit terminates the script immediately
set -e
set -o pipefail

# Note: these commands all require that httpie is installed

# Official license texts, from the publishers
http --follow get https://www.apache.org/licenses/LICENSE-1.0.txt > ./test/data/apache-1.0.txt
http --follow get https://www.apache.org/licenses/LICENSE-1.1.txt > ./test/data/apache-1.1.txt
http --follow get https://www.apache.org/licenses/LICENSE-2.0.txt > ./test/data/apache-2.0.txt

http --follow get https://www.eclipse.org/org/documents/epl-1.0/EPL-1.0.txt > ./test/data/epl-1.0.txt
http --follow get https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.txt > ./test/data/epl-2.0.txt

http --follow get https://spdx.org/licenses/CDDL-1.0.txt > ./test/data/cddl-1.0.txt
http --follow get https://spdx.org/licenses/CDDL-1.1.txt > ./test/data/cddl-1.1.txt

http --follow get https://www.gnu.org/licenses/gpl-1.0.txt > ./test/data/gpl-1.0.txt
http --follow get https://www.gnu.org/licenses/gpl-2.0.txt > ./test/data/gpl-2.0.txt
http --follow get https://www.gnu.org/licenses/gpl-3.0.txt > ./test/data/gpl-3.0.txt
http --follow get https://www.gnu.org/licenses/lgpl-2.0.txt > ./test/data/lgpl-2.0.txt
http --follow get https://www.gnu.org/licenses/lgpl-2.1.txt > ./test/data/lgpl-2.1.txt
http --follow get https://www.gnu.org/licenses/lgpl-3.0.txt > ./test/data/lgpl-3.0.txt
http --follow get https://www.gnu.org/licenses/agpl-3.0.txt > ./test/data/agpl-3.0.txt
# Note: no known URL for Classpath-exception-2.0 in text format - https://www.gnu.org/software/classpath/license.html is the nearest thing

http --follow get https://creativecommons.org/publicdomain/zero/1.0/legalcode.txt > ./test/data/cc0.txt
http --follow get https://creativecommons.org/licenses/by/3.0/legalcode.txt > ./test/data/cc-by-3.0.txt
http --follow get https://creativecommons.org/licenses/by/4.0/legalcode.txt > ./test/data/cc-by-4.0.txt
http --follow get https://creativecommons.org/licenses/by-sa/4.0/legalcode.txt > ./test/data/cc-by-sa-4.0.txt
http --follow get https://creativecommons.org/licenses/by-nc/4.0/legalcode.txt > ./test/data/cc-by-nc-4.0.txt
http --follow get https://creativecommons.org/licenses/by-nc-sa/4.0/legalcode.txt > ./test/data/cc-by-nc-sa-4.0.txt
http --follow get https://creativecommons.org/licenses/by-nd/4.0/legalcode.txt > ./test/data/cc-by-nd-4.0.txt
http --follow get https://creativecommons.org/licenses/by-nc-nd/4.0/legalcode.txt > ./test/data/cc-by-nc-nd-4.0.txt

http --follow get https://www.wtfpl.net/txt/copying/ > ./test/data/wtfpl.txt

http --follow get https://www.mozilla.org/media/MPL/2.0/index.txt > ./test/data/mpl-2.0.txt

http --follow get https://mit-license.org/license.txt > ./test/data/mit.txt

# Project specific license texts that may or may not match the official license texts
http --follow get https://raw.githubusercontent.com/pmonks/clj-spdx/main/LICENSE > ./test/data/clj-spdx.txt
http --follow get https://raw.githubusercontent.com/commonmark/commonmark-java/main/LICENSE.txt > ./test/data/commonmark.txt
http --follow get https://raw.githubusercontent.com/jnr/jffi/master/LICENSE > ./test/data/jffi.txt
http --follow get https://raw.githubusercontent.com/javaee/javamail/master/LICENSE.txt > ./test/data/javamail.txt
