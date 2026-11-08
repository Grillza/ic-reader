package io.github.grillza.icreader

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.IntentCompat
import io.github.grillza.icreader.ui.AppRoot
import io.github.grillza.icreader.ui.AppViewModel
import io.github.grillza.icreader.ui.NfcState
import io.github.grillza.icreader.ui.theme.IcReaderTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()
    private val nfcState = mutableStateOf(NfcState.Unsupported)
    private val adapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IcReaderTheme {
                AppRoot(viewModel, nfcState.value, onExit = ::finish)
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        (application as IcReaderApp).icons.hold = false
        val nfc = adapter
        nfcState.value = when {
            nfc == null -> NfcState.Unsupported
            !nfc.isEnabled -> NfcState.Disabled
            else -> NfcState.Enabled
        }
        enableReader()
        // Android can reject reader mode if asked a moment before it counts us as foreground
        // ("Caller is not in foreground"); ask again shortly after, and on focus below.
        window.decorView.postDelayed({ if (isResumed()) enableReader() }, 400)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableReader()
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        if (isTopResumedActivity) enableReader()
    }

    private fun isResumed() = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)

    /** While the app is in front, any FeliCa tap comes straight here instead of to other apps. */
    private fun enableReader() {
        val nfc = adapter ?: return
        if (!nfc.isEnabled || !isResumed()) return
        nfc.enableReaderMode(
            this,
            ::onTag,
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 500) },
        )
    }

    override fun onPause() {
        super.onPause()
        adapter?.disableReaderMode(this)
    }

    override fun onStop() {
        super.onStop()
        // Safe now: switching aliases closes the task, which doesn't matter once we're in the background.
        if (!isChangingConfigurations) (application as IcReaderApp).icons.applyPending()
    }

    private fun onTag(tag: Tag) {
        buzz()
        viewModel.onTag(tag)
    }

    /** Card tapped while the app was closed: the system launched us with the tag. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action != NfcAdapter.ACTION_TECH_DISCOVERED) return
        IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java)?.let(viewModel::onTag)
    }

    private fun buzz() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
