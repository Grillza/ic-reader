#!/bin/sh
# Builds the Pebble watch app (watchapp/) and bundles it into the Android app's
# assets, where Settings > Pebble watch > Install on watch hands it to the
# Pebble app. Needs the Pebble SDK (pebble-tool); the built .pbw is committed so
# the Android app builds without it.
set -e
cd "$(dirname "$0")/../watchapp"
sh tests/run_tests.sh
pebble build
mkdir -p ../app/src/main/assets/watch
cp build/watchapp.pbw ../app/src/main/assets/watch/ic-reader.pbw
echo "Bundled $(wc -c < ../app/src/main/assets/watch/ic-reader.pbw) bytes into app/src/main/assets/watch/ic-reader.pbw"
