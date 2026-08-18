package com.bugenzhao.mnga

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.bugenzhao.mnga.model.CurrentUserModel
import com.bugenzhao.mnga.model.NotificationModel
import com.bugenzhao.mnga.model.OpenURLModel
import com.bugenzhao.mnga.model.PlusModel
import com.bugenzhao.mnga.model.SchemesModel
import com.bugenzhao.mnga.model.ToastModel
import com.bugenzhao.mnga.model.UsersModel
import com.bugenzhao.mnga.storage.AuthStorage
import com.bugenzhao.mnga.storage.BlockWordsStorage
import com.bugenzhao.mnga.storage.FavoriteForumsStorage
import com.bugenzhao.mnga.storage.PreferencesStorage
import com.bugenzhao.mnga.logicInitialConfigure
import com.bugenzhao.mnga.model.appScope
import kotlinx.coroutines.launch

/** Application-scoped singletons, mirroring the SwiftUI environment objects. */
object App {
    lateinit var prefs: PreferencesStorage
    lateinit var authStorage: AuthStorage
    lateinit var blockWords: BlockWordsStorage
    lateinit var favoriteForums: FavoriteForumsStorage
    lateinit var users: UsersModel
    lateinit var currentUser: CurrentUserModel
    lateinit var notis: NotificationModel
    lateinit var schemes: SchemesModel
    lateinit var openURL: OpenURLModel
    lateinit var plus: PlusModel
    lateinit var sharedPreferences: SharedPreferences

    val isInitialized: Boolean
        get() = ::prefs.isInitialized
}

class MNGAApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val prefs = getSharedPreferences("mnga", Context.MODE_PRIVATE)
        App.sharedPreferences = prefs
        App.prefs = PreferencesStorage(prefs).also { PreferencesStorage.shared = it }
        App.authStorage = AuthStorage(prefs).also { AuthStorage.shared = it }
        App.plus = PlusModel(prefs).also { PlusModel.shared = it }
        App.blockWords = BlockWordsStorage(prefs).also { BlockWordsStorage.shared = it }
        App.favoriteForums =
            FavoriteForumsStorage(this, prefs).also { FavoriteForumsStorage.shared = it }
        App.users = UsersModel().also { UsersModel.shared = it }
        App.currentUser = CurrentUserModel(appScope, App.authStorage)
        App.notis = NotificationModel(appScope).also { NotificationModel.shared = it }
        App.schemes =
            SchemesModel(
                appScope,
                getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager,
            )
        App.openURL = OpenURLModel(this)

        // Configure the Rust logic layer with an app-local writable directory.
        logicInitialConfigure(filesDir.path, isEmulator = isEmulator())

        // Sync favorites & watch auth changes.
        appScope.launch {
            App.favoriteForums.initialSync()
        }
        appScope.launch {
            App.authStorage.authChanged.collect {
                App.currentUser.scheduleClockInAfterAuth()
                App.favoriteForums.initialSync()
            }
        }

        // Route toast haptics.
        listOf(ToastModel.hud, ToastModel.banner, ToastModel.editorAlert)
            .forEach { model ->
                model.haptic = { type -> com.bugenzhao.mnga.util.Haptics.vibrate(this, type) }
            }
    }

    private fun isEmulator(): Boolean =
        (android.os.Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
            android.os.Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
            android.os.Build.MODEL.contains("Emulator", ignoreCase = true) ||
            android.os.Build.MODEL.contains("Android SDK built for x86", ignoreCase = true) ||
            android.os.Build.MANUFACTURER.contains("Genymotion", ignoreCase = true) ||
            android.os.Build.PRODUCT.contains("sdk", ignoreCase = true))
}
