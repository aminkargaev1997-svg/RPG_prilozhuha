package com.example.rpgcreators

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.UUID

/** Editor lives in a full-size, scrolling dialog. No extra controls on the map. */
@Composable
internal fun AdventureObjectEditor(
    marker: LanMarker,
    mapId: String,
    maps: List<LanRoomMapInfo>,
    current: AdventureObject?,
    doors: List<AdventureObject>,
    onClose: () -> Unit,
    onSave: (AdventureObject) -> Unit
) {
    var locked by remember(marker.id) { mutableStateOf(current?.locked ?: false) }
    var target by remember(marker.id) { mutableStateOf(current?.targetMapId.orEmpty()) }
    var houseMode by remember(marker.id) { mutableStateOf(current?.houseMode ?: "map") }
    var x by remember(marker.id) { mutableStateOf(current?.x ?: .5f) }
    var y by remember(marker.id) { mutableStateOf(current?.y ?: .5f) }
    val items = remember(marker.id) { mutableStateListOf<AdventureItem>().apply { addAll(current?.items.orEmpty()) } }
    var itemName by remember(marker.id) { mutableStateOf("") }
    var itemDescription by remember(marker.id) { mutableStateOf("") }
    var itemCategory by remember(marker.id) { mutableStateOf("Прочее") }
    var itemQuantity by remember(marker.id) { mutableStateOf("1") }
    var itemPrice by remember(marker.id) { mutableStateOf("10") }
    var itemStock by remember(marker.id) { mutableStateOf("1") }
    var itemKeyFor by remember(marker.id) { mutableStateOf("") }
    var destinationMenu by remember(marker.id) { mutableStateOf(false) }
    var chestKeyMenu by remember(marker.id) { mutableStateOf(false) }
    var showItemCatalog by remember(marker.id) { mutableStateOf(false) }
    val isShop = marker.type == "Магазин" || (marker.type == "Дом" && houseMode == "shop")
    val carriesItems = isShop || marker.type == "Сундук"
    val canSave = !isShop || items.all { it.price > 0 && it.stock in 0..999 }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.96f).fillMaxHeight(.94f), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Настроить: ${marker.name}", Modifier.weight(1f), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onClose) { Text("Закрыть") }
                }
                HorizontalDivider()
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (marker.type == "Дом") {
                        Text("Что находится в доме?", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = houseMode == "map", onClick = { houseMode = "map" },
                                label = { Text("Переход на карту") })
                            FilterChip(selected = houseMode == "shop", onClick = { houseMode = "shop" },
                                label = { Text("Магазин") })
                        }
                        Text("Для простого магазина отдельная карта не нужна.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (marker.type != "Портал") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Заперто (нужен ключ)", Modifier.weight(1f))
                            Switch(checked = locked, onCheckedChange = { locked = it })
                        }
                        if (locked) Text("ID замка: ${marker.id}. Ключ создаётся в сундуке через базу предметов.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (marker.type in setOf("Дверь", "Дом", "Портал", "Магазин") && !isShop) {
                        Text("Куда ведёт вход", fontWeight = FontWeight.Bold)
                        Box {
                            OutlinedButton(onClick = { destinationMenu = true }) {
                                Text(maps.firstOrNull { it.id == target }?.name ?: "Выбрать карту")
                            }
                            DropdownMenu(expanded = destinationMenu, onDismissRequest = { destinationMenu = false }) {
                                DropdownMenuItem(text = { Text("Без перехода") }, onClick = { target = ""; destinationMenu = false })
                                maps.filter { it.id != mapId }.forEach { m -> DropdownMenuItem(
                                    text = { Text(m.name) }, onClick = { target = m.id; destinationMenu = false }) }
                            }
                        }
                        Text("Точка появления (доля ширины и высоты карты):",
                            style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(x.toString(), { x = it.toFloatOrNull()?.coerceIn(0f,1f) ?: x },
                                label = { Text("X: 0–1") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(y.toString(), { y = it.toFloatOrNull()?.coerceIn(0f,1f) ?: y },
                                label = { Text("Y: 0–1") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                    }
                    if (carriesItems) {
                        HorizontalDivider()
                        Text(if (isShop) "Товары магазина" else "Содержимое сундука", fontWeight = FontWeight.Bold)
                        if (items.isEmpty()) Text("Пока пусто", style = MaterialTheme.typography.bodySmall)
                        items.forEachIndexed { index, item ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth().padding(9.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(item.name, maxLines = 2, fontWeight = FontWeight.SemiBold)
                                            Text(item.category, style = MaterialTheme.typography.bodySmall)
                                        }
                                        TextButton(onClick = { items.remove(item) }) { Text("Удалить") }
                                    }
                                    if (isShop) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(item.price.toString(), { value ->
                                                value.toIntOrNull()?.takeIf { it in 0..1_000_000 }?.let { amount ->
                                                    items[index] = item.copy(price = amount)
                                                }
                                            }, label = { Text("Цена, монет") },
                                                singleLine = true, modifier = Modifier.weight(1f))
                                            OutlinedTextField(item.stock.toString(), { value ->
                                                value.toIntOrNull()?.takeIf { it in 0..999 }?.let { amount ->
                                                    items[index] = item.copy(stock = amount)
                                                }
                                            }, label = { Text("В наличии") },
                                                singleLine = true, modifier = Modifier.weight(1f))
                                        }
                                    } else if (item.keyFor.isNotBlank()) {
                                        Text("Ключ от: ${item.keyFor.take(12)}…", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                        OutlinedButton(onClick = { showItemCatalog = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("📚 Выбрать из базы предметов")
                        }
                        OutlinedTextField(itemName, { itemName = it.take(100) },
                            label = { Text("Название предмета") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(itemDescription, { itemDescription = it.take(500) },
                            label = { Text("Описание") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(itemCategory, { itemCategory = it.take(60) },
                                label = { Text("Категория") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(itemQuantity, { itemQuantity = it.take(20) },
                                label = { Text("Кол-во в предмете") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                        if (isShop) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(itemPrice, { itemPrice = it.filter(Char::isDigit).take(7) },
                                    label = { Text("Цена за товар") }, modifier = Modifier.weight(1f), singleLine = true)
                                OutlinedTextField(itemStock, { itemStock = it.filter(Char::isDigit).take(3) },
                                    label = { Text("Сколько в продаже") }, modifier = Modifier.weight(1f), singleLine = true)
                            }
                        } else {
                            Box {
                                OutlinedButton(onClick = { chestKeyMenu = true }) {
                                    Text(doors.firstOrNull { it.markerId == itemKeyFor }?.let { "Ключ к: ${it.markerId.take(8)}" }
                                        ?: "Не ключ")
                                }
                                DropdownMenu(expanded = chestKeyMenu, onDismissRequest = { chestKeyMenu = false }) {
                                    DropdownMenuItem(text = { Text("Не ключ") }, onClick = { itemKeyFor = ""; chestKeyMenu = false })
                                    doors.forEach { door -> DropdownMenuItem(text = { Text("${door.kind}: ${door.markerId.take(8)}") },
                                        onClick = { itemKeyFor = door.markerId; chestKeyMenu = false }) }
                                }
                            }
                        }
                        val price = itemPrice.toIntOrNull() ?: 0
                        val stock = itemStock.toIntOrNull() ?: 0
                        Button(onClick = {
                            items.add(AdventureItem(name = itemName.trim(), description = itemDescription.trim(),
                                category = itemCategory.ifBlank { "Прочее" }, quantity = itemQuantity.ifBlank { "1" },
                                keyFor = if (isShop) "" else itemKeyFor,
                                price = if (isShop) price else 0, stock = if (isShop) stock else 1))
                            itemName = ""; itemDescription = ""; itemKeyFor = ""; itemQuantity = "1"
                        }, enabled = itemName.isNotBlank() && items.size < 100 &&
                            (!isShop || (price in 1..1_000_000 && stock in 1..999)),
                            modifier = Modifier.fillMaxWidth()) {
                            Text(if (isShop) "Добавить товар" else "Положить в сундук")
                        }
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text("Отмена") }
                    Button(onClick = {
                        onSave(AdventureObject(markerId = marker.id, mapId = mapId, kind = marker.type,
                            locked = locked && marker.type != "Портал",
                            keyId = if (locked && marker.type != "Портал") marker.id else "",
                            targetMapId = if (isShop) "" else target, x = x, y = y,
                            items = if (carriesItems) items.toList() else emptyList(),
                            opened = current?.opened ?: false, houseMode = if (isShop) "shop" else "map"))
                    }, enabled = canSave) { Text("Сохранить") }
                }
            }
        }
    }
    if (showItemCatalog) RpgCatalogPickerDialog(
        title = if (isShop) "Товар из базы" else "Предмет из базы",
        entries = RpgEquipmentAbilitiesCatalog.items,
        onSelect = { picked ->
            itemName = picked.name; itemDescription = picked.description
            itemCategory = picked.group.ifBlank { "Прочее" }
            showItemCatalog = false
        },
        onDismiss = { showItemCatalog = false }
    )
}

@Composable
internal fun AdventureChestDialog(
    title: String,
    items: List<AdventureItem>,
    onTake: (String) -> Unit,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(12.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📦 $title", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onClose) { Text("Закрыть") }
                }
                Text("Предметы видны только тебе. Забранное исчезнет из сундука у всех.")
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (items.isEmpty()) Text("В сундуке пока ничего нет")
                    items.forEach { item -> Card {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.Medium)
                                Text(item.description.ifBlank { item.category }, style = MaterialTheme.typography.bodySmall)
                            }
                            Button(onClick = { onTake(item.id) }) { Text("Забрать") }
                        }
                    } }
                }
            }
        }
    }
}

@Composable
internal fun AdventureTradeDialog(
    myId: String,
    targetName: String,
    myInventory: List<InventoryItem>,
    trade: AdventureTrade?,
    onPropose: (List<Long>) -> Unit,
    onCounter: (List<Long>) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val selected = remember(trade?.id) { mutableStateListOf<Long>() }
    val isProposer = trade?.proposer == myId
    val isRecipient = trade?.recipient == myId
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(12.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⇄ Обмен предметами", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onCancel) { Text("Закрыть") }
                }
                Text(if (trade == null) "Предложение для $targetName" else
                    "Предложение: ${trade.proposer.take(8)} → ${trade.recipient.take(8)}")
                if (trade != null) {
                    Text("Предлагают: ${trade.proposerNames.joinToString().ifBlank { "ничего" }}")
                    Text("В ответ: ${trade.recipientNames.joinToString().ifBlank { "ничего" }}")
                    Text("Обе стороны должны подтвердить окончательное предложение")
                }
                Text("Выбери свои предметы для обмена:")
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    myInventory.filterNot(CharacterStorage::isBoundItem).forEach { item ->
                        Row(Modifier.fillMaxWidth().clickable {
                            if (item.id in selected) selected.remove(item.id) else selected.add(item.id)
                        }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = item.id in selected, onCheckedChange = { flag ->
                                if (flag && item.id !in selected) selected.add(item.id)
                                else if (!flag) selected.remove(item.id)
                            })
                            Column {
                                Text(item.name)
                                Text(item.category, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (trade == null) Button(onClick = { onPropose(selected.toList()) }, enabled = selected.isNotEmpty()) {
                    Text("Отправить предложение")
                } else {
                    if (isRecipient) OutlinedButton(onClick = { onCounter(selected.toList()) }) {
                        Text("Предложить свои предметы")
                    }
                    Button(onClick = onConfirm, enabled = !((isProposer && trade.proposerConfirmed) ||
                        (isRecipient && trade.recipientConfirmed))) {
                        Text(if ((isProposer && trade.proposerConfirmed) || (isRecipient && trade.recipientConfirmed))
                            "Подтверждено" else "Подтвердить обмен")
                    }
                    Text("Обмен завершится после подтверждения обеими сторонами.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
internal fun AdventureHeroStoryDialog(
    playerName: String,
    onClose: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var goodName by remember(playerName) { mutableStateOf("") }
    var goodEffect by remember(playerName) { mutableStateOf("") }
    var badName by remember(playerName) { mutableStateOf("") }
    var badEffect by remember(playerName) { mutableStateOf("") }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(12.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("История героя: $playerName", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onClose) { Text("Закрыть") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("По рассказу игрока придумай дар и недостаток. Это пассивные способности; удалить или изменить их сможет только мастер.",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("✨ Дар героя", fontWeight = FontWeight.Bold)
                    OutlinedTextField(goodName, { goodName = it.take(100) },
                        label = { Text("Название дара") }, placeholder = { Text("Например: Острый слух") },
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(goodEffect, { goodEffect = it.take(500) },
                        label = { Text("Что даёт персонажу этот дар?") },
                        placeholder = { Text("Опиши положительный пассивный эффект") },
                        minLines = 2, modifier = Modifier.fillMaxWidth())
                    HorizontalDivider()
                    Text("⚠ Недостаток героя", fontWeight = FontWeight.Bold)
                    OutlinedTextField(badName, { badName = it.take(100) },
                        label = { Text("Название недостатка") }, placeholder = { Text("Например: Недоверие к дворфам") },
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(badEffect, { badEffect = it.take(500) },
                        label = { Text("Как этот недостаток мешает герою?") },
                        placeholder = { Text("Опиши отрицательный пассивный эффект") },
                        minLines = 2, modifier = Modifier.fillMaxWidth())
                    Text("При повторном рассказе предыдущие две особенности истории заменятся новыми.",
                        style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { onSave(goodName, goodEffect, badName, badEffect) },
                    enabled = listOf(goodName, goodEffect, badName, badEffect).all { it.isNotBlank() }) {
                    Text("Сохранить дар и недостаток")
                }
            }
        }
    }
}

@Composable
internal fun AdventureVoteDialog(
    name: String,
    vote: AdventureVote,
    myId: String,
    scrollRemaining: Int,
    showScroll: Boolean,
    onYes: () -> Unit,
    onNo: () -> Unit,
    onScroll: () -> Unit
) {
    AlertDialog(onDismissRequest = {}, title = { Text("Групповой переход") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Переход через «$name». Все участники должны согласиться.")
            Text("За: ${vote.yes.size}/${vote.voters.size}; против: ${vote.no.size}")
            if (showScroll && myId in vote.yes && myId !in vote.remoteNeedsScroll) {
                Text("Если ты далеко, можно применить свиток: осталось $scrollRemaining из 2 на день.")
                TextButton(enabled = scrollRemaining > 0, onClick = onScroll) { Text("Использовать свиток") }
            }
        }
    }, confirmButton = {
        if (myId !in vote.yes && myId !in vote.no) TextButton(onClick = onYes) { Text("За") }
    }, dismissButton = {
        if (myId !in vote.yes && myId !in vote.no) TextButton(onClick = onNo) { Text("Против") }
    })
}


/** Private shopping window: catalog on the left, only a basket on the right. */
@Composable
internal fun AdventureShopDialog(
    title: String,
    goods: List<AdventureItem>,
    balance: Int,
    waitingDelivery: Boolean,
    onBuy: (Map<String, Int>) -> Unit,
    onClose: () -> Unit
) {
    val cart = remember(title) { mutableStateMapOf<String, Int>() }
    var filter by remember(title) { mutableStateOf("") }
    val available = goods.filter { it.name.contains(filter, ignoreCase = true) ||
        it.category.contains(filter, ignoreCase = true) }
    // Reconcile a stale basket after other players buy the last available item.
    val chosen = goods.mapNotNull { item ->
        val amount = (cart[item.id] ?: 0).coerceAtMost(item.stock)
        if (amount > 0) item to amount else null
    }
    val total = chosen.sumOf { (item, amount) -> item.price.toLong() * amount }
    val canBuy = total > 0L && total <= balance && !waitingDelivery &&
        chosen.size <= 30 && chosen.sumOf { it.second } <= 100
    var isSubmitting by remember(title) { mutableStateOf(false) }
    var previousBalance by remember(title) { mutableStateOf(balance) }
    LaunchedEffect(balance, goods.map { it.id to it.stock }, waitingDelivery) {
        // After the host charges the wallet, the purchased basket must not stay selected.
        if (balance < previousBalance) cart.clear()
        previousBalance = balance
        isSubmitting = false
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.98f).fillMaxHeight(.95f), shape = RoundedCornerShape(18.dp)) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp)) {
                val wide = maxWidth >= 620.dp && maxWidth > maxHeight
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("🏪 $title", Modifier.weight(1f), maxLines = 1,
                            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text("🪙 $balance", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = onClose) { Text("Закрыть") }
                    }
                    HorizontalDivider()
                    @Composable fun GoodsList(modifier: Modifier) {
                        Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            OutlinedTextField(filter, { filter = it.take(80) }, modifier = Modifier.fillMaxWidth(),
                                singleLine = true, label = { Text("Поиск товара") })
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(available, key = { it.id }) { item ->
                                    Card(Modifier.fillMaxWidth()) {
                                        Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                                Text("${item.price} монет · ${item.stock} шт. в наличии",
                                                    style = MaterialTheme.typography.bodySmall)
                                            }
                                            IconButton(onClick = {
                                                val old = cart[item.id] ?: 0
                                                if (old <= 1) cart.remove(item.id) else cart[item.id] = old - 1
                                            }, enabled = (cart[item.id] ?: 0) > 0) { Text("−") }
                                            Text("${(cart[item.id] ?: 0).coerceAtMost(item.stock)}")
                                            IconButton(onClick = { cart[item.id] = (cart[item.id] ?: 0) + 1 },
                                                enabled = (cart[item.id] ?: 0) < item.stock && item.price > 0) { Text("+") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    @Composable fun Basket(modifier: Modifier) {
                        Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("Корзина", fontWeight = FontWeight.Bold)
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (chosen.isEmpty()) Text("Пока ничего не выбрано", style = MaterialTheme.typography.bodySmall)
                                chosen.forEach { (item, count) ->
                                    Text("${item.name} ×$count — ${item.price.toLong() * count} монет",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            HorizontalDivider()
                            Text("На счету: $balance монет")
                            Text("Сумма: $total монет", fontWeight = FontWeight.Bold)
                            Text(if (total <= balance) "Останется: ${balance - total} монет"
                                else "Не хватает: ${total - balance} монет",
                                color = if (total > balance) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface)
                            if (waitingDelivery) Text("Предыдущая покупка доставляется…",
                                style = MaterialTheme.typography.bodySmall)
                            Button(onClick = {
                                isSubmitting = true
                                onBuy(chosen.associate { (item,count) -> item.id to count })
                            }, enabled = canBuy && !isSubmitting, modifier = Modifier.fillMaxWidth()) {
                                Text("Купить")
                            }
                        }
                    }
                    if (wide) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GoodsList(Modifier.weight(1.6f).fillMaxHeight())
                        VerticalDivider()
                        Basket(Modifier.weight(1f).fillMaxHeight())
                    } else {
                        GoodsList(Modifier.weight(1f))
                        HorizontalDivider()
                        Basket(Modifier.weight(.65f))
                    }
                }
            }
        }
    }
}

/** Only the master can adjust currency; this dialog is opened from the "World" tab. */
@Composable
internal fun AdventureWalletEditor(
    players: List<Triple<String, String, String>>,
    balances: Map<String, Int>,
    onSave: (String, Int) -> Unit,
    onClose: () -> Unit
) {
    var chosen by remember { mutableStateOf("") }
    var entered by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.90f).fillMaxHeight(.80f), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🪙 Монеты игроков", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onClose) { Text("Закрыть") }
                }
                Text("Баланс кампании изменяет только мастер. Выбери игрока и установи сумму.",
                    style = MaterialTheme.typography.bodySmall)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    players.forEach { (id,name,characterId) ->
                        val amount = balances[characterId] ?: 0
                        OutlinedButton(onClick = { chosen = id; entered = amount.toString() },
                            modifier = Modifier.fillMaxWidth()) {
                            Text("$name · $amount монет", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (players.isEmpty()) Text("Сначала подключи персонажа к комнате")
                }
                if (chosen.isNotBlank()) {
                    OutlinedTextField(entered, { entered = it.filter(Char::isDigit).take(10) },
                        label = { Text("Монеты персонажа") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = {
                        val id = chosen
                        val amount = entered.toIntOrNull()
                        if (amount != null) { onSave(id, amount); chosen = "" }
                    }, enabled = (entered.toIntOrNull() ?: -1) in 0..1_000_000_000,
                        modifier = Modifier.fillMaxWidth()) { Text("Установить баланс") }
                }
            }
        }
    }
}
