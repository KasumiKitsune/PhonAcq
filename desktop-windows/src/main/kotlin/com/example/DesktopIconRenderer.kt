package com.example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.PathParser
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

/**
 * Renders the official PhonAcq brand icon (Crescent Ring + Soundwave Bars)
 * inside a pure white (#FFFFFF) Samsung One UI pebble (continuous-curvature superellipse squircle)
 * for the Windows title bar, taskbar, Alt+Tab switcher, and native .ico installer icon.
 */
object DesktopIconRenderer {

    private val waveSvgStrings = listOf(
        "M2232.85 1072.89C2238.46 1053.67 2258.59 1042.63 2277.81 1048.24C2297.04 1053.84 2308.07 1073.97 2302.47 1093.2L2195.77 1458.95C2190.17 1478.18 2170.04 1489.21 2150.81 1483.61C2131.59 1478 2120.55 1457.87 2126.16 1438.64Z",
        "M2221.49 1172.62C2215.37 1153.15 2226.2 1132.41 2245.67 1126.29C2265.14 1120.17 2285.89 1131 2292.01 1150.47L2357.6 1359.22C2363.72 1378.69 2352.89 1399.44 2333.42 1405.55C2313.95 1411.67 2293.2 1400.85 2287.08 1381.38Z",
        "M2437 1193 l290 43 l-263.09 61 Z",
        "M2367.78 1187.87C2355.61 1171.48 2359.03 1148.33 2375.42 1136.16C2391.81 1123.99 2414.95 1127.41 2427.12 1143.8L2499.19 1240.84C2511.36 1257.23 2507.94 1280.38 2491.55 1292.55C2475.17 1304.72 2452.02 1301.3 2439.85 1284.91Z",
        "M2356.18 1149.5C2362.09 1129.97 2382.72 1118.92 2402.25 1124.83C2421.79 1130.74 2432.83 1151.37 2426.92 1170.91L2359.8 1392.72C2353.89 1412.26 2333.26 1423.31 2313.73 1417.39C2294.19 1411.48 2283.15 1390.85 2289.06 1371.32Z",
        "M2095.06 1089.57C2100.65 1070.4 2120.72 1059.39 2139.89 1064.98C2159.06 1070.58 2170.07 1090.65 2164.48 1109.82L2099.08 1334.02C2093.49 1353.19 2073.41 1364.2 2054.24 1358.6C2035.07 1353.01 2024.07 1332.94 2029.66 1313.77Z",
        "M1938.48 1191.69C1950.53 1175.47 1973.44 1172.09 1989.66 1184.14C2005.89 1196.18 2009.27 1219.1 1997.22 1235.32L1958.39 1287.6C1946.34 1303.82 1923.42 1307.2 1907.2 1295.16C1890.98 1283.11 1887.6 1260.19 1899.65 1243.97Z",
        "M1944.59 1230.24C1932.13 1214.33 1934.92 1191.34 1950.83 1178.87C1966.73 1166.41 1989.73 1169.2 2002.19 1185.11L2093.48 1301.61C2105.94 1317.51 2103.15 1340.51 2087.24 1352.97C2071.34 1365.43 2048.34 1362.64 2035.88 1346.74Z"
    )

    private const val leftWingSvg = "M0 0 L290 54.747 L26.907 108 Z"

    private const val crescentRingSvg =
        "M1663.2 1278.4 l216.2 52.69 l1.47 5.71 C1923.18 1472.74 2050.07 1571.43 2200.02 1571.43 C2349.98 1571.43 2476.86 1472.74 2519.17 1336.8 L2520.42 1331.97 L2736.8 1279.23 L2736.13 1292.56 C2708.53 1564.1 2479.04 1776 2200.02 1776 C1921 1776 1691.51 1564.1 1663.92 1292.56 Z " +
            "M2200.02 699 C2479.04 699 2708.53 910.899 2736.13 1182.44 L2737 1199.72 L2521.38 1146.77 L2519.17 1138.2 C2476.86 1002.26 2349.98 903.565 2200.02 903.565 C2050.07 903.565 1923.18 1002.26 1880.87 1138.2 L1878.44 1147.66 L1663 1200.56 L1663.92 1182.44 C1691.51 910.899 1921 699 2200.02 699 Z"

    private val composeWavePaths by lazy(LazyThreadSafetyMode.NONE) {
        waveSvgStrings.map {
            PathParser().parsePathString(it).toPath().apply { fillType = PathFillType.EvenOdd }
        }
    }

    private val composeLeftWingPath by lazy(LazyThreadSafetyMode.NONE) {
        PathParser().parsePathString(leftWingSvg).toPath().apply { fillType = PathFillType.EvenOdd }
    }

    private val composeCrescentPath by lazy(LazyThreadSafetyMode.NONE) {
        PathParser().parsePathString(crescentRingSvg).toPath().apply { fillType = PathFillType.EvenOdd }
    }

    /**
     * Builds a Samsung One UI pebble (superellipse squircle with zero straight edges, k = 0.71)
     * in Compose Path coordinates.
     */
    private fun createComposePebblePath(dim: Float): Path {
        val cx = dim * 0.5f
        val cy = dim * 0.5f
        val r = dim * 0.46f
        val c = r * 0.71f
        return Path().apply {
            moveTo(cx, cy - r)
            cubicTo(cx + c, cy - r, cx + r, cy - c, cx + r, cy)
            cubicTo(cx + r, cy + c, cx + c, cy + r, cx, cy + r)
            cubicTo(cx - c, cy + r, cx - r, cy + c, cx - r, cy)
            cubicTo(cx - r, cy - c, cx - c, cy - r, cx, cy - r)
            close()
        }
    }

    /**
     * Builds a Samsung One UI pebble (superellipse squircle with zero straight edges, k = 0.71)
     * in Java2D Path2D coordinates.
     */
    private fun createAwtPebblePath(sizePx: Int): Path2D.Float {
        val cx = sizePx * 0.5f
        val cy = sizePx * 0.5f
        val r = sizePx * 0.46f
        val c = r * 0.71f
        return Path2D.Float().apply {
            moveTo(cx, cy - r)
            curveTo(cx + c, cy - r, cx + r, cy - c, cx + r, cy)
            curveTo(cx + r, cy + c, cx + c, cy + r, cx, cy + r)
            curveTo(cx - c, cy + r, cx - r, cy + c, cx - r, cy)
            curveTo(cx - r, cy - c, cx - c, cy - r, cx, cy - r)
            closePath()
        }
    }

    /**
     * Compose Painter for `Window(icon = ...)` so the window header & taskbar use the crisp PhonAcq vector logo
     * inside a pure white Samsung One UI pebble.
     */
    @Composable
    fun rememberWindowIconPainter(): Painter {
        return remember {
            object : Painter() {
                override val intrinsicSize: Size = Size(256f, 256f)

                override fun DrawScope.onDraw() {
                    val dim = size.minDimension
                    val pebblePath = createComposePebblePath(dim)

                    // Pure solid white Samsung One UI pebble background (no light-blue bottom gradient)
                    drawPath(
                        path = pebblePath,
                        color = Color.White
                    )
                    // Subtle border for crisp edge definition on both light and dark taskbars
                    drawPath(
                        path = pebblePath,
                        color = Color(0xFFDCE4EC),
                        style = Stroke(width = (dim / 48f).coerceAtLeast(1f))
                    )

                    val scale = dim / 108f
                    withTransform({
                        scale(scale, scale, Offset.Zero)
                        translate(14.15f, 14.15f)
                        scale(0.074f, 0.074f, Offset.Zero)
                        translate(-1661f, -699f)
                    }) {
                        val waveColor = Color(0xFFF59E0B)
                        val ringColor = Color(0xFF00ACC1)
                        composeWavePaths.forEach { p ->
                            drawPath(path = p, color = waveColor)
                        }
                        withTransform({
                            translate(1962f, 1186f)
                            scale(-1f, 1f, Offset.Zero)
                        }) {
                            drawPath(path = composeLeftWingPath, color = waveColor)
                        }
                        drawPath(path = composeCrescentPath, color = ringColor)
                    }
                }
            }
        }
    }

    /**
     * Parses a subset of SVG path commands (M, L, l, C, Z) into a Java2D Path2D.Float.
     */
    private fun parseSvgToAwtPath(svg: String): Path2D.Float {
        val path = Path2D.Float(Path2D.WIND_EVEN_ODD)
        val tokenRegex = Regex("""([MLlCZz])|(-?\d+(?:\.\d+)?)""")
        val tokens = tokenRegex.findAll(svg).map { it.value }.toList()
        var i = 0
        var cmd = ' '
        var curX = 0f
        var curY = 0f

        fun nextFloat(): Float = tokens[i++].toFloat()

        while (i < tokens.size) {
            val t = tokens[i]
            if (t.length == 1 && t[0].isLetter()) {
                cmd = t[0]
                i++
                if (cmd == 'Z' || cmd == 'z') {
                    path.closePath()
                    continue
                }
            }
            when (cmd) {
                'M' -> {
                    curX = nextFloat()
                    curY = nextFloat()
                    path.moveTo(curX, curY)
                    cmd = 'L'
                }
                'L' -> {
                    curX = nextFloat()
                    curY = nextFloat()
                    path.lineTo(curX, curY)
                }
                'l' -> {
                    curX += nextFloat()
                    curY += nextFloat()
                    path.lineTo(curX, curY)
                }
                'C' -> {
                    val x1 = nextFloat()
                    val y1 = nextFloat()
                    val x2 = nextFloat()
                    val y2 = nextFloat()
                    curX = nextFloat()
                    curY = nextFloat()
                    path.curveTo(x1, y1, x2, y2, curX, curY)
                }
                else -> i++
            }
        }
        return path
    }

    private val awtWavePaths by lazy { waveSvgStrings.map(::parseSvgToAwtPath) }
    private val awtLeftWingPath by lazy { parseSvgToAwtPath(leftWingSvg) }
    private val awtCrescentPath by lazy { parseSvgToAwtPath(crescentRingSvg) }

    fun renderIconBufferedImage(sizePx: Int): BufferedImage {
        val img = BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

            // Pure solid white Samsung One UI pebble shape (no gradient, no flat edges)
            val pebbleShape = createAwtPebblePath(sizePx)
            g.color = java.awt.Color(255, 255, 255)
            g.fill(pebbleShape)

            // Subtle neutral-cool outline for contrast on light Windows taskbars & Explorer backgrounds
            g.color = java.awt.Color(0xDC, 0xE4, 0xEC)
            g.stroke = BasicStroke((sizePx / 48f).coerceAtLeast(1f))
            g.draw(pebbleShape)

            val baseTx = AffineTransform()
            val scale = sizePx / 108.0
            baseTx.scale(scale, scale)
            baseTx.translate(14.15, 14.15)
            baseTx.scale(0.074, 0.074)
            baseTx.translate(-1661.0, -699.0)

            val savedTx = g.transform
            g.transform(baseTx)

            // Draw Amber Soundwave Bars
            g.color = java.awt.Color(0xF5, 0x9E, 0x0B)
            for (wp in awtWavePaths) {
                g.fill(wp)
            }

            // Draw Left Wing
            val wingTx = g.transform
            g.translate(1962.0, 1186.0)
            g.scale(-1.0, 1.0)
            g.fill(awtLeftWingPath)
            g.transform = wingTx

            // Draw Cyan-Teal Crescent Ring
            g.color = java.awt.Color(0x00, 0xAC, 0xC1)
            g.fill(awtCrescentPath)

            g.transform = savedTx
        } finally {
            g.dispose()
        }
        return img
    }

    fun getMultiResolutionWindowIcons(): List<BufferedImage> {
        return listOf(16, 24, 32, 48, 64, 128, 256).map(::renderIconBufferedImage)
    }

    /**
     * Generates a valid multi-resolution Windows .ico file containing 16, 32, 48, 64, 128, and 256 px PNG frames.
     */
    fun writeIcoFile(targetFile: File) {
        val sizes = listOf(16, 32, 48, 64, 128, 256)
        val pngPayloads = sizes.map { s ->
            val img = renderIconBufferedImage(s)
            ByteArrayOutputStream().use { baos ->
                ImageIO.write(img, "png", baos)
                baos.toByteArray()
            }
        }

        val count = sizes.size
        val headerSize = 6 + count * 16
        val totalSize = headerSize + pngPayloads.sumOf { it.size }
        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // ICONDIR header
        buf.putShort(0) // Reserved
        buf.putShort(1) // Type 1 = ICO
        buf.putShort(count.toShort())

        var currentOffset = headerSize
        for (idx in sizes.indices) {
            val s = sizes[idx]
            val payload = pngPayloads[idx]
            buf.put(if (s >= 256) 0.toByte() else s.toByte()) // Width (0 means 256)
            buf.put(if (s >= 256) 0.toByte() else s.toByte()) // Height (0 means 256)
            buf.put(0) // Color palette count
            buf.put(0) // Reserved
            buf.putShort(1) // Color planes
            buf.putShort(32) // Bits per pixel (32-bit ARGB)
            buf.putInt(payload.size) // Size of image data in bytes
            buf.putInt(currentOffset) // Offset of image data
            currentOffset += payload.size
        }

        for (payload in pngPayloads) {
            buf.put(payload)
        }

        targetFile.parentFile?.mkdirs()
        targetFile.writeBytes(buf.array())
    }

    /**
     * Generates a valid multi-resolution macOS .icns file containing 16, 32, 128, 256, 512, and 1024 px PNG chunks.
     */
    fun writeIcnsFile(targetFile: File) {
        val entries = listOf(
            "icp4" to 16,
            "icp5" to 32,
            "ic07" to 128,
            "ic08" to 256,
            "ic09" to 512,
            "ic10" to 1024
        )
        val chunks = entries.map { (osType, sizePx) ->
            val img = renderIconBufferedImage(sizePx)
            val pngBytes = ByteArrayOutputStream().use { baos ->
                ImageIO.write(img, "png", baos)
                baos.toByteArray()
            }
            osType.toByteArray(Charsets.US_ASCII) to pngBytes
        }

        val totalSize = 8 + chunks.sumOf { 8 + it.second.size }
        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        buf.put("icns".toByteArray(Charsets.US_ASCII))
        buf.putInt(totalSize)

        for ((typeBytes, pngBytes) in chunks) {
            buf.put(typeBytes)
            buf.putInt(8 + pngBytes.size)
            buf.put(pngBytes)
        }

        targetFile.parentFile?.mkdirs()
        targetFile.writeBytes(buf.array())
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val icoPath = args.getOrNull(0) ?: "src/main/resources/phonacq.ico"
        writeIcoFile(File(icoPath))
        val icnsPath = args.getOrNull(1) ?: "src/main/resources/phonacq.icns"
        writeIcnsFile(File(icnsPath))
    }
}
