package com.shiguangbox.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@Composable
fun PetSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("shiguangbox_settings", Context.MODE_PRIVATE)
    }

    var overlayGranted by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    var enabled by rememberSaveable {
        mutableStateOf(prefs.getBoolean("pet_enabled", false))
    }
    var message by rememberSaveable {
        mutableStateOf("")
    }

    var motionPreviewIndex by rememberSaveable {
        mutableStateOf(0)
    }

    var motionPreviewIntensity by rememberSaveable {
        mutableStateOf(1.0f)
    }

    val motionPreviewOptions =
        listOf(
            PetMotion.LOOK_AROUND,
            PetMotion.HEAD_TILT,
            PetMotion.LOOK_UP,
            PetMotion.STRETCH,
            PetMotion.SMALL_JUMP,
            PetMotion.DOZE_NOD,
            PetMotion.SHY,
            PetMotion.SEEK_ATTENTION
        )

    var actionLevel by rememberSaveable {
        mutableStateOf(
            prefs.getInt("pet_action_level", 1)
        )
    }

    var companionEnabled by rememberSaveable {
        mutableStateOf(
            prefs.getBoolean(
                "pet_companion_enabled",
                true
            )
        )
    }

    var clinginessLevel by rememberSaveable {
        mutableStateOf(
            prefs.getInt(
                "pet_clinginess_level",
                1
            )
                .coerceIn(
                    0,
                    3
                )
        )
    }

    var quietNight by rememberSaveable {
        mutableStateOf(
            prefs.getBoolean(
                "pet_quiet_night",
                true
            )
        )
    }

    var autoSleep by rememberSaveable {
        mutableStateOf(
            prefs.getBoolean("pet_auto_sleep", true)
        )
    }

    var sleepMinutes by rememberSaveable {
        mutableStateOf(
            prefs.getInt("pet_sleep_minutes", 4)
        )
    }

    var edgePeek by rememberSaveable {
        mutableStateOf(
            prefs.getBoolean("pet_edge_peek", true)
        )
    }

    var autoSnap by rememberSaveable {
        mutableStateOf(
            prefs.getBoolean("pet_auto_snap", true)
        )
    }

    var petSizeDp by rememberSaveable {
        mutableStateOf(
            prefs.getInt("pet_size_dp", 112)
        )
    }

    var petAlpha by rememberSaveable {
        mutableStateOf(
            prefs.getInt("pet_alpha_percent", 100)
        )
    }

    var selectedPetId by rememberSaveable {
        mutableStateOf(
            prefs.getString(
                "pet_selected_id",
                PetKind.ORANGE.id
            ) ?: PetKind.ORANGE.id
        )
    }

    val selectedPet =
        PetProfiles.fromId(
            selectedPetId
        )

    val selectedPetDrawable =
        when (selectedPet) {
            PetKind.YAYA ->
                R.drawable.pet_yaya_idle
            else ->
                R.drawable.pet_orange_idle
        }

    val selectedPetPainter: Painter =
        if (
            selectedPet ==
            PetKind.YUTUAN
        ) {
            val bitmap =
                YutuanEmbeddedAsset.bitmap

            if (bitmap != null) {
                remember(bitmap) {
                    BitmapPainter(
                        bitmap.asImageBitmap()
                    )
                }
            } else {
                painterResource(
                    R.drawable.pet_orange_idle
                )
            }
        } else {
            painterResource(
                selectedPetDrawable
            )
        }


    val overlayLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        overlayGranted = Settings.canDrawOverlays(context)
        message =
            if (overlayGranted) {
                "悬浮窗权限已开启，可以启动桌宠啦"
            } else {
                "还没有获得悬浮窗权限"
            }
    }

    val transition = rememberInfiniteTransition(label = "pet_preview")
    val scale by transition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(1700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pet_scale"
    )

    fun startPet() {
        if (!Settings.canDrawOverlays(context)) {
            overlayLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + context.packageName)
                )
            )
            return
        }

        prefs.edit().putBoolean("pet_enabled", true).apply()
        enabled = true
        ContextCompat.startForegroundService(
            context,
            Intent(context, PetOverlayService::class.java)
                .setAction(PetOverlayService.ACTION_SHOW)
        )
        message =
            selectedPet.displayName +
                "已经出来陪你啦"
    }

    fun switchPet(id: String) {
        selectedPetId = id

        prefs.edit()
            .putString(
                "pet_selected_id",
                id
            )
            .apply()

        if (
            enabled &&
            overlayGranted
        ) {
            ContextCompat
                .startForegroundService(
                    context,
                    Intent(
                        context,
                        PetOverlayService::class.java
                    ).setAction(
                        PetOverlayService
                            .ACTION_REFRESH_SETTINGS
                    )
                )
        }

        val name =
            PetProfiles
                .fromId(id)
                .displayName

        message =
            "已经切换为" +
                name +
                "啦"
    }

    fun stopPet() {
        prefs.edit().putBoolean("pet_enabled", false).apply()
        enabled = false
        context.stopService(Intent(context, PetOverlayService::class.java))
        message = "桌宠已关闭，随时可以再叫它出来"
    }

    fun applyPetSettings() {
        prefs.edit()
            .putInt(
                "pet_action_level",
                actionLevel.coerceIn(0, 2)
            )
            .putBoolean(
                "pet_companion_enabled",
                companionEnabled
            )
            .putInt(
                "pet_clinginess_level",
                clinginessLevel.coerceIn(0, 3)
            )
            .putBoolean(
                "pet_quiet_night",
                quietNight
            )
            .putBoolean(
                "pet_auto_sleep",
                autoSleep
            )
            .putInt(
                "pet_sleep_minutes",
                sleepMinutes.coerceIn(3, 5)
            )
            .putBoolean(
                "pet_edge_peek",
                edgePeek
            )
            .putBoolean(
                "pet_auto_snap",
                autoSnap
            )
            .putInt(
                "pet_size_dp",
                petSizeDp.coerceIn(88, 150)
            )
            .putInt(
                "pet_alpha_percent",
                petAlpha.coerceIn(55, 100)
            )
            .putString(
                "pet_selected_id",
                selectedPetId
            )
            .apply()

        if (enabled && overlayGranted) {
            ContextCompat.startForegroundService(
                context,
                Intent(
                    context,
                    PetOverlayService::class.java
                ).setAction(
                    PetOverlayService
                        .ACTION_REFRESH_SETTINGS
                )
            )
        }

        message = "陪伴、行为与外观设置已应用"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, "返回")
                }
                Column {
                    Text("我的桌宠", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "V1.9.3 行为编排系统 · 橘团 / 芽芽 / 雨团",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(26.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter =
                            selectedPetPainter,
                        contentDescription =
                            selectedPet.displayName,
                        modifier = Modifier.size(180.dp).scale(scale)
                    )
                    Text(
                        selectedPet.displayName +
                            " " +
                            selectedPet.emoji,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        selectedPet.subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor =
                        MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        "选择陪伴伙伴",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(
                        Modifier.height(10.dp)
                    )
                    Text(
                        "每只宠物拥有独立母版、绑定参数和动作节奏，不是简单换皮。",
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Spacer(
                        Modifier.height(12.dp)
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            )
                    ) {
                        listOf(
                            PetKind.ORANGE,
                            PetKind.YAYA,
                            PetKind.YUTUAN
                        ).forEach { kind ->
                            if (
                                selectedPetId ==
                                kind.id
                            ) {
                                Button(
                                    onClick = {
                                        switchPet(
                                            kind.id
                                        )
                                    },
                                    modifier =
                                        Modifier.weight(1f)
                                ) {
                                    Text(
                                        kind.displayName +
                                            " " +
                                            kind.emoji
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        switchPet(
                                            kind.id
                                        )
                                    },
                                    modifier =
                                        Modifier.weight(1f)
                                ) {
                                    Text(
                                        kind.displayName +
                                            " " +
                                            kind.emoji
                                    )
                                }
                            }
                        }
                    }

                    Spacer(
                        Modifier.height(10.dp)
                    )
                    Text(
                        when (
                            PetProfiles.fromId(
                                selectedPetId
                            )
                        ) {
                            PetKind.YAYA ->
                                "芽芽：平静系垂耳兔 · 耳朵、腿部、挥爪与呼吸更灵动，依然保持柔和自然。"

                            PetKind.YUTUAN ->
                                "雨团：雨天系云朵小狗 · 雨滴更大更密、近景雨更明显，落地水花/涟漪和抖水喷溅同步增强。"

                            else ->
                                "橘团：开心系小橘猫 · 摇尾巴、挥爪和庆祝动作更明显。"
                        },
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Pets, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("悬浮桌宠", fontWeight = FontWeight.Bold)
                            Text(
                                if (overlayGranted) "系统悬浮窗权限：已开启" else "系统悬浮窗权限：未开启",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { checked ->
                                if (checked) startPet() else stopPet()
                            }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    if (!overlayGranted) {
                        OutlinedButton(
                            onClick = {
                                overlayLauncher.launch(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:" + context.packageName)
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("开启“显示在其他应用上层”权限")
                        }
                    }

                    if (overlayGranted && enabled) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                ContextCompat.startForegroundService(
                                    context,
                                    Intent(context, PetOverlayService::class.java)
                                        .setAction(PetOverlayService.ACTION_TEST_WAVE)
                                )
                                message =
                                    "已经让" +
                                        selectedPet.displayName +
                                        "挥爪啦"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("测试 1.35 秒挥爪")
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                ContextCompat.startForegroundService(
                                    context,
                                    Intent(context, PetOverlayService::class.java)
                                        .setAction(PetOverlayService.ACTION_TEST_TAIL)
                                )
                                message =
                                    if (
                                        selectedPet ==
                                        PetKind.YAYA
                                    ) {
                                        "已经让芽芽自然晃耳朵啦"
                                    } else {
                                        "已经让橘团大幅摇尾巴啦"
                                    }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (
                                    selectedPet ==
                                    PetKind.YAYA
                                ) {
                                    "测试自然耳朵轻晃"
                                } else {
                                    "测试大幅摇尾巴"
                                }
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                ContextCompat.startForegroundService(
                                    context,
                                    Intent(
                                        context,
                                        PetOverlayService::class.java
                                    ).setAction(
                                        PetOverlayService
                                            .ACTION_TEST_PETTING
                                    )
                                )
                                message =
                                    selectedPet.displayName +
                                    "进入被摸摸状态啦"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("测试长按摸摸效果")
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                ContextCompat.startForegroundService(
                                    context,
                                    Intent(
                                        context,
                                        PetOverlayService::class.java
                                    ).setAction(
                                        PetOverlayService
                                            .ACTION_TEST_BUBBLE
                                    )
                                )
                                message =
                                    "已经弹出新版气泡预览"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                when (selectedPet) {
                                    PetKind.ORANGE ->
                                        "预览橘团专属猫咪气泡"

                                    PetKind.YAYA ->
                                        "预览芽芽专属花叶气泡"

                                    PetKind.YUTUAN ->
                                        "预览雨团专属雨云气泡"
                                }
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                ContextCompat.startForegroundService(
                                    context,
                                    Intent(
                                        context,
                                        PetOverlayService::class.java
                                    ).setAction(
                                        PetOverlayService
                                            .ACTION_TEST_COMPANION
                                    )
                                )
                                message =
                                    "已经触发一次" +
                                        selectedPet.displayName +
                                        "主动陪伴"
                            },
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {
                            Text("测试主动陪伴")
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                ContextCompat.startForegroundService(
                                    context,
                                    Intent(context, PetOverlayService::class.java)
                                        .setAction(PetOverlayService.ACTION_TEST_BLINK)
                                )
                                message =
                                    "已经让" +
                                        selectedPet.displayName +
                                        "眨眼啦"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("测试眨眼")
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                ContextCompat.startForegroundService(
                                    context,
                                    Intent(context, PetOverlayService::class.java)
                                        .setAction(PetOverlayService.ACTION_TEST_TIRED)
                                )
                                message = selectedPet.displayName +
                                    "会先犯困，约 4 秒后进入真正睡姿"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("测试：犯困 → 睡觉")
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                ContextCompat.startForegroundService(
                                    context,
                                    Intent(context, PetOverlayService::class.java)
                                        .setAction(PetOverlayService.ACTION_TEST_SLEEP)
                                )
                                message = selectedPet.displayName +
                                    "会切换为真正闭眼睡姿，并保持慢呼吸"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("直接测试睡觉")
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                ContextCompat.startForegroundService(
                                    context,
                                    Intent(context, PetOverlayService::class.java)
                                        .setAction(PetOverlayService.ACTION_TEST_WAKE)
                                )
                                message = selectedPet.displayName +
                                    "会从睡姿切到伸懒腰，再回到待机"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("测试醒来")
                        }
                    }

                    if (message.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors =
                    CardDefaults
                        .cardColors(
                            containerColor =
                                MaterialTheme
                                    .colorScheme
                                    .surface
                        ),
                shape =
                    RoundedCornerShape(
                        22.dp
                    )
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        "动作与行为实验室",
                        fontWeight =
                            FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    Spacer(
                        Modifier.height(6.dp)
                    )

                    Text(
                        "8 个新动作可单独播放，也可以直接演示 V1.9.3 的完整行为链；三只宠物会使用不同节奏和动作组合。",
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(
                        Modifier.height(14.dp)
                    )

                    val selectedMotion =
                        motionPreviewOptions[
                            motionPreviewIndex
                                .coerceIn(
                                    0,
                                    motionPreviewOptions
                                        .lastIndex
                                )
                        ]

                    Text(
                        "当前动作：" +
                            selectedMotion
                                .displayName,
                        fontWeight =
                            FontWeight.Medium
                    )

                    Slider(
                        value =
                            motionPreviewIndex
                                .toFloat(),
                        onValueChange = {
                            motionPreviewIndex =
                                it.toInt()
                                    .coerceIn(
                                        0,
                                        motionPreviewOptions
                                            .lastIndex
                                    )
                        },
                        valueRange =
                            0f..
                                motionPreviewOptions
                                    .lastIndex
                                    .toFloat(),
                        steps =
                            (
                                motionPreviewOptions
                                    .size -
                                    2
                                )
                                .coerceAtLeast(
                                    0
                                )
                    )

                    Text(
                        when (
                            selectedMotion
                        ) {
                            PetMotion.LOOK_AROUND ->
                                "左看看 → 右看看 → 回中间，头部和身体有轻微反向补偿"

                            PetMotion.HEAD_TILT ->
                                "头部局部歪向一侧，中段会眨眼，不是整张图片旋转"

                            PetMotion.LOOK_UP ->
                                "头部轻轻抬起，适合看气泡、看彩虹和发现东西"

                            PetMotion.STRETCH ->
                                "上半身拉伸、身体舒展，适合久坐或完成任务之后"

                            PetMotion.SMALL_JUMP ->
                                "蓄力 → 腾空 → 落地压缩 → 回弹，橘团会更明显"

                            PetMotion.DOZE_NOD ->
                                "眼睛慢慢闭上、头往下点，再重新醒过来，不会直接睡着"

                            PetMotion.SHY ->
                                "上半身轻缩、低头并小幅歪头，适合被摸或被夸"

                            PetMotion.SEEK_ATTENTION ->
                                "身体向一侧靠近、抬头并轻轻弹动，作为主动求关注姿态"

                            else ->
                                ""
                        },
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(
                        Modifier.height(12.dp)
                    )

                    Text(
                        "动作强度：" +
                            String.format(
                                "%.1f",
                                motionPreviewIntensity
                            ) +
                            "×"
                    )

                    Slider(
                        value =
                            motionPreviewIntensity,
                        onValueChange = {
                            motionPreviewIntensity =
                                it.coerceIn(
                                    0.6f,
                                    1.4f
                                )
                        },
                        valueRange =
                            0.6f..1.4f
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Button(
                        onClick = {
                            if (
                                enabled &&
                                overlayGranted
                            ) {
                                ContextCompat
                                    .startForegroundService(
                                        context,
                                        Intent(
                                            context,
                                            PetOverlayService::class.java
                                        )
                                            .setAction(
                                                PetOverlayService
                                                    .ACTION_TEST_MOTION
                                            )
                                            .putExtra(
                                                PetOverlayService
                                                    .EXTRA_MOTION_ID,
                                                selectedMotion
                                                    .name
                                            )
                                            .putExtra(
                                                PetOverlayService
                                                    .EXTRA_MOTION_INTENSITY,
                                                motionPreviewIntensity
                                            )
                                    )

                                message =
                                    "正在播放：" +
                                        selectedMotion
                                            .displayName
                            } else {
                                message =
                                    "先开启悬浮桌宠，再测试动作"
                            }
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "播放「" +
                                selectedMotion
                                    .displayName +
                                "」"
                        )
                    }

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    OutlinedButton(
                        onClick = {
                            if (
                                enabled &&
                                overlayGranted
                            ) {
                                ContextCompat
                                    .startForegroundService(
                                        context,
                                        Intent(
                                            context,
                                            PetOverlayService::class.java
                                        ).setAction(
                                            PetOverlayService
                                                .ACTION_TEST_MOTION_SHOWCASE
                                        )
                                    )

                                message =
                                    "开始连续演示 8 个新动作，完整看完约半分钟"
                            } else {
                                message =
                                    "先开启悬浮桌宠，再测试动作"
                            }
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "连续演示 8 个新动作"
                        )
                    }

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Button(
                        onClick = {
                            if (
                                enabled &&
                                overlayGranted
                            ) {
                                ContextCompat
                                    .startForegroundService(
                                        context,
                                        Intent(
                                            context,
                                            PetOverlayService::class.java
                                        ).setAction(
                                            PetOverlayService
                                                .ACTION_TEST_BEHAVIOR_SEQUENCE
                                        )
                                    )

                                message =
                                    "正在演示 " +
                                        selectedPet.displayName +
                                        " 的 V1.9.3 行为链"
                            } else {
                                message =
                                    "先开启悬浮桌宠，再测试行为链"
                            }
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "演示 V1.9.3 完整行为链"
                        )
                    }

                    Text(
                        when (
                            selectedPet
                        ) {
                            PetKind.ORANGE ->
                                "橘团：左右观察 → 主动求关注 → 歪头 → 再给反馈。"

                            PetKind.YAYA ->
                                "芽芽：抬头 → 歪头 → 轻轻害羞，节奏更慢、更克制。"

                            PetKind.YUTUAN ->
                                "雨团：抬头 → 歪头 → 轻轻求关注，整体节奏更柔和。"
                        },
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier =
                            Modifier.padding(
                                top = 8.dp
                            )
                    )
                }
            }
        }

        item {
            Card(
                colors =
                    CardDefaults
                        .cardColors(
                            containerColor =
                                MaterialTheme
                                    .colorScheme
                                    .surface
                        ),
                shape =
                    RoundedCornerShape(
                        22.dp
                    )
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        "主动陪伴",
                        fontWeight =
                            FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Row(
                        verticalAlignment =
                            Alignment
                                .CenterVertically
                    ) {
                        Column(
                            Modifier.weight(1f)
                        ) {
                            Text(
                                "让宠物主动生活",
                                fontWeight =
                                    FontWeight.Medium
                            )
                            Text(
                                "会主动做小动作、来找你、根据待办和心情说话",
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }

                        Switch(
                            checked =
                                companionEnabled,
                            onCheckedChange = {
                                companionEnabled =
                                    it
                            }
                        )
                    }

                    if (
                        companionEnabled
                    ) {
                        Spacer(
                            Modifier.height(12.dp)
                        )

                        val clinginessLabel =
                            when (
                                clinginessLevel
                            ) {
                                0 -> "安静"
                                2 -> "粘人"
                                3 -> "超粘人"
                                else -> "陪伴"
                            }

                        Text(
                            "粘人度：" +
                                clinginessLabel,
                            fontWeight =
                                FontWeight.Medium
                        )

                        Slider(
                            value =
                                clinginessLevel
                                    .toFloat(),
                            onValueChange = {
                                clinginessLevel =
                                    it.toInt()
                                        .coerceIn(
                                            0,
                                            3
                                        )
                            },
                            valueRange =
                                0f..3f,
                            steps = 2
                        )

                        Text(
                            when (
                                clinginessLevel
                            ) {
                                0 ->
                                    "大部分时间安静待着，偶尔才主动找你"

                                2 ->
                                    "会比较主动地挥爪、撒娇、提醒和来找你"

                                3 ->
                                    "存在感很强，会更频繁地主动互动和冒气泡"

                                else ->
                                    "偶尔主动互动，保持陪伴感但不会一直打扰"
                            },
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant,
                            fontSize = 12.sp
                        )

                        Spacer(
                            Modifier.height(12.dp)
                        )

                        Row(
                            verticalAlignment =
                                Alignment
                                    .CenterVertically
                        ) {
                            Column(
                                Modifier.weight(1f)
                            ) {
                                Text(
                                    "夜间安静",
                                    fontWeight =
                                        FontWeight.Medium
                                )
                                Text(
                                    "23:30–08:00 主动气泡暂停，只保留很轻的小动作",
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }

                            Switch(
                                checked =
                                    quietNight,
                                onCheckedChange = {
                                    quietNight =
                                        it
                                }
                            )
                        }

                        Spacer(
                            Modifier.height(10.dp)
                        )

                        Text(
                            when (
                                selectedPet
                            ) {
                                PetKind.ORANGE ->
                                    "橘团偏主动热情，同一粘人度下更容易挥爪、摇尾巴和来找你。"

                                PetKind.YAYA ->
                                    "芽芽更克制安静，同一粘人度下更多是轻动作和温柔陪伴。"

                                PetKind.YUTUAN ->
                                    "雨团偏情绪表达，会把主动互动和雨、安静状态结合起来。"
                            },
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .primary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor =
                        MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        "行为与外观",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "活跃程度：" +
                            when (actionLevel) {
                                0 -> "安静"
                                2 -> "活泼"
                                else -> "自然"
                            }
                    )

                    Slider(
                        value = actionLevel.toFloat(),
                        onValueChange = {
                            actionLevel =
                                it.toInt().coerceIn(0, 2)
                        },
                        valueRange = 0f..2f,
                        steps = 1
                    )

                    Text(
                        when (selectedPet) {
                            PetKind.YAYA ->
                                "控制随机眨眼、耳朵轻晃、新微动作与中动作出现的频率"

                            PetKind.YUTUAN ->
                                "控制随机眨眼、抬爪、新微动作与中动作出现的频率；真实细雨会持续自然变化"

                            else ->
                                "控制随机眨眼、摇尾巴、挥爪和新生活动作出现的频率"
                        },
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        fontSize = 12.sp
                    )

                    Spacer(Modifier.height(14.dp))

                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "自动犯困睡觉",
                                fontWeight =
                                    FontWeight.Medium
                            )
                            Text(
                                "长时间不互动后先犯困，再蜷睡",
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }

                        Switch(
                            checked = autoSleep,
                            onCheckedChange = {
                                autoSleep = it
                            }
                        )
                    }

                    if (autoSleep) {
                        Text(
                            "无互动 " +
                                sleepMinutes +
                                " 分钟后开始犯困"
                        )

                        Slider(
                            value =
                                sleepMinutes.toFloat(),
                            onValueChange = {
                                sleepMinutes =
                                    it.toInt()
                                        .coerceIn(3, 5)
                            },
                            valueRange = 3f..5f,
                            steps = 1
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Text(
                            "松手自动吸边",
                            Modifier.weight(1f)
                        )
                        Switch(
                            checked = autoSnap,
                            onCheckedChange = {
                                autoSnap = it
                            }
                        )
                    }

                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("吸边后偶尔探头")
                            Text(
                                "缩进去一点，再探出来眨眼 / 挥爪",
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }

                        Switch(
                            checked = edgePeek,
                            onCheckedChange = {
                                edgePeek = it
                            }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "宠物大小：" +
                            petSizeDp +
                            " dp"
                    )

                    Slider(
                        value = petSizeDp.toFloat(),
                        onValueChange = {
                            petSizeDp =
                                it.toInt()
                                    .coerceIn(88, 150)
                        },
                        valueRange = 88f..150f
                    )

                    Text(
                        "透明度：" +
                            petAlpha +
                            "%"
                    )

                    Slider(
                        value = petAlpha.toFloat(),
                        onValueChange = {
                            petAlpha =
                                it.toInt()
                                    .coerceIn(55, 100)
                        },
                        valueRange = 55f..100f
                    )

                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = {
                            applyPetSettings()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("应用设置")
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text(
                        selectedPet.displayName +
                            "现在能做什么",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("• 悬浮在其他 App 上方，可拖动并自动吸边")
                    Text("• 待机动作使用高清母版实时局部网格绑定，不是整张图片硬摇")
                    Text(
                        when (selectedPet) {
                            PetKind.YAYA ->
                                "• 耳根稳定 → 耳身传递 → 耳尖柔和延迟；腿从根部传递，脸部锁定"

                            PetKind.YUTUAN ->
                                "• 两只垂耳从耳根传递到耳尖；雨滴会落地形成水花/涟漪，也会命中耳朵和围巾并挂水珠"

                            else ->
                                "• 摇尾巴只影响尾巴局部，头脸锁定；可配合眨眼和挥爪"
                        }
                    )
                    Text("• 3–5 分钟无互动可自动犯困 → 打哈欠 → 闭眼蜷睡，时间可调")
                    Text("• 橘团、芽芽、雨团均使用各自的透明原图气泡；文字区会自动伸缩，整体继续保持小巧不挡屏幕")
                    Text("• 待办提前 10 分钟轻提醒；到点正式提醒；完成后根据今天剩余任务庆祝")
                    Text(
                        "• 点击" +
                            selectedPet.displayName +
                            "：记一下 / 加待办 / 查看今天"
                    )
                    Text("• 每天第一次见面会根据时间、今天待办和心情说一句不同的话")
                    Text("• 主动陪伴会根据粘人度自行决定：只做小动作、主动说话、来找你、轻提醒或安静陪伴")
                    Text("• 动作引擎 2.0 新增左右观察、歪头、抬头、伸懒腰、小跳、困倦点头、害羞、主动求关注 8 个独立动作")
                    Text("• V1.9.3 行为编排系统：动作按顺序完整播放，加入自然停顿和回中，不再用固定延时互相覆盖")
                    Text("• 单击、双击、连续点击、长按摸摸都接入不同的三宠专属动作链")
                    Text("• 用户拖动、正式提醒、睡眠等高优先级状态会安全中断普通行为链")
                    Text("• 连续行为链：先动作、再观察、再气泡；部分事件可以直接回应“摸摸你 / 我先忙”")
                    Text("• 新增短期记忆：同一种生活事件不会短时间重复；刚完成的任务过一会儿也可能被宠物重新提起")
                    Text("• 长时间没互动后，橘团 / 芽芽 / 雨团会用各自性格来找你；亲密感会在后台慢慢积累，不显示游戏数值")
                    Text("• 夜间安静模式默认 23:30–08:00，只保留很轻的小动作")
                    Text(
                        "• 心情切换后" +
                            selectedPet.displayName +
                            "会用不同语气回应"
                    )
                    Text(
                        "• 待办到点后，" +
                            selectedPet.displayName +
                            "会挥爪并弹出提醒"
                    )
                    Text("• 提醒里可以直接完成，或者延后 10 分钟")
                    Text("• 完成任务后会有庆祝反馈")
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "目前已经正式接入橘团、芽芽与雨团。后续困困、墨墨、盒仔继续沿用这套多宠物 Profile 架构接入。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
