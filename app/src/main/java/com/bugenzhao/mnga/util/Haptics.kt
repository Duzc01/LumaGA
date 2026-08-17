package com.bugenzhao.mnga.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Haptic feedback helpers, ported from `iOS/Utilities/HapticUtils.swift`.
 * Notification types map to confirmation/reject effects; the light impact
 * maps to a short, weak vibration.
 */
object Haptics {
    enum class NotificationType { SUCCESS, WARNING, ERROR }

    fun play(view: View?, type: NotificationType) {
        val v = view ?: return
        when (type) {
            NotificationType.SUCCESS ->
                v.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            NotificationType.WARNING ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    v.performHapticFeedback(HapticFeedbackConstants.REJECT)
                } else {
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            NotificationType.ERROR ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    v.performHapticFeedback(HapticFeedbackConstants.REJECT)
                } else {
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
        }
    }

    /** Light impact, e.g. on upvote. */
    fun lightImpact(view: View?) {
        view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun vibrator(context: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    /** Fallback vibration used when no `View` is available for feedback. */
    fun vibrate(context: Context, type: NotificationType) {
        val ms = when (type) {
            NotificationType.SUCCESS -> 20
            NotificationType.WARNING -> 40
            NotificationType.ERROR -> 60
        }
        try {
            vibrator(context).vibrate(
                VibrationEffect.createOneShot(ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } catch (e: Exception) {
            // Haptics must never crash the app (missing permission, etc.).
        }
    }
}
