package com.sitecam.app.feature.icon

import androidx.core.graphics.PathParser
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppIconVectorTest {
    @Test fun iconCPathsCanBeParsedWithoutFallingBackToTheApplicationIcon() {
        val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        for (name in listOf("ic_launcher_c_foreground", "ic_launcher_c_monochrome")) {
            val document = factory.newDocumentBuilder().parse(File(res, "drawable/$name.xml"))
            val paths = document.getElementsByTagName("path")
            for (index in 0 until paths.length) {
                val path = paths.item(index) as org.w3c.dom.Element
                val data = path.getAttributeNS("http://schemas.android.com/apk/res/android", "pathData")
                assertNotNull("$name path $index", PathParser.createPathFromPathData(data))
            }
        }
    }
}
