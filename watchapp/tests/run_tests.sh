#!/bin/sh
# Builds and runs the watch app's pure C logic tests on the host.
set -e
cd "$(dirname "$0")/.."
mkdir -p tests/bin
${CC:-tools/hostcc} -std=c99 -Wall -Wextra -Wno-unused-parameter -Werror -Isrc/c tests/test_main.c src/c/codec.c src/c/format.c -o tests/bin/tests
tests/bin/tests
