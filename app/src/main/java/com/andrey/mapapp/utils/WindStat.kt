package com.andrey.mapapp.utils

data class WindStat(
    val directionIndex: Int,    // 0..7
    val frequency: Double,      // frequency in %
    val avgSpeed: Double         // average speed in km/h
)
