package com.shiguangbox.app

import kotlin.random.Random

data class PetBehaviorStep(
    val motion: PetMotion,
    val intensity: Float = 1f,
    val speed: Float = 1f,
    val direction: Float = 0f,
    val pauseAfterMs: Long = 220L
) {
    fun resolvedDirection(): Float =
        when {
            direction < 0f -> -1f
            direction > 0f -> 1f
            Random.nextBoolean() -> 1f
            else -> -1f
        }

    fun durationMs(): Long =
        (
            motion.durationSeconds *
                1000.0 /
                speed.coerceIn(
                    0.55f,
                    1.55f
                )
            )
            .toLong()
            .coerceAtLeast(
                120L
            )
}

data class PetBehaviorSequence(
    val id: String,
    val steps: List<PetBehaviorStep>,
    val interruptible: Boolean = true,
    val settleAfterMs: Long = 180L
) {
    fun estimatedDurationMs(): Long =
        steps.sumOf {
            it.durationMs() +
                it.pauseAfterMs
        } +
            settleAfterMs
}

object PetBehaviorLibrary {

    fun forLifeEvent(
        kind: PetKind,
        event: PetLifeEvent
    ): PetBehaviorSequence =
        when (kind) {
            PetKind.ORANGE ->
                orangeLife(
                    event
                )

            PetKind.YAYA ->
                yayaLife(
                    event
                )

            PetKind.YUTUAN ->
                yutuanLife(
                    event
                )
        }

    fun forTap(
        kind: PetKind,
        tapCount: Int
    ): PetBehaviorSequence =
        when {
            tapCount <= 1 ->
                when (kind) {
                    PetKind.ORANGE ->
                        sequence(
                            "orange_tap_single",
                            step(
                                PetMotion.HEAD_TILT,
                                1.05f,
                                1.08f
                            )
                        )

                    PetKind.YAYA ->
                        sequence(
                            "yaya_tap_single",
                            step(
                                PetMotion.LOOK_UP,
                                0.88f,
                                0.94f
                            ),
                            step(
                                PetMotion.HEAD_TILT,
                                0.86f,
                                0.92f,
                                pauseAfterMs = 120L
                            )
                        )

                    PetKind.YUTUAN ->
                        sequence(
                            "yutuan_tap_single",
                            step(
                                PetMotion.LOOK_UP,
                                0.94f,
                                0.94f
                            )
                        )
                }

            tapCount == 2 ->
                when (kind) {
                    PetKind.ORANGE ->
                        sequence(
                            "orange_tap_double",
                            step(
                                PetMotion.SMALL_JUMP,
                                1.10f,
                                1.08f
                            ),
                            step(
                                PetMotion.HEAD_TILT,
                                1.04f,
                                1.04f
                            )
                        )

                    PetKind.YAYA ->
                        sequence(
                            "yaya_tap_double",
                            step(
                                PetMotion.HEAD_TILT,
                                0.92f,
                                0.96f
                            ),
                            step(
                                PetMotion.SHY,
                                0.88f,
                                0.92f
                            )
                        )

                    PetKind.YUTUAN ->
                        sequence(
                            "yutuan_tap_double",
                            step(
                                PetMotion.STRETCH,
                                0.96f,
                                0.96f
                            ),
                            step(
                                PetMotion.LOOK_UP,
                                0.92f,
                                0.94f
                            )
                        )
                }

            else ->
                when (kind) {
                    PetKind.ORANGE ->
                        sequence(
                            "orange_tap_many",
                            step(
                                PetMotion.SMALL_JUMP,
                                1.16f,
                                1.12f,
                                pauseAfterMs = 130L
                            ),
                            step(
                                PetMotion.SEEK_ATTENTION,
                                1.12f,
                                1.08f
                            )
                        )

                    PetKind.YAYA ->
                        sequence(
                            "yaya_tap_many",
                            step(
                                PetMotion.SHY,
                                0.94f,
                                0.98f
                            ),
                            step(
                                PetMotion.HEAD_TILT,
                                0.92f,
                                0.94f
                            )
                        )

                    PetKind.YUTUAN ->
                        sequence(
                            "yutuan_tap_many",
                            step(
                                PetMotion.LOOK_AROUND,
                                0.98f,
                                1.00f
                            ),
                            step(
                                PetMotion.SEEK_ATTENTION,
                                0.98f,
                                0.96f
                            )
                        )
                }
        }

    fun forLongPress(
        kind: PetKind
    ): PetBehaviorSequence =
        when (kind) {
            PetKind.ORANGE ->
                sequence(
                    "orange_long_press",
                    step(
                        PetMotion.SHY,
                        1.02f,
                        0.96f
                    ),
                    step(
                        PetMotion.STRETCH,
                        1.00f,
                        0.98f
                    )
                )

            PetKind.YAYA ->
                sequence(
                    "yaya_long_press",
                    step(
                        PetMotion.SHY,
                        0.90f,
                        0.88f
                    ),
                    step(
                        PetMotion.HEAD_TILT,
                        0.86f,
                        0.90f
                    )
                )

            PetKind.YUTUAN ->
                sequence(
                    "yutuan_long_press",
                    step(
                        PetMotion.SHY,
                        0.92f,
                        0.90f
                    ),
                    step(
                        PetMotion.STRETCH,
                        0.90f,
                        0.90f
                    )
                )
        }

    fun forPositiveResponse(
        kind: PetKind
    ): PetBehaviorSequence =
        when (kind) {
            PetKind.ORANGE ->
                sequence(
                    "orange_positive_response",
                    step(
                        PetMotion.SMALL_JUMP,
                        1.14f,
                        1.08f
                    ),
                    step(
                        PetMotion.SEEK_ATTENTION,
                        1.08f,
                        1.04f
                    )
                )

            PetKind.YAYA ->
                sequence(
                    "yaya_positive_response",
                    step(
                        PetMotion.HEAD_TILT,
                        0.90f,
                        0.92f
                    ),
                    step(
                        PetMotion.STRETCH,
                        0.88f,
                        0.90f
                    )
                )

            PetKind.YUTUAN ->
                sequence(
                    "yutuan_positive_response",
                    step(
                        PetMotion.STRETCH,
                        0.94f,
                        0.92f
                    ),
                    step(
                        PetMotion.LOOK_UP,
                        0.92f,
                        0.92f
                    )
                )
        }

    fun forBusyResponse(
        kind: PetKind
    ): PetBehaviorSequence =
        when (kind) {
            PetKind.ORANGE ->
                sequence(
                    "orange_busy_response",
                    step(
                        PetMotion.SHY,
                        0.88f,
                        0.92f
                    )
                )

            PetKind.YAYA ->
                sequence(
                    "yaya_busy_response",
                    step(
                        PetMotion.DOZE_NOD,
                        0.76f,
                        0.82f
                    )
                )

            PetKind.YUTUAN ->
                sequence(
                    "yutuan_busy_response",
                    step(
                        PetMotion.SHY,
                        0.82f,
                        0.84f
                    )
                )
        }

    private fun orangeLife(
        event: PetLifeEvent
    ): PetBehaviorSequence =
        when (event) {
            PetLifeEvent.MISS_YOU ->
                sequence(
                    "orange_miss_you",
                    step(
                        PetMotion.LOOK_AROUND,
                        1.06f,
                        1.04f,
                        pauseAfterMs = 140L
                    ),
                    step(
                        PetMotion.SEEK_ATTENTION,
                        1.12f,
                        1.06f,
                        pauseAfterMs = 130L
                    ),
                    step(
                        PetMotion.HEAD_TILT,
                        1.02f,
                        1.00f
                    )
                )

            PetLifeEvent.WELCOME_BACK ->
                sequence(
                    "orange_welcome_back",
                    step(
                        PetMotion.SMALL_JUMP,
                        1.13f,
                        1.10f
                    ),
                    step(
                        PetMotion.SEEK_ATTENTION,
                        1.10f,
                        1.06f
                    )
                )

            PetLifeEvent.PROUD_OF_YOU,
            PetLifeEvent.AFTER_TASK ->
                sequence(
                    "orange_task_memory",
                    step(
                        PetMotion.SMALL_JUMP,
                        1.08f,
                        1.05f
                    ),
                    step(
                        PetMotion.STRETCH,
                        1.02f,
                        1.00f
                    )
                )

            PetLifeEvent.MOOD_COMPANY ->
                sequence(
                    "orange_mood_company",
                    step(
                        PetMotion.HEAD_TILT,
                        0.98f,
                        0.94f
                    ),
                    step(
                        PetMotion.SHY,
                        0.92f,
                        0.92f
                    )
                )

            PetLifeEvent.TASK_NUDGE ->
                sequence(
                    "orange_task_nudge",
                    step(
                        PetMotion.LOOK_UP,
                        1.02f,
                        1.00f
                    ),
                    step(
                        PetMotion.SEEK_ATTENTION,
                        1.06f,
                        1.04f
                    )
                )

            PetLifeEvent.SILENT_ACTION ->
                orangeSilent()

            else ->
                sequence(
                    "orange_check_in",
                    step(
                        PetMotion.LOOK_AROUND,
                        1.00f,
                        1.00f
                    ),
                    step(
                        PetMotion.HEAD_TILT,
                        0.98f,
                        0.98f
                    )
                )
        }

    private fun yayaLife(
        event: PetLifeEvent
    ): PetBehaviorSequence =
        when (event) {
            PetLifeEvent.MISS_YOU ->
                sequence(
                    "yaya_miss_you",
                    step(
                        PetMotion.LOOK_UP,
                        0.88f,
                        0.90f
                    ),
                    step(
                        PetMotion.HEAD_TILT,
                        0.88f,
                        0.90f
                    ),
                    step(
                        PetMotion.SHY,
                        0.82f,
                        0.86f
                    )
                )

            PetLifeEvent.WELCOME_BACK ->
                sequence(
                    "yaya_welcome_back",
                    step(
                        PetMotion.LOOK_UP,
                        0.90f,
                        0.92f
                    ),
                    step(
                        PetMotion.STRETCH,
                        0.88f,
                        0.90f
                    )
                )

            PetLifeEvent.PROUD_OF_YOU,
            PetLifeEvent.AFTER_TASK ->
                sequence(
                    "yaya_task_memory",
                    step(
                        PetMotion.STRETCH,
                        0.88f,
                        0.88f
                    ),
                    step(
                        PetMotion.HEAD_TILT,
                        0.84f,
                        0.88f
                    )
                )

            PetLifeEvent.MOOD_COMPANY ->
                sequence(
                    "yaya_mood_company",
                    step(
                        PetMotion.SHY,
                        0.82f,
                        0.84f
                    ),
                    step(
                        PetMotion.DOZE_NOD,
                        0.76f,
                        0.80f
                    )
                )

            PetLifeEvent.TASK_NUDGE ->
                sequence(
                    "yaya_task_nudge",
                    step(
                        PetMotion.LOOK_UP,
                        0.86f,
                        0.90f
                    ),
                    step(
                        PetMotion.HEAD_TILT,
                        0.84f,
                        0.88f
                    )
                )

            PetLifeEvent.SILENT_ACTION ->
                yayaSilent()

            else ->
                sequence(
                    "yaya_check_in",
                    step(
                        PetMotion.LOOK_AROUND,
                        0.84f,
                        0.88f
                    )
                )
        }

    private fun yutuanLife(
        event: PetLifeEvent
    ): PetBehaviorSequence =
        when (event) {
            PetLifeEvent.MISS_YOU ->
                sequence(
                    "yutuan_miss_you",
                    step(
                        PetMotion.LOOK_UP,
                        0.94f,
                        0.90f
                    ),
                    step(
                        PetMotion.HEAD_TILT,
                        0.90f,
                        0.90f
                    ),
                    step(
                        PetMotion.SEEK_ATTENTION,
                        0.92f,
                        0.90f
                    )
                )

            PetLifeEvent.WELCOME_BACK ->
                sequence(
                    "yutuan_welcome_back",
                    step(
                        PetMotion.STRETCH,
                        0.92f,
                        0.90f
                    ),
                    step(
                        PetMotion.LOOK_UP,
                        0.92f,
                        0.90f
                    )
                )

            PetLifeEvent.PROUD_OF_YOU,
            PetLifeEvent.AFTER_TASK ->
                sequence(
                    "yutuan_task_memory",
                    step(
                        PetMotion.STRETCH,
                        0.92f,
                        0.90f
                    ),
                    step(
                        PetMotion.LOOK_UP,
                        0.88f,
                        0.88f
                    )
                )

            PetLifeEvent.MOOD_COMPANY ->
                sequence(
                    "yutuan_mood_company",
                    step(
                        PetMotion.DOZE_NOD,
                        0.82f,
                        0.82f
                    ),
                    step(
                        PetMotion.SHY,
                        0.84f,
                        0.84f
                    )
                )

            PetLifeEvent.TASK_NUDGE ->
                sequence(
                    "yutuan_task_nudge",
                    step(
                        PetMotion.LOOK_AROUND,
                        0.90f,
                        0.90f
                    ),
                    step(
                        PetMotion.LOOK_UP,
                        0.90f,
                        0.90f
                    )
                )

            PetLifeEvent.SILENT_ACTION ->
                yutuanSilent()

            else ->
                sequence(
                    "yutuan_check_in",
                    step(
                        PetMotion.LOOK_AROUND,
                        0.88f,
                        0.88f
                    )
                )
        }

    private fun orangeSilent():
        PetBehaviorSequence =
        listOf(
            sequence(
                "orange_silent_observe",
                step(
                    PetMotion.LOOK_AROUND,
                    0.98f,
                    0.98f
                ),
                step(
                    PetMotion.HEAD_TILT,
                    0.94f,
                    0.96f
                )
            ),
            sequence(
                "orange_silent_stretch",
                step(
                    PetMotion.STRETCH,
                    0.98f,
                    0.94f
                )
            ),
            sequence(
                "orange_silent_jump",
                step(
                    PetMotion.SMALL_JUMP,
                    0.96f,
                    1.00f
                )
            )
        )
            .random()

    private fun yayaSilent():
        PetBehaviorSequence =
        listOf(
            sequence(
                "yaya_silent_observe",
                step(
                    PetMotion.LOOK_AROUND,
                    0.78f,
                    0.82f
                )
            ),
            sequence(
                "yaya_silent_tilt",
                step(
                    PetMotion.HEAD_TILT,
                    0.80f,
                    0.84f
                )
            ),
            sequence(
                "yaya_silent_doze",
                step(
                    PetMotion.DOZE_NOD,
                    0.72f,
                    0.78f
                )
            )
        )
            .random()

    private fun yutuanSilent():
        PetBehaviorSequence =
        listOf(
            sequence(
                "yutuan_silent_observe",
                step(
                    PetMotion.LOOK_AROUND,
                    0.82f,
                    0.84f
                )
            ),
            sequence(
                "yutuan_silent_up",
                step(
                    PetMotion.LOOK_UP,
                    0.84f,
                    0.84f
                )
            ),
            sequence(
                "yutuan_silent_shy",
                step(
                    PetMotion.SHY,
                    0.78f,
                    0.80f
                )
            )
        )
            .random()

    private fun sequence(
        id: String,
        vararg steps: PetBehaviorStep
    ): PetBehaviorSequence =
        PetBehaviorSequence(
            id = id,
            steps =
                steps.toList()
        )

    private fun step(
        motion: PetMotion,
        intensity: Float,
        speed: Float,
        direction: Float = 0f,
        pauseAfterMs: Long = 220L
    ): PetBehaviorStep =
        PetBehaviorStep(
            motion = motion,
            intensity = intensity,
            speed = speed,
            direction = direction,
            pauseAfterMs = pauseAfterMs
        )
}
