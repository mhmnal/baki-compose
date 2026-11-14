package app.baki

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import java.awt.FileDialog
import java.awt.Frame
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

actual fun decodeReceiptImage(base64: String): ImageBitmap? = runCatching {
    Image.makeFromEncoded(Base64.getDecoder().decode(base64)).toComposeImageBitmap()
}.getOrNull()

@Composable
actual fun rememberReceiptPicker(onPicked: (String?) -> Unit): () -> Unit = remember(onPicked) {
    {
        val dialog = FileDialog(null as Frame?, "Choose receipt", FileDialog.LOAD).apply {
            setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp") }
            isVisible = true
        }
        val file = dialog.file?.let { File(dialog.directory, it) }
        onPicked(file?.let { compressReceipt(it) })
    }
}

private fun compressReceipt(file: File): String? = runCatching {
    var image = ImageIO.read(file) ?: return@runCatching null
    val max = maxOf(image.width, image.height)
    if (max > 1200) {
        val scale = 1200.0 / max
        image = image.scale((image.width * scale).toInt(), (image.height * scale).toInt())
    }
    var quality = 0.72f
    while (quality >= 0.32f) {
        val out = ByteArrayOutputStream()
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val params = writer.defaultWriteParam.apply { compressionMode = ImageWriteParam.MODE_EXPLICIT; compressionQuality = quality }
        ImageIO.createImageOutputStream(out).use { stream ->
            writer.output = stream
            writer.write(null, IIOImage(image, null, null), params)
        }
        writer.dispose()
        val encoded = Base64.getEncoder().encodeToString(out.toByteArray())
        if (encoded.length <= MAX_RECEIPT_CHARS) return@runCatching encoded
        quality -= 0.10f
    }
    null
}.getOrNull()

private fun BufferedImage.scale(width: Int, height: Int): BufferedImage {
    val out = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val g = out.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    g.drawImage(this, 0, 0, width, height, null)
    g.dispose()
    return out
}
