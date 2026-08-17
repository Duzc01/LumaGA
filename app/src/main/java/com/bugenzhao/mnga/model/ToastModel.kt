package com.bugenzhao.mnga.model

import com.bugenzhao.mnga.util.Haptics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Four independent single-slot toast channels, ported from
 * `Models/ToastModel.swift`. Every user-visible feedback flows through
 * [showAuto].
 */
class ToastModel(val channel: Channel = Channel.BANNER) {

    enum class Channel { HUD, BANNER, ALERT, EDITOR_ALERT }

    sealed class Message {
        abstract val id: Long

        data class Success(val message: String, override val id: Long = nextId()) : Message()
        data class Error(val error: String, override val id: Long = nextId()) : Message()
        data class CacheLoaded(val message: String, override val id: Long = nextId()) : Message()
        data class Notification(val count: Int, override val id: Long = nextId()) : Message()
        data class UserSwitch(val name: String, override val id: Long = nextId()) : Message()
        data class ClockIn(val message: String, override val id: Long = nextId()) : Message()
        data class OpenURL(val url: String, override val id: Long = nextId()) : Message()
        data object AutoRefreshed : Message() {
            override val id: Long get() = nextId()
        }
        data class RequirePlus(val feature: PlusFeature, override val id: Long = nextId()) :
            Message()
    }

    private val _message = MutableStateFlow<Message?>(null)
    val message: StateFlow<Message?> = _message

    /** Haptic callback wired by the UI layer (needs a View). */
    var haptic: ((Haptics.NotificationType) -> Unit)? = null

    fun show(message: Message?) {
        if (message == null) return
        _message.value = message
        val type = when (message) {
            is Message.Error -> Haptics.NotificationType.ERROR
            is Message.Notification,
            is Message.RequirePlus,
            is Message.CacheLoaded -> Haptics.NotificationType.WARNING
            else -> Haptics.NotificationType.SUCCESS
        }
        haptic?.invoke(type)
    }

    fun dismiss() {
        _message.value = null
    }

    companion object {
        private val nextIdCounter = java.util.concurrent.atomic.AtomicLong(0)
        fun nextId(): Long = nextIdCounter.incrementAndGet()

        val hud = ToastModel(Channel.HUD)
        val banner = ToastModel(Channel.BANNER)
        val alert = ToastModel(Channel.ALERT)
        val editorAlert = ToastModel(Channel.EDITOR_ALERT)

        fun showAuto(message: Message?) {
            when (message) {
                null -> {}
                is Message.Success -> banner.show(message)
                is Message.Error -> banner.show(message)
                is Message.CacheLoaded -> banner.show(message)
                is Message.Notification -> hud.show(message)
                is Message.UserSwitch -> hud.show(message)
                is Message.ClockIn -> hud.show(message)
                is Message.OpenURL -> hud.show(message)
                Message.AutoRefreshed -> hud.show(message)
                is Message.RequirePlus -> alert.show(message)
            }
        }
    }
}
