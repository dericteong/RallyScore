package com.courtside.pickleball.player

import android.content.Context
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Player(
    val id: String,
    val name: String,
    val createdAt: Long,
    val lastPlayed: Long?
)

object PlayerRepository {
    private const val PREFS_NAME = "rallyscore_players"
    private const val KEY_PLAYER_IDS = "player_ids"
    private const val KEY_NAME_PREFIX = "player_name_"
    private const val KEY_CREATED_PREFIX = "player_created_"
    private const val KEY_LAST_PLAYED_PREFIX = "player_last_played_"
    private const val ID_SEPARATOR = "|"

    private val _players = MutableStateFlow<List<Player>>(emptyList())
    val players: StateFlow<List<Player>> = _players.asStateFlow()

    private var appContext: Context? = null
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        load()
    }

    fun addPlayer(name: String, now: Long = System.currentTimeMillis()): Player? =
        upsertPlayer(name = name, markPlayed = false, now = now)

    @Synchronized
    fun upsertPlayer(
        name: String,
        markPlayed: Boolean,
        now: Long = System.currentTimeMillis()
    ): Player? {
        val cleanName = name.cleanPlayerName()
        if (cleanName.isEmpty()) return null

        val existing = _players.value.firstOrNull { it.name.samePlayerName(cleanName) }
        val player = if (existing != null) {
            if (markPlayed) existing.copy(lastPlayed = now) else existing
        } else {
            Player(
                id = UUID.randomUUID().toString(),
                name = cleanName,
                createdAt = now,
                lastPlayed = if (markPlayed) now else null
            )
        }
        replacePlayer(player)
        return player
    }

    @Synchronized
    fun markPlayersPlayed(names: List<String>, now: Long = System.currentTimeMillis()) {
        names.forEach { upsertPlayer(name = it, markPlayed = true, now = now) }
    }

    @Synchronized
    fun renamePlayer(id: String, name: String) {
        val cleanName = name.cleanPlayerName()
        if (cleanName.isEmpty()) return
        val current = _players.value.firstOrNull { it.id == id } ?: return
        val duplicate = _players.value.firstOrNull {
            it.id != id && it.name.samePlayerName(cleanName)
        }
        if (duplicate != null) return
        val updated = current.copy(name = cleanName)
        replacePlayer(updated)
    }

    @Synchronized
    fun deletePlayer(id: String) {
        val next = _players.value.filterNot { it.id == id }.sortedForDisplay()
        _players.value = next
        persist(next)
    }

    @Synchronized
    private fun replacePlayer(player: Player) {
        val next = (_players.value.filterNot { it.id == player.id } + player).sortedForDisplay()
        _players.value = next
        persist(next)
    }

    private fun load() {
        val context = appContext ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val players = prefs.getString(KEY_PLAYER_IDS, null)
            ?.split(ID_SEPARATOR)
            .orEmpty()
            .filter { it.isNotBlank() }
            .mapNotNull { id ->
                val name = prefs.getString(KEY_NAME_PREFIX + id, null)?.cleanPlayerName().orEmpty()
                if (name.isEmpty()) {
                    null
                } else {
                    val createdAt = prefs.getLong(KEY_CREATED_PREFIX + id, 0L).takeIf { it > 0L }
                        ?: System.currentTimeMillis()
                    val lastPlayed = prefs.getLong(KEY_LAST_PLAYED_PREFIX + id, 0L).takeIf { it > 0L }
                    Player(id = id, name = name, createdAt = createdAt, lastPlayed = lastPlayed)
                }
            }
            .sortedForDisplay()
        _players.value = players
    }

    private fun persist(players: List<Player>) {
        val context = appContext ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previousIds = prefs.getString(KEY_PLAYER_IDS, null)
            ?.split(ID_SEPARATOR)
            .orEmpty()
            .filter { it.isNotBlank() }
        val nextIds = players.map { it.id }.toSet()
        val editor = prefs.edit()
        previousIds.filterNot { it in nextIds }.forEach { id ->
            editor.remove(KEY_NAME_PREFIX + id)
            editor.remove(KEY_CREATED_PREFIX + id)
            editor.remove(KEY_LAST_PLAYED_PREFIX + id)
        }
        editor.putString(KEY_PLAYER_IDS, players.joinToString(ID_SEPARATOR) { it.id })
        players.forEach { player ->
            editor.putString(KEY_NAME_PREFIX + player.id, player.name)
            editor.putLong(KEY_CREATED_PREFIX + player.id, player.createdAt)
            editor.putLong(KEY_LAST_PLAYED_PREFIX + player.id, player.lastPlayed ?: 0L)
        }
        editor.apply()
    }
}

private fun List<Player>.sortedForDisplay(): List<Player> =
    sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

private fun String.cleanPlayerName(): String =
    trim()
        .replace('\n', ' ')
        .replace('\r', ' ')
        .replace(Regex("\\s+"), " ")

private fun String.samePlayerName(other: String): Boolean =
    trim().lowercase(Locale.ENGLISH) == other.trim().lowercase(Locale.ENGLISH)
