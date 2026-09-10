package com.sitecam.app.feature.annotation

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import com.sitecam.app.feature.annotation.model.AnnotationElement
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditRecipeTest {
    @Test fun serializedDrawAndTransformKeepCoordinatesAndOrder() {
        val recipe = listOf(EditStep.Draw(listOf(AnnotationElement.Arrow(Offset(20f,30f), Offset(70f,80f), androidx.compose.ui.graphics.Color.Red, 10f)), 200f, 300f), EditStep.Transform("rotate"), EditStep.Transform("crop", .1f,.2f,.8f,.9f))
        assertEquals(recipe, EditRecipe.decode(EditRecipe.encode(recipe)))
    }
    @Test fun wholeImageTransformsPreservePixelsAndCropDimensions() {
        val bitmap = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0,0,Color.RED)
        val flipped = EditRecipe.transform(bitmap, EditStep.Transform("horizontal"))
        assertEquals(Color.RED, flipped.getPixel(3,0))
        val rotated = EditRecipe.transform(bitmap, EditStep.Transform("rotate"))
        assertEquals(2, rotated.width); assertEquals(4, rotated.height)
        val crop = EditRecipe.transform(bitmap, EditStep.Transform("crop", .25f,0f,.75f,1f))
        assertEquals(2, crop.width); assertEquals(2,crop.height)
    }
}
