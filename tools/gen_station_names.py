#!/usr/bin/env python3
"""Builds app/src/main/assets/StationNamesEn.csv (English station and operator names).

Station names and operators come from Wikidata (CC0). They are matched to StationCode.csv by
Japanese station name, preferring a match on operator as well.

    python3 tools/gen_station_names.py            # fetch from Wikidata, then build
    python3 tools/gen_station_names.py --cache D  # reuse/keep downloaded files in directory D
"""
import csv
import collections
import pathlib
import re
import subprocess
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/main/assets"
UA = "ic-reader-dev/0.1 (personal project; github Grillza)"
# Wikidata classes: railway station, metro station, railway stop, tram stop, monorail station, ...
CLASSES = ["Q55488", "Q928830", "Q55678", "Q2175765", "Q1339195", "Q1793804", "Q12819564"]

# Short, familiar English names for the big operators (Wikidata gives formal names).
OPERATOR_OVERRIDES = {
    "東日本旅客鉄道": "JR East",
    "西日本旅客鉄道": "JR West",
    "東海旅客鉄道": "JR Central",
    "北海道旅客鉄道": "JR Hokkaido",
    "九州旅客鉄道": "JR Kyushu",
    "四国旅客鉄道": "JR Shikoku",
    "東京都交通局": "Toei",
    "東京地下鉄": "Tokyo Metro",
    "大阪市交通局": "Osaka Metro",
    "大阪市高速電気軌道": "Osaka Metro",
    "名古屋市交通局": "Nagoya Subway",
    "横浜市交通局": "Yokohama Subway",
    "福岡市交通局": "Fukuoka Subway",
    "京都市交通局": "Kyoto Subway",
    "神戸市交通局": "Kobe Subway",
    "札幌市交通局": "Sapporo Subway",
    "仙台市交通局": "Sendai Subway",
}


# Names Wikidata doesn't match (special entries and a few renamed stations).
MANUAL = {
    "羽田空港第1ビル": "Haneda Airport Terminal 1",
    "羽田空港第2ビル": "Haneda Airport Terminal 2",
    "羽田空港第3ビル": "Haneda Airport Terminal 3",
    "モバイルSuica": "Mobile Suica",
    "Suicaインターネットサービス": "Suica Internet Service",
    "臨時窓口": "Temporary counter",
}


def sparql(query: str) -> list[dict]:
    # The query service may rate-limit hard (1 request/min); wait it out.
    for attempt in range(6):
        r = subprocess.run(
            ["curl", "-s", "--data-urlencode", "query=" + query, "-H", "Accept: text/csv", "-A", UA,
             "https://query.wikidata.org/sparql"],
            capture_output=True, text=True,
        )
        if r.stdout.startswith(("s,", "op,")):
            return list(csv.DictReader(r.stdout.splitlines()))
        time.sleep(65)
    raise SystemExit("Wikidata query failed: " + r.stdout[:200])


def fetch(cache: pathlib.Path):
    cache.mkdir(parents=True, exist_ok=True)
    for cls in CLASSES:
        f = cache / f"st_{cls}.csv"
        if f.exists():
            continue
        rows = sparql(
            f'SELECT ?s ?ja ?en ?op WHERE {{ ?s wdt:P31 wd:{cls}; wdt:P17 wd:Q17; rdfs:label ?ja. FILTER(lang(?ja)="ja") '
            f'?s rdfs:label ?en. FILTER(lang(?en)="en") OPTIONAL {{ ?s wdt:P137 ?op }} }}'
        )
        write(f, ["s", "ja", "en", "op"], rows)
        time.sleep(3)
    f = cache / "ops.csv"
    if not f.exists():
        ops = sorted({r["op"].rsplit("/", 1)[1] for r in stations(cache) if r["op"]})
        rows = []
        for i in range(0, len(ops), 200):
            vals = " ".join("wd:" + q for q in ops[i:i + 200])
            rows += sparql(
                f'SELECT ?op ?ja ?en WHERE {{ VALUES ?op {{{vals}}} OPTIONAL{{?op rdfs:label ?ja FILTER(lang(?ja)="ja")}} '
                f'OPTIONAL{{?op rdfs:label ?en FILTER(lang(?en)="en")}} }}'
            )
            time.sleep(2)
        write(f, ["op", "ja", "en"], rows)


def write(path, fields, rows):
    with open(path, "w", encoding="utf-8", newline="") as fh:
        w = csv.DictWriter(fh, fields)
        w.writeheader()
        w.writerows(rows)


def stations(cache):
    for f in sorted(cache.glob("st_*.csv")):
        yield from csv.DictReader(open(f, encoding="utf-8"))


def fold(s: str) -> str:
    """Full-width ASCII to half-width, trimmed."""
    return "".join(chr(ord(c) - 0xFEE0) if "！" <= c <= "～" else (" " if c == "　" else c) for c in s).strip()


VARIANTS = str.maketrans({"ヶ": "ケ", "ヵ": "ケ", "ｹ": "ケ", "鴬": "鶯", "廚": "厨", "龍": "竜", "髙": "高", "﨑": "崎", "塚": "塚"})


def clean_ja(s: str) -> str:
    s = re.sub(r"[（(].*?[）)]", "", fold(s)).strip().translate(VARIANTS)
    return s[:-1] if s.endswith("駅") and len(s) > 1 else s


def clean_en(s: str) -> str:
    s = re.sub(r"\s*\(.*?\)", "", s).strip().replace("\u2010", "-").replace("\u2011", "-")
    s = re.sub(r"\s+(Station|station|Stop|stop|Tram Stop|Stn\.?)$", "", s).strip()
    return s


def fetch_by_label(cache: pathlib.Path, names: list[str]):
    """Stations Wikidata files under other classes (big interchanges): look them up by "<name>駅"."""
    f = cache / "st_bylabel.csv"
    if f.exists():
        return
    rows = []
    for i in range(0, len(names), 150):
        labels = []
        for n in names[i:i + 150]:
            for v in {n, n.replace("ケ", "ヶ")}:
                labels.append('"%s駅"@ja' % v.replace('"', ""))
        rows += sparql(
            'SELECT ?s ?ja ?en ?op WHERE { VALUES ?ja { %s } ?s rdfs:label ?ja; wdt:P17 wd:Q17. '
            '?s rdfs:label ?en FILTER(lang(?en)="en") OPTIONAL { ?s wdt:P137 ?op } }' % " ".join(labels)
        )
        time.sleep(65)
    write(f, ["s", "ja", "en", "op"], rows)


def main():
    cache = pathlib.Path(sys.argv[sys.argv.index("--cache") + 1]) if "--cache" in sys.argv else ROOT / "build/wikidata"
    fetch(cache)
    known = {clean_ja(r["ja"]) for r in stations(cache)}
    missing = sorted({clean_ja(r[5]) for r in csv.reader(open(ASSETS / "StationCode.csv", encoding="utf-8"))
                      if len(r) >= 6 and clean_ja(r[5]) not in known and "試験" not in r[5]})
    fetch_by_label(cache, missing)

    ops = {}
    for r in csv.DictReader(open(cache / "ops.csv", encoding="utf-8")):
        ops[r["op"]] = (r.get("ja") or "", r.get("en") or "")
    op_en = {fold(ja): en for ja, en in ops.values() if ja and en}
    op_en.update(OPERATOR_OVERRIDES)

    by_name = collections.defaultdict(list)  # ja station name -> [(operator ja, en name)]
    for r in stations(cache):
        en = clean_en(r["en"])
        if not en or re.search(r"[぀-ヿ一-鿿]", en):
            continue
        op_ja = fold(ops.get(r["op"], ("", ""))[0]) if r["op"] else ""
        by_name[clean_ja(r["ja"])].append((op_ja, en))

    out, matched, total = [], 0, 0
    for row in csv.reader(open(ASSETS / "StationCode.csv", encoding="utf-8")):
        if len(row) < 6:
            continue
        area, line, station, company, _line_name, name = row[:6]
        total += 1
        company = fold(company)
        cands = by_name.get(clean_ja(name), [])
        en = None
        same_op = [e for o, e in cands if o and (o == company or o in company or company in o)]
        if same_op:
            en = collections.Counter(same_op).most_common(1)[0][0]
        elif cands:
            en = collections.Counter(e for _, e in cands).most_common(1)[0][0]
        if not en:
            en = MANUAL.get(fold(name)) or ("Terminal test" if "試験" in name else None)
        if en:
            matched += 1
        out.append([area, line, station, en or "", op_en.get(company, "")])

    with open(ASSETS / "StationNamesEn.csv", "w", encoding="utf-8", newline="") as fh:
        csv.writer(fh, lineterminator="\n").writerows(out)
    print(f"English names for {matched}/{total} stations")


if __name__ == "__main__":
    main()
