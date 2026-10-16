package com.ammiratv.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Draws [content] as a QR code. Always black on white (even in the dark theme) because phone
 * scanners fail on inverted codes, and keeps a 4-module quiet zone.
 */
@Composable
fun QrCode(content: String, size: Dp, modifier: Modifier = Modifier) {
    val matrix = remember(content) {
        runCatching {
            QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                0,
                0,
                mapOf(
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN to 4
                )
            )
        }.getOrNull()
    }

    Canvas(modifier = modifier.size(size)) {
        drawRect(Color.White)
        val m = matrix ?: return@Canvas
        val cell = this.size.width / m.width
        for (x in 0 until m.width) {
            for (y in 0 until m.height) {
                if (m.get(x, y)) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(x * cell, y * cell),
                        // +0.5 avoids hairline gaps between modules after rounding
                        size = Size(cell + 0.5f, cell + 0.5f)
                    )
                }
            }
        }
    }
}
