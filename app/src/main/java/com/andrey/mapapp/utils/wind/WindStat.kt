package com.andrey.mapapp.utils.wind

data class WindStat(
    val directionIndex: Int,    // 0..7 from north to west-north по часовой
    val frequency: Double,      // frequency in %
    val avgSpeed: Double         // average speed in km/h
)