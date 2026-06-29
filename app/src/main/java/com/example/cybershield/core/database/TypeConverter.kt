package com.example.cybershield.core.database

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromList(list: List<String>): String = list.joinToString("||")

    @TypeConverter
    fun toList(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split("||")
}
