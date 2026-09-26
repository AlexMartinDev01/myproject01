package com.shiguangbox.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun PetHome3DScreen(
    onBack: () -> Unit
) {
    var view by
        remember {
            mutableStateOf<
                PetHome3DView?
                >(
                null
            )
        }

    var autoTour by
        remember {
            mutableStateOf(
                true
            )
        }

    val lifecycleOwner =
        LocalLifecycleOwner
            .current

    DisposableEffect(
        lifecycleOwner,
        view
    ) {
        val observer =
            LifecycleEventObserver {
                    _,
                    event ->
                when (
                    event
                ) {
                    Lifecycle.Event.ON_RESUME ->
                        view
                            ?.onResume()

                    Lifecycle.Event.ON_PAUSE ->
                        view
                            ?.onPause()

                    else ->
                        Unit
                }
            }

        lifecycleOwner
            .lifecycle
            .addObserver(
                observer
            )

        onDispose {
            lifecycleOwner
                .lifecycle
                .removeObserver(
                    observer
                )

            view
                ?.onPause()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color(
                        0xFFF0E9DE
                    )
                )
    ) {
        AndroidView(
            factory = {
                    context ->
                PetHome3DView(
                    context
                )
                    .also {
                        petHomeView ->
                        view =
                            petHomeView

                        petHomeView
                            .setAutoTour(
                                autoTour
                            )

                        petHomeView
                            .onResume()
                    }
            },
            update = {
                it.setAutoTour(
                    autoTour
                )
            },
            modifier =
                Modifier
                    .fillMaxSize()
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 10.dp,
                        end = 10.dp,
                        top = 8.dp
                    ),
            verticalAlignment =
                Alignment
                    .CenterVertically
        ) {
            Card(
                shape =
                    RoundedCornerShape(
                        18.dp
                    ),
                colors =
                    CardDefaults
                        .cardColors(
                            containerColor =
                                MaterialTheme
                                    .colorScheme
                                    .surface
                                    .copy(
                                        alpha =
                                            0.92f
                                    )
                        )
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
            }

            Spacer(
                Modifier.weight(
                    1f
                )
            )

            Card(
                shape =
                    RoundedCornerShape(
                        18.dp
                    ),
                colors =
                    CardDefaults
                        .cardColors(
                            containerColor =
                                MaterialTheme
                                    .colorScheme
                                    .surface
                                    .copy(
                                        alpha =
                                            0.92f
                                    )
                        )
            ) {
                IconButton(
                    onClick = {
                        view
                            ?.resetCamera()
                    }
                ) {
                    Icon(
                        Icons.Outlined
                            .Refresh,
                        "重置视角"
                    )
                }
            }
        }

        Card(
            modifier =
                Modifier
                    .align(
                        Alignment.BottomCenter
                    )
                    .fillMaxWidth()
                    .padding(
                        12.dp
                    ),
            shape =
                RoundedCornerShape(
                    24.dp
                ),
            colors =
                CardDefaults
                    .cardColors(
                        containerColor =
                            MaterialTheme
                                .colorScheme
                                .surface
                                .copy(
                                    alpha =
                                        0.94f
                                )
                    )
        ) {
            Column(
                modifier =
                    Modifier.padding(
                        16.dp
                    )
            ) {
                Text(
                    "橘团 3D 小窝 · 技术 Demo",
                    fontSize =
                        18.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    "手指拖动可以旋转视角，双指缩放。橘团会自己在床边、窗边和地毯之间移动。",
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                    fontSize =
                        12.sp,
                    lineHeight =
                        17.sp
                )

                Spacer(
                    Modifier.height(
                        10.dp
                    )
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            7.dp
                        )
                ) {
                    OutlinedButton(
                        onClick = {
                            view
                                ?.goToBed()
                        },
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    ) {
                        Text(
                            "去床边",
                            fontSize =
                                12.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            view
                                ?.goToWindow()
                        },
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    ) {
                        Text(
                            "去窗边",
                            fontSize =
                                12.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            view
                                ?.goToRug()
                        },
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    ) {
                        Text(
                            "去地毯",
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

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Column(
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    ) {
                        Text(
                            "自动巡游",
                            fontWeight =
                                FontWeight.Medium,
                            fontSize =
                                13.sp
                        )

                        Text(
                            "到达目标后停留约 3 秒，再自己选择下一个位置。",
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant,
                            fontSize =
                                11.sp
                        )
                    }

                    Switch(
                        checked =
                            autoTour,
                        onCheckedChange = {
                            enabled ->
                            autoTour =
                                enabled

                            view
                                ?.setAutoTour(
                                    enabled
                                )
                        }
                    )
                }
            }
        }
    }
}
