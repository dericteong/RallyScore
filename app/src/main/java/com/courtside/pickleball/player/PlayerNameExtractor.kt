package com.courtside.pickleball.player

import java.util.Locale

private val BLOCKED_LINES = setOf(
    "attendee list", "attendees", "guest list", "guests", "player list", "players",
    "waitlist", "wait list", "confirmed", "checked in", "check in", "rsvp",
    "search", "back", "done", "close", "cancel", "save", "edit", "delete",
    "add", "share", "export", "import", "settings", "menu", "home"
)

private val NAME_PATTERN = Regex("^[A-Za-z][A-Za-z'.-]*(\\s+[A-Za-z][A-Za-z'.-]*){0,3}$")
private val AVATAR_INITIALS_TOKEN = Regex("^[A-Za-z]{1,3}$")

/**
 * Best-effort extraction of player names from OCR text captured off an
 * attendee-list style screenshot (e.g. the OpenSports app "Attendee List").
 * OCR output is noisy and includes headers, timestamps, status-bar icons,
 * and avatar-initial chips (e.g. "CW") next to each real name. Players
 * without a profile photo have their initials chip sit close enough to
 * their name that ML Kit merges both into one recognized line (e.g.
 * "DH Danielle Hughson" on one device, "wc Wendy Cooper" on another -
 * OCR case for the same chip varies by device/model), so this strips
 * that leading chip token before filtering down to lines that look like
 * a person's name.
 */
object PlayerNameExtractor {
    fun extractNames(rawText: String): List<String> =
        rawText
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { stripLeadingAvatarInitials(it) }
            .filterNot { it.lowercase(Locale.ENGLISH) in BLOCKED_LINES }
            .filter { NAME_PATTERN.matches(it) }
            .filter { line -> line.contains(' ') || line.length >= 3 }
            .distinctBy { it.lowercase(Locale.ENGLISH) }

    private fun stripLeadingAvatarInitials(line: String): String {
        val words = line.split(' ')
        if (words.size < 3) return line
        val chip = words.first()
        val rest = words.drop(1)
        val restLooksLikeName = rest.all { word ->
            word.isNotEmpty() && word.first().isUpperCase() &&
                (word.length == 1 || word.any { it.isLowerCase() })
        }
        return if (AVATAR_INITIALS_TOKEN.matches(chip) && restLooksLikeName) {
            rest.joinToString(" ")
        } else {
            line
        }
    }
}
