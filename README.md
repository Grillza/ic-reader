# IC Reader

Android app for reading Japanese FeliCa IC cards: balance and history for Suica, PASMO, ICOCA and the
other transit cards, plus Rakuten Edy, nanaco and WAON. Saves multiple cards, builds up history
across scans, lets the launcher icon match your card, and shows your cards on a Pebble watch.

## Download

Get the APK from [Releases](https://github.com/Grillza/ic-reader/releases) (Android 8 or later,
with NFC). The Pebble watch app is installed from inside the app (Settings → Pebble watch).
Found a problem? [Open an issue](https://github.com/Grillza/ic-reader/issues).

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
  green panel, ICOCA's cut-corner blue panel, Kitaca's grey sky over a forest…), with the
  lettering placed as on the real card. Logos and characters aren't reproduced; add a photo of
  your own card for the real thing: edit the card, **Choose photo**, then fit it to the
  card-shaped frame (drag, pinch, twist or the straighten slider, and 90° turns). It's saved cut to the card's outline, notch included. Card info (menu) shows the
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
- Station names on the watch can be English (the default), Japanese or both: Settings → Pebble
  watch → Station names on the watch. Japanese needs the watch's Japanese language pack; without
  it, Japanese text shows blank.

Set up in the app under **Settings → Pebble watch**:

1. Install the Pebble app and pair your watch.
2. Switch on **Enable Pebble watch integration** (off by default; while it's off the Pebble app
   can't reach IC Reader at all).
3. Tap **Install on watch**. The Pebble app opens and installs IC Reader on the watch.
4. Tap **Open on watch**, or open IC Reader from the watch's app menu.

The phone and watch talk over [PebbleKit 2](https://github.com/pebble-dev/PebbleKitAndroid2).
When the watch app opens, the Pebble app starts IC Reader's `PebbleListenerService`; the watch
says hello with the card art size it wants, and the phone answers with the card list and each
card's face. A scan while the watch app is open sends the new balance at once. The watch app
names this app as its companion (`companionApp` in `watchapp/package.json`), so the Pebble app
only passes its messages to IC Reader.

Card faces are drawn on the phone at the watch's size, made bolder, and each colour is matched to
the Pebble colour that *looks* closest on the watch's screen, which shows colours much paler than
their values (`WatchColors`, using pebble-tool's screen colour table). They're sent run-length
compressed (1-2 KB a card). A card photo is smoothed into flat patches until it fits (6 KB, 2 KB
on the original Pebble). Black-and-white watches get a black and white version.

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

Debug builds have two extra menu items, for trying things without real cards:

- **Load demo card** runs a made-up Suica scan through the normal flow (preview, then save).
- **Test cards** adds cards of any type with a balance you choose, and changes any card's
  balance. The watch updates as it would after a scan, so it's a quick way to check every card
  face and the watch app. Test cards can be removed from the same dialog; real cards can't.

Release builds leave both out (`src/debug` and `src/release` each have their own `DemoData` and
`DebugTools`).

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
  Open Sans Bold and Noto Serif Bold cut down to Latin letters (`tools/fonts`, SIL OFL). Edit `ICONS` and re-run to add or change an icon.

Station data (`assets/StationCode.csv`) is the community-maintained Suica station code list that
circulates in open-source FeliCa readers. English names (`assets/StationNamesEn.csv`) are built from
Wikidata (CC0) by `tools/gen_station_names.py`, which matches on Japanese name and operator.

## Card artwork and photos

All original IC card artwork, logos and names belong to their respective companies. IC Reader
isn't affiliated with any card issuer and doesn't include their artwork: its card faces are its
own simple designs in each brand's colours. A photo you add of your own card is only for
identifying the card in the app and on your watch. It stays on your phone and watch (and in
backups you make yourself), and is never uploaded, published or used for profit.

## License

IC Reader is developed by Grillza and is free software under the
[GNU General Public License v3.0](LICENSE). The bundled fonts in `tools/fonts` are under the SIL
Open Font License.
