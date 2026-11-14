package com.seatspot.library.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.seatspot.library.data.Board
import com.seatspot.library.data.ConfirmEmailException
import com.seatspot.library.data.SeatSpotApi
import com.seatspot.library.data.SeatSpotException
import com.seatspot.library.data.User
import com.seatspot.library.data.credentialsError
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Screen { Login, Signup, Now, Floors, Seat, Pass, Account }

enum class Sheet { Consent, Delete }

data class UiState(
    val screen: Screen = Screen.Login,
    val user: User? = null,
    val board: Board? = null,
    val error: String = "",
    val busy: Boolean = false,
    val floor: Int = 3,
    val filterFree: Boolean = false,
    val filterWindow: Boolean = false,
    val filterOutlet: Boolean = false,
    val seatId: String? = null,
    val minutes: Int = 60,
    val ask: String = "",
    val sheet: Sheet? = null,
    val name: String = "",
    val email: String = "",
    val password: String = "",
)

class SeatSpotViewModel(private val api: SeatSpotApi) : ViewModel() {
    var ui by mutableStateOf(UiState())
        private set

    private var refreshJob: Job? = null
    private var nudgedFor: String? = null

    init {
        viewModelScope.launch { restore() }
    }

    fun update(block: UiState.() -> UiState) {
        ui = ui.block()
    }

    fun go(screen: Screen) {
        ui = ui.copy(screen = screen, error = "", sheet = null)
    }

    fun openSeat(id: String, minutes: Int) {
        ui = ui.copy(seatId = id, minutes = minutes, screen = Screen.Seat, error = "", sheet = null)
    }

    fun login() {
        val error = credentialsError(ui.email, ui.password)
        if (error != null) {
            ui = ui.copy(error = error)
            return
        }
        auth { api.login(ui.email.trim(), ui.password) }
    }

    fun signup() {
        val error = credentialsError(ui.email, ui.password, ui.name)
        if (error != null) {
            ui = ui.copy(error = error)
            return
        }
        auth { api.signup(ui.name.trim(), ui.email.trim(), ui.password) }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                api.logout()
            } catch (_: Exception) {
            }
            disconnect()
            ui = UiState(screen = Screen.Login)
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            try {
                api.deleteAccount()
                disconnect()
                ui = UiState(screen = Screen.Login)
            } catch (error: Exception) {
                ui = ui.copy(sheet = null, error = error.message ?: "The account could not be deleted.")
            }
        }
    }

    fun reserve() {
        val seatId = ui.seatId ?: return
        viewModelScope.launch {
            ui = ui.copy(busy = true, error = "")
            try {
                val board = api.reserve(seatId, ui.minutes)
                ui = ui.copy(board = board, screen = Screen.Pass, busy = false)
            } catch (error: Exception) {
                ui = ui.copy(busy = false, error = error.message ?: "That seat could not be reserved.")
            }
        }
    }

    fun act(kind: String) {
        viewModelScope.launch {
            ui = ui.copy(busy = true, error = "")
            try {
                val board = when (kind) {
                    "checkin" -> api.checkIn()
                    "extend" -> api.extend()
                    else -> api.release()
                }
                ui = ui.copy(board = board, busy = false)
            } catch (error: Exception) {
                ui = ui.copy(busy = false, error = error.message ?: "Something went wrong.")
            }
        }
    }

    fun requestMatch() {
        if (ui.user?.matchConsent != true) {
            ui = ui.copy(sheet = Sheet.Consent)
            return
        }
        runMatch()
    }

    fun agreeAndMatch() {
        viewModelScope.launch {
            ui = ui.copy(sheet = null, busy = true, error = "")
            try {
                val user = api.update(matchConsent = true)
                ui = ui.copy(user = user)
                val board = api.match(ui.ask)
                ui = ui.copy(
                    board = board,
                    minutes = board.recommended?.minutes ?: ui.minutes,
                    busy = false,
                )
            } catch (error: Exception) {
                ui = ui.copy(busy = false, error = error.message ?: "Match is unavailable right now.")
            }
        }
    }

    fun setPref(key: String, value: Boolean) {
        viewModelScope.launch {
            try {
                val user = when (key) {
                    "silent" -> api.update(silent = value)
                    "window" -> api.update(window = value)
                    else -> api.update(outlet = value)
                }
                val board = api.state()
                ui = ui.copy(user = user, board = board, error = "")
            } catch (error: Exception) {
                ui = ui.copy(error = error.message ?: "Preferences were not saved.")
            }
        }
    }

    fun tickClock() {
        val mine = ui.board?.mine ?: return
        if (mine.remainingSeconds <= 0) return
        ui = ui.copy(board = ui.board?.copy(mine = mine.copy(remainingSeconds = mine.remainingSeconds - 1)))
    }

    fun maybeNudge() {
        val mine = ui.board?.mine ?: return
        if (!mine.checkedIn || mine.remainingSeconds > 15 * 60 || mine.nudge != null || nudgedFor == mine.code) return
        nudgedFor = mine.code
        viewModelScope.launch {
            try {
                val nudge = api.nudge()
                if (!nudge.text.isNullOrBlank() && ui.board?.mine?.code == mine.code) {
                    ui = ui.copy(board = ui.board?.copy(mine = ui.board?.mine?.copy(nudge = nudge.text)))
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun runMatch() {
        viewModelScope.launch {
            ui = ui.copy(busy = true, error = "")
            try {
                val board = api.match(ui.ask)
                ui = ui.copy(board = board, minutes = board.recommended?.minutes ?: ui.minutes, busy = false)
            } catch (error: Exception) {
                ui = ui.copy(busy = false, error = error.message ?: "Match is unavailable right now.")
            }
        }
    }

    private fun auth(call: suspend () -> User) {
        viewModelScope.launch {
            ui = ui.copy(busy = true, error = "")
            try {
                val user = call()
                val board = api.state()
                ui = ui.copy(user = user, board = board, screen = Screen.Now, busy = false, password = "", error = "")
                connect()
            } catch (error: ConfirmEmailException) {
                ui = ui.copy(
                    busy = false,
                    password = "",
                    screen = Screen.Login,
                    error = error.message ?: "Check your email to confirm this account, then sign in.",
                )
            } catch (error: Exception) {
                ui = ui.copy(busy = false, error = error.message ?: "Sign in failed.")
            }
        }
    }

    private suspend fun restore() {
        try {
            val user = api.me()
            val board = api.state()
            ui = ui.copy(user = user, board = board, screen = Screen.Now, error = "")
            connect()
        } catch (_: SeatSpotException) {
            ui = ui.copy(screen = Screen.Login)
        } catch (_: Exception) {
            ui = ui.copy(screen = Screen.Login)
        }
    }

    private fun connect() {
        disconnect()
        refreshJob = viewModelScope.launch {
            while (isActive) {
                delay(8000)
                refresh()
            }
        }
    }

    private fun disconnect() {
        refreshJob?.cancel()
        refreshJob = null
    }

    private suspend fun refresh() {
        try {
            val board = api.state()
            ui = ui.copy(board = board)
        } catch (error: SeatSpotException) {
            if (error.message?.contains("Sign in") == true) {
                disconnect()
                ui = UiState(screen = Screen.Login)
            }
        }
    }

    override fun onCleared() {
        disconnect()
    }
}

class SeatSpotViewModelFactory(private val api: SeatSpotApi) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = SeatSpotViewModel(api) as T
}
