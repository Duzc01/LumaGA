package com.bugenzhao.mnga

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.bugenzhao.mnga.model.NavigationIdentifier
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for the pasteboard auto-jump fix: copying the current
 * topic's own LumaGA link must not make the app dismiss and re-present the
 * topic on the next resume/foreground.
 *
 * Runs on an emulator (see .github/workflows/emulator-test.yml). It drives
 * the real app: opens a topic through the deep-link machinery, puts the
 * topic's own mnga:// link on the clipboard, backgrounds/foregrounds the
 * app, then asserts no re-navigation happened (observed via
 * App.schemes.navID emissions — a jump would re-emit the topic id after a
 * dismiss).
 */
@RunWith(AndroidJUnit4::class)
class PasteboardJumpTest {

    @Test
    fun clipboardLinkToCurrentTopicDoesNotTriggerRejump() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val clipboard =
            targetContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val device = UiDevice.getInstance(instrumentation)

        val emissions =
            Collections.synchronizedList(mutableListOf<NavigationIdentifier?>())
        val collectorScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val collector = collectorScope.launch {
            App.schemes.navID.collect { emissions.add(it) }
        }

        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                // Let the cold start settle (resume fires with an empty clipboard).
                Thread.sleep(2000)
                emissions.clear()

                // Open a topic through the same machinery a deep link uses.
                val topic = NavigationIdentifier.TopicID("12345", null)
                App.schemes.navigateTo(topic)

                // Wait for the push + dismiss cycle: TopicID, then null.
                assertTrue(
                    "topic was never presented through the deep-link machinery",
                    poll(15_000) {
                        emissions.size >= 2 &&
                            emissions[emissions.size - 2] == topic &&
                            emissions.last() == null
                    },
                )
                emissions.clear()

                // Copy the current topic's own LumaGA link, like the
                // "LumaGA Link" menu entry does.
                clipboard.setPrimaryClip(
                    ClipData.newPlainText("link", "mnga://topic/12345"),
                )

                // Background and foreground the app: fires onResume and the
                // window-focus callback that drive the pasteboard check.
                device.pressHome()
                Thread.sleep(500)
                val relaunch =
                    targetContext.packageManager.getLaunchIntentForPackage(
                        targetContext.packageName,
                    )!!
                relaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                targetContext.startActivity(relaunch)

                // onResume waits 350ms before the check; a (buggy) jump would
                // re-present after another 500ms. 4s is ample margin.
                Thread.sleep(4000)

                assertTrue(
                    "pasteboard auto-jump re-presented the current topic " +
                        "(navID emissions after foreground: $emissions)",
                    emissions.isEmpty(),
                )
            }
        } finally {
            collector.cancel()
            collectorScope.cancel()
        }
    }

    private fun poll(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        do {
            if (condition()) return true
            Thread.sleep(200)
        } while (System.currentTimeMillis() < deadline)
        return condition()
    }
}
