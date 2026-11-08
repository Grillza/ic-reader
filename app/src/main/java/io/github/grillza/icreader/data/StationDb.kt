package io.github.grillza.icreader.data

import java.io.InputStream

data class Station(
    val company: String,
    val line: String,
    val name: String,
    val nameEn: String? = null,
    val companyEn: String? = null,
)

/** How station names are shown. */
enum class StationLanguage(val label: String) {
    JAPANESE("Japanese (日本語)"),
    ENGLISH("English"),
    BOTH("English with Japanese"),
}

/**
 * The station as it should be displayed. In English the line (only known in Japanese) is
 * replaced by the operator's English name.
 */
fun Station.localized(language: StationLanguage): Station = when (language) {
    StationLanguage.JAPANESE -> this
    StationLanguage.ENGLISH -> copy(name = nameEn ?: name, company = companyEn ?: company, line = companyEn ?: line)
    StationLanguage.BOTH -> copy(
        name = nameEn?.let { "$it ($name)" } ?: name,
        company = companyEn ?: company,
        line = companyEn ?: line,
    )
}

/**
 * Station names from assets/StationCode.csv (area,line,station,company,line name,station name).
 * Areas: 0 = JR and Kanto private lines, 1 = Chubu, 2 = Kansai, 3 = elsewhere.
 */
class StationDb(
    private val open: () -> InputStream,
    /** Optional English names (area,line,station,name,operator), see tools/gen_station_names.py. */
    private val openEnglish: (() -> InputStream)? = null,
) {

    private val stations: Map<Int, Station> by lazy {
        val map = HashMap<Int, Station>(8000)
        open().bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                val f = line.split(',')
                if (f.size < 6) return@forEach
                val area = f[0].trim().toIntOrNull() ?: return@forEach
                val ln = f[1].trim().toIntOrNull() ?: return@forEach
                val st = f[2].trim().toIntOrNull() ?: return@forEach
                map[key(area, ln, st)] = Station(normalize(f[3]), normalize(f[4]), normalize(f[5]))
            }
        }
        runCatching {
            openEnglish?.invoke()?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
                lines.forEach { line ->
                    val f = line.split(',')
                    if (f.size < 5) return@forEach
                    val k = key(f[0].toIntOrNull() ?: return@forEach, f[1].toIntOrNull() ?: return@forEach, f[2].toIntOrNull() ?: return@forEach)
                    val s = map[k] ?: return@forEach
                    map[k] = s.copy(nameEn = f[3].ifBlank { null }, companyEn = f[4].ifBlank { null })
                }
            }
        }
        map
    }

    fun warmUp() {
        stations.size
    }

    /** Looks in the encoded area first, then the others (region bits aren't always reliable). */
    fun lookup(ref: StationRef): Station? =
        stations[key(ref.area, ref.line, ref.station)]
            ?: (0..3).firstNotNullOfOrNull { a -> if (a == ref.area) null else stations[key(a, ref.line, ref.station)] }

    /**
     * Likely brands, best first. The card doesn't record its issuer in any readable form, and
     * where it is used says little about who issued it (a Suica rides the subway as much as a
     * PASMO), so this picks the region the card is used in and returns that region's brands.
     * A "new card issued" record, when still on the card, puts that issuer first.
     */
    fun suggestTransitTypes(history: List<Transaction>): List<CardType> {
        val issuer = history.firstOrNull { (it.process and 0x7F) == 0x07 }
            ?.let { tx -> (tx.exit ?: tx.entry)?.let(::lookup)?.let(::brandFor) }
        val votes = history.flatMap { listOfNotNull(it.entry, it.exit) }
            .mapNotNull { lookup(it)?.let(::brandFor) }
            .groupingBy { region(it) }.eachCount()
        val region = issuer?.let(::region) ?: votes.maxByOrNull { it.value }?.key ?: return emptyList()
        val brands = REGIONS.getValue(region)
        return if (issuer != null) listOf(issuer) + (brands - issuer) else brands
    }

    fun suggestTransitType(history: List<Transaction>): CardType? = suggestTransitTypes(history).firstOrNull()

    private fun region(type: CardType): String = REGIONS.entries.first { type in it.value }.key

    private fun brandFor(station: Station): CardType? {
        val c = station.company
        // JR companies appear both as formal names (東日本旅客鉄道) and as "JR東日本".
        val jr = c.startsWith("JR") || c.endsWith("旅客鉄道")
        return when {
            jr && "東日本" in c -> CardType.SUICA
            jr && "西日本" in c -> CardType.ICOCA
            jr && "四国" in c -> CardType.ICOCA
            jr && "東海" in c -> CardType.TOICA
            jr && "北海道" in c -> CardType.KITACA
            jr && "九州" in c -> CardType.SUGOCA
            c == "西日本鉄道" -> CardType.NIMOCA
            "福岡市" in c -> CardType.HAYAKAKEN
            else -> regionBrand(station)
        }
    }

    private val chubuCompanies = setOf("名古屋鉄道", "名古屋市交通局", "愛知高速交通", "名古屋臨海高速鉄道", "豊橋鉄道")
    private val kansaiHints = listOf("大阪", "京都", "神戸", "阪急", "阪神", "京阪", "南海", "近畿日本", "山陽電気", "北大阪", "泉北")
    private val kantoHints = listOf("東京", "東急", "京王", "小田急", "西武", "東武", "京急", "京浜急行", "京成", "相模", "横浜", "東葉", "つくば", "首都圏", "りんかい", "ゆりかもめ", "埼玉", "千葉", "多摩")

    private fun regionBrand(station: Station): CardType? = when {
        station.company in chubuCompanies -> CardType.MANACA
        kansaiHints.any { it in station.company } -> CardType.ICOCA
        kantoHints.any { it in station.company } -> CardType.PASMO
        else -> null
    }

    private fun key(area: Int, line: Int, station: Int) = (area shl 16) or (line shl 8) or station

    companion object {
        /** Brands per region; the first is the more common one and is preselected. */
        private val REGIONS: Map<String, List<CardType>> = linkedMapOf(
            "kanto" to listOf(CardType.SUICA, CardType.PASMO),
            "kansai" to listOf(CardType.ICOCA, CardType.PITAPA),
            "chubu" to listOf(CardType.TOICA, CardType.MANACA),
            "hokkaido" to listOf(CardType.KITACA),
            "kyushu" to listOf(CardType.SUGOCA, CardType.NIMOCA, CardType.HAYAKAKEN),
        )

        /** The CSV uses full-width Latin letters (ＪＲ東日本); fold them to ASCII. */
        fun normalize(s: String): String = buildString(s.length) {
            for (ch in s.trim()) {
                append(if (ch in '！'..'～') (ch.code - 0xFEE0).toChar() else if (ch == '　') ' ' else ch)
            }
        }
    }
}
