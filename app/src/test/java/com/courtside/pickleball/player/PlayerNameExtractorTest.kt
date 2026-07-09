package com.courtside.pickleball.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerNameExtractorTest {

    @Test
    fun extractsNamesFromOpenSportsAttendeeListOcrTextWithSeparateInitialsLines() {
        val ocrText = """
            7:35 pm
            •••
            Attendee List
            CW
            Chris Warner
            Deric Teong
            Steve Walkom
            Janine Walkom
            WD
            William Doggett
            Alexis Lincoln
            KE
            Kayla Eickenloff
            DH
            Danielle Hughson
            TD
            Troy D
            DE
            Daniella Eisenbeis
            Kerrie Bradley
        """.trimIndent()

        val names = PlayerNameExtractor.extractNames(ocrText)

        assertEquals(
            listOf(
                "Chris Warner",
                "Deric Teong",
                "Steve Walkom",
                "Janine Walkom",
                "William Doggett",
                "Alexis Lincoln",
                "Kayla Eickenloff",
                "Danielle Hughson",
                "Troy D",
                "Daniella Eisenbeis",
                "Kerrie Bradley"
            ),
            names
        )
    }

    @Test
    fun extractsNamesFromOpenSportsAttendeeListOcrTextWithMergedInitialsLines() {
        // ML Kit line-grouping merges an avatar-initials chip into the same
        // recognized line as the name when no profile photo is present,
        // since both sit on the same horizontal row (real bug report).
        val ocrText = """
            7:35 pm
            •••
            Attendee List
            CW Chris Warner
            Deric Teong
            Steve Walkom
            Janine Walkom
            WD William Doggett
            Alexis Lincoln
            KE Kayla Eickenloff
            DH Danielle Hughson
            TD Troy D
            DE Daniella Eisenbeis
            Kerrie Bradley
        """.trimIndent()

        val names = PlayerNameExtractor.extractNames(ocrText)

        assertEquals(
            listOf(
                "Chris Warner",
                "Deric Teong",
                "Steve Walkom",
                "Janine Walkom",
                "William Doggett",
                "Alexis Lincoln",
                "Kayla Eickenloff",
                "Danielle Hughson",
                "Troy D",
                "Daniella Eisenbeis",
                "Kerrie Bradley"
            ),
            names
        )
    }

    @Test
    fun stripsLowercaseAvatarInitialsChip() {
        // Observed on a real phone: the same merged-chip artifact came back
        // in lowercase ("wc") instead of uppercase ("WC").
        val names = PlayerNameExtractor.extractNames("wc Wendy Cooper")

        assertEquals(listOf("Wendy Cooper"), names)
    }

    @Test
    fun doesNotStripAWordThatIsPartOfALegitimateAllCapsFirstWordName() {
        // A three-word name whose first word is genuinely capitalized normally
        // (mixed case) must not be touched by the avatar-initials stripping.
        val names = PlayerNameExtractor.extractNames("Mary Jane Smith")

        assertEquals(listOf("Mary Jane Smith"), names)
    }

    @Test
    fun dropsHeaderTimestampAndAvatarInitialNoise() {
        val names = PlayerNameExtractor.extractNames(
            "Attendee List\n7:35 pm\nCW\nAB\nSearch\nDone"
        )

        assertEquals(emptyList<String>(), names)
    }

    @Test
    fun deduplicatesCaseInsensitiveDuplicateLines() {
        val names = PlayerNameExtractor.extractNames("Chris Warner\nchris warner\nCHRIS WARNER")

        assertEquals(listOf("Chris Warner"), names)
    }
}
