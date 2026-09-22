package com.shiguangbox.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun KnowledgeImageImportScreen(
    db: AppDatabase,
    onBack: () -> Unit,
    onCreated: (Long) -> Unit
) {
    val journal = LocalJournalTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var importedPaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var importing by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            importing = true
            scope.launch {
                val newPaths = withContext(Dispatchers.IO) {
                    ImageStorage.importImages(context, uris)
                }
                importedPaths = (importedPaths + newPaths).take(6)
                importing = false
                message =
                    if (newPaths.isEmpty()) "图片读取失败，请换一张试试"
                    else "已导入 " + newPaths.size + " 张图片"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(journal.background)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, "返回")
            }
            Text("新建图文知识卡", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = journal.surface),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "图片 / 截图 / 书页 / PPT / 图表",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "最多一次选择 6 张。AI 会结合图片中真正可见的信息和你附带的文字一起整理。",
                    color = journal.muted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { picker.launch(arrayOf("image/*")) },
                    enabled = !importing && importedPaths.size < 6,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.AddPhotoAlternate, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (importing) "正在导入…"
                        else "选择图片（已选 " + importedPaths.size + "/6）"
                    )
                }

                if (message.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(message, color = journal.secondary, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("标题") },
            placeholder = { Text("例如：产品经理面试截图") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
            label = { Text("附带文字（可选）") },
            placeholder = { Text("可以补充来源、上下文、复制的正文……") },
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
            label = { Text("我的想法（可选）") },
            placeholder = { Text("为什么想留下它？") },
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = {
                if (importedPaths.isEmpty()) return@Button

                scope.launch {
                    val id = db.favoriteDao().insert(
                        FavoriteEntity(
                            title = title.ifBlank { "图文知识卡" }.trim(),
                            platform = "图片",
                            rawText = text.trim(),
                            note = note.trim(),
                            knowledgeType = "图文",
                            imagePaths = ImageStorage.encodePaths(importedPaths),
                            processingStatus = "等待AI整理"
                        )
                    )

                    KnowledgeOrganizeWorker.enqueue(context, id)
                    onCreated(id)
                }
            },
            enabled = importedPaths.isNotEmpty() && !importing,
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Icon(Icons.Outlined.AutoAwesome, null)
            Spacer(Modifier.width(8.dp))
            Text("保存并 AI 整理", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "图片会先保存在拾光盒本机。执行 AI 分析时，所选图片和附带文字会发送到你配置的 DeepSeek 接口。",
            color = journal.muted,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}
