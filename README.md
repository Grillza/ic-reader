# IC Reader

Android app for reading Japanese FeliCa IC cards: balance and history for Suica, PASMO, ICOCA and the
other transit cards, plus Rakuten Edy, nanaco and WAON. Saves multiple cards, builds up history
across scans, and lets the launcher icon match your card.

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

Release signing uses `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`)
or the `ICR_*` environment variables. Without either, `assembleRelease` produces an unsigned APK.

## Layout

- `nfc/Felica.kt`: Polling, Request System Code and Read Without Encryption over `NfcF`
- `nfc/Parsers.kt`: block decoders; `nfc/CardReader.kt` works out the card family
- `data/`: models, `CardRepository` (JSON in app storage), `HistoryMerge`, `StationDb`, `Codes`
- `icon/`: `AppIconManager` (activity-alias switching); `IconResources.kt` is generated
- `ui/`: Compose screens
- `tools/gen_icons.py`: generates the launcher icons, colours, `IconResources.kt` and the manifest
  aliases. Edit `ICONS` there and re-run it to add or change an icon.

Station data (`assets/StationCode.csv`) is the community-maintained Suica station code list that
circulates in open-source FeliCa readers. English names (`assets/StationNamesEn.csv`) are built from
Wikidata (CC0) by `tools/gen_station_names.py`, which matches on Japanese name and operator.
