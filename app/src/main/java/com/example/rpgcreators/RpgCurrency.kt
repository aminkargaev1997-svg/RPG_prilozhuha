package com.example.rpgcreators

/** Currency math is independent from Compose and from the network protocol.
 * D&D convention: one gold = ten silver = one hundred copper.
 */
internal object RpgCurrency {
    const val MAX_COPPER = 1_000_000_000_000L
    fun denomination(name: String): Int = when (name.trim().lowercase(java.util.Locale.ROOT)) {
        "золотая монета", "золотые монеты", "золотых монет", "золото", "золотых", "золотые" -> 100
        "серебряная монета", "серебряные монеты", "серебряных монет", "серебро", "серебряных", "серебряные" -> 10
        "медная монета", "медные монеты", "медных монет", "медь", "медных", "медные" -> 1
        else -> 0
    }
    fun display(copper: Long): String {
        val safe = copper.coerceAtLeast(0L)
        return "${safe / 100} зол. · ${(safe % 100) / 10} сер. · ${safe % 10} мед."
    }
    fun newBalance(current: Long, denomination: Int, quantity: Long, add: Boolean): Long? {
        if (quantity !in 1..1_000_000 || denomination !in setOf(1,10,100)) return null
        val change = denomination.toLong() * quantity
        val result = if (add) current + change else current - change
        return result.takeIf { it in 0..MAX_COPPER }
    }
}
