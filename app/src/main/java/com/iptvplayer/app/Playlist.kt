package com.iptvplayer.app

const val USER_AGENT = "Mozilla/5.0 (Linux; Android) IPTVPlayer/1.0"

data class Channel(
    val name: String,
    val url: String,
    val logo: String?,
    val group: String
)

/** Lista atualmente exibida, usada pelo player para trocar de canal. */
object Playlist {
    var current: List<Channel> = emptyList()
}

object M3uParser {
    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(text: String): List<Channel> {
        val result = mutableListOf<Channel>()
        var name: String? = null
        var logo: String? = null
        var group = "Sem grupo"

        for (raw in text.lineSequence()) {
            val line = raw.removePrefix("\uFEFF").trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = attrRegex.findAll(line)
                        .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                    group = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: "Sem grupo"
                    val afterAttrs = line.substring(line.lastIndexOf('"') + 1)
                    name = afterAttrs.substringAfter(",", "").trim()
                        .ifBlank { attrs["tvg-name"] ?: "Canal" }
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    group = line.substringAfter(":").trim().ifBlank { group }
                }
                line.isEmpty() || line.startsWith("#") -> Unit
                else -> {
                    result.add(Channel(name ?: line.substringAfterLast("/"), line, logo, group))
                    name = null
                    logo = null
                    group = "Sem grupo"
                }
            }
        }
        return result
    }
}
