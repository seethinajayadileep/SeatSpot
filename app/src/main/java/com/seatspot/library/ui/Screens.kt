package com.seatspot.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seatspot.library.data.Seat
import com.seatspot.library.ui.theme.Accent
import com.seatspot.library.ui.theme.Danger
import com.seatspot.library.ui.theme.DangerSoft
import com.seatspot.library.ui.theme.Display
import com.seatspot.library.ui.theme.Green
import com.seatspot.library.ui.theme.GreenSoft
import com.seatspot.library.ui.theme.Ink
import com.seatspot.library.ui.theme.Line
import com.seatspot.library.ui.theme.Muted
import com.seatspot.library.ui.theme.Paper
import com.seatspot.library.ui.theme.Paper2
import com.seatspot.library.ui.theme.Sans
import com.seatspot.library.ui.theme.Taken
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale

private val Zone = ZoneId.of("Asia/Kolkata")
private val CardShape = RoundedCornerShape(24.dp)
private val ControlShape = RoundedCornerShape(16.dp)

@Composable
fun SeatSpotApp(vm: SeatSpotViewModel) {
    val ui = vm.ui
    LaunchedEffect(ui.board?.mine?.code, ui.board?.mine?.endsAt) {
        while (true) {
            delay(1000)
            vm.tickClock()
        }
    }
    Column(Modifier.fillMaxSize().background(Paper)) {
        Box(Modifier.weight(1f)) {
            when (ui.screen) {
                Screen.Login -> AuthScreen(ui, signup = false, vm)
                Screen.Signup -> AuthScreen(ui, signup = true, vm)
                Screen.Now -> NowScreen(ui, vm)
                Screen.Floors -> FloorsScreen(ui, vm)
                Screen.Seat -> SeatScreen(ui, vm)
                Screen.Pass -> PassScreen(ui, vm)
                Screen.Account -> AccountScreen(ui, vm)
            }
            if (ui.sheet == Sheet.Consent) {
                Sheet(
                    title = "Match uses OpenAI",
                    body = "Match sends this sentence, your seat preferences, and which seats are free to OpenAI to choose a seat. It does not send your password or email.",
                ) {
                    PrimaryButton("Agree and match", ui.busy) { vm.agreeAndMatch() }
                    TextButton("Not now") { vm.update { copy(sheet = null) } }
                }
            }
            if (ui.sheet == Sheet.Delete) {
                Sheet(
                    title = "Delete this account?",
                    body = "This removes your profile, preferences, match history, and study sessions, and releases a seat you still hold.",
                ) {
                    PrimaryButton("Yes, delete my account", false) { vm.deleteAccount() }
                    TextButton("Keep my account") { vm.update { copy(sheet = null) } }
                }
            }
        }
        if (ui.user != null && ui.screen != Screen.Login && ui.screen != Screen.Signup) {
            BottomNav(ui.screen) { vm.go(it) }
        }
    }
}

@Composable
private fun AuthScreen(ui: UiState, signup: Boolean, vm: SeatSpotViewModel) {
    Scroll {
        if (signup) TextButton("Sign in") { vm.go(Screen.Login) } else Brand()
        Text(if (signup) "Create your account." else "Find a quiet seat.", style = headline())
        Text(
            if (signup) "One seat at a time, under your name." else "Central Library, open until 11:00 PM.",
            color = Muted,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(20.dp))
        Error(ui.error)
        if (signup) Field("Name", ui.name) { vm.update { copy(name = it) } }
        Field("Email", ui.email, KeyboardType.Email) { vm.update { copy(email = it) } }
        Field("Password", ui.password, KeyboardType.Password, password = true) { vm.update { copy(password = it) } }
        Spacer(Modifier.height(4.dp))
        PrimaryButton(if (signup) "Create account" else "Sign in", ui.busy) {
            if (signup) vm.signup() else vm.login()
        }
        if (!signup) {
            TextButton(
                "Create an account",
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { vm.go(Screen.Signup) }
        }
    }
}

@Composable
private fun NowScreen(ui: UiState, vm: SeatSpotViewModel) {
    val board = ui.board
    Scroll {
        Error(ui.error)
        val closed = board?.library?.open == false
        val quiet = board?.quietOpen ?: 0
        Text(greet(), style = kicker())
        Text(if (closed) "The library is closed" else quietLine(quiet), style = headline())
        Text(
            when {
                closed -> "Seats stay listed until the doors open."
                board?.calmFloor != null -> "Level ${board.calmFloor} is the calmest floor right now."
                else -> "Seats update as people sit down and leave."
            },
            color = Muted,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(18.dp))
        Card {
            Text("What do you need?", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            TextField(
                value = ui.ask,
                onValueChange = { vm.update { copy(ask = it.take(280)) } },
                modifier = Modifier.fillMaxWidth().border(1.dp, Line, ControlShape),
                placeholder = { Text("Two quiet hours by a window", color = Muted) },
                minLines = 2,
                shape = ControlShape,
                colors = fieldColors(),
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(if (ui.busy) "Matching…" else "Match", ui.busy) { vm.requestMatch() }
        }
        val pick = board?.recommended
        val seat = board?.seats?.find { it.id == pick?.seatId }
        Card {
            if (pick == null || seat == null) {
                Text(
                    when {
                        closed -> "A suggestion shows up once the library opens."
                        quiet > 0 -> "Free seats are on Floors. Pick one when you are ready."
                        else -> "Every seat on the board is taken."
                    },
                    color = Muted,
                    lineHeight = 22.sp,
                )
            } else {
                Text(if (pick.reason != null) "Suggested for you" else "Recommended for you", style = kicker())
                Spacer(Modifier.height(6.dp))
                Text(seat.id, style = seatTitle())
                Text(amenityWords(seat).joinToString(" · "), color = Muted)
                if (!pick.reason.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(pick.reason, lineHeight = 22.sp)
                }
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Reserve", false) { vm.openSeat(seat.id, pick.minutes) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Green))
            Spacer(Modifier.width(8.dp))
            Text("Live", style = kicker())
        }
        val feed = board?.feed.orEmpty()
        if (feed.isEmpty()) {
            Text("Seat changes will show up here.", color = Muted, modifier = Modifier.padding(vertical = 8.dp))
        }
        feed.forEach { item ->
            val whenLabel = formatTime(item.at)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Paper2)
                    .border(1.dp, Line, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.text, modifier = Modifier.weight(1f))
                if (whenLabel != item.at) {
                    Spacer(Modifier.width(12.dp))
                    Text(whenLabel, color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun FloorsScreen(ui: UiState, vm: SeatSpotViewModel) {
    val board = ui.board
    val floor = board?.floors?.find { it.id == ui.floor } ?: board?.floors?.firstOrNull()
    Scroll {
        Error(ui.error)
        Text("Central", style = kicker())
        Text(floor?.name ?: "Floors", style = headline())
        Text(floor?.blurb.orEmpty(), color = Muted, fontSize = 16.sp)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            board?.floors?.forEach { item ->
                Chip(item.name, item.id == ui.floor) { vm.update { copy(floor = item.id) } }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Free only", ui.filterFree) { vm.update { copy(filterFree = !filterFree) } }
            Chip("Window", ui.filterWindow) { vm.update { copy(filterWindow = !filterWindow) } }
            Chip("Outlet", ui.filterOutlet) { vm.update { copy(filterOutlet = !filterOutlet) } }
        }
        Spacer(Modifier.height(14.dp))
        val seats = board?.seats.orEmpty().filter { it.floor == (floor?.id ?: ui.floor) }.filter { seat ->
            (!ui.filterFree || seat.status == "free" || seat.status == "yours") &&
                (!ui.filterWindow || seat.window) &&
                (!ui.filterOutlet || seat.outlet)
        }
        FloorPlan(seats) { seat ->
            if (seat.status == "yours") vm.go(Screen.Pass) else vm.openSeat(seat.id, ui.minutes)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(Green, "Free")
            LegendDot(Taken, "Taken")
            LegendDot(Color.Transparent, "Yours", ring = true)
        }
    }
}

@Composable
private fun FloorPlan(seats: List<Seat>, onSeat: (Seat) -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(360.dp)
            .clip(CardShape)
            .background(Paper2)
            .border(1.dp, Line, CardShape),
    ) {
        seats.forEach { seat ->
            val x = maxWidth * (seat.x.toFloat() / 100f) - 26.dp
            val y = maxHeight * (seat.y.toFloat() / 100f) - 18.dp
            val label = when (seat.status) {
                "yours" -> "Yours"
                "taken" -> "Taken"
                else -> "Free"
            }
            Column(
                Modifier
                    .offset(x, y)
                    .width(52.dp)
                    .semantics { contentDescription = "${seat.id}, $label" }
                    .clickable(enabled = seat.status != "taken") { onSeat(seat) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            when (seat.status) {
                                "free" -> Green
                                "yours" -> Paper
                                else -> Taken
                            },
                        )
                        .border(if (seat.status == "yours") 2.dp else 0.dp, Accent, CircleShape),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    seat.id,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (seat.status == "taken") Muted else Ink,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SeatScreen(ui: UiState, vm: SeatSpotViewModel) {
    val seat = ui.board?.seats?.find { it.id == ui.seatId }
    Scroll {
        Error(ui.error)
        TextButton("Floors") { vm.go(Screen.Floors) }
        Text(seat?.id ?: "Seat", style = headline())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (seat != null) amenityWords(seat).forEach { word ->
                Text(
                    word,
                    color = Green,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(GreenSoft)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(seat?.blurb.orEmpty(), color = Muted, fontSize = 16.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(18.dp))
        Text("Duration", style = kicker())
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 60, 90, 120, 180).forEach { minutes ->
                Chip(durationLabel(minutes), minutes == ui.minutes) { vm.update { copy(minutes = minutes) } }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("You can hold one seat. Check in within 10 minutes or it is released.", color = Muted, lineHeight = 20.sp)
        Spacer(Modifier.height(16.dp))
        val label = when (seat?.status) {
            "taken" -> "Taken"
            "yours" -> "Open pass"
            else -> "Reserve this seat"
        }
        PrimaryButton(label, ui.busy || seat?.status == "taken") {
            if (seat?.status == "yours") vm.go(Screen.Pass) else vm.reserve()
        }
    }
}

@Composable
private fun PassScreen(ui: UiState, vm: SeatSpotViewModel) {
    val mine = ui.board?.mine
    LaunchedEffect(mine?.code, mine?.checkedIn) { vm.maybeNudge() }
    Scroll {
        Error(ui.error)
        Text("Your seat", style = kicker())
        if (mine == null) {
            Text("No seat yet.", style = headline())
            Text("When you reserve a seat, the pass shows up here.", color = Muted, fontSize = 16.sp)
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Find a seat", false) { vm.go(Screen.Floors) }
            return@Scroll
        }
        val seat = ui.board?.seats?.find { it.id == mine.seatId }
        Card {
            Text(mine.seatId, style = seatTitle())
            Text("${seat?.let { amenityWords(it).joinToString(" · ") } ?: mine.zone} · Level ${mine.floor}", color = Muted)
            Spacer(Modifier.height(16.dp))
            Text("Until", style = kicker())
            Text(formatTime(mine.endsAt), style = seatTitle(), maxLines = 1)
            Text(
                formatLeft(mine.remainingSeconds),
                color = Ink,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(CircleShape)
                    .background(GreenSoft)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                mine.code.forEach { letter ->
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, Line, RoundedCornerShape(14.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(letter.toString(), fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                PrimaryButton(if (mine.checkedIn) "Checked in" else "I'm here", mine.checkedIn || ui.busy) {
                    vm.act("checkin")
                }
            }
            Box(Modifier.weight(1f)) { GhostButton("Release") { vm.act("release") } }
        }
        TextButton("Extend 30 min") { vm.act("extend") }
        if (!mine.nudge.isNullOrBlank()) {
            Text(
                mine.nudge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ControlShape)
                    .background(GreenSoft)
                    .padding(14.dp),
                lineHeight = 22.sp,
            )
        }
    }
}

@Composable
private fun AccountScreen(ui: UiState, vm: SeatSpotViewModel) {
    val user = ui.user ?: return
    Scroll {
        Error(ui.error)
        Text("Account", style = headline())
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        user.name.firstOrNull()?.uppercaseChar()?.toString() ?: "S",
                        color = Color.White,
                        fontFamily = Display,
                        fontSize = 24.sp,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(user.name, fontFamily = Display, fontSize = 26.sp, color = Ink)
                    Text(user.email, color = Muted)
                    Text("Signed in", color = Green, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
        Text("Preferences", style = kicker(), modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        Card {
            Pref("Silent floor", user.silent) { vm.setPref("silent", it) }
            Pref("Window", user.window) { vm.setPref("window", it) }
            Pref("Outlet", user.outlet) { vm.setPref("outlet", it) }
        }
        Text("Recent sessions", style = kicker(), modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        val history = ui.board?.history.orEmpty()
        Card {
            if (history.isEmpty()) {
                Text("Your finished sessions will show up here.", color = Muted)
            }
            history.forEach { item ->
                Text(
                    "${item.seatId} · ${durationPhrase(item.minutes)}",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        GhostButton("Log out") { vm.logout() }
        TextButton("Delete account", danger = true) { vm.update { copy(sheet = Sheet.Delete) } }
        Text(
            "Your name, email, and seat holds stay in Supabase. Match sends the sentence and your seat preferences to OpenAI. It does not send your password.",
            color = Muted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun Scroll(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .imePadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
    ) { content() }
}

@Composable
private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 22.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Ink), contentAlignment = Alignment.Center) {
            Text("S", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text("SeatSpot", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    }
}

@Composable
private fun Error(message: String) {
    if (message.isNotBlank()) {
        Text(
            message,
            color = Danger,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clip(ControlShape)
                .background(DangerSoft)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    onChange: (String) -> Unit,
) {
    Text(label, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Ink)
    Spacer(Modifier.height(6.dp))
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp).border(1.dp, Line, ControlShape),
        singleLine = true,
        shape = ControlShape,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = fieldColors(),
    )
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = Accent,
    focusedTextColor = Ink,
    unfocusedTextColor = Ink,
)

@Composable
private fun PrimaryButton(label: String, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(52.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Accent,
            contentColor = Color.White,
            disabledContainerColor = Accent.copy(alpha = 0.38f),
            disabledContentColor = Color.White,
        ),
    ) { Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
}

@Composable
private fun GhostButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(52.dp),
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Ink),
    ) { Text(label, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun TextButton(
    label: String,
    danger: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    androidx.compose.material3.TextButton(onClick = onClick, modifier = modifier) {
        Text(label, color = if (danger) Danger else Accent, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (on) Color.White else Ink,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (on) Accent else Color.White)
            .border(1.dp, if (on) Accent else Line, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(CardShape)
            .background(Paper2)
            .border(1.dp, Line, CardShape)
            .padding(18.dp),
    ) { content() }
}

@Composable
private fun Sheet(title: String, body: String, actions: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0x801C1917)).clickable(enabled = false) {},
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Paper2)
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 16.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 14.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Line),
            )
            Text(title, fontFamily = Display, fontSize = 28.sp, color = Ink)
            Spacer(Modifier.height(8.dp))
            Text(body, color = Muted, lineHeight = 22.sp)
            Spacer(Modifier.height(14.dp))
            actions()
        }
    }
}

@Composable
private fun BottomNav(current: Screen, go: (Screen) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Paper)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Ink)
            .padding(6.dp),
    ) {
        listOf(
            Screen.Now to "Now",
            Screen.Floors to "Floors",
            Screen.Pass to "Pass",
            Screen.Account to "Account",
        ).forEach { (screen, label) ->
            val selected = current == screen || (current == Screen.Seat && screen == Screen.Floors)
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (selected) Color.White else Color.Transparent)
                    .clickable { go(screen) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    color = if (selected) Ink else Color(0xFFBDBDBD),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun Pref(label: String, on: Boolean, change: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontWeight = FontWeight.Medium)
        Box(
            Modifier
                .width(48.dp)
                .height(28.dp)
                .border(if (on) 0.dp else 1.dp, Line, CircleShape)
                .clip(CircleShape)
                .background(if (on) Ink else Color.White)
                .clickable { change(!on) }
                .semantics { contentDescription = label },
        ) {
            Box(
                Modifier
                    .padding(3.dp)
                    .size(22.dp)
                    .offset(x = if (on) 20.dp else 0.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String, ring: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
                .border(if (ring) 2.dp else 0.dp, Accent, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = Muted, fontSize = 13.sp)
    }
}

private fun headline() = androidx.compose.ui.text.TextStyle(
    fontFamily = Sans,
    fontWeight = FontWeight.Bold,
    fontSize = 40.sp,
    lineHeight = 44.sp,
    letterSpacing = (-1).sp,
    color = Ink,
)

private fun seatTitle() = androidx.compose.ui.text.TextStyle(
    fontFamily = Sans,
    fontWeight = FontWeight.Bold,
    fontSize = 40.sp,
    letterSpacing = (-1).sp,
    color = Ink,
)

private fun kicker() = androidx.compose.ui.text.TextStyle(
    fontFamily = Sans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 12.sp,
    letterSpacing = 0.2.sp,
    color = Muted,
)

private fun amenityWords(seat: Seat): List<String> = buildList {
    add(if (seat.noise == "silent") "Silent" else "Quiet")
    if (seat.window) add("Window")
    if (seat.outlet) add("Outlet")
}

private fun durationLabel(minutes: Int) = when (minutes) {
    30 -> "30 min"
    60 -> "1 hour"
    90 -> "90 min"
    120 -> "2 hours"
    180 -> "3 hours"
    else -> "$minutes min"
}

private fun durationPhrase(minutes: Int): String {
    if (minutes < 60) return "$minutes min"
    val hours = minutes / 60
    val rest = minutes % 60
    val label = "$hours hour${if (hours == 1) "" else "s"}"
    return if (rest == 0) label else "$label $rest min"
}

private fun quietLine(count: Int) = when (count) {
    0 -> "No quiet seats open"
    1 -> "1 quiet seat open"
    else -> "$count quiet seats open"
}

private fun greet(): String {
    val hour = java.time.ZonedDateTime.now(Zone).hour
    return when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

private fun formatTime(iso: String): String {
    val instant = runCatching { Instant.parse(iso.trim()) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(iso.trim()).toInstant() }.getOrNull()
        ?: return iso
    val zoned = instant.atZone(Zone)
    val hour = zoned.hour
    val clockHour = if (hour % 12 == 0) 12 else hour % 12
    val suffix = if (hour < 12) "AM" else "PM"
    return "%d:%02d %s".format(Locale.ENGLISH, clockHour, zoned.minute, suffix)
}

private fun formatLeft(seconds: Int): String {
    if (seconds <= 0) return "Time is up"
    val minutes = (seconds + 59) / 60
    if (minutes < 60) return "$minutes min left"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0) "$hours hr left" else "$hours hr $rest min left"
}
