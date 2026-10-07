package com.babakriazi.nava.data

import java.util.UUID

data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val songIds: MutableList<Long> = mutableListOf()
)
