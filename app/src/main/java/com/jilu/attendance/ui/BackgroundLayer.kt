package com.jilu.attendance.ui

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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jilu.attendance.data.AppSettings
import com.jilu.attendance.data.BackgroundFit
import com.jilu.attendance.data.BackgroundImageStore
import com.jilu.attendance.data.BackgroundMode

data class BackgroundPreset(
    val id: String,
    val label: String,
    val colors: List<Color>
)

val backgroundPresets = listOf(
    BackgroundPreset("mint", "薄荷", listOf(Color(0xFFE8F5E9), Color(0xFFF1F8E9))),
    BackgroundPreset("sky", "天空", listOf(Color(0xFFE3F2FD), Color(0xFFEDE7F6))),
    BackgroundPreset("sand", "沙丘", listOf(Color(0xFFFFF8E1), Color(0xFFFBE9E7))),
    BackgroundPreset("rose", "樱粉", listOf(Color(0xFFFCE4EC), Color(0xFFFFF3E0))),
    BackgroundPreset("clay", "灰白", listOf(Color(0xFFF5F5F5), Color(0xFFECEFF1)))
)

fun presetOf(id: String): BackgroundPreset =
    backgroundPresets.firstOrNull { it.id == id } ?: backgroundPresets.first()

/** 铺满整个页面的背景层：默认 / 预设渐变 / 自定义图片 + 半透明遮罩。 */
@Composable
fun BackgroundLayer(settings: AppSettings, modifier: Modifier = Modifier) {
    when (settings.backgroundMode) {
        BackgroundMode.DEFAULT -> {
            Box(modifier.background(MaterialTheme.colorScheme.background))
        }

        BackgroundMode.COLOR -> {
            val preset = presetOf(settings.backgroundColorId)
            Box(
                modifier.background(
                    Brush.verticalGradient(colors = preset.colors)
                )
            )
        }

        BackgroundMode.IMAGE -> {
            val context = LocalContext.current
            val imageName = settings.backgroundImageName
            val bitmap = remember(imageName, settings.backgroundImageUpdatedAt) {
                imageName?.let { BackgroundImageStore(context).load(it) }
            }

            if (bitmap == null) {
                Box(modifier.background(MaterialTheme.colorScheme.background))
            } else {
                Box(modifier) {
                    val imageBitmap = bitmap.asImageBitmap()
                    when (settings.backgroundFit) {
                        BackgroundFit.COVER -> {
                            // 铺满：等比放大到铺满屏幕，裁掉超出的部分
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        BackgroundFit.FIT -> {
                            // 完整显示：先铺一层同一张图的模糊版填满空白，再把整张图按比例放进屏幕
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().blur(28.dp),
                                contentScale = ContentScale.Crop
                            )
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                    // 遮罩：让日期数字和统计文字在任何照片上都读得清
                    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.55f)))
                }
            }
        }
    }
}
