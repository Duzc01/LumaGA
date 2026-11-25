package com.bugenzhao.mnga.ui.components.imageviewer

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bugenzhao.mnga.MainActivity
import com.bugenzhao.mnga.model.ViewingImageModel
import java.nio.file.Files
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageViewerDialogTest {
    @Test
    fun imageNumberFollowsHorizontalSwipes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val directory = Files.createTempDirectory(
            instrumentation.targetContext.cacheDir.toPath(),
            "image-viewer-test",
        ).toFile()

        try {
            val urls = listOf(Color.RED, Color.GREEN, Color.BLUE).mapIndexed { index, color ->
                val file = directory.resolve("$index.png")
                val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(color)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                Uri.fromFile(file).toString()
            }
            val model = ViewingImageModel().apply { show(urls, urls[1]) }

            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent { ImageViewerDialog(model) } }
                assertTrue("Viewer should open at image 2", device.wait(Until.hasObject(By.text("2")), 5_000))

                val left = device.displayWidth / 5
                val right = device.displayWidth * 4 / 5
                val middle = device.displayHeight / 2
                assertTrue(device.swipe(right, middle, left, middle, 30))
                assertTrue("Swiping left should show image 3", device.wait(Until.hasObject(By.text("3")), 5_000))

                assertTrue(device.swipe(left, middle, right, middle, 30))
                assertTrue("Swiping right should show image 2", device.wait(Until.hasObject(By.text("2")), 5_000))
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
