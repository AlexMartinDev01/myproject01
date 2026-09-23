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
                        "V1.1.2 睡醒过渡修正版 · 打哈欠 / 蜷睡 / 伸懒腰 / 更明显尾巴",
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("橘团现在能做什么", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("• 悬浮在其他 App 上方，可拖动并自动吸边")
                    Text("• 同一张高清母版实时网格变形，不切换整只猫图片")
                    Text("• 待机时会轻微呼吸、随机眨眼；尾巴摆动幅度再次加大但保持慢速柔和")
                    Text("• 5 分钟没有互动会先进入打哈欠犯困姿态，约 4 秒后切换成真正闭眼蜷睡")
                    Text("• 点击、拖动、提醒都会把橘团从困倦/睡眠中唤醒，并经过伸懒腰过渡")
                    Text("• 到点提醒会连续挥爪；完成待办会进入开心庆祝状态")
                    Text("• 点击橘团：记一下 / 加待办 / 查看今天")
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
