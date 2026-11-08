package io.github.grillza.icreader.data

/**
 * Cards only hold their last few records (20 for transit cards), so every scan is merged into
 * what we already have, building up a longer history over time.
 */
object HistoryMerge {

    /** Newest first. */
    val ORDER: Comparator<Transaction> =
        compareByDescending<Transaction> { it.date }.thenByDescending { it.seq }

    fun merge(existing: List<Transaction>, scanned: List<Transaction>): List<Transaction> {
        val byKey = LinkedHashMap<String, Transaction>()
        existing.forEach { byKey[it.key] = it }
        scanned.forEach { byKey[it.key] = it }
        return withAmounts(byKey.values.sortedWith(ORDER))
    }

    /**
     * Transit records only store the balance afterwards, so the amount is the difference from the
     * previous record - known only when that record is the immediately preceding sequence number.
     */
    fun withAmounts(newestFirst: List<Transaction>): List<Transaction> =
        newestFirst.mapIndexed { i, tx ->
            if (tx.amountFromCard) return@mapIndexed tx
            val previous = newestFirst.getOrNull(i + 1)
            val amount = if (previous != null && !previous.amountFromCard && ((previous.seq + 1) and 0xFFFF) == tx.seq) {
                tx.balance - previous.balance
            } else {
                tx.amount
            }
            if (amount == tx.amount) tx else tx.copy(amount = amount)
        }

    fun newCount(existing: List<Transaction>, scanned: List<Transaction>): Int {
        val keys = existing.mapTo(HashSet()) { it.key }
        return scanned.count { it.key !in keys }
    }
}
