package com.seatspot.library

import com.seatspot.library.data.Board
import com.seatspot.library.data.authMessage
import com.seatspot.library.data.credentialsError
import com.seatspot.library.data.dbMessage
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeatSpotApiTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun databaseErrorsStayReadable() {
        assertEquals(
            "Sign in required.",
            dbMessage("ERROR: Sign in required.\n  Where: PL/pgSQL function public.me(text)"),
        )
        assertFalse(dbMessage("ERROR: The OpenAI key was rejected.").contains("sk-"))
    }

    @Test
    fun signupNeedsARealEmailAndPassword() {
        assertEquals("Enter your name.", credentialsError("user@example.com", "secret1", ""))
        assertEquals("Enter a valid email address.", credentialsError("maya", "secret1"))
        assertEquals("Password must be at least 6 characters.", credentialsError("user@example.com", "short"))
        assertEquals(null, credentialsError("user@example.com", "secret1", "Maya"))
    }

    @Test
    fun supabaseAuthErrorsStayReadable() {
        assertEquals(
            "That email already has an account.",
            authMessage("""{"msg":"User already registered"}""", "failed"),
        )
        assertEquals(
            "Check your email and password.",
            authMessage("""{"error":"invalid_grant","error_description":"Invalid login credentials"}""", "failed"),
        )
    }

    @Test
    fun boardJsonKeepsTheRecommendedSeat() {
        val board = json.decodeFromString<Board>(BOARD)
        assertEquals(12, board.quietOpen)
        assertEquals(3, board.calmFloor)
        assertEquals("L3-C4", board.recommended?.seatId)
        assertEquals("free", board.seats.first { it.id == "L3-C4" }.status)
        assertTrue(board.library.open)
    }
}

private const val BOARD = """
{"library":{"name":"Ashworth Library","open":true,"closes_at":"2026-10-31T17:30:00+00:00","timezone":"Asia/Kolkata"},
"floors":[{"id":3,"name":"Level 3","tone":"Silent","blurb":"Silent floor"}],
"seats":[{"id":"L3-C4","floor":3,"zone":"Silent row","noise":"silent","window":true,"outlet":true,"blurb":"Next to the north windows. Level 3.","x":86.0,"y":74.0,"status":"free"}],
"quiet_open":12,"calm_floor":3,
"recommended":{"seat_id":"L3-C4","minutes":60,"reason":null,"source":"rules"},
"mine":null,"feed":[],"history":[]}
"""
