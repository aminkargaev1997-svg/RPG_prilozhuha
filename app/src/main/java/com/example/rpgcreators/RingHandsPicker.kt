package com.example.rpgcreators

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/**
 * Interactive illustration of the two hands.
 * Tap a finger to equip/inspect its ring. Drag a finger to sway/curl it: the
 * ring mark is drawn on the same moving segment and follows the motion.
 * Poses are purely decorative and never modify saved equipment or bonuses.
 */
@Composable
internal fun RingHandsPicker(
    equipment: Map<String, Long>,
    inventory: List<InventoryItem>,
    onChooseSlot: (String) -> Unit
) {
    var rightHand by remember { mutableStateOf(false) }
    // Both hands retain their pose when switching between them in this screen.
    val leftTips = remember { mutableStateListOf<HandPoint>().apply { addAll(HandFingerGeometry.restingTips) } }
    val rightTips = remember { mutableStateListOf<HandPoint>().apply { addAll(HandFingerGeometry.restingTips) } }
    val tips: SnapshotStateList<HandPoint> = if (rightHand) rightTips else leftTips
    val chooseSlot by rememberUpdatedState(onChooseSlot)
    val slots = if (rightHand) ItemCatalogCategories.rightFingerSlots else ItemCatalogCategories.leftFingerSlots
    val wearing = slots.count { equipment[it] != null }
    val fillColor = MaterialTheme.colorScheme.surfaceVariant
    val strokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
    val ringColor = MaterialTheme.colorScheme.tertiary
    val emptyRingColor = MaterialTheme.colorScheme.outline
    val fingerNailColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
    val moved = tips.indices.any { tips[it].distanceTo(HandFingerGeometry.restingTips[it]) > 0.5f }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            FilterChip(selected = !rightHand, onClick = { rightHand = false }, label = { Text("Левая кисть") })
            FilterChip(selected = rightHand, onClick = { rightHand = true }, label = { Text("Правая кисть") })
        }
        Text(
            "Нажми на палец — выбрать кольцо. Потяни палец — подвигать его · $wearing/5",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        BoxWithConstraints(Modifier.fillMaxWidth().height(275.dp), contentAlignment = Alignment.Center) {
            val diagramWidth = minOf(maxWidth, 280.dp)
            val diagramHeight = diagramWidth * (250f / 260f)
            Canvas(
                Modifier
                    .width(diagramWidth)
                    .height(diagramHeight)
                    .semantics {
                        contentDescription = "Кисть с пятью подвижными пальцами. Коснись пальца для выбора кольца, перетащи для движения."
                    }
                    .pointerInput(rightHand) {
                        // All touches are resolved against the actual finger segments,
                        // including tips, rather than against overlapping 40dp buttons.
                        fun toArt(point: Offset): HandPoint {
                            val x = point.x * HandFingerGeometry.WIDTH / size.width.coerceAtLeast(1)
                            val y = point.y * HandFingerGeometry.HEIGHT / size.height.coerceAtLeast(1)
                            return HandPoint(if (rightHand) HandFingerGeometry.WIDTH - x else x, y)
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val active = HandFingerGeometry.hitFinger(toArt(down.position), tips.toList())
                            var previous = down.position
                            var isDragging = false
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (change.changedToUpIgnoreConsumed()) {
                                    if (!isDragging && active != null) {
                                        chooseSlot(ItemCatalogCategories.fingerSlot(rightHand, active))
                                    }
                                    break
                                }
                                if (!change.pressed) break
                                val travelled = (change.position - down.position).getDistance()
                                if (active != null && travelled > viewConfiguration.touchSlop) isDragging = true
                                if (active != null && isDragging) {
                                    val difference = change.position - previous
                                    val dir = if (rightHand) -1f else 1f
                                    val dx = dir * difference.x * HandFingerGeometry.WIDTH / size.width.coerceAtLeast(1)
                                    val dy = difference.y * HandFingerGeometry.HEIGHT / size.height.coerceAtLeast(1)
                                    tips[active] = HandFingerGeometry.restrictTip(
                                        active, tips[active] + HandPoint(dx, dy)
                                    )
                                    change.consume()
                                }
                                previous = change.position
                            }
                        }
                    }
            ) {
                val sx = size.width / HandFingerGeometry.WIDTH
                val sy = size.height / HandFingerGeometry.HEIGHT
                // Reflect drawn coordinates and hit testing consistently for the right hand.
                fun mirror(p: HandPoint): Offset = Offset(
                    if (rightHand) HandFingerGeometry.WIDTH - p.x else p.x, p.y
                )
                fun mirrorRectX(x: Float, width: Float) = if (rightHand) HandFingerGeometry.WIDTH - x - width else x

                withTransform({ scale(scaleX = sx, scaleY = sy, pivot = Offset.Zero) }) {
                    // Each finger has its own fixed root and independently dragged tip.
                    // Rings are rendered AFTER the fingers using the identical geometry.
                    for (index in 0..4) {
                        val base = mirror(HandFingerGeometry.roots[index])
                        val tip = mirror(tips[index])
                        val width = if (index == 0) 35f else 29f
                        drawLine(strokeColor, base, tip, width, cap = StrokeCap.Round)
                        drawLine(fillColor, base, tip, width - 5f, cap = StrokeCap.Round)
                        drawCircle(fingerNailColor, 4.1f, tip)
                    }
                    // Palm and wrist cover finger bases, without hiding the ring markings.
                    drawRoundRect(strokeColor, Offset(mirrorRectX(79f, 118f), 113f), Size(118f, 107f), CornerRadius(27f))
                    drawRoundRect(fillColor, Offset(mirrorRectX(83f, 110f), 116f), Size(110f, 99f), CornerRadius(24f))
                    drawRoundRect(strokeColor, Offset(mirrorRectX(106f, 65f), 192f), Size(65f, 50f), CornerRadius(12f))
                    drawRoundRect(fillColor, Offset(mirrorRectX(110f, 57f), 192f), Size(57f, 46f), CornerRadius(11f))

                    for (index in 0..4) {
                        val slot = ItemCatalogCategories.fingerSlot(rightHand, index)
                        val ring = inventory.firstOrNull { it.id == equipment[slot] }
                        val ringAt = mirror(HandFingerGeometry.marker(index, tips[index]))
                        // A ring moves with the finger because it is drawn from its
                        // current root-to-tip vector, not a fixed screen coordinate.
                        val base = mirror(HandFingerGeometry.roots[index])
                        val tip = mirror(tips[index])
                        val length = hypot(tip.x - base.x, tip.y - base.y).coerceAtLeast(1f)
                        val nx = -(tip.y - base.y) / length
                        val ny = (tip.x - base.x) / length
                        if (ring != null) {
                            val ringHalfWidth = 15f
                            drawLine(
                                ringColor,
                                Offset(ringAt.x - nx * ringHalfWidth, ringAt.y - ny * ringHalfWidth),
                                Offset(ringAt.x + nx * ringHalfWidth, ringAt.y + ny * ringHalfWidth),
                                strokeWidth = 7f,
                                cap = StrokeCap.Round
                            )
                            drawCircle(fillColor, radius = 4.5f, center = ringAt)
                            drawCircle(ringColor, radius = 4.5f, center = ringAt, style = Stroke(width = 2f))
                        } else {
                            drawCircle(emptyRingColor.copy(alpha = .85f), 7.5f, ringAt, style = Stroke(width = 2.2f))
                            drawCircle(fillColor, 3.0f, ringAt)
                        }
                    }
                }
            }
        }

        if (moved) {
            TextButton(onClick = {
                tips.indices.forEach { tips[it] = HandFingerGeometry.restingTips[it] }
            }) { Text("Вернуть положение пальцев") }
        }
        val rings = slots.mapNotNull { slot ->
            inventory.firstOrNull { it.id == equipment[slot] }?.let { item ->
                ItemCatalogCategories.fingerLabel(slot) + ": " + item.name
            }
        }
        if (rings.isNotEmpty()) {
            Text(rings.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}
