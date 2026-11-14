package com.seatspot.library.data

import com.seatspot.library.BuildConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

internal fun credentialsError(email: String, password: String, name: String? = null): String? {
    if (name != null && name.trim().isEmpty()) return "Enter your name."
    val trimmed = email.trim()
    if (trimmed.isEmpty()) return "Enter your email."
    if (!trimmed.contains("@") || !trimmed.substringAfter("@").contains(".")) {
        return "Enter a valid email address."
    }
    if (password.isEmpty()) return "Enter a password."
    if (password.length < 6) return "Password must be at least 6 characters."
    return null
}

internal fun authMessage(body: String, fallback: String): String {
    val parsed = runCatching {
        Json.parseToJsonElement(body).jsonObject
    }.getOrNull()
    val raw = listOf("error_description", "msg", "message", "error")
        .mapNotNull { key -> parsed?.get(key)?.jsonPrimitive?.contentOrNull }
        .firstOrNull { it.isNotBlank() }
        ?: body.lineSequence().firstOrNull { it.isNotBlank() }
        ?: fallback
    val lower = raw.lowercase()
    return when {
        "already registered" in lower || "already been registered" in lower || "email_exists" in lower ->
            "That email already has an account."
        "invalid login" in lower || "invalid_grant" in lower ->
            "Check your email and password."
        "not confirmed" in lower ->
            "Check your email to confirm this account, then sign in."
        "at least 6" in lower || "password" in lower && "characters" in lower ->
            "Password must be at least 6 characters."
        "invalid format" in lower || "unable to validate email" in lower ->
            "Enter a valid email address."
        else -> raw.ifBlank { fallback }
    }
}

class ConfirmEmailException : SeatSpotException("Check your email to confirm this account, then sign in.")

internal class SupabaseAuth(
    private val json: Json,
    private val url: String = BuildConfig.SUPABASE_URL,
    private val anonKey: String = BuildConfig.SUPABASE_ANON_KEY,
) {
    fun signUp(email: String, password: String, name: String): AuthSession {
        val body = json.encodeToString(
            PasswordAuthBody(email = email, password = password, data = mapOf("name" to name)),
        )
        return parseSession(post("/signup", body), "That account could not be created.")
    }

    fun signIn(email: String, password: String): AuthSession {
        val body = json.encodeToString(PasswordAuthBody(email = email, password = password))
        return parseSession(
            post("/token?grant_type=password", body),
            "Check your email and password.",
        )
    }

    fun logout(accessToken: String) {
        runCatching { post("/logout", "{}", accessToken) }
    }

    fun deleteUser(accessToken: String) {
        runCatching { request("DELETE", "/user", "", accessToken) }
    }

    private fun parseSession(response: Pair<Int, String>, fallback: String): AuthSession {
        val (code, body) = response
        if (code == 429) throw SeatSpotException("Too many attempts. Wait a minute and try again.")
        val session = runCatching { json.decodeFromString<AuthSession>(body) }.getOrNull()
        if (code !in 200..299) {
            throw SeatSpotException(authMessage(body, fallback))
        }
        return session ?: throw SeatSpotException(fallback)
    }

    private fun post(path: String, body: String, bearer: String? = null): Pair<Int, String> =
        request("POST", path, body, bearer)

    private fun request(method: String, path: String, body: String, bearer: String?): Pair<Int, String> {
        if (url.isBlank() || anonKey.isBlank()) {
            throw SeatSpotException("Add SUPABASE_ANON_KEY in secrets.properties from Supabase → Project Settings → API.")
        }
        val connection = (URL("${url.trimEnd('/')}/auth/v1$path").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doInput = true
            connection.setRequestProperty("apikey", anonKey)
            connection.setRequestProperty("Authorization", "Bearer ${bearer ?: anonKey}")
            connection.setRequestProperty("Content-Type", "application/json")
            if (method != "GET" && method != "DELETE") {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            } else if (method == "DELETE") {
                connection.doOutput = false
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.readText().orEmpty()
            return code to text
        } finally {
            connection.disconnect()
        }
    }
}

@Serializable
internal data class PasswordAuthBody(
    val email: String,
    val password: String,
    val data: Map<String, String>? = null,
)

@Serializable
internal data class AuthSession(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val user: AuthUser? = null,
)

@Serializable
internal data class AuthUser(
    val id: String? = null,
    val email: String? = null,
    @SerialName("email_confirmed_at") val emailConfirmedAt: String? = null,
)
