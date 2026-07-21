package com.defy.notivault.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "uber_calls")
data class UberCallEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactName: String,
    val keyword: String,
    val pickupAddress: String,
    val dropoffAddress: String,
    val calledAt: Long,
    val sourceTitle: String,
    val sourceContent: String
)
