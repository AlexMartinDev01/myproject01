package com.shiguangbox.app

import android.view.MotionEvent
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
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.sqrt

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

    val cameraTouchState =
        remember {
            floatArrayOf(
                0f,
                0f,
                0f
            )
        }


    DisposableEffect(
        view
    ) {
        onDispose {
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
                    .pointerInteropFilter {
                            event ->
                        when (
                            event.actionMasked
                        ) {
                            MotionEvent.ACTION_DOWN -> {
                                cameraTouchState[0] =
                                    event.x
                                cameraTouchState[1] =
                                    event.y
                                cameraTouchState[2] =
                                    0f
                            }

                            MotionEvent.ACTION_POINTER_DOWN -> {
                                if (
                                    event.pointerCount >=
                                    2
                                ) {
                                    val dx =
                                        event.getX(
                                            0
                                        ) -
                                            event.getX(
                                                1
                                            )

                                    val dy =
                                        event.getY(
                                            0
                                        ) -
                                            event.getY(
                                                1
                                            )

                                    cameraTouchState[2] =
                                        sqrt(
                                            dx *
                                                dx +
                                                dy *
                                                dy
                                        )
                                }
                            }

                            MotionEvent.ACTION_MOVE -> {
                                if (
                                    event.pointerCount >=
                                    2
                                ) {
                                    val dx =
                                        event.getX(
                                            0
                                        ) -
                                            event.getX(
                                                1
                                            )

                                    val dy =
                                        event.getY(
                                            0
                                        ) -
                                            event.getY(
                                                1
                                            )

                                    val distance =
                                        sqrt(
                                            dx *
                                                dx +
                                                dy *
                                                dy
                                        )

                                    val previous =
                                        cameraTouchState[2]

                                    if (
                                        previous >
                                        1f &&
                                        distance >
                                        1f
                                    ) {
                                        view
                                            ?.zoomCamera(
                                                (
                                                    distance /
                                                        previous
                                                    )
                                                    .coerceIn(
                                                        0.88f,
                                                        1.14f
                                                    )
                                            )
                                    }

                                    cameraTouchState[2] =
                                        distance
                                } else {
                                    val dx =
                                        event.x -
                                            cameraTouchState[0]

                                    val dy =
                                        event.y -
                                            cameraTouchState[1]

                                    if (
                                        kotlin.math.abs(
                                            dx
                                        ) >
                                        0.25f ||
                                        kotlin.math.abs(
                                            dy
                                        ) >
                                        0.25f
                                    ) {
                                        view
                                            ?.orbitCamera(
                                                dx,
                                                dy
                                            )
                                    }

                                    cameraTouchState[0] =
                                        event.x
                                    cameraTouchState[1] =
                                        event.y
                                }
                            }

                            MotionEvent.ACTION_POINTER_UP -> {
                                cameraTouchState[2] =
                                    0f

                                if (
                                    event.pointerCount >
                                    1
                                ) {
                                    val remainingIndex =
                                        if (
                                            event.actionIndex ==
                                            0
                                        ) {
                                            1
                                        } else {
                                            0
                                        }

                                    cameraTouchState[0] =
                                        event.getX(
                                            remainingIndex
                                        )

                                    cameraTouchState[1] =
                                        event.getY(
                                            remainingIndex
                                        )
                                }
                            }

                            MotionEvent.ACTION_UP,
                            MotionEvent.ACTION_CANCEL -> {
                                cameraTouchState[2] =
                                    0f
                            }
                        }

                        true
                    }
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
                        Alignment.TopCenter
                    )
                    .padding(
                        top = 10.dp
                    ),
            shape =
                RoundedCornerShape(
                    16.dp
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
                                        0.88f
                                )
                    )
        ) {
            Text(
                "拖动旋转 · 双指缩放 · 下方按钮可直接测试镜头",
                modifier =
                    Modifier.padding(
                        horizontal = 14.dp,
                        vertical = 8.dp
                    ),
                fontSize =
                    12.sp,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )
        }

        Card(
            modifier =
                Modifier
                    .align(
                        Alignment.BottomCenter
                    )
                    .fillMaxWidth()
                    .padding(
                        10.dp
                    ),
            shape =
                RoundedCornerShape(
                    20.dp
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
            Column(
                modifier =
                    Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 10.dp
                    )
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            6.dp
                        ),
                    verticalAlignment =
                        Alignment.CenterVertically
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
                            "床",
                            fontSize =
                                11.sp
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
                            "窗",
                            fontSize =
                                11.sp
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
                            "地毯",
                            fontSize =
                                11.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            view
                                ?.goToToy()
                        },
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    ) {
                        Text(
                            "玩具",
                            fontSize =
                                11.sp
                        )
                    }
                }

                Spacer(
                    Modifier.height(
                        5.dp
                    )
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            5.dp
                        )
                ) {
                    OutlinedButton(
                        onClick = {
                            view
                                ?.orbitCamera(
                                    -55f,
                                    0f
                                )
                        },
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    ) {
                        Text(
                            "←",
                            fontSize =
                                13.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            view
                                ?.orbitCamera(
                                    55f,
                                    0f
                                )
                        },
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    ) {
                        Text(
                            "→",
                            fontSize =
                                13.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            view
                                ?.orbitCamera(
                                    0f,
                                    -45f
                                )
                        },
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    ) {
                        Text(
                            "↑",
                            fontSize =
                                13.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            view
                                ?.orbitCamera(
                                    0f,
                                    45f
                                )
                        },
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    ) {
                        Text(
                            "↓",
                            fontSize =
                                13.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            view
                                ?.zoomCamera(
                                    1.18f
                                )
                        },
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    ) {
                        Text(
                            "+",
                            fontSize =
                                13.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            view
                                ?.zoomCamera(
                                    0.84f
                                )
                        },
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    ) {
                        Text(
                            "−",
                            fontSize =
                                13.sp
                        )
                    }
                }

                Spacer(
                    Modifier.height(
                        4.dp
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
                                12.sp
                        )

                        Text(
                            "会自己在小窝不同区域之间走动",
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant,
                            fontSize =
                                10.sp
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
