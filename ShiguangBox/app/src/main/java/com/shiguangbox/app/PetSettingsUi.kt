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
    var message by rememberSaveable { mutableStateOf("") }

    var actionLevel by rememberSaveable {
        mutableStateOf(
            prefs.getInt("pet_action_level", 1)
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

    val overlayLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        overlayGranted = Settings.canDrawOverlays(context)
        message = if (overlayGranted) "悬浮窗权限已开启，可以启动橘团啦" else "还没有获得悬浮窗权限"
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
        message = "橘团已经出来陪你啦"
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

        message = "行为与外观设置已应用"
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
                        "V1.4.0 场景联动版 · 真气泡 / 待办 / 时间 / 心情 / 每日问候",
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
                        painter = painterResource(R.drawable.pet_orange_idle),
                        contentDescription = "橘团",
                        modifier = Modifier.size(180.dp).scale(scale)
                    )
                    Text("橘团 ☀️", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "开心系小橘猫 · 会提醒、会记事、会帮你快速加待办",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
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
                                message = "已经让橘团挥爪啦"
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
                                message = "已经让橘团大幅摇尾巴啦"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("测试大幅摇尾巴")
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
                                    "橘团进入被摸摸状态啦"
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
                            Text("预览新版气泡")
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                ContextCompat.startForegroundService(
                                    context,
                                    Intent(context, PetOverlayService::class.java)
                                        .setAction(PetOverlayService.ACTION_TEST_BLINK)
                                )
                                message = "已经让橘团眨眼啦"
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
                                message = "橘团会先打哈欠犯困，约 4 秒后进入真正的蜷睡姿态"
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
                                message = "橘团会切换为真正闭眼蜷睡姿态，并保持慢呼吸"
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
                                message = "橘团会从蜷睡切到伸懒腰，再回到待机"
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
                        "控制随机眨眼、摇尾巴和挥爪出现的频率",
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
                    Text("橘团现在能做什么", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("• 悬浮在其他 App 上方，可拖动并自动吸边")
                    Text("• 同一张高清母版实时网格变形，不切换整只猫图片")
                    Text("• 摇尾巴只影响尾巴局部，头脸锁定；摇尾巴期间会自然配合眨眼和挥爪")
                    Text("• 3–5 分钟无互动可自动犯困 → 打哈欠 → 闭眼蜷睡，时间可调")
                    Text("• 真正带尖角尾巴的奶油系气泡，会自动贴近橘团并避开屏幕边缘")
                    Text("• 待办提前 10 分钟轻提醒；到点正式提醒；完成后根据今天剩余任务庆祝")
                    Text("• 点击橘团：记一下 / 加待办 / 查看今天")
                    Text("• 每天第一次见面会根据时间、今天待办和心情说一句不同的话")
                    Text("• 深夜动作自动放慢；日常陪伴气泡有冷却，不会一直弹")
                    Text("• 心情切换后橘团会用不同语气回应")
                    Text("• 待办到点后，橘团会挥爪并弹出提醒")
                    Text("• 提醒里可以直接完成，或者延后 10 分钟")
                    Text("• 完成任务后会有庆祝反馈")
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "这一阶段只接入橘团；芽芽、困困、雨团、墨墨、盒仔会在同一套桌宠引擎稳定后接入。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
