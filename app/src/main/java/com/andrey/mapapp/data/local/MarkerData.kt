package com.andrey.mapapp.data.local

import com.andrey.mapapp.data.local.enums.MarkerType

// this class holds the related object data for markers on the map
data class MarkerData (
    val id: Int?,
    val type: MarkerType
)