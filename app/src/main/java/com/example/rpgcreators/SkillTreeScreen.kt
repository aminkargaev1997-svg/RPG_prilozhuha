package com.example.rpgcreators

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.hypot

/** Scalable independent Android Canvas tree. Learning never modifies base attributes. */
@Composable
fun SkillTreeScreen(
    character: Character,
    onBack: () -> Unit,
    editable: Boolean = true,
    asMaster: Boolean = false,
    onUpdated: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val nodes = remember(character.race, character.profession) { SkillTreeEngine.nodes(character) }
    var state by remember(character.id) { mutableStateOf(SkillTreeStorage.load(context, character.id)) }
    var selected by remember { mutableStateOf<TalentNode?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(0.58f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val available = SkillTreeEngine.totalPoints(character, state) - SkillTreeEngine.spent(nodes, state)
    val accent = Color(0xFFF2C777)
    val center = Offset(size.width / 2f, size.height / 2f) + pan
    fun save(next: TalentState) { state = next; SkillTreeStorage.save(context, character.id, next); onUpdated?.invoke() }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF0D1524))) {
        val landscape = maxWidth > maxHeight
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Назад", color = accent) }
                Column(Modifier.weight(1f)) {
                    Text("Древо навыков", color = accent, fontWeight = FontWeight.Bold)
                    Text("${character.race} · ${character.profession} · Ур. ${character.level}/20", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                }
                Text("Очков: $available", color = accent)
                TextButton(onClick = { pan = Offset.Zero; scale = 0.58f }) { Text("Центр", color = accent) }
                if (asMaster) TextButton(onClick = { confirmReset = true }) { Text("Сброс", color = accent) }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Canvas(Modifier.fillMaxSize().onSizeChanged { size = it }
                    .pointerInput(character.id, size, nodes, scale, pan) {
                        detectTapGestures { tap ->
                            val hit = nodes.asReversed().firstOrNull { node ->
                                val pos = center + Offset(node.x * scale, node.y * scale)
                                hypot((pos.x - tap.x).toDouble(), (pos.y - tap.y).toDouble()) < (if (node.id == "root") 27f else 18f) * scale.coerceAtLeast(0.65f)
                            }
                            if (hit != null && hit.id != "root") selected = hit
                        }
                    }
                    .pointerInput(character.id) {
                        detectTransformGestures { centroid, movement, zoom, _ ->
                            val next = (scale * zoom).coerceIn(0.24f, 2.3f)
                            pan += movement + (centroid - Offset(size.width / 2f, size.height / 2f) - pan) * (1f - next / scale)
                            scale = next
                        }
                    }) {
                    drawRect(Color(0xFF0F1929))
                    val coordinates = nodes.associate { it.id to center + Offset(it.x * scale, it.y * scale) }
                    nodes.filter { it.parentId != null }.forEach { node ->
                        val parent = coordinates[node.parentId] ?: return@forEach
                        val destination = coordinates[node.id] ?: return@forEach
                        drawLine(if (node.id in state.unlocked) accent else Color(0xFF3D5167), parent, destination,
                            strokeWidth = if (node.id in state.unlocked) 3.5f else 1.8f, cap = StrokeCap.Round)
                    }
                    nodes.forEach { node ->
                        val position = coordinates[node.id] ?: return@forEach
                        val unlocked = node.id == "root" || node.id in state.unlocked
                        val eligible = SkillTreeEngine.canLearn(character, state, node, nodes)
                        val color = when {
                            unlocked -> accent
                            eligible -> Color(0xFF72C6BA)
                            node.tier == TalentTier.LEGENDARY -> Color(0xFF9A669F)
                            else -> Color(0xFF66768A)
                        }
                        val radius = (if (node.id == "root") 19f else when(node.tier) {
                            TalentTier.SMALL -> 7f; TalentTier.NOTABLE -> 10f; TalentTier.KEYSTONE -> 13f; TalentTier.LEGENDARY -> 17f
                        }) * scale.coerceAtLeast(0.72f)
                        if (unlocked || eligible) drawCircle(color.copy(alpha = 0.12f), radius * 1.6f, position)
                        drawCircle(Color(0xFF172536), radius, position)
                        drawCircle(color, radius, position, style = Stroke(if (unlocked) 3f else 2f))
                        if (unlocked) drawCircle(color, radius * 0.3f, position)
                    }
                }
                Text("Сдвигайте карту пальцем · Два пальца — масштаб", color = Color.LightGray,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(7.dp))
            }
        }
        selected?.let { node ->
            val unlocked = node.id in state.unlocked
            val canLearn = editable && SkillTreeEngine.canLearn(character, state, node, nodes)
            AlertDialog(
                onDismissRequest = { selected = null },
                title = { Text(node.title) },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(node.branch); Text(node.description)
                    Text("${node.tier.title} · ${node.tier.cost} очк. · с ${node.tier.minLevel} уровня")
                    if (unlocked) Text("Изучен", color = MaterialTheme.colorScheme.primary)
                    if (!editable) Text("Изучение отключено мастером для этой кампании")
                    if (!unlocked && character.level < node.tier.minLevel) Text("Требуется уровень ${node.tier.minLevel}")
                } },
                confirmButton = {
                    TextButton(enabled = canLearn, onClick = { save(SkillTreeEngine.learn(character, state, node, nodes)); selected = null }) {
                        Text(if (unlocked) "Изучено" else "Изучить")
                    }
                },
                dismissButton = { TextButton(onClick = { selected = null }) { Text("Закрыть") } }
            )
        }
        if (confirmReset && asMaster) AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Сбросить древо?") },
            text = { Text("Все изученные навыки будут сняты, потраченные очки вернутся.") },
            confirmButton = { TextButton(onClick = { save(SkillTreeEngine.reset(state)); confirmReset = false }) { Text("Сбросить") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Отмена") } }
        )
    }
}
