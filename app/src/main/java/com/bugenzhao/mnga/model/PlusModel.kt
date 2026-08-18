package com.bugenzhao.mnga.model

import android.content.SharedPreferences
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** Catalog of Plus-gated features; case order = display order. */
enum class PlusFeature(val label: String, val description: String) {
    CUSTOM_APPEARANCE("Custom Appearance", "Full access to customizing the appearance of MNGA."),
    MULTI_ACCOUNT("Multiple Accounts", "Log in and switch between multiple accounts."),
    TOPIC_HISTORY("History", "View your footprint of topics you have explored."),
    MULTI_FAVORITE("Multiple Favorite Folders", "Organize favorite topics into multiple folders."),
    AUTHOR_ONLY("Author Only", "Check posts and replies from a specific author in a topic."),
    JUMP("Jump", "Jump to arbitrary floor or page in a topic."),
    RESUME_PROGRESS("Resume Reading Progress", "Resume reading progress from where you left off."),
    BLOCK_CONTENTS("Block Contents", "Block contents from specific users, or with specific keywords."),
    SYNC_FORUMS("Sync Favorite Forums", "Sync favorite forums across devices."),
    ANONYMOUS("Anonymous", "Post, quote, reply, comment, and create new topics anonymously."),
    NEW_TOPIC("New Topic", "Create new topics in all forums."),
    HOT_TOPIC("Hot Topics", "View hot topics from the past 24 hours, week, or month."),
    SHORT_MESSAGE("Short Messages", "Send and receive short messages with other users."),
}

/** IAP unlock status; case order defines comparison (lite < trial < paid). */
sealed class UnlockStatus : Comparable<UnlockStatus> {
    data object Lite : UnlockStatus()
    data class Trial(val expiration: Date) : UnlockStatus()
    data object Paid : UnlockStatus()

    val level: Int
        get() = when (this) {
            Lite -> 0
            is Trial -> 1
            Paid -> 2
        }

    val trialValid: Boolean?
        get() = (this as? Trial)?.let { it.expiration > Date() }

    val isUnlocked: Boolean
        get() = this == Paid || (trialValid == true)

    val isPaid: Boolean get() = this == Paid

    val isLiteCanTry: Boolean get() = this == Lite

    val tryOrUnlock: String get() = if (isLiteCanTry) "Try Plus" else "Unlock Plus"

    val shouldUseProminent: Boolean
        get() = when (this) {
            Lite -> true
            is Trial -> trialValid != true
            Paid -> false
        }

    override fun compareTo(other: UnlockStatus): Int = level.compareTo(other.level)

    companion object {
        fun decode(json: String?): UnlockStatus =
            try {
                when {
                    json == null -> Lite
                    JSONObject(json).has("paid") -> Paid
                    JSONObject(json).has("trial") -> {
                        val seconds = JSONObject(json).getJSONObject("trial").optDouble("expiration")
                        Trial(Date((seconds * 1000).toLong()))
                    }
                    else -> Lite
                }
            } catch (e: Exception) {
                Lite
            }
    }

    fun encode(): String =
        when (this) {
            Lite -> """{"lite":{}}"""
            is Trial -> """{"trial":{"expiration":${expiration.time / 1000.0}}}"""
            Paid -> """{"paid":{}}"""
        }
}

/**
 * Plus gating / paywall model, ported from `Models/PlusModel.swift`.
 *
 * The iOS app uses StoreKit; this port keeps the same status semantics and
 * persistence key, and treats Google Play Billing as the store when wired.
 * No store is wired here, so [ALWAYS_UNLOCKED] makes every Plus feature
 * available and turns the paywall into a feature overview.
 */
class PlusModel(private val prefs: SharedPreferences) {

    companion object {
        var shared: PlusModel? = null

        const val TRIAL_DAYS = 14

        /**
         * This port ships without a store, so Plus starts unlocked for everyone
         * and the paywall degrades into an informational feature list. Flip to
         * `false` to restore the iOS gating; the debug override keeps working
         * either way, so Lite/Trial can still be forced at runtime to exercise
         * the paywall UI.
         */
        const val ALWAYS_UNLOCKED = true

        fun trialExpiration(from: Date): Date = Calendar.getInstance().apply {
            time = from
            add(Calendar.DAY_OF_YEAR, TRIAL_DAYS)
        }.time

        fun checkPlus(feature: PlusFeature): Boolean {
            val model = shared
            if (model == null || model.isUnlocked) return model != null
            ToastModel.showAuto(ToastModel.Message.RequirePlus(feature))
            return false
        }

        inline fun withPlusCheck(feature: PlusFeature, body: () -> Boolean): Boolean {
            if (!checkPlus(feature)) return false
            return body()
        }
    }

    private val _isShowingModal = MutableStateFlow(false)
    val isShowingModal: StateFlow<Boolean> = _isShowingModal

    private val _cachedStatus = MutableStateFlow<UnlockStatus>(UnlockStatus.Lite)
    val cachedStatus: StateFlow<UnlockStatus> = _cachedStatus

    private val _debugOverride = MutableStateFlow<UnlockStatus?>(null)
    val debugOverride: StateFlow<UnlockStatus?> = _debugOverride

    private val _isStatusTrusted = MutableStateFlow(false)
    val isStatusTrusted: StateFlow<Boolean> = _isStatusTrusted

    /** Optional billing bridge, set by the billing wiring at startup. */
    var billing: BillingBridge? = null

    interface BillingBridge {
        suspend fun fetchStatus(): UnlockStatus
        suspend fun purchase(productId: String): Boolean
        suspend fun restore(): Boolean
    }

    val status: UnlockStatus
        get() = _debugOverride.value ?: _cachedStatus.value

    val trustedStatus: UnlockStatus?
        get() = status.takeIf { _isStatusTrusted.value }

    val isUnlocked: Boolean
        get() = status.isUnlocked

    init {
        if (ALWAYS_UNLOCKED) {
            // There is no store to consult, so start unlocked and trusted
            // rather than leaving the UI waiting on a lookup that never lands.
            _cachedStatus.value = UnlockStatus.Paid
            _isStatusTrusted.value = true
        } else {
            _cachedStatus.value =
                UnlockStatus.decode(prefs.getString("cachedUnlockStatus", null))
            appScope.launch {
                val store = billing?.fetchStatus()
                if (store != null) updateStatus(store, initial = true)
            }
        }
    }

    fun showPaywall() {
        _isShowingModal.value = true
    }

    fun dismissPaywall() {
        _isShowingModal.value = false
    }

    fun setDebugOverride(status: UnlockStatus?) {
        _debugOverride.value = status
    }

    /** Never let a stale local cache downgrade what the store reported. */
    @Synchronized
    fun updateStatus(newStatus: UnlockStatus, initial: Boolean = false) {
        if (ALWAYS_UNLOCKED) {
            // Nothing can downgrade an unlocked build, and there is no receipt
            // worth writing to disk.
            _cachedStatus.value = UnlockStatus.Paid
            _isStatusTrusted.value = true
            return
        }
        if (initial) {
            _isStatusTrusted.value = newStatus >= _cachedStatus.value
            if (newStatus > _cachedStatus.value) _cachedStatus.value = newStatus
        } else {
            _isStatusTrusted.value = true
            _cachedStatus.value = newStatus
        }
        persist()
    }

    private fun persist() {
        prefs.edit().putString("cachedUnlockStatus", _cachedStatus.value.encode()).apply()
    }
}

/** Shared app-scope for fire-and-forget model work. */
val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
