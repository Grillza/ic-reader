package io.github.grillza.icreader

import io.github.grillza.icreader.data.BackupMerge
import io.github.grillza.icreader.data.CardFamily
import io.github.grillza.icreader.data.RestoreResult
import io.github.grillza.icreader.data.SavedCard
import io.github.grillza.icreader.data.Transaction
import io.github.grillza.icreader.data.TxKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMergeTest {

    private fun tx(seq: Int, day: Int, balance: Int) =
        Transaction(key = "k$seq", seq = seq, year = 2026, month = 11, day = day, kind = TxKind.TRAIN, balance = balance, raw = "")

    private fun card(id: String, scanned: Long, balance: Int, txs: List<Transaction>, nickname: String? = null, image: String? = null) =
        SavedCard(id = id, family = CardFamily.TRANSIT, typeId = "suica", nickname = nickname, balance = balance,
            addedAt = scanned, lastScanned = scanned, customImage = image, transactions = txs)

    @Test
    fun restoringIntoAnEmptyAppAddsEveryCard() {
        val backup = listOf(card("a", 10, 500, listOf(tx(1, 1, 500))), card("b", 20, 100, emptyList()))
        val (cards, result) = BackupMerge.merge(emptyList(), backup)
        assertEquals(listOf("a", "b"), cards.map { it.id })
        assertEquals(RestoreResult(added = 2, updated = 0), result)
        assertEquals(backup, cards)
    }

    @Test
    fun aCardScannedSinceTheBackupKeepsItsNewerBalanceAndGainsOlderHistory() {
        // Backed up with records 1-3; since then the app was reinstalled and the card rescanned,
        // and the card itself only still holds records 3-4.
        val backup = card("a", 10, 300, listOf(tx(3, 3, 300), tx(2, 2, 400), tx(1, 1, 500)), nickname = "Mine", image = "a.jpg")
        val current = card("a", 50, 200, listOf(tx(4, 4, 200), tx(3, 3, 300)))
        val (cards, result) = BackupMerge.merge(listOf(current), listOf(backup))
        val merged = cards.single()
        assertEquals(RestoreResult(added = 0, updated = 1), result)
        assertEquals(200, merged.balance)
        assertEquals(50, merged.lastScanned)
        assertEquals(10, merged.addedAt)
        assertEquals(listOf(4, 3, 2, 1), merged.transactions.map { it.seq })
        assertEquals(-100, merged.transactions.first().amount)
        assertEquals("Mine", merged.nickname)
        assertEquals("a.jpg", merged.customImage)
    }

    @Test
    fun anOlderBackupDoesNotRollBackTheBalanceOrRenameTheCard() {
        val current = card("a", 50, 200, listOf(tx(4, 4, 200)), nickname = "Work")
        val backup = card("a", 10, 999, listOf(tx(1, 1, 999)), nickname = "Old name")
        val merged = BackupMerge.merge(listOf(current), listOf(backup)).first.single()
        assertEquals(200, merged.balance)
        assertEquals("Work", merged.nickname)
    }

    @Test
    fun onlyAppMadePhotoNamesAreAccepted() {
        assertTrue(BackupMerge.isSafeImageName("0123ABCD-1731200000000.jpg"))
        assertFalse(BackupMerge.isSafeImageName("../cards.json"))
        assertFalse(BackupMerge.isSafeImageName("a/b.jpg"))
        assertFalse(BackupMerge.isSafeImageName(".hidden"))
        assertFalse(BackupMerge.isSafeImageName(""))
    }
}
