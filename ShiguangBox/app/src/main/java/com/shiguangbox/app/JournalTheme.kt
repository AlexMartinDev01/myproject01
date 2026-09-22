package com.shiguangbox.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlin.math.abs

data class JournalThemeProfile(
    val id: String,
    val name: String,
    val moodId: String,
    val moodLabel: String,
    val moodEmoji: String,
    val background: Color,
    val surface: Color,
    val primary: Color,
    val secondary: Color,
    val text: Color,
    val muted: Color,
    val palePrimary: Color,
    val paleSecondary: Color,
    val stickerEmoji: String,
    val tagline: String
)

data class MoodOption(
    val id: String,
    val label: String,
    val emoji: String,
    val description: String
)

val JournalMoodOptions = listOf(
    MoodOption("happy", "开心", "☀️", "今天想轻快一点"),
    MoodOption("calm", "平静", "🌿", "慢慢来就很好"),
    MoodOption("tired", "有点累", "☕", "今天给自己留点余量"),
    MoodOption("sad", "难过", "🌧️", "想要柔和安静一点"),
    MoodOption("quiet", "想安静", "🌙", "少一点打扰，多一点留白"),
    MoodOption("random", "随机一天", "🎲", "让拾光盒替今天抽一套手账")
)

private val JournalThemes = listOf(
    JournalThemeProfile(
        "sunny_orange", "晴天橘子", "happy", "开心", "☀️",
        Color(0xFFFFF5E6), Color(0xFFFFFCF7), Color(0xFFF4A261), Color(0xFFE76F51),
        Color(0xFF503A2E), Color(0xFF8D7668), Color(0xFFFFE4BB), Color(0xFFFFD8D0),
        "🍊", "今天适合收集一点小快乐"
    ),
    JournalThemeProfile(
        "strawberry_milk", "草莓牛奶", "happy", "开心", "🍓",
        Color(0xFFFFF2F5), Color(0xFFFFFBFC), Color(0xFFE996A7), Color(0xFFF1B8C4),
        Color(0xFF543D43), Color(0xFF92767D), Color(0xFFFFDCE4), Color(0xFFFFE9EF),
        "🍓", "甜一点，也轻一点"
    ),
    JournalThemeProfile(
        "sea_breeze", "海边晚风", "happy", "开心", "🌊",
        Color(0xFFF2F8F7), Color(0xFFFCFEFD), Color(0xFF79AFA7), Color(0xFFF0B77B),
        Color(0xFF314947), Color(0xFF718784), Color(0xFFDCEEEB), Color(0xFFFFE9CE),
        "🐚", "把今天吹成一阵轻轻的风"
    ),
    JournalThemeProfile(
        "forest_walk", "森林散步", "calm", "平静", "🌿",
        Color(0xFFF4F7EC), Color(0xFFFEFFF9), Color(0xFF8EAA78), Color(0xFFC7A977),
        Color(0xFF3E4938), Color(0xFF7E8878), Color(0xFFE4EED8), Color(0xFFF0E4CC),
        "🌱", "慢慢走，也是在往前"
    ),
    JournalThemeProfile(
        "cream_coffee", "奶油咖啡", "calm", "平静", "☕",
        Color(0xFFFBF4E8), Color(0xFFFFFCF7), Color(0xFFB88A68), Color(0xFFD3B79C),
        Color(0xFF4F4036), Color(0xFF89786B), Color(0xFFF1DFCF), Color(0xFFF6EBDD),
        "🥐", "今天像一杯温温的咖啡"
    ),
    JournalThemeProfile(
        "cherry_afternoon", "樱花下午", "calm", "平静", "🌸",
        Color(0xFFFFF7F6), Color(0xFFFFFDFC), Color(0xFFD89AA2), Color(0xFFA9B88F),
        Color(0xFF514143), Color(0xFF8C777A), Color(0xFFF8DDDF), Color(0xFFE5ECD9),
        "🌸", "给普通的一天留一点柔软"
    ),
    JournalThemeProfile(
        "lavender_nap", "薰衣草小憩", "tired", "有点累", "☕",
        Color(0xFFF5F1F8), Color(0xFFFDFBFE), Color(0xFFA998C8), Color(0xFFD0B28F),
        Color(0xFF484052), Color(0xFF81778A), Color(0xFFE7DEF1), Color(0xFFF2E4D3),
        "💤", "今天不用把电量用到零"
    ),
    JournalThemeProfile(
        "cat_home", "小猫宅家", "tired", "有点累", "🐈",
        Color(0xFFFFF5E9), Color(0xFFFFFCF8), Color(0xFFC99A72), Color(0xFFB5A394),
        Color(0xFF4E4036), Color(0xFF8A7B70), Color(0xFFF1DFCC), Color(0xFFE8E0D9),
        "🐾", "做一点，歇一会儿，也很好"
    ),
    JournalThemeProfile(
        "rain_store", "雨后便利店", "sad", "难过", "🌧️",
        Color(0xFFF0F4F5), Color(0xFFFBFDFD), Color(0xFF7F9FA9), Color(0xFFE0B47B),
        Color(0xFF37484D), Color(0xFF72858B), Color(0xFFDDE9EC), Color(0xFFF5E5CC),
        "☔", "今天也可以慢一点"
    ),
    JournalThemeProfile(
        "blue_window", "蓝灰小窗", "sad", "难过", "🪟",
        Color(0xFFF2F5F7), Color(0xFFFDFEFE), Color(0xFF8CA5B5), Color(0xFFC4B4A4),
        Color(0xFF3E4950), Color(0xFF77858C), Color(0xFFE0E8ED), Color(0xFFECE4DD),
        "🕯️", "先让今天安静地待在这里"
    ),
    JournalThemeProfile(
        "moon_desk", "深夜书桌", "quiet", "想安静", "🌙",
        Color(0xFFF2F0F5), Color(0xFFFDFCFD), Color(0xFF8E85A6), Color(0xFFC6B68F),
        Color(0xFF443F4D), Color(0xFF7A7482), Color(0xFFE4DFED), Color(0xFFF0E8D5),
        "📚", "少一点声音，多一点自己的空间"
    ),
    JournalThemeProfile(
        "cloud_sunday", "云朵星期天", "quiet", "想安静", "☁️",
        Color(0xFFF7F7F2), Color(0xFFFFFFFF), Color(0xFFA3ADB2), Color(0xFFD5BFA7),
        Color(0xFF44494A), Color(0xFF7E8587), Color(0xFFE7EBEC), Color(0xFFF1E7DD),
        "☁️", "今天留白一点也没关系"
    )
)

val LocalJournalTheme = staticCompositionLocalOf {
    JournalThemes.first { it.id == "forest_walk" }
}

fun resolveJournalTheme(
    moodId: String,
    date: LocalDate = LocalDate.now(),
    offset: Int = 0
): JournalThemeProfile {
    val pool = if (moodId == "random") JournalThemes
    else JournalThemes.filter { it.moodId == moodId }.ifEmpty { JournalThemes }

    val raw = date.toEpochDay().toInt() * 31 + offset * 17 + moodId.hashCode()
    val index = abs(raw).mod(pool.size)
    return pool[index]
}

@Composable
fun JournalMaterialTheme(
    profile: JournalThemeProfile,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalJournalTheme provides profile) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = profile.primary,
                secondary = profile.secondary,
                background = profile.background,
                surface = profile.surface,
                surfaceVariant = profile.palePrimary,
                primaryContainer = profile.palePrimary,
                secondaryContainer = profile.paleSecondary,
                onPrimary = Color.White,
                onSecondary = Color.White,
                onBackground = profile.text,
                onSurface = profile.text,
                onSurfaceVariant = profile.muted
            ),
            content = content
        )
    }
}

@Composable
fun JournalDayHeader(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalJournalTheme.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = style.surface),
        shape = RoundedCornerShape(26.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .padding(start = 26.dp, top = 2.dp)
                    .width(72.dp)
                    .height(18.dp)
                    .rotate(-4f)
                    .background(style.paleSecondary.copy(alpha = 0.82f), RoundedCornerShape(3.dp))
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(style.stickerEmoji, fontSize = 36.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        style.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = style.text
                    )
                    Text(
                        style.moodEmoji + " " + style.moodLabel + " · " + style.tagline,
                        color = style.muted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = style.muted)
            }
        }
    }
}

@Composable
fun MoodJournalScreen(
    currentMoodId: String,
    onSelectMood: (String) -> Unit,
    onShuffle: () -> Unit,
    onBack: () -> Unit
) {
    val style = LocalJournalTheme.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(style.background)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            Spacer(Modifier.width(4.dp))
            Column {
                Text("今天的手账", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("心情由你自己选，拾光盒只负责陪你换氛围。", color = style.muted, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        JournalDayHeader(onOpen = onShuffle)

        Spacer(Modifier.height(18.dp))

        Text("今天感觉怎么样？", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))

        JournalMoodOptions.forEach { mood ->
            val selected = currentMoodId == mood.id
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clickable { onSelectMood(mood.id) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) style.palePrimary else style.surface
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(mood.emoji, fontSize = 28.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(mood.label, fontWeight = FontWeight.Bold)
                        Text(mood.description, color = style.muted, fontSize = 12.sp)
                    }
                    if (selected) {
                        Text("今天", color = style.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        OutlinedButton(
            onClick = onShuffle,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.Casino, null)
            Spacer(Modifier.width(8.dp))
            Text("同一种心情，换一套手账")
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Palette, null, tint = style.secondary)
            Spacer(Modifier.width(8.dp))
            Text(
                "同一天选好后会保持这套风格；第二天会根据心情自动换一套。",
                color = style.muted,
                fontSize = 12.sp
            )
        }
    }
}
