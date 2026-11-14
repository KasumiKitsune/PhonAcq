@file:Suppress("PackageDirectoryMismatch")

package android.graphics

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.roundToInt

/**
 * Desktop JVM bridge for `android.graphics.Matrix` supporting rotation for EXIF normalization.
 */
class Matrix {
    var rotationDegrees: Float = 0f
        private set

    fun postRotate(degrees: Float): Boolean {
        rotationDegrees = (rotationDegrees + degrees) % 360f
        return true
    }
}

/**
 * Desktop JVM bridge for `android.graphics.Bitmap` and `BitmapFactory` so shared spectrogram,
 * waveform, and picture-wordlist image pipelines work seamlessly on Windows / macOS / Linux.
 */
class Bitmap private constructor(
    val pixels: IntArray,
    val width: Int,
    val height: Int,
    val config: Config
) {
    enum class Config {
        ARGB_8888,
        RGB_565,
        ALPHA_8
    }

    enum class CompressFormat {
        JPEG,
        PNG,
        WEBP
    }

    fun recycle() {
        // Managed by JVM GC on Desktop
    }

    fun hasAlpha(): Boolean = config == Config.ARGB_8888 || config == Config.ALPHA_8

    fun getPixels(
        outPixels: IntArray,
        offset: Int,
        stride: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) {
        val safeW = width.coerceAtLeast(0)
        val safeH = height.coerceAtLeast(0)
        for (row in 0 until safeH) {
            val srcY = y + row
            if (srcY !in 0 until this.height) continue
            val srcRowStart = srcY * this.width + x
            val dstRowStart = offset + row * stride
            val copyLen = minOf(safeW, this.width - x, outPixels.size - dstRowStart).coerceAtLeast(0)
            if (copyLen > 0 && srcRowStart >= 0 && srcRowStart + copyLen <= pixels.size) {
                System.arraycopy(pixels, srcRowStart, outPixels, dstRowStart, copyLen)
            }
        }
    }

    fun compress(format: CompressFormat, quality: Int, stream: OutputStream): Boolean {
        return try {
            val w = width.coerceAtLeast(1)
            val h = height.coerceAtLeast(1)
            if (format == CompressFormat.PNG) {
                val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                if (pixels.size >= w * h) {
                    img.setRGB(0, 0, w, h, pixels, 0, w)
                }
                ImageIO.write(img, "png", stream)
            } else {
                // Flatten any alpha channel onto warm white before writing 3-channel RGB JPEG
                val rgbPixels = IntArray(w * h)
                val limit = minOf(pixels.size, w * h)
                for (i in 0 until limit) {
                    val c = pixels[i]
                    val a = (c ushr 24) and 0xFF
                    if (a >= 250) {
                        rgbPixels[i] = c and 0x00FFFFFF
                    } else {
                        val r = (c ushr 16) and 0xFF
                        val g = (c ushr 8) and 0xFF
                        val b = c and 0xFF
                        val invA = 255 - a
                        val outR = (r * a + 250 * invA) / 255
                        val outG = (g * a + 250 * invA) / 255
                        val outB = (b * a + 248 * invA) / 255
                        rgbPixels[i] = (outR shl 16) or (outG shl 8) or outB
                    }
                }
                val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
                img.setRGB(0, 0, w, h, rgbPixels, 0, w)
                val writers = ImageIO.getImageWritersByFormatName("jpg")
                if (writers.hasNext()) {
                    val writer = writers.next()
                    try {
                        val param = writer.defaultWriteParam
                        if (param.canWriteCompressed()) {
                            param.compressionMode = ImageWriteParam.MODE_EXPLICIT
                            param.compressionQuality = (quality.coerceIn(10, 100) / 100f)
                        }
                        ImageIO.createImageOutputStream(stream).use { ios ->
                            writer.output = ios
                            writer.write(null, IIOImage(img, null, null), param)
                        }
                        true
                    } finally {
                        writer.dispose()
                    }
                } else {
                    ImageIO.write(img, "jpg", stream)
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        @JvmStatic
        fun createBitmap(
            colors: IntArray,
            width: Int,
            height: Int,
            config: Config
        ): Bitmap {
            return Bitmap(
                pixels = colors.copyOf(),
                width = width.coerceAtLeast(1),
                height = height.coerceAtLeast(1),
                config = config
            )
        }

        @JvmStatic
        fun createBitmap(
            width: Int,
            height: Int,
            config: Config
        ): Bitmap {
            val w = width.coerceAtLeast(1)
            val h = height.coerceAtLeast(1)
            return Bitmap(
                pixels = IntArray(w * h),
                width = w,
                height = h,
                config = config
            )
        }

        @JvmStatic
        fun createScaledBitmap(
            src: Bitmap,
            dstWidth: Int,
            dstHeight: Int,
            filter: Boolean
        ): Bitmap {
            val targetW = dstWidth.coerceAtLeast(1)
            val targetH = dstHeight.coerceAtLeast(1)
            if (src.width == targetW && src.height == targetH) return src
            val srcImg = BufferedImage(src.width.coerceAtLeast(1), src.height.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
            if (src.pixels.size >= srcImg.width * srcImg.height) {
                srcImg.setRGB(0, 0, srcImg.width, srcImg.height, src.pixels, 0, srcImg.width)
            }
            val dstImg = BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB)
            val g2d = dstImg.createGraphics()
            try {
                g2d.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    if (filter) RenderingHints.VALUE_INTERPOLATION_BILINEAR else RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                )
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                g2d.drawImage(srcImg, 0, 0, targetW, targetH, null)
            } finally {
                g2d.dispose()
            }
            val outPixels = IntArray(targetW * targetH)
            dstImg.getRGB(0, 0, targetW, targetH, outPixels, 0, targetW)
            return Bitmap(outPixels, targetW, targetH, src.config)
        }

        @JvmStatic
        fun createBitmap(
            source: Bitmap,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            m: Matrix?,
            filter: Boolean
        ): Bitmap {
            val cropW = width.coerceIn(1, (source.width - x).coerceAtLeast(1))
            val cropH = height.coerceIn(1, (source.height - y).coerceAtLeast(1))
            val cropped = BufferedImage(cropW, cropH, BufferedImage.TYPE_INT_ARGB)
            val srcImg = BufferedImage(source.width.coerceAtLeast(1), source.height.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
            if (source.pixels.size >= srcImg.width * srcImg.height) {
                srcImg.setRGB(0, 0, srcImg.width, srcImg.height, source.pixels, 0, srcImg.width)
            }
            val cg = cropped.createGraphics()
            try {
                cg.drawImage(srcImg, 0, 0, cropW, cropH, x, y, x + cropW, y + cropH, null)
            } finally {
                cg.dispose()
            }

            val deg = ((m?.rotationDegrees ?: 0f).roundToInt() % 360 + 360) % 360
            if (deg == 0) {
                val out = IntArray(cropW * cropH)
                cropped.getRGB(0, 0, cropW, cropH, out, 0, cropW)
                return Bitmap(out, cropW, cropH, source.config)
            }
            val swapAxes = deg == 90 || deg == 270
            val outW = if (swapAxes) cropH else cropW
            val outH = if (swapAxes) cropW else cropH
            val rotated = BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB)
            val rg = rotated.createGraphics()
            try {
                when (deg) {
                    90 -> {
                        rg.translate(outW.toDouble(), 0.0)
                        rg.rotate(Math.toRadians(90.0))
                    }
                    180 -> {
                        rg.translate(outW.toDouble(), outH.toDouble())
                        rg.rotate(Math.toRadians(180.0))
                    }
                    270 -> {
                        rg.translate(0.0, outH.toDouble())
                        rg.rotate(Math.toRadians(270.0))
                    }
                }
                rg.drawImage(cropped, 0, 0, null)
            } finally {
                rg.dispose()
            }
            val outPixels = IntArray(outW * outH)
            rotated.getRGB(0, 0, outW, outH, outPixels, 0, outW)
            return Bitmap(outPixels, outW, outH, source.config)
        }
    }
}

object BitmapFactory {
    class Options {
        var inJustDecodeBounds: Boolean = false
        var inSampleSize: Int = 1
        var outWidth: Int = 0
        var outHeight: Int = 0
        var inPreferredConfig: Bitmap.Config = Bitmap.Config.ARGB_8888
    }

    @JvmStatic
    fun decodeFile(pathName: String, opts: Options? = null): Bitmap? {
        return try {
            val file = File(pathName)
            if (!file.exists() || !file.isFile) return null
            file.inputStream().use { decodeStream(it, null, opts) }
        } catch (_: Exception) {
            null
        }
    }

    @JvmStatic
    fun decodeByteArray(data: ByteArray, offset: Int, length: Int, opts: Options? = null): Bitmap? {
        return try {
            ByteArrayInputStream(data, offset, length).use { decodeStream(it, null, opts) }
        } catch (_: Exception) {
            null
        }
    }

    @JvmStatic
    fun decodeStream(isStream: InputStream?, outPadding: Any? = null, opts: Options? = null): Bitmap? {
        if (isStream == null) return null
        return try {
            val img = ImageIO.read(isStream) ?: return null
            opts?.outWidth = img.width
            opts?.outHeight = img.height
            if (opts?.inJustDecodeBounds == true) {
                return null
            }
            val sample = (opts?.inSampleSize ?: 1).coerceAtLeast(1)
            val targetW = (img.width / sample).coerceAtLeast(1)
            val targetH = (img.height / sample).coerceAtLeast(1)
            val argbImg = BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB)
            val g = argbImg.createGraphics()
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g.drawImage(img, 0, 0, targetW, targetH, null)
            } finally {
                g.dispose()
            }
            val pixels = IntArray(targetW * targetH)
            argbImg.getRGB(0, 0, targetW, targetH, pixels, 0, targetW)
            Bitmap.createBitmap(pixels, targetW, targetH, opts?.inPreferredConfig ?: Bitmap.Config.ARGB_8888)
        } catch (_: Exception) {
            null
        }
    }
}
