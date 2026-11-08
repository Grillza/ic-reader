package io.github.grillza.icreader.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime

/** What kind of data layout the card uses; decides how it is read. */
@Serializable
enum class CardFamily(val label: String) {
    TRANSIT("Transit IC"),
    EDY("Rakuten Edy"),
    NANACO("nanaco"),
    WAON("WAON"),
}

/**
 * The brand of a card, used for its artwork and app icon. Transit brands share one data layout,
 * so the brand can't be read from the card reliably; it is suggested and the user confirms it.
 */
enum class CardType(
    val id: String,
    val displayName: String,
    val nameJa: String,
    val issuer: String,
    val family: CardFamily,
    val colorStart: Long,
    val colorEnd: Long,
    val onColor: Long = 0xFFFFFFFF,
) {
    SUICA("suica", "Suica", "スイカ", "JR East", CardFamily.TRANSIT, 0xFF1E9E4A, 0xFF8BC53F),
    PASMO("pasmo", "PASMO", "パスモ", "PASMO Co.", CardFamily.TRANSIT, 0xFFE8338A, 0xFFF7A1C4),
    ICOCA("icoca", "ICOCA", "イコカ", "JR West", CardFamily.TRANSIT, 0xFF1D50A2, 0xFF5B9BE0),
    TOICA("toica", "TOICA", "トイカ", "JR Central", CardFamily.TRANSIT, 0xFFF39800, 0xFFFFCB5C),
    MANACA("manaca", "manaca", "マナカ", "Nagoya", CardFamily.TRANSIT, 0xFF9B2A8C, 0xFFE05BB5),
    KITACA("kitaca", "Kitaca", "キタカ", "JR Hokkaido", CardFamily.TRANSIT, 0xFF3FB59A, 0xFF9EDCC8),
    SUGOCA("sugoca", "SUGOCA", "スゴカ", "JR Kyushu", CardFamily.TRANSIT, 0xFF00A3AF, 0xFF7FD3D8),
    NIMOCA("nimoca", "nimoca", "ニモカ", "Nishitetsu", CardFamily.TRANSIT, 0xFFF6C200, 0xFFFFE680, 0xFF2B2B2B),
    HAYAKAKEN("hayakaken", "Hayakaken", "はやかけん", "Fukuoka Subway", CardFamily.TRANSIT, 0xFFD6001C, 0xFFFF6B6B),
    PITAPA("pitapa", "PiTaPa", "ピタパ", "Surutto KANSAI", CardFamily.TRANSIT, 0xFF14286E, 0xFF4767B8),
    TRANSIT("transit", "Transit IC", "交通系IC", "Other", CardFamily.TRANSIT, 0xFF455A64, 0xFF90A4AE),
    EDY("edy", "Rakuten Edy", "楽天Edy", "Rakuten", CardFamily.EDY, 0xFFBF0000, 0xFFF0444A),
    NANACO("nanaco", "nanaco", "ナナコ", "Seven & i", CardFamily.NANACO, 0xFFF5A200, 0xFFFFD866, 0xFF2B2B2B),
    WAON("waon", "WAON", "ワオン", "AEON", CardFamily.WAON, 0xFF00A0E9, 0xFF86D3F7),
    ;

    companion object {
        fun fromId(id: String): CardType = entries.firstOrNull { it.id == id } ?: TRANSIT

        fun defaultFor(family: CardFamily): CardType = when (family) {
            CardFamily.TRANSIT -> TRANSIT
            CardFamily.EDY -> EDY
            CardFamily.NANACO -> NANACO
            CardFamily.WAON -> WAON
        }
    }
}

@Serializable
enum class TxKind { TRAIN, BUS, PURCHASE, CHARGE, OTHER }

/** A station as encoded on transit cards: area (0-3, see StationDb), line and station code. */
@Serializable
data class StationRef(val area: Int, val line: Int, val station: Int)

@Serializable
data class Transaction(
    /** Stable identity used to merge repeated scans of the same record. */
    val key: String,
    val seq: Int,
    val year: Int,
    val month: Int,
    val day: Int,
    val secondOfDay: Int? = null,
    val kind: TxKind,
    val terminal: Int = 0,
    val process: Int = 0,
    /** Balance after this transaction, in yen. */
    val balance: Int,
    /** Signed change in yen (negative = spent); null when it can't be worked out. */
    val amount: Int? = null,
    /** True when [amount] came straight from the card rather than from balance differences. */
    val amountFromCard: Boolean = false,
    val entry: StationRef? = null,
    val exit: StationRef? = null,
    val busOperator: Int? = null,
    val busStop: Int? = null,
    val raw: String,
) {
    /** Card data can be garbage; clamp rather than throw. */
    val date: LocalDate
        get() {
            val first = LocalDate.of(year.coerceIn(2000, 2099), month.coerceIn(1, 12), 1)
            return first.withDayOfMonth(day.coerceIn(1, first.lengthOfMonth()))
        }
    val dateTime: LocalDateTime? get() = secondOfDay?.let { date.atStartOfDay().plusSeconds(it.toLong()) }
}

@Serializable
data class SavedCard(
    /** IDm of the card's main system, hex. */
    val id: String,
    val family: CardFamily,
    val typeId: String,
    val nickname: String? = null,
    val balance: Int,
    val addedAt: Long,
    val lastScanned: Long,
    /** Raw public card info (attributes block, PMm) from the last scan, for diagnostics. */
    val cardInfo: Map<String, String> = emptyMap(),
    /** File name of a user photo under filesDir/card_images, shown instead of the generated art. */
    val customImage: String? = null,
    val transactions: List<Transaction> = emptyList(),
) {
    val type: CardType get() = CardType.fromId(typeId)
    val title: String get() = nickname?.takeIf { it.isNotBlank() } ?: type.displayName
}

/** Everything read from one tap. */
data class ScanResult(
    val idm: String,
    val family: CardFamily,
    val balance: Int,
    val transactions: List<Transaction>,
    val systemCodes: List<Int>,
    /**
     * Likely brands, best first. Transit brands in the same region can't be told apart from the
     * card's data, so this is usually a region's set (e.g. Suica and PASMO for Kanto).
     */
    val suggestedTypes: List<CardType> = emptyList(),
    /** Raw public card info (attributes block, PMm), kept for diagnostics. */
    val cardInfo: Map<String, String> = emptyMap(),
) {
    val suggestedType: CardType? get() = suggestedTypes.firstOrNull()
}
