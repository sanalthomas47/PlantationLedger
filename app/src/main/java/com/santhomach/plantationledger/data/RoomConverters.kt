package com.santhomach.plantationledger.data

import androidx.room.TypeConverter
import java.math.BigDecimal

/**
 * Room type converters. Money values are stored as TEXT to avoid floating point errors.
 */
class RoomConverters {

    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? {
        if (value == null) return null
        return try {
            BigDecimal(value)
        } catch (e: NumberFormatException) {
            BigDecimal.ZERO
        }
    }
}
