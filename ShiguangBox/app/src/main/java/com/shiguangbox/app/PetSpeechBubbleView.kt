package com.shiguangbox.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class PetSpeechBubbleView(
    context: Context
) : LinearLayout(context) {

    enum class Tone {
        NORMAL,
        REMINDER,
        SUCCESS,
        MOOD
    }

    private val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1.25f)
        }

    private val bubblePath = Path()

    private var tailAtTop = false
    private var tailCenter = 0f
    private var tone = Tone.NORMAL
    private var actionsRow:
        LinearLayout? = null

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

    fun bind(
        title: String,
        message: String,
        tone: Tone = Tone.NORMAL
    ) {
        removeAllViews()
        actionsRow = null
        this.tone = tone
        applyTone(tone)

        val badge = TextView(context).apply {
            text = title
            textSize = 11.5f
            setTypeface(
                typeface,
                Typeface.BOLD
            )
            setTextColor(
                accentColor(tone)
            )
            setPadding(
                dpInt(9),
                dpInt(4),
                dpInt(9),
                dpInt(4)
            )
            background =
                GradientDrawable().apply {
                    shape =
                        GradientDrawable.RECTANGLE
                    cornerRadius = dp(11f)
                    setColor(
                        accentPaleColor(tone)
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
                textSize = 14f
                setTextColor(
                    Color.rgb(
                        77,
                        59,
                        48
                    )
                )
                setLineSpacing(
                    0f,
                    1.14f
                )
                maxLines = 4
            }

        addView(
            messageView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpInt(9)
            }
        )
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
                    topMargin = dpInt(10)
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
                            GradientDrawable.RECTANGLE
                        cornerRadius = dp(13f)
                        setColor(
                            if (primary) {
                                accentColor(tone)
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
                dpInt(38),
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
    }

    fun setTail(
        atTop: Boolean,
        centerPx: Float
    ) {
        tailAtTop = atTop
        tailCenter = centerPx
        updatePadding()
        invalidate()
    }

    override fun onDraw(
        canvas: Canvas
    ) {
        bubblePath.reset()

        val left = outerInset
        val right = width - outerInset

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
                safeCenter - tailHalfWidth,
                top
            )
            bubblePath.lineTo(
                safeCenter,
                outerInset
            )
            bubblePath.lineTo(
                safeCenter + tailHalfWidth,
                top
            )
        } else {
            bubblePath.moveTo(
                safeCenter - tailHalfWidth,
                bottom
            )
            bubblePath.lineTo(
                safeCenter,
                height - outerInset
            )
            bubblePath.lineTo(
                safeCenter + tailHalfWidth,
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

    private fun updatePadding() {
        val horizontal = dpInt(21)
        val bodyVertical = dpInt(15)
        val tail = dpInt(12)

        setPadding(
            horizontal,
            bodyVertical +
                if (tailAtTop) tail
                else 0,
            horizontal,
            bodyVertical +
                if (tailAtTop) 0
                else tail
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
}
