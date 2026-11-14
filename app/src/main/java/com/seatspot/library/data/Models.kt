package com.seatspot.library.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val name: String,
    val email: String,
    val silent: Boolean = true,
    val window: Boolean = true,
    val outlet: Boolean = true,
    @SerialName("match_consent") val matchConsent: Boolean = false,
)

@Serializable
data class LibraryInfo(
    val name: String,
    val open: Boolean,
    @SerialName("closes_at") val closesAt: String,
    val timezone: String,
)

@Serializable
data class Floor(
    val id: Int,
    val name: String,
    val tone: String,
    val blurb: String,
)

@Serializable
data class Seat(
    val id: String,
    val floor: Int,
    val zone: String,
    val noise: String,
    val window: Boolean,
    val outlet: Boolean,
    val blurb: String,
    val x: Double,
    val y: Double,
    val status: String,
)

@Serializable
data class Recommendation(
    @SerialName("seat_id") val seatId: String,
    val minutes: Int = 60,
    val reason: String? = null,
    val source: String? = null,
)

@Serializable
data class Reservation(
    @SerialName("seat_id") val seatId: String,
    val floor: Int,
    val zone: String,
    val noise: String,
    val window: Boolean,
    val outlet: Boolean,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
    @SerialName("checked_in") val checkedIn: Boolean,
    val code: String,
    @SerialName("remaining_seconds") val remainingSeconds: Int,
    val nudge: String? = null,
)

@Serializable
data class FeedItem(val text: String, val at: String)

@Serializable
data class StudySession(
    @SerialName("seat_id") val seatId: String,
    val minutes: Int,
    @SerialName("ended_at") val endedAt: String,
)

@Serializable
data class Board(
    val library: LibraryInfo,
    val floors: List<Floor> = emptyList(),
    val seats: List<Seat> = emptyList(),
    @SerialName("quiet_open") val quietOpen: Int = 0,
    @SerialName("calm_floor") val calmFloor: Int? = null,
    val recommended: Recommendation? = null,
    val mine: Reservation? = null,
    val feed: List<FeedItem> = emptyList(),
    val history: List<StudySession> = emptyList(),
    val match: Recommendation? = null,
)

@Serializable
data class Nudge(val text: String? = null)

open class SeatSpotException(message: String) : Exception(message)
