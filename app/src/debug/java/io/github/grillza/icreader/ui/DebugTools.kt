package io.github.grillza.icreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.grillza.icreader.IcReaderApp
import io.github.grillza.icreader.data.CardRepository
import io.github.grillza.icreader.data.CardType
import io.github.grillza.icreader.data.SavedCard
import io.github.grillza.icreader.data.ScanResult
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Debug builds only: made-up cards of any type with balances you can change, for checking the
 * card faces and the watch app quickly. Release builds have an empty stand-in (src/release).
 */
internal object DebugTools {
    const val AVAILABLE = true
    const val MENU_LABEL = "Test cards (debug)"

    /** Test cards' IDs start with this, so they can be told apart from real ones. */
    private const val TEST_PREFIX = "7E57"

    private fun isTest(card: SavedCard) = card.id.startsWith(TEST_PREFIX)

    private suspend fun add(repo: CardRepository, type: CardType, balance: Int, name: String) {
        val id = TEST_PREFIX + (1..12).joinToString("") { "%X".format(Random.nextInt(16)) }
        repo.saveScan(ScanResult(id, type.family, balance, emptyList(), emptyList()), type, name.ifBlank { null })
    }

    private suspend fun setBalance(repo: CardRepository, id: String, balance: Int) {
        repo.edit(id) { it.copy(balance = balance, lastScanned = System.currentTimeMillis()) }
    }

    /** Add test cards and change any card's balance (the watch updates as it would after a scan). */
    @Composable
    fun TestCardsDialog(onDismiss: () -> Unit) {
        val repo = (LocalContext.current.applicationContext as IcReaderApp).repository
        val cards by repo.cards.collectAsState()
        val scope = rememberCoroutineScope()
        var type by remember { mutableStateOf(CardType.SUICA) }
        var typeMenu by remember { mutableStateOf(false) }
        var name by remember { mutableStateOf("") }
        var balance by remember { mutableStateOf("20000") }

        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
            title = { Text("Test cards") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add a card", style = MaterialTheme.typography.titleSmall)
                    Box {
                        OutlinedButton(onClick = { typeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(type.displayName, Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(typeMenu, onDismissRequest = { typeMenu = false }) {
                            CardType.entries.forEach { t ->
                                DropdownMenuItem(text = { Text(t.displayName) }, onClick = { type = t; typeMenu = false })
                            }
                        }
                    }
                    OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        balance,
                        { balance = it.filter(Char::isDigit).take(6) },
                        label = { Text("Balance (¥)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { scope.launch { add(repo, type, balance.toIntOrNull() ?: 0, name) }; name = "" },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Add ${type.displayName}") }

                    if (cards.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text("Balances", style = MaterialTheme.typography.titleSmall)
                        cards.forEach { card -> BalanceRow(card, isTest(card), repo) }
                    }
                }
            },
        )
    }

    @Composable
    private fun BalanceRow(card: SavedCard, test: Boolean, repo: CardRepository) {
        val scope = rememberCoroutineScope()
        var value by remember(card.id, card.balance) { mutableStateOf(card.balance.toString()) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(card.title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (test) "${card.type.displayName} · test" else "${card.type.displayName} · ${formatYen(card.balance)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value,
                { value = it.filter(Char::isDigit).take(6) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(96.dp),
            )
            TextButton(
                onClick = { value.toIntOrNull()?.let { scope.launch { setBalance(repo, card.id, it) } } },
                enabled = value.toIntOrNull() != null && value.toIntOrNull() != card.balance,
            ) { Text("Set") }
            // Only test cards can be removed here, so a real card can't be lost by accident.
            if (test) {
                IconButton(onClick = { scope.launch { repo.delete(card.id) } }) { Icon(Icons.Default.Delete, "Remove") }
            }
        }
    }
}
