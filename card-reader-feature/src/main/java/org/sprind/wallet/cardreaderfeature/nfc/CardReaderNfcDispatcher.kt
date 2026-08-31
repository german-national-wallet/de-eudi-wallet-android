/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sprind.wallet.cardreaderfeature.nfc

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Handler
import android.os.Looper
import eu.europa.ec.uilogic.extension.findActivity

/**
 * Foreground NFC dispatcher owned by the card reader feature.
 *
 * It mirrors the SDK's own dispatcher (continuous polling via
 * [NfcAdapter.enableReaderMode]) but hands the detected [Tag] to [onTagDetected]
 * instead of forwarding it directly to the SDK.
 *
 * [restart] re-arms reader mode so the platform re-discovers a card that is
 * already resting on the sensor and emits a *fresh* [Tag] for it. This is what
 * lets a left-on-the-sensor card be read without a re-tap: an Android [Tag]
 * becomes stale once its connection cycle ends, so it cannot be re-dispatched;
 * a re-discovery is required to obtain a usable tag.
 *
 * A single reader-mode toggle often fails to re-enumerate a card that never left
 * the RF field and was just connected — this is the case for the follow-up
 * CAN / Transport PIN reads. [restart] therefore keeps re-arming on an interval
 * until a usable tag arrives or the attempt budget is spent, after which reader
 * mode is left enabled so a manual tap is still picked up.
 */
class CardReaderNfcDispatcher(
    private val context: Context,
    private val onTagDetected: (Tag) -> Unit,
) {

    private val nfcAdapter: NfcAdapter? by lazy {
        NfcAdapter.getDefaultAdapter(context)
    }

    // FLAG_READER_NFC_A | FLAG_READER_NFC_B | FLAG_READER_SKIP_NDEF_CHECK
    private val readerFlags: Int =
        NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK

    private val isoDepTechnology: String = IsoDep::class.java.name

    private val mainHandler = Handler(Looper.getMainLooper())
    private val enableRunnable = Runnable { enable() }
    private val rearmRunnable = Runnable { rearm() }

    private var isReaderModeEnabled = false
    private var rearmAttempt = 0

    fun start() {
        rearmAttempt = 0
        enable()
    }

    fun stop() {
        cancelPending()
        rearmAttempt = 0
        disable()
    }

    /**
     * Turns reader mode off and on again after a short settle delay so the NFC
     * controller re-polls and re-discovers a card that is still on the sensor,
     * retrying on an interval because one toggle is often not enough to
     * re-enumerate a card that was just connected.
     */
    fun restart() {
        rearmAttempt = 0
        rearm()
    }

    private fun rearm() {
        cancelPending()
        disable()
        mainHandler.postDelayed(enableRunnable, FIELD_SETTLE_DELAY_MS)
        if (rearmAttempt < MAX_REARM_ATTEMPTS - 1) {
            rearmAttempt++
            mainHandler.postDelayed(rearmRunnable, FIELD_SETTLE_DELAY_MS + REARM_RETRY_INTERVAL_MS)
        }
    }

    private fun cancelPending() {
        mainHandler.removeCallbacks(enableRunnable)
        mainHandler.removeCallbacks(rearmRunnable)
    }

    private fun enable() {
        if (isReaderModeEnabled) return
        val adapter = nfcAdapter ?: return
        isReaderModeEnabled = true
        adapter.enableReaderMode(context.findActivity(), ::onTagDiscovered, readerFlags, null)
    }

    private fun disable() {
        if (!isReaderModeEnabled) return
        isReaderModeEnabled = false
        nfcAdapter?.disableReaderMode(context.findActivity())
    }

    private fun onTagDiscovered(tag: Tag) {
        if (isoDepTechnology !in tag.techList) return
        // enableReaderMode delivers this callback on a binder thread, not the
        // main thread. Hop to the main thread so all dispatcher state and the
        // retry cancellation are touched from a single thread and are serialized
        // with rearm(); cancelling only the next re-arm (not a pending re-enable)
        // leaves reader mode on for the read that is about to start.
        mainHandler.post {
            mainHandler.removeCallbacks(rearmRunnable)
            rearmAttempt = 0
            onTagDetected(tag)
        }
    }

    private companion object {
        // Keep the RF field down long enough for the controller to actually drop
        // and re-enumerate a card that is still resting on the sensor; 300ms was
        // too short for a card that had just been connected (CAN / Transport PIN).
        const val FIELD_SETTLE_DELAY_MS = 750L

        // Gap after reader mode is re-enabled before the next re-arm attempt,
        // giving a present card time to be enumerated before toggling again.
        const val REARM_RETRY_INTERVAL_MS = 1200L

        // Bounded so reader mode is not toggled forever when no card is present.
        const val MAX_REARM_ATTEMPTS = 8
    }
}