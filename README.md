# IC Reader

Android app for reading Japanese FeliCa IC cards: balance and history for Suica, PASMO, ICOCA and the
other transit cards, plus Rakuten Edy, nanaco and WAON. Saves multiple cards, builds up history
across scans, lets the launcher icon match your card, and shows your cards on a Pebble watch.

## Features

- **Scan**: hold a card to the back of the phone while the app is open, or tap it with the app
  closed and the app opens on its own. A new card opens as a **preview** first, and nothing is
  stored until you tap **Save card**.
- **Wallet**: all saved cards with their balances and a total. Long-press a card to reorder it.
- **History**: grouped by day, with station names (about 5,900 stations), fares worked out from
  balance changes, purchase times, and a details sheet with the raw record. Export it as CSV.
- **Station names in English**: Settings → Station names switches between Japanese, English, or
  English with Japanese ("Oshiage (押上)"). In English, the operator (e.g. Tokyo Metro) replaces the
  line name, because line names are only available in Japanese.
- **Card type**: transit cards all use the same data format and don't record their issuer, so
  the brand is suggested and you confirm it. If the "new card issued" record is still on the card
  (cards keep only their last 20 records), the issuing station decides. Otherwise the app works
  out the region from your trips and marks that region's cards as likely (Kanto: Suica or PASMO,
  Kansai: ICOCA or PiTaPa, Chubu: TOICA or manaca, Kyushu: SUGOCA, nimoca or Hayakaken).
- **Artwork**: each brand's colour layout on a silver card with the transit-card notch (Suica's
  green sweep, TOICA's cyan wave, manaca's yellow circle…). Logos and characters aren't
  reproduced; add a photo of your own card for the real thing. Card info (menu) shows the
  card's raw IDm, PMm and attributes block.
- **App icon**: 15 launcher icons (one per brand plus a default), from the menu or from a card.
  The switch happens when you leave the app, because Android closes the app if its launcher alias
  is changed while it's open.
- **Backup**: Settings → Backup saves every card, its history and its photo to a `.zip` you
  pick a place for, and restores from one. Restoring merges: new cards are added, and a card
  you already have keeps its newer balance and gains the backup's older history. Use it before
  reinstalling or moving phones (or switching between debug and release builds, which are signed
  differently and can't update each other).
- **Pebble watch**: see [below](#pebble-watch).

## Pebble watch

The IC Reader watch app shows one card at a time: the card's face (the same design or photo
as in the app), its name, its balance and when you last scanned it ("Scanned today 14:20").

- **UP / DOWN**: previous / next card. The dots at the bottom show where you are.
- **SELECT**: the card's history (last 50 records), newest first; UP / DOWN scroll. Each row
  has the route or description, the date and time, and the amount (money in shows green).
- The watch saves the card list, so balances show straight away and while the phone is away
  ("Offline"). Card faces and history come from the phone each time.
- Station names are in English on the watch, because its fonts have no Japanese. A station without
  an English name shows as its code (Station 1A-2B).

Set up in the app under **Settings → Pebble watch**:

1. Install the Pebble app and pair your watch.
2. Tap **Install on watch**. The Pebble app opens and installs IC Reader on the watch.
3. Tap **Open on watch**, or open IC Reader from the watch's app menu.

The phone and watch talk over [PebbleKit 2](https://github.com/pebble-dev/PebbleKitAndroid2).
When the watch app opens, the Pebble app starts IC Reader's `PebbleListenerService`; the watch
says hello with the card art size it wants, and the phone answers with the card list and each
card's face. A scan while the watch app is open sends the new balance at once. The watch app
names this app as its companion (`companionApp` in `watchapp/package.json`), so the Pebble app
only passes its messages to IC Reader.

Card faces are drawn on the phone at the watch's size, in flat colours rounded to the Pebble's
64, and sent run-length compressed (1-2 KB a card). Black-and-white watches get a black and white
version.

It runs on every Pebble: Time 2, Round 2, Time, Time Round, Pebble 2 and 2 Duo, and the
original Pebble (6 cards there).

## What can be read

| Card | Balance | History |
|---|---|---|
| Transit IC (system 0003) | yes | last 20 records (service 090F) |
| Rakuten Edy | yes | last 6 records |
| nanaco | yes | last 5 records |
| WAON | yes | not yet |

Only the unencrypted public areas are read. Gate-entry times and bus stop names are not decoded.

## Build

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug builds have **Load demo card** in the menu, which runs a made-up Suica scan through the
normal flow so you can try the UI on an emulator with no NFC.

The watch app is in `watchapp/`. The Android app bundles it as `assets/watch/ic-reader.pbw`, which
is committed so the Android build doesn't need the Pebble SDK. After changing the watch app, run
`tools/build_watchapp.sh` (needs [pebble-tool](https://developer.repebble.com/sdk/)): it runs
the watch's host tests (`watchapp/tests`), builds it and updates the bundled copy.

Run `connectedDebugAndroidTest` on an emulator to check that every card face fits on every watch
(not on your own phone: it uninstalls the app afterwards, and your saved cards with it).

Release signing uses `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`)
or the `ICR_*` environment variables. Without either, `assembleRelease` produces an unsigned APK.

## Layout

- `nfc/Felica.kt`: Polling, Request System Code and Read Without Encryption over `NfcF`
- `nfc/Parsers.kt`: block decoders; `nfc/CardReader.kt` works out the card family
- `data/`: models, `CardRepository` (JSON in app storage), `HistoryMerge`, `StationDb`, `Codes`
- `icon/`: `AppIconManager` (activity-alias switching); `IconResources.kt` is generated
- `ui/`: Compose screens
- `pebble/`: the watch link: `WatchSync` (what to send when), `WatchProtocol` (message layout,
  shared with `watchapp/src/c/protocol.h` and `codec.h`), `WatchArt` (card faces for the
  watch) and `PebbleListenerService`
- `watchapp/`: the Pebble watch app (C): `card_window` and `history_window` are the two
  screens, `cards` holds the list, art and history, `comms` the AppMessage link
- `tools/gen_icons.py`: renders the launcher icons (a miniature of each card face, as PNG layers
  per density), `IconResources.kt` and the manifest aliases. Needs Pillow; uses the bundled
  Open Sans Bold (`tools/fonts`, SIL OFL). Edit `ICONS` and re-run to add or change an icon.

Station data (`assets/StationCode.csv`) is the community-maintained Suica station code list that
circulates in open-source FeliCa readers. English names (`assets/StationNamesEn.csv`) are built from
Wikidata (CC0) by `tools/gen_station_names.py`, which matches on Japanese name and operator.
