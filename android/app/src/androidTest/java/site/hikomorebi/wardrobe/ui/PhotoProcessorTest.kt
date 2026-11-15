package site.hikomorebi.wardrobe.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import site.hikomorebi.wardrobe.data.PhotoProcessor

class PhotoProcessorTest {
    @Test
    fun orientationIsAppliedImageIsResizedAndMetadataIsRemoved() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val input = PhotoProcessor.captureFile(context)
        val bitmap =
            Bitmap.createBitmap(3000, 1500, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.BLUE)
            }
        input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        ExifInterface(input).apply {
            setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_ROTATE_90.toString(),
            )
            setLatLong(30.5, 104.0)
            saveAttributes()
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", input)
        val output = PhotoProcessor.prepare(context, uri)
        try {
            val image = BitmapFactory.decodeFile(output.absolutePath)
            assertEquals(1024, image.width)
            assertEquals(2048, image.height)
            image.recycle()
            val metadata = ExifInterface(output)
            assertNull(metadata.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
            assertNull(metadata.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
            assertTrue(metadata.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) in listOf(0, 1))
        } finally {
            input.delete()
            output.delete()
        }
    }

    @Test
    fun transparentPhotoIsFlattenedOntoWhite() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val input = File(PhotoProcessor.captureFile(context).parentFile, "transparent.png")
        val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val output =
            PhotoProcessor.prepare(
                context,
                FileProvider.getUriForFile(context, context.packageName + ".files", input),
            )
        try {
            val image = BitmapFactory.decodeFile(output.absolutePath)
            assertTrue(Color.red(image.getPixel(20, 20)) > 245)
            image.recycle()
        } finally {
            input.delete()
            output.delete()
        }
    }
}
