package com.example

data class ActiveAppTimer(
    val packageName: String,
    val secondsLeft: Int,
    val totalSecondsMax: Int
)
