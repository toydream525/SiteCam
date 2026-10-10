package com.sitecam.app.feature.icon

import android.graphics.BitmapFactory
import androidx.core.graphics.PathParser
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppIconVectorTest {
    @Test fun allForegroundImagesAndMonochromePathsCanBeLoaded() {
        val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        for (choice in listOf("a", "b", "c", "d")) {
            val image = BitmapFactory.decodeFile(File(res, "drawable-nodpi/ic_launcher_${choice}_foreground.png").path)
            assertNotNull("$choice foreground", image)
            assertTrue("$choice image dimensions", image.width > 0 && image.height > 0)
            image.recycle()
            val name = "ic_launcher_${choice}_monochrome"
            val document = factory.newDocumentBuilder().parse(File(res, "drawable/$name.xml"))
            val paths = document.getElementsByTagName("path")
            assertTrue("$name has visible paths", paths.length > 0)
            for (index in 0 until paths.length) {
                val path = paths.item(index) as org.w3c.dom.Element
                val data = path.getAttributeNS("http://schemas.android.com/apk/res/android", "pathData")
                assertNotNull("$name path $index", PathParser.createPathFromPathData(data))
            }
        }
    }
}
