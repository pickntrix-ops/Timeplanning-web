package com.timeplanning.app

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * A small, hand-drawn icon set (no icon library — this project has deliberately
 * avoided one; an earlier attempt at material-icons-extended failed to resolve
 * cross-platform). Every icon is drawn on a fixed 24x24 logical grid, scaled to
 * fit whatever size is requested.
 */
@Composable
fun CategoryIconGlyph(icon: CategoryIcon, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.width / 24f
        val stroke = Stroke(width = 1.8f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun pt(x: Float, y: Float) = Offset(x * s, y * s)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, pt(x1, y1), pt(x2, y2), strokeWidth = stroke.width, cap = StrokeCap.Round)
        fun circle(cx: Float, cy: Float, r: Float, fill: Boolean = false) {
            if (fill) drawCircle(tint, r * s, pt(cx, cy)) else drawCircle(tint, r * s, pt(cx, cy), style = stroke)
        }

        when (icon) {
            CategoryIcon.HOME -> {
                // A solid silhouette (roof + body as one filled shape) — matches the reference's filled house glyph, not an outline.
                val house = Path().apply {
                    moveTo(pt(12f, 3.6f).x, pt(12f, 3.6f).y)
                    lineTo(pt(21f, 11.2f).x, pt(21f, 11.2f).y)
                    lineTo(pt(21f, 12.9f).x, pt(21f, 12.9f).y)
                    lineTo(pt(18.6f, 12.9f).x, pt(18.6f, 12.9f).y)
                    lineTo(pt(18.6f, 20.4f).x, pt(18.6f, 20.4f).y)
                    lineTo(pt(13.4f, 20.4f).x, pt(13.4f, 20.4f).y)
                    lineTo(pt(13.4f, 14.6f).x, pt(13.4f, 14.6f).y)
                    lineTo(pt(10.6f, 14.6f).x, pt(10.6f, 14.6f).y)
                    lineTo(pt(10.6f, 20.4f).x, pt(10.6f, 20.4f).y)
                    lineTo(pt(5.4f, 20.4f).x, pt(5.4f, 20.4f).y)
                    lineTo(pt(5.4f, 12.9f).x, pt(5.4f, 12.9f).y)
                    lineTo(pt(3f, 12.9f).x, pt(3f, 12.9f).y)
                    lineTo(pt(3f, 11.2f).x, pt(3f, 11.2f).y)
                    close()
                }
                drawPath(house, tint, style = androidx.compose.ui.graphics.drawscope.Fill)
            }
            CategoryIcon.BRIEFCASE -> {
                // Solid filled body (matches the reference's bold briefcase glyph) with a stroked handle arc on top.
                drawRoundRect(
                    color = tint,
                    topLeft = pt(3.5f, 8.5f),
                    size = Size(17f * s, 10.5f * s),
                    cornerRadius = CornerRadius(1.6f * s),
                    style = androidx.compose.ui.graphics.drawscope.Fill,
                )
                val handle = Path().apply {
                    moveTo(pt(9f, 8.5f).x, pt(9f, 8.5f).y)
                    cubicTo(
                        pt(9f, 5.9f).x, pt(9f, 5.9f).y,
                        pt(10.3f, 4.6f).x, pt(10.3f, 4.6f).y,
                        pt(12f, 4.6f).x, pt(12f, 4.6f).y,
                    )
                    cubicTo(
                        pt(13.7f, 4.6f).x, pt(13.7f, 4.6f).y,
                        pt(15f, 5.9f).x, pt(15f, 5.9f).y,
                        pt(15f, 8.5f).x, pt(15f, 8.5f).y,
                    )
                }
                drawPath(handle, tint, style = Stroke(width = 1.9f * s, cap = StrokeCap.Round))
                line(3.5f, 13.2f, 20.5f, 13.2f)
            }
            CategoryIcon.DRINK -> {
                val glass = Path().apply {
                    moveTo(pt(5f, 5.5f).x, pt(5f, 5.5f).y)
                    lineTo(pt(19f, 5.5f).x, pt(19f, 5.5f).y)
                    lineTo(pt(12f, 13.5f).x, pt(12f, 13.5f).y)
                    close()
                }
                drawPath(glass, tint, style = stroke)
                line(12f, 13.5f, 12f, 19.5f)
                line(8f, 19.5f, 16f, 19.5f)
            }
            CategoryIcon.FITNESS -> {
                drawRoundRect(tint, pt(2.5f, 9.5f), Size(4f * s, 5f * s), CornerRadius(1f * s), style = stroke)
                drawRoundRect(tint, pt(17.5f, 9.5f), Size(4f * s, 5f * s), CornerRadius(1f * s), style = stroke)
                line(6.5f, 12f, 17.5f, 12f)
            }
            CategoryIcon.CARD -> {
                drawRoundRect(tint, pt(3f, 6f), Size(18f * s, 12f * s), CornerRadius(1.8f * s), style = stroke)
                drawRoundRect(tint, pt(3f, 9.6f), Size(18f * s, 2.6f * s), CornerRadius(0f), style = androidx.compose.ui.graphics.drawscope.Fill)
            }
            CategoryIcon.PEOPLE -> {
                circle(9.5f, 8.5f, 2.6f)
                circle(15.5f, 9.7f, 2.2f)
                val body1 = Path().apply {
                    moveTo(pt(4f, 19f).x, pt(4f, 19f).y)
                    cubicTo(
                        pt(4f, 14.5f).x, pt(4f, 14.5f).y,
                        pt(15f, 14.5f).x, pt(15f, 14.5f).y,
                        pt(15f, 19f).x, pt(15f, 19f).y,
                    )
                }
                drawPath(body1, tint, style = stroke)
                val body2 = Path().apply {
                    moveTo(pt(13.5f, 19f).x, pt(13.5f, 19f).y)
                    cubicTo(
                        pt(13.5f, 15.6f).x, pt(13.5f, 15.6f).y,
                        pt(20.5f, 15.6f).x, pt(20.5f, 15.6f).y,
                        pt(20.5f, 19f).x, pt(20.5f, 19f).y,
                    )
                }
                drawPath(body2, tint, style = stroke)
            }
            CategoryIcon.BOOK -> {
                val left = Path().apply {
                    moveTo(pt(12f, 7.5f).x, pt(12f, 7.5f).y)
                    cubicTo(
                        pt(9f, 5.8f).x, pt(9f, 5.8f).y,
                        pt(6f, 5.8f).x, pt(6f, 5.8f).y,
                        pt(4f, 6.6f).x, pt(4f, 6.6f).y,
                    )
                    lineTo(pt(4f, 17.4f).x, pt(4f, 17.4f).y)
                    cubicTo(
                        pt(6f, 16.6f).x, pt(6f, 16.6f).y,
                        pt(9f, 16.6f).x, pt(9f, 16.6f).y,
                        pt(12f, 18.3f).x, pt(12f, 18.3f).y,
                    )
                    close()
                }
                drawPath(left, tint, style = stroke)
                val right = Path().apply {
                    moveTo(pt(12f, 7.5f).x, pt(12f, 7.5f).y)
                    cubicTo(
                        pt(15f, 5.8f).x, pt(15f, 5.8f).y,
                        pt(18f, 5.8f).x, pt(18f, 5.8f).y,
                        pt(20f, 6.6f).x, pt(20f, 6.6f).y,
                    )
                    lineTo(pt(20f, 17.4f).x, pt(20f, 17.4f).y)
                    cubicTo(
                        pt(18f, 16.6f).x, pt(18f, 16.6f).y,
                        pt(15f, 16.6f).x, pt(15f, 16.6f).y,
                        pt(12f, 18.3f).x, pt(12f, 18.3f).y,
                    )
                    close()
                }
                drawPath(right, tint, style = stroke)
            }
            CategoryIcon.HEART -> {
                val heart = Path().apply {
                    moveTo(pt(12f, 19f).x, pt(12f, 19f).y)
                    cubicTo(
                        pt(4f, 13f).x, pt(4f, 13f).y,
                        pt(4.5f, 6.5f).x, pt(4.5f, 6.5f).y,
                        pt(9f, 6.5f).x, pt(9f, 6.5f).y,
                    )
                    cubicTo(
                        pt(10.5f, 6.5f).x, pt(10.5f, 6.5f).y,
                        pt(11.5f, 7.3f).x, pt(11.5f, 7.3f).y,
                        pt(12f, 8.3f).x, pt(12f, 8.3f).y,
                    )
                    cubicTo(
                        pt(12.5f, 7.3f).x, pt(12.5f, 7.3f).y,
                        pt(13.5f, 6.5f).x, pt(13.5f, 6.5f).y,
                        pt(15f, 6.5f).x, pt(15f, 6.5f).y,
                    )
                    cubicTo(
                        pt(19.5f, 6.5f).x, pt(19.5f, 6.5f).y,
                        pt(20f, 13f).x, pt(20f, 13f).y,
                        pt(12f, 19f).x, pt(12f, 19f).y,
                    )
                    close()
                }
                drawPath(heart, tint, style = stroke)
            }
            CategoryIcon.PLANE -> {
                // Solid filled paper-plane silhouette, matching the reference's bold glyph, with a thin crease line.
                val plane = Path().apply {
                    moveTo(pt(21f, 4f).x, pt(21f, 4f).y)
                    lineTo(pt(3f, 12f).x, pt(3f, 12f).y)
                    lineTo(pt(10.5f, 14.2f).x, pt(10.5f, 14.2f).y)
                    lineTo(pt(13f, 20.5f).x, pt(13f, 20.5f).y)
                    lineTo(pt(15.3f, 13.6f).x, pt(15.3f, 13.6f).y)
                    close()
                }
                drawPath(plane, tint, style = androidx.compose.ui.graphics.drawscope.Fill)
                val crease = Path().apply {
                    moveTo(pt(10.5f, 14.2f).x, pt(10.5f, 14.2f).y)
                    lineTo(pt(21f, 4f).x, pt(21f, 4f).y)
                }
                drawPath(crease, Color(0xFFF7F7F9), style = Stroke(width = 1.3f * s, cap = StrokeCap.Round))
            }
            CategoryIcon.CART -> {
                line(2.5f, 4.5f, 5.2f, 4.5f)
                val basket = Path().apply {
                    moveTo(pt(5.2f, 4.5f).x, pt(5.2f, 4.5f).y)
                    lineTo(pt(7f, 15f).x, pt(7f, 15f).y)
                    lineTo(pt(18f, 15f).x, pt(18f, 15f).y)
                    lineTo(pt(20.5f, 7f).x, pt(20.5f, 7f).y)
                    lineTo(pt(6.1f, 7f).x, pt(6.1f, 7f).y)
                }
                drawPath(basket, tint, style = stroke)
                circle(9f, 18.5f, 1.4f)
                circle(16f, 18.5f, 1.4f)
            }
            CategoryIcon.PAW -> {
                circle(7.2f, 9.5f, 1.7f, fill = true)
                circle(11.5f, 6.8f, 1.7f, fill = true)
                circle(16.3f, 7.7f, 1.7f, fill = true)
                circle(19f, 11.8f, 1.6f, fill = true)
                val pad = Path().apply {
                    moveTo(pt(12f, 13.5f).x, pt(12f, 13.5f).y)
                    cubicTo(
                        pt(16.5f, 13.5f).x, pt(16.5f, 13.5f).y,
                        pt(18.5f, 17.5f).x, pt(18.5f, 17.5f).y,
                        pt(15.5f, 19.8f).x, pt(15.5f, 19.8f).y,
                    )
                    cubicTo(
                        pt(13.5f, 21.2f).x, pt(13.5f, 21.2f).y,
                        pt(10.5f, 21.2f).x, pt(10.5f, 21.2f).y,
                        pt(8.5f, 19.8f).x, pt(8.5f, 19.8f).y,
                    )
                    cubicTo(
                        pt(5.5f, 17.5f).x, pt(5.5f, 17.5f).y,
                        pt(7.5f, 13.5f).x, pt(7.5f, 13.5f).y,
                        pt(12f, 13.5f).x, pt(12f, 13.5f).y,
                    )
                    close()
                }
                drawPath(pad, tint, style = androidx.compose.ui.graphics.drawscope.Fill)
            }
            CategoryIcon.MORE -> {
                circle(6.5f, 12f, 1.4f, fill = true)
                circle(12f, 12f, 1.4f, fill = true)
                circle(17.5f, 12f, 1.4f, fill = true)
            }
        }
    }
}

/** A few extra glyphs used only inside the category wizard's own UI (not selectable/stored) — kept separate from CategoryIcon so the 12-icon picker grid stays exactly the fixed set the person chooses from. */
enum class WizardGlyph { ORDER, CALENDAR, CHECK_CIRCLE, REPEAT, PERSON, PLUS, CLOSE, LINK, CLOCK, LIST, SUN, BOLT, CHECK, INFO }

@Composable
fun WizardIconGlyph(icon: WizardGlyph, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.width / 24f
        val stroke = Stroke(width = 1.8f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun pt(x: Float, y: Float) = Offset(x * s, y * s)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, pt(x1, y1), pt(x2, y2), strokeWidth = stroke.width, cap = StrokeCap.Round)
        fun circle(cx: Float, cy: Float, r: Float, fill: Boolean = false) {
            if (fill) drawCircle(tint, r * s, pt(cx, cy)) else drawCircle(tint, r * s, pt(cx, cy), style = stroke)
        }

        when (icon) {
            WizardGlyph.ORDER -> {
                line(8f, 18f, 8f, 6f)
                val upHead = Path().apply {
                    moveTo(pt(5f, 9f).x, pt(5f, 9f).y)
                    lineTo(pt(8f, 6f).x, pt(8f, 6f).y)
                    lineTo(pt(11f, 9f).x, pt(11f, 9f).y)
                }
                drawPath(upHead, tint, style = stroke)
                line(16f, 6f, 16f, 18f)
                val downHead = Path().apply {
                    moveTo(pt(13f, 15f).x, pt(13f, 15f).y)
                    lineTo(pt(16f, 18f).x, pt(16f, 18f).y)
                    lineTo(pt(19f, 15f).x, pt(19f, 15f).y)
                }
                drawPath(downHead, tint, style = stroke)
            }
            WizardGlyph.CALENDAR -> {
                drawRoundRect(
                    color = tint,
                    topLeft = pt(4f, 5f),
                    size = Size(16f * s, 15f * s),
                    cornerRadius = CornerRadius(2f * s),
                    style = stroke,
                )
                line(4f, 9.5f, 20f, 9.5f)
                line(8.5f, 3.3f, 8.5f, 6.5f)
                line(15.5f, 3.3f, 15.5f, 6.5f)
            }
            WizardGlyph.REPEAT -> {
                drawArc(tint, 200f, 140f, false, pt(5f, 5f), Size(14f * s, 14f * s), style = stroke)
                drawArc(tint, 20f, 140f, false, pt(5f, 5f), Size(14f * s, 14f * s), style = stroke)
                val head1 = Path().apply {
                    moveTo(pt(19f, 5.5f).x, pt(19f, 5.5f).y)
                    lineTo(pt(19f, 9.6f).x, pt(19f, 9.6f).y)
                    lineTo(pt(15f, 9.6f).x, pt(15f, 9.6f).y)
                }
                val head2 = Path().apply {
                    moveTo(pt(5f, 18.5f).x, pt(5f, 18.5f).y)
                    lineTo(pt(5f, 14.4f).x, pt(5f, 14.4f).y)
                    lineTo(pt(9f, 14.4f).x, pt(9f, 14.4f).y)
                }
                drawPath(head1, tint, style = stroke)
                drawPath(head2, tint, style = stroke)
            }
            WizardGlyph.PERSON -> {
                drawCircle(tint, 3.6f * s, pt(12f, 8f), style = stroke)
                val body = Path().apply {
                    moveTo(pt(5f, 20f).x, pt(5f, 20f).y)
                    cubicTo(pt(5f, 12.5f).x, pt(5f, 12.5f).y, pt(19f, 12.5f).x, pt(19f, 12.5f).y, pt(19f, 20f).x, pt(19f, 20f).y)
                }
                drawPath(body, tint, style = stroke)
            }
            WizardGlyph.PLUS -> {
                line(12f, 5f, 12f, 19f)
                line(5f, 12f, 19f, 12f)
            }
            WizardGlyph.CLOSE -> {
                line(6f, 6f, 18f, 18f)
                line(18f, 6f, 6f, 18f)
            }
            WizardGlyph.LINK -> {
                drawRoundRect(tint, pt(3f, 9f), Size(11f * s, 6f * s), CornerRadius(3f * s), style = stroke)
                drawRoundRect(tint, pt(10f, 9f), Size(11f * s, 6f * s), CornerRadius(3f * s), style = stroke)
            }
            WizardGlyph.CHECK_CIRCLE -> {
                drawCircle(tint, 9f * s, pt(12f, 12f), style = stroke)
                val check = Path().apply {
                    moveTo(pt(8f, 12.3f).x, pt(8f, 12.3f).y)
                    lineTo(pt(10.8f, 15f).x, pt(10.8f, 15f).y)
                    lineTo(pt(16f, 9f).x, pt(16f, 9f).y)
                }
                drawPath(check, tint, style = stroke)
            }
            WizardGlyph.CHECK -> {
                val check = Path().apply {
                    moveTo(pt(5f, 12.3f).x, pt(5f, 12.3f).y)
                    lineTo(pt(9.5f, 17f).x, pt(9.5f, 17f).y)
                    lineTo(pt(19f, 6.5f).x, pt(19f, 6.5f).y)
                }
                drawPath(check, tint, style = stroke)
            }
            WizardGlyph.CLOCK -> {
                drawCircle(tint, 9f * s, pt(12f, 12.5f), style = stroke)
                val hands = Path().apply {
                    moveTo(pt(12f, 12.5f).x, pt(12f, 12.5f).y)
                    lineTo(pt(12f, 7.5f).x, pt(12f, 7.5f).y)
                    moveTo(pt(12f, 12.5f).x, pt(12f, 12.5f).y)
                    lineTo(pt(15.5f, 14.5f).x, pt(15.5f, 14.5f).y)
                }
                drawPath(hands, tint, style = stroke)
            }
            WizardGlyph.LIST -> {
                line(9f, 6.5f, 20f, 6.5f)
                line(9f, 12f, 20f, 12f)
                line(9f, 17.5f, 20f, 17.5f)
                circle(4.5f, 6.5f, 1.3f, fill = true)
                circle(4.5f, 12f, 1.3f, fill = true)
                circle(4.5f, 17.5f, 1.3f, fill = true)
            }
            WizardGlyph.SUN -> {
                circle(12f, 12f, 4f)
                line(12f, 2.8f, 12f, 5.2f)
                line(12f, 18.8f, 12f, 21.2f)
                line(2.8f, 12f, 5.2f, 12f)
                line(18.8f, 12f, 21.2f, 12f)
                line(5.4f, 5.4f, 7.1f, 7.1f)
                line(16.9f, 16.9f, 18.6f, 18.6f)
                line(5.4f, 18.6f, 7.1f, 16.9f)
                line(16.9f, 7.1f, 18.6f, 5.4f)
            }
            WizardGlyph.BOLT -> {
                val bolt = Path().apply {
                    moveTo(pt(13f, 3f).x, pt(13f, 3f).y)
                    lineTo(pt(5.5f, 13.5f).x, pt(5.5f, 13.5f).y)
                    lineTo(pt(11f, 13.5f).x, pt(11f, 13.5f).y)
                    lineTo(pt(10f, 21f).x, pt(10f, 21f).y)
                    lineTo(pt(18.5f, 9.8f).x, pt(18.5f, 9.8f).y)
                    lineTo(pt(13f, 9.8f).x, pt(13f, 9.8f).y)
                    close()
                }
                drawPath(bolt, tint, style = androidx.compose.ui.graphics.drawscope.Fill)
            }
            WizardGlyph.INFO -> {
                drawCircle(tint, 9f * s, pt(12f, 12f), style = stroke)
                circle(12f, 8.3f, 0.35f, fill = true)
                line(12f, 11f, 12f, 16f)
            }
        }
    }
}
