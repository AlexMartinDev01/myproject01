package com.shiguangbox.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 雨团专属真实雨系统。
 *
 * 透明 Canvas 上直接绘制，不包含任何背景色：
 * - 远 / 中 / 近三层雨
 * - 轻微风偏与不同终端速度
 * - 雨滴撞击地面 -> 水花 / 二次水滴 / 涟漪
 * - 脚边极淡积水
 * - 雨滴命中头顶 / 两只耳朵 / 围巾 -> 挂水珠 / 耳尖滴水
 * - 雨团抖水时从耳朵、头部、身体向外喷溅
 */
class YutuanRainSystem {

    private enum class Surface {
        HEAD,
        LEFT_EAR,
        RIGHT_EAR,
        SCARF
    }

    private data class Drop(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val layer: Int,
        val length: Float,
        val stroke: Float,
        val alpha: Int
    )

    private data class Ripple(
        val x: Float,
        val y: Float,
        var age: Float,
        val life: Float,
        val maxRadius: Float,
        val alpha: Int
    )

    private data class SplashDrop(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var age: Float,
        val life: Float,
        val radius: Float
    )

    private data class AttachedBead(
        val surface: Surface,
        val offset: Float,
        var age: Float,
        val life: Float,
        val radius: Float
    )

    private val random = Random(20260924)

    private val drops = ArrayList<Drop>(108)
    private val ripples = ArrayList<Ripple>(40)
    private val splashes = ArrayList<SplashDrop>(48)
    private val beads = ArrayList<AttachedBead>(16)

    private val rainPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
        }

    private val effectPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

    private val ripplePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }

    private var lastNanos = 0L
    private var puddleAmount = 0f

    // Weather controller: service changes the target while this class
    // eases the actual intensity so the rain never switches off abruptly.
    private var rainTargetScale = 1f
    private var rainScale = 1f

    private val spawnCarry = FloatArray(3)

    fun setRainTargetScale(scale: Float) {
        rainTargetScale =
            scale.coerceIn(
                0f,
                1f
            )
    }

    fun reset() {
        drops.clear()
        ripples.clear()
        splashes.clear()
        beads.clear()
        puddleAmount = 0f
        lastNanos = 0L
        rainTargetScale = 1f
        rainScale = 1f
        for (i in spawnCarry.indices) {
            spawnCarry[i] = 0f
        }
    }

    fun update(
        nowNanos: Long,
        width: Float,
        height: Float,
        state: PetRigView.State,
        stateSeconds: Double
    ) {
        if (width <= 0f || height <= 0f) return

        val dt =
            if (lastNanos == 0L) {
                1f / 60f
            } else {
                (
                    (nowNanos - lastNanos)
                        .coerceAtLeast(0L)
                        .coerceAtMost(50_000_000L) /
                        1_000_000_000.0
                    ).toFloat()
            }

        lastNanos = nowNanos

        val rainEase =
            (
                dt *
                    if (
                        rainTargetScale <
                        rainScale
                    ) {
                        2.35f
                    } else {
                        1.45f
                    }
                ).coerceIn(
                0f,
                1f
            )

        rainScale +=
            (
                rainTargetScale -
                    rainScale
                ) *
                rainEase

        if (
            rainTargetScale <= 0f &&
            rainScale < 0.008f
        ) {
            rainScale = 0f
        }

        val timeSeconds =
            nowNanos /
                1_000_000_000.0

        val stateMultiplier =
            when (state) {
                PetRigView.State.SLEEP -> 0.58f
                PetRigView.State.TIRED -> 0.74f
                PetRigView.State.PETTED -> 0.78f
                PetRigView.State.DRAGGING -> 0.82f
                PetRigView.State.TAIL_WAG -> 1.20f
                else -> 1.03f
            }

        val breathingRain =
            (
                0.96 +
                    0.14 *
                        sin(
                            timeSeconds *
                                2.0 *
                                PI /
                                22.0
                        ) +
                    0.05 *
                        sin(
                            timeSeconds *
                                2.0 *
                                PI /
                                7.8
                        )
                ).toFloat()

        val intensity =
            (
                stateMultiplier *
                    breathingRain *
                    rainScale
                )
                .coerceIn(
                    0f,
                    1.28f
                )

        spawnDrops(
            dt,
            timeSeconds,
            width,
            height,
            intensity
        )

        updateDrops(
            dt,
            timeSeconds,
            width,
            height
        )

        updateRipples(dt)
        updateSplashes(
            dt,
            width,
            height
        )
        updateBeads(dt)

        puddleAmount =
            (
                puddleAmount -
                    dt *
                        0.0015f
                ).coerceIn(
                    0f,
                    1f
                )
    }

    fun drawBehind(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {
        if (width <= 0f || height <= 0f) return

        drawPuddle(
            canvas,
            width,
            height
        )

        drawRainLayer(
            canvas,
            width,
            height,
            0
        )
    }

    fun drawFront(
        canvas: Canvas,
        width: Float,
        height: Float,
        state: PetRigView.State,
        stateSeconds: Double
    ) {
        if (width <= 0f || height <= 0f) return

        drawRainLayer(
            canvas,
            width,
            height,
            1
        )

        drawRainLayer(
            canvas,
            width,
            height,
            2
        )

        drawRipples(
            canvas,
            width,
            height
        )

        drawSplashes(canvas)
        drawAttachedBeads(
            canvas,
            width,
            height
        )

        if (
            state ==
            PetRigView.State.TAIL_WAG
        ) {
            drawShakeSpray(
                canvas,
                width,
                height,
                stateSeconds
            )
        }
    }

    private fun spawnDrops(
        dt: Float,
        timeSeconds: Double,
        width: Float,
        height: Float,
        intensity: Float
    ) {
        val rates =
            floatArrayOf(
                16.0f,
                13.5f,
                6.3f
            )

        for (layer in 0..2) {
            spawnCarry[layer] +=
                rates[layer] *
                    intensity *
                    dt

            var spawnCount =
                spawnCarry[layer]
                    .toInt()

            spawnCarry[layer] =
                spawnCarry[layer] -
                    spawnCount.toFloat()

            if (
                random.nextFloat() <
                spawnCarry[layer]
            ) {
                spawnCount += 1
                spawnCarry[layer] = 0f
            }

            repeat(spawnCount) {
                if (drops.size >= 108) return@repeat

                val terminal =
                    when (layer) {
                        0 -> height * 1.68f
                        1 -> height * 2.48f
                        else -> height * 3.42f
                    }

                val windAngle =
                    (
                        5.0 +
                            1.6 *
                                sin(
                                    timeSeconds *
                                        0.31 +
                                        layer *
                                            0.47
                                )
                        ) *
                        PI /
                        180.0

                val speed =
                    terminal *
                        (
                            0.88f +
                                random.nextFloat() *
                                    0.24f
                            )

                val length =
                    when (layer) {
                        0 ->
                            height *
                                (
                                    0.024f +
                                        random.nextFloat() *
                                            0.014f
                                    )

                        1 ->
                            height *
                                (
                                    0.041f +
                                        random.nextFloat() *
                                            0.021f
                                    )

                        else ->
                            height *
                                (
                                    0.064f +
                                        random.nextFloat() *
                                            0.034f
                                    )
                    }

                drops +=
                    Drop(
                        x =
                            -width *
                                0.10f +
                                random.nextFloat() *
                                    width *
                                    1.20f,
                        y =
                            -height *
                                (
                                    0.06f +
                                        random.nextFloat() *
                                            0.34f
                                    ),
                        vx =
                            (
                                sin(windAngle) *
                                    speed
                                ).toFloat() +
                                (
                                    random.nextFloat() -
                                        0.5f
                                    ) *
                                    width *
                                    0.025f,
                        vy =
                            (
                                cos(windAngle) *
                                    speed
                                ).toFloat(),
                        layer = layer,
                        length = length,
                        stroke =
                            when (layer) {
                                0 -> width * 0.0048f
                                1 -> width * 0.0066f
                                else -> width * 0.0096f
                            }.coerceAtLeast(1f),
                        alpha =
                            when (layer) {
                                0 -> 58
                                1 -> 104
                                else -> 146
                            } +
                                random.nextInt(
                                    -10,
                                    11
                                )
                    )
            }
        }
    }

    private fun updateDrops(
        dt: Float,
        timeSeconds: Double,
        width: Float,
        height: Float
    ) {
        val groundY =
            height *
                0.905f

        var index = drops.lastIndex

        while (index >= 0) {
            val drop =
                drops[index]

            val terminal =
                when (drop.layer) {
                    0 -> height * 1.90f
                    1 -> height * 2.68f
                    else -> height * 3.62f
                }

            drop.vy =
                (
                    drop.vy +
                        height *
                            0.38f *
                            dt
                    ).coerceAtMost(
                    terminal
                )

            val wind =
                (
                    sin(
                        timeSeconds *
                            0.28
                    ) *
                        width *
                        0.030 +
                    sin(
                        timeSeconds *
                            0.91
                    ) *
                        width *
                        0.012
                    ).toFloat()

            drop.vx +=
                wind *
                    dt *
                    0.15f

            drop.x +=
                drop.vx *
                    dt

            drop.y +=
                drop.vy *
                    dt

            var consumed = false

            if (
                drop.layer >= 1 &&
                drop.y <
                    groundY -
                        height *
                            0.04f
            ) {
                val surface =
                    hitPetSurface(
                        drop.x,
                        drop.y,
                        width,
                        height
                    )

                if (surface != null) {
                    if (
                        random.nextFloat() <
                        0.50f
                    ) {
                        addAttachedBead(
                            surface,
                            width
                        )
                    }

                    consumed = true
                }
            }

            if (
                !consumed &&
                drop.y >=
                    groundY
            ) {
                spawnGroundImpact(
                    drop,
                    width,
                    height
                )
                consumed = true
            }

            if (
                consumed ||
                drop.x <
                    -width *
                        0.35f ||
                drop.x >
                    width *
                        1.35f ||
                drop.y >
                    height *
                        1.18f
            ) {
                drops.removeAt(index)
            }

            index -= 1
        }
    }

    private fun hitPetSurface(
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ): Surface? {
        fun insideEllipse(
            cx: Float,
            cy: Float,
            rx: Float,
            ry: Float
        ): Boolean {
            val dx =
                (x - cx) /
                    rx

            val dy =
                (y - cy) /
                    ry

            return dx *
                dx +
                dy *
                    dy <=
                1f
        }

        if (
            insideEllipse(
                width * 0.22f,
                height * 0.34f,
                width * 0.17f,
                height * 0.18f
            )
        ) {
            return Surface.LEFT_EAR
        }

        if (
            insideEllipse(
                width * 0.78f,
                height * 0.34f,
                width * 0.17f,
                height * 0.18f
            )
        ) {
            return Surface.RIGHT_EAR
        }

        if (
            insideEllipse(
                width * 0.50f,
                height * 0.30f,
                width * 0.23f,
                height * 0.20f
            )
        ) {
            return Surface.HEAD
        }

        if (
            insideEllipse(
                width * 0.58f,
                height * 0.57f,
                width * 0.28f,
                height * 0.075f
            )
        ) {
            return Surface.SCARF
        }

        return null
    }

    private fun addAttachedBead(
        surface: Surface,
        width: Float
    ) {
        if (beads.size >= 16) return

        beads +=
            AttachedBead(
                surface = surface,
                offset =
                    random.nextFloat(),
                age = 0f,
                life =
                    when (surface) {
                        Surface.SCARF ->
                            0.48f +
                                random.nextFloat() *
                                    0.30f

                        else ->
                            0.68f +
                                random.nextFloat() *
                                    0.42f
                    },
                radius =
                    (
                        width *
                            (
                                0.010f +
                                    random.nextFloat() *
                                        0.006f
                                )
                        ).coerceAtLeast(
                        1.5f
                    )
            )
    }

    private fun spawnGroundImpact(
        drop: Drop,
        width: Float,
        height: Float
    ) {
        val groundY =
            height *
                0.905f

        val energy =
            0.82f +
                drop.layer *
                    0.18f

        ripples +=
            Ripple(
                x = drop.x,
                y =
                    groundY +
                        height *
                            0.004f,
                age = 0f,
                life =
                    0.44f +
                        random.nextFloat() *
                            0.34f,
                maxRadius =
                    width *
                        (
                            0.040f +
                                random.nextFloat() *
                                    0.048f
                            ) *
                        energy,
                alpha =
                    52 +
                        random.nextInt(
                            0,
                            30
                        )
            )

        val chance =
            random.nextFloat()

        val splashCount =
            when {
                chance < 0.08f ->
                    random.nextInt(
                        4,
                        7
                    )

                chance < 0.36f ->
                    random.nextInt(
                        2,
                        4
                    )

                else -> 0
            }

        repeat(splashCount) {
            if (splashes.size >= 48) return@repeat

            val scale =
                (
                    0.72f +
                        drop.layer *
                            0.16f
                    )

            splashes +=
                SplashDrop(
                    x =
                        drop.x,
                    y =
                        groundY -
                            height *
                                0.002f,
                    vx =
                        (
                            random.nextFloat() *
                                2f -
                                1f
                            ) *
                            width *
                            0.55f *
                            scale,
                    vy =
                        -height *
                            (
                                0.62f +
                                    random.nextFloat() *
                                        0.52f
                                ) *
                            scale,
                    age = 0f,
                    life =
                        0.20f +
                            random.nextFloat() *
                                0.24f,
                    radius =
                        (
                            width *
                                (
                                    0.007f +
                                        random.nextFloat() *
                                            0.007f
                                    )
                            ).coerceAtLeast(
                            1f
                        )
                )
        }

        puddleAmount =
            (
                puddleAmount +
                    0.0052f *
                        (
                            1f +
                                drop.layer *
                                    0.35f
                            )
                ).coerceAtMost(
                1f
            )
    }

    private fun updateRipples(
        dt: Float
    ) {
        var index =
            ripples.lastIndex

        while (index >= 0) {
            val ripple =
                ripples[index]

            ripple.age += dt

            if (
                ripple.age >=
                ripple.life
            ) {
                ripples.removeAt(index)
            }

            index -= 1
        }
    }

    private fun updateSplashes(
        dt: Float,
        width: Float,
        height: Float
    ) {
        val groundY =
            height *
                0.905f

        var index =
            splashes.lastIndex

        while (index >= 0) {
            val splash =
                splashes[index]

            splash.age += dt
            splash.vy +=
                height *
                    3.0f *
                    dt

            splash.x +=
                splash.vx *
                    dt

            splash.y +=
                splash.vy *
                    dt

            if (
                splash.age >=
                    splash.life ||
                splash.y >=
                    groundY
            ) {
                splashes.removeAt(index)
            }

            index -= 1
        }
    }

    private fun updateBeads(
        dt: Float
    ) {
        var index =
            beads.lastIndex

        while (index >= 0) {
            val bead =
                beads[index]

            bead.age += dt

            if (
                bead.age >=
                bead.life
            ) {
                beads.removeAt(index)
            }

            index -= 1
        }
    }

    private fun drawRainLayer(
        canvas: Canvas,
        width: Float,
        height: Float,
        layer: Int
    ) {
        for (drop in drops) {
            if (
                drop.layer !=
                layer
            ) {
                continue
            }

            val speed =
                hypot(
                    drop.vx.toDouble(),
                    drop.vy.toDouble()
                ).toFloat()
                    .coerceAtLeast(
                        1f
                    )

            val ux =
                drop.vx /
                    speed

            val uy =
                drop.vy /
                    speed

            val endX =
                drop.x -
                    ux *
                        drop.length

            val endY =
                drop.y -
                    uy *
                        drop.length

            rainPaint.strokeWidth =
                drop.stroke

            rainPaint.color =
                Color.argb(
                    drop.alpha
                        .coerceIn(
                            0,
                            255
                        ),
                    188,
                    214,
                    229
                )

            canvas.drawLine(
                drop.x,
                drop.y,
                endX,
                endY,
                rainPaint
            )

            if (
                layer ==
                2
            ) {
                rainPaint.strokeWidth =
                    (
                        drop.stroke *
                            2.1f
                        ).coerceAtLeast(
                        1f
                    )

                rainPaint.color =
                    Color.argb(
                        34,
                        200,
                        224,
                        237
                    )

                canvas.drawLine(
                    drop.x,
                    drop.y,
                    endX,
                    endY,
                    rainPaint
                )

                rainPaint.strokeWidth =
                    (
                        drop.stroke *
                            0.45f
                        ).coerceAtLeast(
                        0.8f
                    )

                rainPaint.color =
                    Color.argb(
                        164,
                        230,
                        242,
                        248
                    )

                canvas.drawLine(
                    drop.x,
                    drop.y,
                    endX,
                    endY,
                    rainPaint
                )
            }
        }
    }

    private fun drawPuddle(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {
        if (
            puddleAmount <=
            0.012f
        ) {
            return
        }

        val groundY =
            height *
                0.905f

        val amount =
            (
                puddleAmount *
                    1.45f
                ).coerceIn(
                0f,
                1f
            )

        val halfWidth =
            width *
                (
                    0.25f +
                        0.17f *
                            amount
                    )

        val halfHeight =
            height *
                (
                    0.010f +
                        0.010f *
                            amount
                    )

        effectPaint.color =
            Color.argb(
                (
                    12 +
                        25 *
                            amount
                    ).toInt(),
                132,
                160,
                174
            )

        canvas.drawOval(
            RectF(
                width *
                    0.50f -
                    halfWidth,
                groundY -
                    halfHeight,
                width *
                    0.50f +
                    halfWidth,
                groundY +
                    halfHeight
            ),
            effectPaint
        )

        ripplePaint.style =
            Paint.Style.STROKE

        ripplePaint.strokeWidth =
            (
                width *
                    0.004f
                ).coerceAtLeast(
                1f
            )

        ripplePaint.color =
            Color.argb(
                (
                    12 +
                        21 *
                            amount
                    ).toInt(),
                175,
                201,
                214
            )

        canvas.drawOval(
            RectF(
                width *
                    0.50f -
                    halfWidth,
                groundY -
                    halfHeight,
                width *
                    0.50f +
                    halfWidth,
                groundY +
                    halfHeight
            ),
            ripplePaint
        )
    }

    private fun drawRipples(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {
        for (ripple in ripples) {
            val p =
                (
                    ripple.age /
                        ripple.life
                    ).coerceIn(
                    0f,
                    1f
                )

            val radius =
                ripple.maxRadius *
                    p

            val ry =
                radius *
                    0.22f

            ripplePaint.style =
                Paint.Style.STROKE

            ripplePaint.strokeWidth =
                (
                    width *
                        0.0035f
                    ).coerceAtLeast(
                    0.8f
                )

            ripplePaint.color =
                Color.argb(
                    (
                        ripple.alpha *
                            (
                                1f -
                                    p
                                )
                        ).toInt(),
                    176,
                    205,
                    219
                )

            canvas.drawOval(
                RectF(
                    ripple.x -
                        radius,
                    ripple.y -
                        ry,
                    ripple.x +
                        radius,
                    ripple.y +
                        ry
                ),
                ripplePaint
            )

            if (
                p <
                0.42f
            ) {
                val inner =
                    radius *
                        0.54f

                val innerY =
                    ry *
                        0.68f

                ripplePaint.color =
                    Color.argb(
                        (
                            ripple.alpha *
                                0.50f *
                                (
                                    1f -
                                        p /
                                            0.42f
                                    )
                            ).toInt(),
                        215,
                        232,
                        240
                    )

                canvas.drawOval(
                    RectF(
                        ripple.x -
                            inner,
                        ripple.y -
                            innerY,
                        ripple.x +
                            inner,
                        ripple.y +
                            innerY
                    ),
                    ripplePaint
                )
            }
        }
    }

    private fun drawSplashes(
        canvas: Canvas
    ) {
        for (splash in splashes) {
            val p =
                (
                    splash.age /
                        splash.life
                    ).coerceIn(
                    0f,
                    1f
                )

            effectPaint.color =
                Color.argb(
                    (
                        185 *
                            (
                                1f -
                                    p
                                )
                        ).toInt(),
                    191,
                    221,
                    234
                )

            canvas.drawCircle(
                splash.x,
                splash.y,
                splash.radius *
                    (
                        1f -
                            p *
                                0.25f
                        ),
                effectPaint
            )
        }
    }

    private fun drawAttachedBeads(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {
        for (bead in beads) {
            val p =
                (
                    bead.age /
                        bead.life
                    ).coerceIn(
                    0f,
                    1f
                )

            when (bead.surface) {
                Surface.SCARF -> {
                    val x =
                        width *
                            (
                                0.48f +
                                    bead.offset *
                                        0.22f
                                )

                    val y =
                        height *
                            (
                                0.555f +
                                    p *
                                        0.024f
                                )

                    effectPaint.color =
                        Color.argb(
                            (
                                72 *
                                    (
                                        1f -
                                            p
                                        )
                                ).toInt(),
                            128,
                            165,
                            190
                        )

                    canvas.drawOval(
                        RectF(
                            x -
                                bead.radius *
                                    1.8f,
                            y -
                                bead.radius *
                                    0.7f,
                            x +
                                bead.radius *
                                    1.8f,
                            y +
                                bead.radius *
                                    0.7f
                        ),
                        effectPaint
                    )
                }

                else -> {
                    val base =
                        when (bead.surface) {
                            Surface.LEFT_EAR ->
                                Pair(
                                    width *
                                        (
                                            0.15f +
                                                bead.offset *
                                                    0.10f
                                            ),
                                    height *
                                        0.36f
                                )

                            Surface.RIGHT_EAR ->
                                Pair(
                                    width *
                                        (
                                            0.75f +
                                                bead.offset *
                                                    0.10f
                                            ),
                                    height *
                                        0.36f
                                )

                            else ->
                                Pair(
                                    width *
                                        (
                                            0.44f +
                                                bead.offset *
                                                    0.12f
                                            ),
                                    height *
                                        0.245f
                                )
                        }

                    val drift =
                        when (bead.surface) {
                            Surface.LEFT_EAR,
                            Surface.RIGHT_EAR ->
                                height *
                                    0.115f *
                                    p

                            else ->
                                height *
                                    0.075f *
                                    p
                        }

                    val x =
                        base.first +
                            sin(
                                p *
                                    PI
                            ).toFloat() *
                                width *
                                0.008f

                    val y =
                        base.second +
                            drift

                    val alpha =
                        (
                            196 *
                                (
                                    1f -
                                        p *
                                            0.55f
                                    )
                            ).toInt()

                    effectPaint.color =
                        Color.argb(
                            alpha,
                            180,
                            215,
                            232
                        )

                    canvas.drawCircle(
                        x,
                        y,
                        bead.radius,
                        effectPaint
                    )

                    effectPaint.color =
                        Color.argb(
                            (
                                alpha *
                                    0.72f
                                ).toInt(),
                            235,
                            246,
                            250
                        )

                    canvas.drawCircle(
                        x -
                            bead.radius *
                                0.28f,
                        y -
                            bead.radius *
                                0.32f,
                        bead.radius *
                            0.28f,
                        effectPaint
                    )

                    if (
                        p >
                        0.72f &&
                        (
                            bead.surface ==
                                Surface.LEFT_EAR ||
                                bead.surface ==
                                Surface.RIGHT_EAR
                            )
                    ) {
                        val dropProgress =
                            (
                                (p - 0.72f) /
                                    0.28f
                                ).coerceIn(
                                0f,
                                1f
                            )

                        effectPaint.color =
                            Color.argb(
                                (
                                    175 *
                                        (
                                            1f -
                                                dropProgress
                                            )
                                    ).toInt(),
                                184,
                                217,
                                233
                            )

                        canvas.drawOval(
                            RectF(
                                x -
                                    bead.radius *
                                        0.55f,
                                y +
                                    bead.radius *
                                        0.65f +
                                    height *
                                        0.035f *
                                        dropProgress,
                                x +
                                    bead.radius *
                                        0.55f,
                                y +
                                    bead.radius *
                                        1.85f +
                                    height *
                                        0.050f *
                                        dropProgress
                            ),
                            effectPaint
                        )
                    }
                }
            }
        }
    }

    private fun drawShakeSpray(
        canvas: Canvas,
        width: Float,
        height: Float,
        stateSeconds: Double
    ) {
        val p =
            (
                stateSeconds /
                    1.60
                ).toFloat()
                .coerceIn(
                    0f,
                    1f
                )

        if (
            p >=
            1f
        ) {
            return
        }

        val centers =
            arrayOf(
                Pair(
                    width *
                        0.20f,
                    height *
                        0.34f
                ),
                Pair(
                    width *
                        0.80f,
                    height *
                        0.34f
                ),
                Pair(
                    width *
                        0.50f,
                    height *
                        0.27f
                ),
                Pair(
                    width *
                        0.40f,
                    height *
                        0.50f
                ),
                Pair(
                    width *
                        0.60f,
                    height *
                        0.50f
                )
            )

        for (
            centerIndex in
            centers.indices
        ) {
            val center =
                centers[centerIndex]

            repeat(3) { dropletIndex ->
                val local =
                    (
                        p +
                            centerIndex *
                                0.063f +
                            dropletIndex *
                                0.048f
                        ) %
                        1f

                val baseAngle =
                    -0.95 +
                        centerIndex *
                            0.47 +
                        dropletIndex *
                            0.20

                val radius =
                    min(
                        width,
                        height
                    ) *
                        (
                            0.10f +
                                0.42f *
                                    local
                            )

                val x =
                    center.first +
                        cos(
                            baseAngle.toDouble()
                        ).toFloat() *
                            radius

                val y =
                    center.second +
                        sin(
                            baseAngle.toDouble()
                        ).toFloat() *
                            radius *
                            0.85f

                val r =
                    (
                        width *
                            (
                                0.007f +
                                    dropletIndex *
                                        0.0020f
                                )
                        ).coerceAtLeast(
                        1f
                    )

                effectPaint.color =
                    Color.argb(
                        (
                            205 *
                                (
                                    1f -
                                        local
                                    )
                            ).toInt(),
                        178,
                        214,
                        232
                    )

                canvas.drawCircle(
                    x,
                    y,
                    r,
                    effectPaint
                )
            }
        }
    }
}
