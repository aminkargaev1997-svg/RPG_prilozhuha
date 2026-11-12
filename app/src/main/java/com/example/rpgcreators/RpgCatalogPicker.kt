package com.example.rpgcreators

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Адаптивный каталог: в альбомной ориентации — двухпанельный, в портретной — компактный.
 * В отличие от AlertDialog не зажимает длинный список в фиксированные 330 dp.
 * Подходит для рас, профессий и меток без изменения вызывающих экранов.
 */
@Composable
fun RpgCatalogPickerDialog(
    title: String,
    entries: List<CatalogEntry>,
    onSelect: (CatalogEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val markerCatalog = entries === RpgCatalog.markerPresets
    var query by remember(title) { mutableStateOf("") }
    var group by remember(title) { mutableStateOf("Все") }
    var genre by remember(title) { mutableStateOf("Все жанры") }
    var groupMenuExpanded by remember { mutableStateOf(false) }
    var genreMenuExpanded by remember { mutableStateOf(false) }

    val groups = remember(entries) {
        listOf("Все") + entries.map(CatalogEntry::group).distinct().sorted()
    }
    val genres = remember(entries) {
        listOf("Все жанры") + entries.map(CatalogEntry::genre)
            .filter { it.isNotBlank() }.distinct().sorted()
    }
    val found = remember(entries, query, group, genre) {
        val term = query.trim()
        entries.filter { entry ->
            (group == "Все" || entry.group == group) &&
                    (genre == "Все жанры" || entry.genre == genre) &&
                    (term.isEmpty() || entry.name.contains(term, ignoreCase = true) ||
                            entry.description.contains(term, ignoreCase = true) ||
                            entry.group.contains(term, ignoreCase = true) ||
                            entry.genre.contains(term, ignoreCase = true))
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.94f)
        ) {
            val isWide = maxWidth > maxHeight && maxWidth >= 570.dp
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismiss) { Text("Закрыть") }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it.take(80) },
                            placeholder = { Text("Поиск по названию или описанию") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        if (genres.size > 2) {
                            Box {
                                OutlinedButton(onClick = { genreMenuExpanded = true }) {
                                    Text(
                                        text = if (genre == "Все жанры") "Жанры ▾" else "$genre ▾",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.width(if (isWide) 130.dp else 74.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = genreMenuExpanded,
                                    onDismissRequest = { genreMenuExpanded = false }
                                ) {
                                    genres.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option) },
                                            onClick = { genre = option; genreMenuExpanded = false }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.width(178.dp).fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text("Категории", style = MaterialTheme.typography.labelMedium)
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(groups, key = { it }) { item ->
                                        CatalogGroupItem(
                                            name = item,
                                            selected = group == item,
                                            onClick = { group = item }
                                        )
                                    }
                                }
                            }
                            CatalogResultList(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                entries = found,
                                markerCatalog = markerCatalog,
                                showGenre = genres.size > 2,
                                stateKey = "$group/$genre/$query",
                                onSelect = onSelect
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box {
                                OutlinedButton(onClick = { groupMenuExpanded = true }) {
                                    Text(
                                        text = if (group == "Все") "Все категории ▾" else "$group ▾",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                DropdownMenu(
                                    expanded = groupMenuExpanded,
                                    onDismissRequest = { groupMenuExpanded = false }
                                ) {
                                    groups.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option) },
                                            onClick = { group = option; groupMenuExpanded = false }
                                        )
                                    }
                                }
                            }
                            CatalogResultList(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                entries = found,
                                markerCatalog = markerCatalog,
                                showGenre = genres.size > 2,
                                stateKey = "$group/$genre/$query",
                                onSelect = onSelect
                            )
                        }
                    }
                    HorizontalDivider()
                    Text(
                        text = "Найдено: ${found.size} из ${entries.size} · Нажми на вариант для выбора",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogGroupItem(name: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
    val textColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier.fillMaxWidth().background(background, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick).padding(horizontal = 9.dp, vertical = 8.dp)
    ) {
        Text(
            text = name,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CatalogResultList(
    modifier: Modifier,
    entries: List<CatalogEntry>,
    markerCatalog: Boolean,
    showGenre: Boolean,
    stateKey: String,
    onSelect: (CatalogEntry) -> Unit
) {
    // key сбрасывает прокрутку при смене фильтра или поискового запроса.
    key(stateKey) {
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (entries.isEmpty()) {
                item {
                    Text(
                        "Ничего не найдено. Можно выбрать другой фильтр или вписать свой вариант вручную.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            items(entries, key = { "${it.name}|${it.genre}|${it.group}" }) { entry ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(entry) },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.47f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        if (markerCatalog) Text(
                            RpgCatalog.markerIcon(entry.markerType),
                            fontSize = 18.sp,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                entry.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                entry.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (showGenre) {
                                Text(
                                    entry.genre,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
