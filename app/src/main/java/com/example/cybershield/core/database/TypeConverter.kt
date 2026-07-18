package com.example.cybershield.core.database

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json

    @TypeConverter
    fun fromList(list: List<String>): String = json.encodeToString(list)

    @TypeConverter
    fun toList(value: String): List<String> =
        if (value.isBlank()) emptyList()
        else json.decodeFromString(value)
}
