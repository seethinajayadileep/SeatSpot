package com.seatspot.library.data

import android.content.Context
import com.seatspot.library.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Types

internal fun dbMessage(raw: String): String {
    val line = raw.lineSequence().firstOrNull { it.contains("ERROR:") }
        ?: raw.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
    val message = line.substringAfter("ERROR:", line).trim()
    return message.ifBlank { "Check that and try again." }
}

class SeatSpotApi(context: Context) {
    private val prefs = context.getSharedPreferences("seatspot", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val supabase = SupabaseAuth(json)
    private val lock = Any()
    private var connection: Connection? = null
    private var token: String? = prefs.getString(TOKEN, null)

    suspend fun login(email: String, password: String): User = withContext(Dispatchers.IO) {
        credentialsError(email, password)?.let { throw SeatSpotException(it) }
        try {
            val session = supabase.signIn(email.trim(), password)
            saveAuth(session)
            libraryUser(email.trim(), password)
        } catch (error: SeatSpotException) {
            val message = error.message.orEmpty()
            if ("SUPABASE_ANON_KEY" in message || "Too many attempts" in message) throw error
            try {
                decode<AuthPayload>(call("select public.login(?, ?)::text", email.trim(), password)).also {
                    saveToken(it.token)
                }.user
            } catch (_: Exception) {
                throw error
            }
        }
    }

    suspend fun signup(name: String, email: String, password: String): User = withContext(Dispatchers.IO) {
        credentialsError(email, password, name)?.let { throw SeatSpotException(it) }
        val session = supabase.signUp(email.trim(), password, name.trim())
        if (session.accessToken.isNullOrBlank()) throw ConfirmEmailException()
        saveAuth(session)
        libraryUser(email.trim(), password, name.trim())
    }

    suspend fun logout() {
        withContext(Dispatchers.IO) {
            val access = prefs.getString(ACCESS, null)
            if (access != null) supabase.logout(access)
            val current = token
            if (current != null) {
                runCatching { call("select public.logout(?)::text", current) }
            }
            saveToken(null)
            saveAuth(null)
        }
    }

    suspend fun me(): User = decode(call("select public.me(?)::text", requiredToken()))

    suspend fun update(
        silent: Boolean? = null,
        window: Boolean? = null,
        outlet: Boolean? = null,
        matchConsent: Boolean? = null,
    ): User = decode(
        call(
            "select public.update_me(?, ?, ?, ?, ?)::text",
            requiredToken(),
            silent,
            window,
            outlet,
            matchConsent,
        ),
    )

    suspend fun deleteAccount() {
        withContext(Dispatchers.IO) {
            call("select public.delete_me(?)::text", requiredToken())
            val access = prefs.getString(ACCESS, null)
            if (access != null) supabase.deleteUser(access)
            saveToken(null)
            saveAuth(null)
        }
    }

    suspend fun state(): Board = decode(call("select public.board(?)::text", requiredToken()))

    suspend fun reserve(seatId: String, minutes: Int): Board =
        decode(call("select public.reserve_seat(?, ?, ?)::text", requiredToken(), seatId, minutes))

    suspend fun checkIn(): Board = decode(call("select public.check_in(?)::text", requiredToken()))

    suspend fun extend(): Board = decode(call("select public.extend_seat(?)::text", requiredToken()))

    suspend fun release(): Board = decode(call("select public.release_seat(?)::text", requiredToken()))

    suspend fun match(query: String): Board =
        decode(call("select public.match_seat(?, ?)::text", requiredToken(), query))

    suspend fun nudge(): Nudge = decode(call("select public.nudge_seat(?)::text", requiredToken()))

    private fun requiredToken(): String = token ?: throw SeatSpotException("Sign in required.")

    private suspend fun call(sql: String, vararg args: Any?): String = withContext(Dispatchers.IO) {
        synchronized(lock) {
            try {
                open().prepareStatement(sql).use { statement ->
                    args.forEachIndexed { index, value ->
                        if (value == null) statement.setNull(index + 1, Types.BOOLEAN)
                        else statement.setObject(index + 1, value)
                    }
                    statement.executeQuery().use { rows ->
                        if (!rows.next()) throw SeatSpotException("The library did not answer.")
                        rows.getString(1)
                    }
                }
            } catch (error: SeatSpotException) {
                throw error
            } catch (error: Throwable) {
                connection = null
                throw SeatSpotException(dbMessage(error.message ?: "Check that and try again."))
            }
        }
    }

    private fun open(): Connection {
        val current = connection
        if (current != null && !current.isClosed && current.isValid(2)) return current
        Class.forName("org.postgresql.Driver")
        val next = DriverManager.getConnection(BuildConfig.DB_URL, BuildConfig.DB_USER, BuildConfig.DB_PASSWORD)
        next.autoCommit = true
        connection = next
        return next
    }

    private suspend fun libraryUser(email: String, password: String, name: String? = null): User {
        return try {
            if (name != null) {
                decode<AuthPayload>(call("select public.signup(?, ?, ?)::text", name, email, password)).also {
                    saveToken(it.token)
                }.user
            } else {
                decode<AuthPayload>(call("select public.login(?, ?)::text", email, password)).also {
                    saveToken(it.token)
                }.user
            }
        } catch (first: Exception) {
            if (name == null) {
                try {
                    decode<AuthPayload>(
                        call("select public.signup(?, ?, ?)::text", email.substringBefore("@"), email, password),
                    ).also { saveToken(it.token) }.user
                } catch (_: Exception) {
                    throw first
                }
            } else {
                try {
                    decode<AuthPayload>(call("select public.login(?, ?)::text", email, password)).also {
                        saveToken(it.token)
                    }.user
                } catch (_: Exception) {
                    throw first
                }
            }
        }
    }

    private fun saveToken(value: String?) {
        token = value
        prefs.edit().putString(TOKEN, value).apply()
    }

    private fun saveAuth(session: AuthSession?) {
        prefs.edit()
            .putString(ACCESS, session?.accessToken)
            .putString(REFRESH, session?.refreshToken)
            .apply()
    }

    private inline fun <reified T> decode(text: String): T = json.decodeFromString(text)

    @kotlinx.serialization.Serializable
    private data class AuthPayload(val token: String, val user: User)

    private companion object {
        const val TOKEN = "token"
        const val ACCESS = "access"
        const val REFRESH = "refresh"
    }
}
