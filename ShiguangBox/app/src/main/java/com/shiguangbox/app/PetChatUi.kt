package com.shiguangbox.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private data class PetChatTurn(
    val fromUser: Boolean,
    val text: String
)

@Composable
fun PetChatScreen(
    db: AppDatabase,
    onBack: () -> Unit,
    onAiSettings: () -> Unit
) {
    val context =
        LocalContext.current

    val prefs =
        remember {
            PetWorldStore
                .prefs(
                    context
                )
        }

    val scope =
        rememberCoroutineScope()

    val petKind =
        PetProfiles.fromId(
            prefs.getString(
                "pet_selected_id",
                PetKind.ORANGE.id
            )
        )

    val affection =
        prefs.getInt(
            "pet_affection_" +
                petKind.id,
            0
        )

    val bond =
        PetWorldStore
            .bondStage(
                affection
            )

    var input by
        rememberSaveable {
            mutableStateOf("")
        }

    var loading by
        rememberSaveable {
            mutableStateOf(false)
        }

    var error by
        rememberSaveable {
            mutableStateOf("")
        }

    val greeting =
        remember(
            petKind,
            bond
        ) {
            when (
                petKind
            ) {
                PetKind.ORANGE ->
                    if (
                        bond >=
                        PetBondStage.CLOSE
                    ) {
                        "你来啦～这次想和我说什么？"
                    } else {
                        "嘿，我在呢。想聊点什么？"
                    }

                PetKind.YAYA ->
                    if (
                        bond >=
                        PetBondStage.CLOSE
                    ) {
                        "我在这里，慢慢说就好。"
                    } else {
                        "嗯，我听着。"
                    }

                PetKind.YUTUAN ->
                    if (
                        bond >=
                        PetBondStage.CLOSE
                    ) {
                        "今天也一起待一会儿吧。你想说什么？"
                    } else {
                        "我在听。外面的雨先放轻一点。"
                    }
            }
        }

    val turns =
        remember(
            petKind
        ) {
            mutableStateListOf(
                PetChatTurn(
                    fromUser =
                        false,
                    text =
                        greeting
                )
            )
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme
                        .colorScheme
                        .background
                )
                .padding(
                    16.dp
                )
    ) {
        Row(
            verticalAlignment =
                Alignment
                    .CenterVertically
        ) {
            IconButton(
                onClick =
                    onBack
            ) {
                Icon(
                    Icons.Outlined
                        .ArrowBack,
                    "返回"
                )
            }

            Column(
                modifier =
                    Modifier.weight(
                        1f
                    )
            ) {
                Text(
                    "和" +
                        petKind.displayName +
                        "聊聊 " +
                        petKind.emoji,
                    fontSize =
                        22.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    "关系：" +
                        bond.label +
                        " · " +
                        bond.description,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                    fontSize =
                        12.sp
                )
            }
        }

        Spacer(
            Modifier.height(
                8.dp
            )
        )

        if (
            !SecureApiKeyStore
                .exists(
                    context
                )
        ) {
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth(),
                shape =
                    RoundedCornerShape(
                        18.dp
                    )
            ) {
                Column(
                    Modifier.padding(
                        16.dp
                    )
                ) {
                    Row(
                        verticalAlignment =
                            Alignment
                                .CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined
                                .AutoAwesome,
                            null
                        )
                        Spacer(
                            Modifier.width(
                                8.dp
                            )
                        )
                        Text(
                            "AI 对话还没有连接",
                            fontWeight =
                                FontWeight.Bold
                        )
                    }

                    Spacer(
                        Modifier.height(
                            8.dp
                        )
                    )

                    Text(
                        "宠物的生活事件、回忆和收藏都可以离线运行。只有自由聊天需要你已有的 DeepSeek API Key。",
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        fontSize =
                            13.sp
                    )

                    Spacer(
                        Modifier.height(
                            10.dp
                        )
                    )

                    Button(
                        onClick =
                            onAiSettings,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                    ) {
                        Text(
                            "去连接 DeepSeek"
                        )
                    }
                }
            }

            Spacer(
                Modifier.height(
                    10.dp
                )
            )
        }

        LazyColumn(
            modifier =
                Modifier
                    .weight(
                        1f
                    )
                    .fillMaxWidth(),
            verticalArrangement =
                Arrangement
                    .spacedBy(
                        8.dp
                    ),
            contentPadding =
                PaddingValues(
                    vertical =
                        10.dp
                )
        ) {
            items(
                turns
            ) {
                turn ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    horizontalArrangement =
                        if (
                            turn.fromUser
                        ) {
                            Arrangement.End
                        } else {
                            Arrangement.Start
                        }
                ) {
                    Surface(
                        shape =
                            RoundedCornerShape(
                                18.dp
                            ),
                        color =
                            if (
                                turn.fromUser
                            ) {
                                MaterialTheme
                                    .colorScheme
                                    .primaryContainer
                            } else {
                                MaterialTheme
                                    .colorScheme
                                    .surfaceVariant
                            },
                        modifier =
                            Modifier
                                .widthIn(
                                    max =
                                        310.dp
                                )
                    ) {
                        Text(
                            turn.text,
                            modifier =
                                Modifier.padding(
                                    horizontal =
                                        14.dp,
                                    vertical =
                                        10.dp
                                ),
                            lineHeight =
                                21.sp
                        )
                    }
                }
            }

            if (
                loading
            ) {
                item {
                    Row(
                        verticalAlignment =
                            Alignment
                                .CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier =
                                Modifier.size(
                                    18.dp
                                ),
                            strokeWidth =
                                2.dp
                        )

                        Spacer(
                            Modifier.width(
                                8.dp
                            )
                        )

                        Text(
                            petKind.displayName +
                                "正在想一下…",
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant,
                            fontSize =
                                13.sp
                        )
                    }
                }
            }
        }

        if (
            error.isNotBlank()
        ) {
            Text(
                error,
                color =
                    MaterialTheme
                        .colorScheme
                        .error,
                fontSize =
                    12.sp,
                modifier =
                    Modifier.padding(
                        bottom =
                            6.dp
                    )
            )
        }

        Row(
            verticalAlignment =
                Alignment
                    .Bottom
        ) {
            OutlinedTextField(
                value =
                    input,
                onValueChange = {
                    input =
                        it
                },
                modifier =
                    Modifier.weight(
                        1f
                    ),
                placeholder = {
                    Text(
                        "和" +
                            petKind.displayName +
                            "说句话…"
                    )
                },
                maxLines =
                    4,
                shape =
                    RoundedCornerShape(
                        18.dp
                    )
            )

            Spacer(
                Modifier.width(
                    8.dp
                )
            )

            IconButton(
                onClick = {
                    val message =
                        input
                            .trim()

                    if (
                        message.isBlank() ||
                        loading
                    ) {
                        return@IconButton
                    }

                    val apiKey =
                        SecureApiKeyStore
                            .load(
                                context
                            )

                    if (
                        apiKey
                            .isNullOrBlank()
                    ) {
                        error =
                            "请先连接 DeepSeek API Key"
                        return@IconButton
                    }

                    input =
                        ""

                    error =
                        ""

                    turns.add(
                        PetChatTurn(
                            fromUser =
                                true,
                            text =
                                message
                        )
                    )

                    loading =
                        true

                    scope.launch {
                        val pending =
                            withContext(
                                Dispatchers.IO
                            ) {
                                db.taskDao()
                                    .getAllOnce()
                                    .count {
                                        !it.completed
                                    }
                            }

                        val mood =
                            if (
                                prefs.getString(
                                    "journal_mood_date",
                                    ""
                                ) ==
                                LocalDate.now()
                                    .toString()
                            ) {
                                prefs.getString(
                                    "journal_mood",
                                    "calm"
                                ) ?:
                                    "calm"
                            } else {
                                "calm"
                            }

                        val memories =
                            PetWorldStore
                                .conversationMemories(
                                    prefs =
                                        prefs,
                                    petKind =
                                        petKind,
                                    limit =
                                        12
                                )

                        val result =
                            withContext(
                                Dispatchers.IO
                            ) {
                                DeepSeekClient
                                    .petConversation(
                                        apiKey =
                                            apiKey,
                                        petKind =
                                            petKind,
                                        bondStage =
                                            bond,
                                        userMessage =
                                            message,
                                        memories =
                                            memories,
                                        currentMood =
                                            mood,
                                        pendingTasks =
                                            pending
                                    )
                            }

                        loading =
                            false

                        if (
                            result.success
                        ) {
                            turns.add(
                                PetChatTurn(
                                    fromUser =
                                        false,
                                    text =
                                        result.content
                                )
                            )

                            PetWorldStore
                                .addMemory(
                                    prefs,
                                    PetMemory(
                                        id =
                                            "chat_" +
                                                System
                                                    .currentTimeMillis(),
                                        type =
                                            "chat",
                                        petId =
                                            petKind.id,
                                        title =
                                            "和" +
                                                petKind
                                                    .displayName +
                                                "聊了一会儿",
                                        detail =
                                            (
                                                "你：" +
                                                    message +
                                                    " / " +
                                                    petKind
                                                        .displayName +
                                                    "：" +
                                                    result.content
                                                )
                                                .take(
                                                    520
                                                ),
                                        createdAt =
                                            System
                                                .currentTimeMillis()
                                    )
                                )
                        } else {
                            error =
                                result.error
                        }
                    }
                },
                enabled =
                    !loading
            ) {
                Icon(
                    Icons.Outlined
                        .Send,
                    "发送"
                )
            }
        }

        Spacer(
            Modifier.height(
                6.dp
            )
        )

        Text(
            "AI 只会把本机回忆当作已发生事实；回忆里没有的经历不会假装记得。",
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
            fontSize =
                11.sp
        )
    }
}
