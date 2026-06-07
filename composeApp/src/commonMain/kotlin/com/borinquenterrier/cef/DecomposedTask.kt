package com.borinquenterrier.cef

import kotlinx.serialization.Serializable

@Serializable
data class DecomposedTask(
    val title: String,
    val daysBeforeDue: Int,
    val description: String
)
