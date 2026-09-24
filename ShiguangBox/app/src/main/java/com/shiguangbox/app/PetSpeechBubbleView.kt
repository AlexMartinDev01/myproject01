package com.shiguangbox.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class PetSpeechBubbleView(
    context: Context,
    private val petKind: PetKind = PetKind.ORANGE
) : LinearLayout(context) {

    enum class Tone {
        NORMAL,
        REMINDER,
        SUCCESS,
        MOOD
    }

    private val yayaTheme =
        petKind == PetKind.YAYA

    private val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1.25f)
        }

    private val bitmapPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG or
                Paint.FILTER_BITMAP_FLAG
        )

    private val sizingPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val bubblePath = Path()

    private val yayaBubbleBitmap: Bitmap? =
        if (yayaTheme) {
            BitmapFactory.decodeResource(
                resources,
                R.drawable.pet_yaya_bubble
            )
        } else {
            null
        }

    private var tailAtTop = false
    private var tailCenter = 0f
    private var tone = Tone.NORMAL
    private var actionsRow:
        LinearLayout? = null

    private var boundTitle = ""
    private var boundMessage = ""
    private var actionCount = 0

    private val outerInset = dp(7f)
    private val tailHeight = dp(12f)
    private val tailHalfWidth = dp(12f)
    private val corner = dp(22f)

    init {
        orientation = VERTICAL
        gravity = Gravity.START
        setWillNotDraw(false)
        setLayerType(
            View.LAYER_TYPE_SOFTWARE,
            null
        )
        setBackgroundColor(Color.TRANSPARENT)
        clipChildren = false
        clipToPadding = false
        elevation = dp(10f)
        updatePadding()
        applyTone(Tone.NORMAL)
    }

    fun isYayaTheme(): Boolean =
        yayaTheme

    fun tailAnchorFraction(): Float =
        if (yayaTheme) {
            0.47f
        } else {
            0.50f
        }

    fun preferredWidthPx(
        screenWidthPx: Int
    ): Int {
        if (!yayaTheme) {
            return dpInt(248)
        }

        val minWidth = dpInt(210)

        val maxWidth =
            minOf(
                dpInt(330),
                (
                    screenWidthPx *
                        0.78f
                    ).toInt()
            ).coerceAtLeast(minWidth)

        sizingPaint.textSize =
            sp(14.5f)

        sizingPaint.typeface =
            Typeface.DEFAULT_BOLD

        val widestMessage =
            boundMessage
                .split("\n")
                .maxOfOrNull {
                    sizingPaint
                        .measureText(it)
                } ?: 0f

        sizingPaint.textSize =
            sp(11f)

        val titleWidth =
            sizingPaint
                .measureText(boundTitle)

        var desired =
            maxOf(
                minWidth,
                (
                    widestMessage +
                        dp(64f)
                    ).toInt(),
                (
                    titleWidth +
                        dp(58f)
                    ).toInt()
            )

        val length =
            boundMessage.length

        desired =
            when {
                actionCount > 0 ->
                    maxOf(
                        desired,
                        dpInt(300)
                    )

                length >= 34 ->
                    maxWidth

                length >= 22 ->
                    maxOf(
                        desired,
                        dpInt(292)
                    )

                length >= 12 ->
                    maxOf(
                        desired,
                        dpInt(252)
                    )

                else -> desired
            }

        return desired
            .coerceIn(
                minWidth,
                maxWidth
            )
    }

    fun bind(
        title: String,
        message: String,
        tone: Tone = Tone.NORMAL
    ) {
        removeAllViews()
        actionsRow = null
        actionCount = 0
        boundTitle = title
        boundMessage = message
        this.tone = tone
        applyTone(tone)

        val badge =
            TextView(context).apply {
                text = title
                textSize =
                    if (yayaTheme) {
                        10.8f
                    } else {
                        11.5f
                    }

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                setTextColor(
                    accentColor(tone)
                )

                setPadding(
                    dpInt(
                        if (yayaTheme) {
                            10
                        } else {
                            9
                        }
                    ),
                    dpInt(4),
                    dpInt(
                        if (yayaTheme) {
                            10
                        } else {
                            9
                        }
                    ),
                    dpInt(4)
                )

                background =
                    GradientDrawable().apply {
                        shape =
                            GradientDrawable
                                .RECTANGLE

                        cornerRadius =
                            dp(
                                if (yayaTheme) {
                                    13f
                                } else {
                                    11f
                                }
                            )

                        setColor(
                            accentPaleColor(
                                tone
                            )
                        )
                    }
            }

        addView(
            badge,
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        val messageView =
            TextView(context).apply {
                text = message

                textSize =
                    if (yayaTheme) {
                        14.5f
                    } else {
                        14f
                    }

                setTextColor(
                    if (yayaTheme) {
                        Color.rgb(
                            58,
                            88,
                            42
                        )
                    } else {
                        Color.rgb(
                            77,
                            59,
                            48
                        )
                    }
                )

                if (yayaTheme) {
                    setTypeface(
                        typeface,
                        Typeface.BOLD
                    )
                }

                setLineSpacing(
                    0f,
                    if (yayaTheme) {
                        1.19f
                    } else {
                        1.14f
                    }
                )

                maxLines =
                    if (yayaTheme) {
                        6
                    } else {
                        4
                    }
            }

        addView(
            messageView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin =
                    dpInt(
                        if (yayaTheme) {
                            10
                        } else {
                            9
                        }
                    )
            }
        )

        requestLayout()
    }

    fun addAction(
        label: String,
        primary: Boolean = false,
        onClick: () -> Unit
    ) {
        var row = actionsRow

        if (row == null) {
            row =
                LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER
                }

            actionsRow = row

            addView(
                row,
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin =
                        dpInt(
                            if (yayaTheme) {
                                12
                            } else {
                                10
                            }
                        )
                }
            )
        }

        val button =
            Button(context).apply {
                text = label
                isAllCaps = false
                textSize = 12f
                minimumHeight = 0
                minHeight = 0

                setTextColor(
                    if (primary) {
                        Color.WHITE
                    } else if (yayaTheme) {
                        Color.rgb(
                            66,
                            97,
                            48
                        )
                    } else {
                        Color.rgb(
                            95,
                            67,
                            48
                        )
                    }
                )

                background =
                    GradientDrawable().apply {
                        shape =
                            GradientDrawable
                                .RECTANGLE

                        cornerRadius =
                            dp(
                                if (yayaTheme) {
                                    15f
                                } else {
                                    13f
                                }
                            )

                        setColor(
                            if (primary) {
                                accentColor(tone)
                            } else if (yayaTheme) {
                                Color.rgb(
                                    238,
                                    248,
                                    216
                                )
                            } else {
                                Color.rgb(
                                    255,
                                    239,
                                    218
                                )
                            }
                        )
                    }

                setOnClickListener {
                    onClick()
                }
            }

        row.addView(
            button,
            LinearLayout.LayoutParams(
                0,
                dpInt(
                    if (yayaTheme) {
                        40
                    } else {
                        38
                    }
                ),
                1f
            ).apply {
                marginEnd =
                    if (
                        (
                            actionsRow
                                ?.childCount
                                ?: 0
                            ) == 0
                    ) {
                        dpInt(6)
                    } else {
                        0
                    }
            }
        )

        actionCount += 1
        requestLayout()
    }

    fun setTail(
        atTop: Boolean,
        centerPx: Float
    ) {
        tailAtTop = atTop
        tailCenter = centerPx

        if (!yayaTheme) {
            updatePadding()
        }

        invalidate()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        super.onMeasure(
            widthMeasureSpec,
            heightMeasureSpec
        )

        if (yayaTheme) {
            val minHeight =
                dpInt(
                    if (actionCount > 0) {
                        205
                    } else {
                        166
                    }
                )

            if (measuredHeight < minHeight) {
                setMeasuredDimension(
                    measuredWidth,
                    minHeight
                )
            }
        }
    }

    override fun onDraw(
        canvas: Canvas
    ) {
        if (yayaTheme) {
            drawYayaBubble(canvas)
            super.onDraw(canvas)
            return
        }

        bubblePath.reset()

        val left = outerInset
        val right =
            width - outerInset

        val top =
            outerInset +
                if (tailAtTop) {
                    tailHeight
                } else {
                    0f
                }

        val bottom =
            height -
                outerInset -
                if (tailAtTop) {
                    0f
                } else {
                    tailHeight
                }

        val body =
            RectF(
                left,
                top,
                right,
                bottom
            )

        bubblePath.addRoundRect(
            body,
            corner,
            corner,
            Path.Direction.CW
        )

        val safeCenter =
            tailCenter.coerceIn(
                left + corner,
                right - corner
            )

        if (tailAtTop) {
            bubblePath.moveTo(
                safeCenter -
                    tailHalfWidth,
                top
            )

            bubblePath.lineTo(
                safeCenter,
                outerInset
            )

            bubblePath.lineTo(
                safeCenter +
                    tailHalfWidth,
                top
            )
        } else {
            bubblePath.moveTo(
                safeCenter -
                    tailHalfWidth,
                bottom
            )

            bubblePath.lineTo(
                safeCenter,
                height - outerInset
            )

            bubblePath.lineTo(
                safeCenter +
                    tailHalfWidth,
                bottom
            )
        }

        bubblePath.close()

        fillPaint.setShadowLayer(
            dp(12f),
            0f,
            dp(4f),
            Color.argb(
                42,
                64,
                43,
                26
            )
        )

        canvas.drawPath(
            bubblePath,
            fillPaint
        )

        fillPaint.clearShadowLayer()

        canvas.drawPath(
            bubblePath,
            strokePaint
        )

        super.onDraw(canvas)
    }

    private fun drawYayaBubble(
        canvas: Canvas
    ) {
        val bitmap =
            yayaBubbleBitmap ?: return

        if (
            width <= 0 ||
            height <= 0
        ) {
            return
        }

        val sourceW =
            bitmap.width

        val sourceH =
            bitmap.height

        val x1 = 465
        val x2 = 600
        val y1 = 364
        val y2 = 430

        val densityScale =
            resources
                .displayMetrics
                .density /
                3f

        val fixedW =
            x1 +
                (
                    sourceW -
                        x2
                    )

        val fixedH =
            y1 +
                (
                    sourceH -
                        y2
                    )

        val fixedScale =
            minOf(
                densityScale,
                width.toFloat() /
                    (
                        fixedW +
                            1f
                        ),
                height.toFloat() /
                    (
                        fixedH +
                            1f
                        )
            ).coerceAtLeast(0.18f)

        val leftDst =
            x1 *
                fixedScale

        val rightDst =
            (
                sourceW -
                    x2
                ) *
                fixedScale

        val topDst =
            y1 *
                fixedScale

        val bottomDst =
            (
                sourceH -
                    y2
                ) *
                fixedScale

        val dx1 = leftDst
        val dx2 =
            width -
                rightDst

        val dy1 = topDst
        val dy2 =
            height -
                bottomDst

        drawPatch(
            canvas,
            bitmap,
            0,
            0,
            x1,
            y1,
            0f,
            0f,
            dx1,
            dy1
        )

        drawPatch(
            canvas,
            bitmap,
            x1,
            0,
            x2,
            y1,
            dx1,
            0f,
            dx2,
            dy1
        )

        drawPatch(
            canvas,
            bitmap,
            x2,
            0,
            sourceW,
            y1,
            dx2,
            0f,
            width.toFloat(),
            dy1
        )

        drawPatch(
            canvas,
            bitmap,
            0,
            y1,
            x1,
            y2,
            0f,
            dy1,
            dx1,
            dy2
        )

        drawPatch(
            canvas,
            bitmap,
            x1,
            y1,
            x2,
            y2,
            dx1,
            dy1,
            dx2,
            dy2
        )

        drawPatch(
            canvas,
            bitmap,
            x2,
            y1,
            sourceW,
            y2,
            dx2,
            dy1,
            width.toFloat(),
            dy2
        )

        drawPatch(
            canvas,
            bitmap,
            0,
            y2,
            x1,
            sourceH,
            0f,
            dy2,
            dx1,
            height.toFloat()
        )

        drawPatch(
            canvas,
            bitmap,
            x1,
            y2,
            x2,
            sourceH,
            dx1,
            dy2,
            dx2,
            height.toFloat()
        )

        drawPatch(
            canvas,
            bitmap,
            x2,
            y2,
            sourceW,
            sourceH,
            dx2,
            dy2,
            width.toFloat(),
            height.toFloat()
        )
    }

    private fun drawPatch(
        canvas: Canvas,
        bitmap: Bitmap,
        srcLeft: Int,
        srcTop: Int,
        srcRight: Int,
        srcBottom: Int,
        dstLeft: Float,
        dstTop: Float,
        dstRight: Float,
        dstBottom: Float
    ) {
        if (
            dstRight <= dstLeft ||
            dstBottom <= dstTop
        ) {
            return
        }

        canvas.drawBitmap(
            bitmap,
            Rect(
                srcLeft,
                srcTop,
                srcRight,
                srcBottom
            ),
            RectF(
                dstLeft,
                dstTop,
                dstRight,
                dstBottom
            ),
            bitmapPaint
        )
    }

    private fun updatePadding() {
        if (yayaTheme) {
            setPadding(
                dpInt(28),
                dpInt(30),
                dpInt(28),
                dpInt(42)
            )
            return
        }

        val horizontal = dpInt(21)
        val bodyVertical = dpInt(15)
        val tail = dpInt(12)

        setPadding(
            horizontal,
            bodyVertical +
                if (tailAtTop) {
                    tail
                } else {
                    0
                },
            horizontal,
            bodyVertical +
                if (tailAtTop) {
                    0
                } else {
                    tail
                }
        )
    }

    private fun applyTone(
        tone: Tone
    ) {
        fillPaint.style =
            Paint.Style.FILL

        fillPaint.color =
            when (tone) {
                Tone.NORMAL ->
                    Color.rgb(
                        255,
                        251,
                        245
                    )

                Tone.REMINDER ->
                    Color.rgb(
                        255,
                        247,
                        231
                    )

                Tone.SUCCESS ->
                    Color.rgb(
                        248,
                        251,
                        239
                    )

                Tone.MOOD ->
                    Color.rgb(
                        250,
                        247,
                        252
                    )
            }

        strokePaint.color =
            when (tone) {
                Tone.NORMAL ->
                    Color.rgb(
                        239,
                        216,
                        190
                    )

                Tone.REMINDER ->
                    Color.rgb(
                        239,
                        187,
                        121
                    )

                Tone.SUCCESS ->
                    Color.rgb(
                        201,
                        220,
                        169
                    )

                Tone.MOOD ->
                    Color.rgb(
                        220,
                        207,
                        232
                    )
            }
    }

    private fun accentColor(
        tone: Tone
    ): Int {
        if (yayaTheme) {
            return when (tone) {
                Tone.NORMAL ->
                    Color.rgb(
                        84,
                        126,
                        50
                    )

                Tone.REMINDER ->
                    Color.rgb(
                        109,
                        137,
                        47
                    )

                Tone.SUCCESS ->
                    Color.rgb(
                        65,
                        128,
                        62
                    )

                Tone.MOOD ->
                    Color.rgb(
                        91,
                        126,
                        70
                    )
            }
        }

        return when (tone) {
            Tone.NORMAL ->
                Color.rgb(
                    221,
                    145,
                    76
                )

            Tone.REMINDER ->
                Color.rgb(
                    224,
                    127,
                    44
                )

            Tone.SUCCESS ->
                Color.rgb(
                    116,
                    148,
                    79
                )

            Tone.MOOD ->
                Color.rgb(
                    142,
                    116,
                    166
                )
        }
    }

    private fun accentPaleColor(
        tone: Tone
    ): Int {
        if (yayaTheme) {
            return when (tone) {
                Tone.NORMAL ->
                    Color.rgb(
                        239,
                        248,
                        218
                    )

                Tone.REMINDER ->
                    Color.rgb(
                        247,
                        246,
                        211
                    )

                Tone.SUCCESS ->
                    Color.rgb(
                        227,
                        245,
                        216
                    )

                Tone.MOOD ->
                    Color.rgb(
                        235,
                        245,
                        224
                    )
            }
        }

        return when (tone) {
            Tone.NORMAL ->
                Color.rgb(
                    255,
                    237,
                    215
                )

            Tone.REMINDER ->
                Color.rgb(
                    255,
                    229,
                    193
                )

            Tone.SUCCESS ->
                Color.rgb(
                    232,
                    241,
                    215
                )

            Tone.MOOD ->
                Color.rgb(
                    239,
                    231,
                    246
                )
        }
    }

    private fun dp(
        value: Float
    ): Float {
        return value *
            resources
                .displayMetrics
                .density
    }

    private fun dpInt(
        value: Int
    ): Int {
        return (
            value *
                resources
                    .displayMetrics
                    .density
            ).toInt()
    }

    private fun sp(
        value: Float
    ): Float {
        return value *
            resources
                .displayMetrics
                .scaledDensity
    }
}
