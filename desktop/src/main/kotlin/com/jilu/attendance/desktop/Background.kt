package com.jilu.attendance.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.jilu.attendance.model.BackgroundFit
import com.jilu.attendance.model.BackgroundMode

/** 用 Skia 解码字节为 Compose 位图；失败返回 null。 */
fun decodeImage(bytes: ByteArray?): ImageBitmap? {
    if (bytes == null) return null
    return runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
}

/**
 * 背景层：默认 / 预设渐变 / 图片（完整显示或铺满裁切），最后统一盖 55% 白遮罩。
 * 与安卓端的视觉口径保持一致。
 */
@Composable
fun BackgroundLayer(state: AppState, modifier: Modifier = Modifier) {
    when (state.settings.backgroundMode) {
        BackgroundMode.DEFAULT -> {
            Box(modifier.background(MaterialTheme.colorScheme.background))
        }

        BackgroundMode.COLOR -> {
            val preset = presetOf(state.settings.backgroundColorId)
            Box(modifier.background(Brush.verticalGradient(colors = preset.colors)))
        }

        BackgroundMode.IMAGE -> {
            val bytes = state.backgroundBytes
            val bitmap = remember(bytes) { decodeImage(bytes) }
            if (bitmap == null) {
                Box(modifier.background(MaterialTheme.colorScheme.background))
            } else {
                Box(modifier) {
                    when (state.settings.backgroundFit) {
                        BackgroundFit.COVER -> {
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        BackgroundFit.FIT -> {
                            // 先铺同一张图的模糊版填满留白，再把整张图按比例放进去
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().blur(28.dp),
                                contentScale = ContentScale.Crop
                            )
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.55f)))
                }
            }
        }
    }
}
