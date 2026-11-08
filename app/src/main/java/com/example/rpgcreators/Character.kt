package com.example.rpgcreators

data class Character(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val race: String,
    val profession: String
)
