package com.courtside.pickleball.ui.setup

import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.courtside.pickleball.player.Player
import com.courtside.pickleball.player.ScreenshotImportResult
import com.courtside.pickleball.player.importPlayerNamesFromScreenshots
import com.courtside.pickleball.ui.theme.Ink
import com.courtside.pickleball.ui.theme.Paper
import com.courtside.pickleball.ui.theme.ProblemRed
import com.courtside.pickleball.ui.theme.SetupControlCornerRadius
import kotlinx.coroutines.launch

@Composable
internal fun PlayerManagementScreen(
    players: List<Player>,
    isTabletLayout: Boolean,
    onBack: () -> Unit,
    onAddPlayer: (String) -> Unit,
    onRenamePlayer: (String, String) -> Unit,
    onDeletePlayer: (String) -> Unit,
    onDeleteAllPlayers: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var newPlayerName by remember { mutableStateOf("") }
    var editingPlayer by remember { mutableStateOf<Player?>(null) }
    var editingName by remember { mutableStateOf("") }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var isImportingScreenshots by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<ScreenshotImportResult?>(null) }
    var selectedImportNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        isImportingScreenshots = true
        coroutineScope.launch {
            val result = importPlayerNamesFromScreenshots(
                context = context,
                imageUris = uris,
                existingNames = players.map { it.name }
            )
            isImportingScreenshots = false
            importResult = result
            selectedImportNames = result.newNames.toSet()
        }
    }
    val filteredPlayers = remember(players, search) {
        players.filter { it.matchesQuery(search) }
    }
    val recentlyPlayed = remember(players, search) {
        players
            .filter { it.lastPlayed != null && it.matchesQuery(search) }
            .sortedByDescending { it.lastPlayed }
            .take(6)
    }
    val listPlayers = remember(filteredPlayers, recentlyPlayed) {
        filteredPlayers.filter { player -> recentlyPlayed.none { it.id == player.id } }
    }
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .background(Paper),
        color = Paper
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(
                    horizontal = if (isTabletLayout) 32.dp else 18.dp,
                    vertical = if (isTabletLayout) 18.dp else 8.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (isTabletLayout) 14.dp else 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = "MANAGE PLAYERS",
                    color = Ink,
                    fontSize = if (isTabletLayout) 28.sp else 22.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                OutlinedButton(
                    modifier = Modifier.height(if (isTabletLayout) 44.dp else 38.dp),
                    onClick = onBack,
                    shape = RoundedCornerShape(SetupControlCornerRadius),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                ) {
                    Text("DONE", color = Ink, fontWeight = FontWeight.Black, maxLines = 1)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerManagementInput(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (isTabletLayout) 52.dp else 46.dp),
                    value = newPlayerName,
                    hint = "Add player",
                    compact = !isTabletLayout,
                    onValueChange = { newPlayerName = normalizePlayerNamesInput(it) },
                    onDone = {
                        onAddPlayer(newPlayerName)
                        newPlayerName = ""
                    }
                )
                Button(
                    modifier = Modifier
                        .height(if (isTabletLayout) 52.dp else 46.dp)
                        .width(if (isTabletLayout) 116.dp else 88.dp),
                    onClick = {
                        onAddPlayer(newPlayerName)
                        newPlayerName = ""
                    },
                    enabled = newPlayerName.trim().isNotEmpty(),
                    shape = RoundedCornerShape(SetupControlCornerRadius),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("ADD", fontWeight = FontWeight.Black, maxLines = 1)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (isTabletLayout) 48.dp else 42.dp),
                    enabled = !isImportingScreenshots,
                    onClick = {
                        importLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(SetupControlCornerRadius),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = if (isImportingScreenshots) "READING…" else "IMPORT SCREENSHOT",
                        color = Ink,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        fontSize = if (isTabletLayout) 14.sp else 11.sp
                    )
                }
                OutlinedButton(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (isTabletLayout) 48.dp else 42.dp),
                    enabled = players.isNotEmpty(),
                    onClick = { showDeleteAllConfirm = true },
                    shape = RoundedCornerShape(SetupControlCornerRadius),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "DELETE ALL PLAYERS",
                        color = ProblemRed,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        fontSize = if (isTabletLayout) 14.sp else 11.sp
                    )
                }
            }

            PlayerManagementInput(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isTabletLayout) 52.dp else 46.dp),
                value = search,
                hint = "Search players",
                compact = !isTabletLayout,
                onValueChange = { search = normalizePlayerNamesInput(it) },
                onDone = {}
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (recentlyPlayed.isNotEmpty()) {
                    PlayerListSection(
                        title = "RECENT PLAYERS",
                        players = recentlyPlayed,
                        isTabletLayout = isTabletLayout,
                        onEdit = { player ->
                            editingPlayer = player
                            editingName = player.name
                        },
                        onDelete = onDeletePlayer
                    )
                }
                PlayerListSection(
                    title = "ALL PLAYERS",
                    players = listPlayers,
                    isTabletLayout = isTabletLayout,
                    onEdit = { player ->
                        editingPlayer = player
                        editingName = player.name
                    },
                    onDelete = onDeletePlayer
                )
            }
        }
    }

    val playerBeingEdited = editingPlayer
    if (playerBeingEdited != null) {
        AlertDialog(
            onDismissRequest = { editingPlayer = null },
            title = { Text("Edit Player", fontWeight = FontWeight.Black) },
            text = {
                PlayerManagementInput(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    value = editingName,
                    hint = "Player name",
                    compact = false,
                    onValueChange = { editingName = normalizePlayerNamesInput(it) },
                    onDone = {
                        onRenamePlayer(playerBeingEdited.id, editingName)
                        editingPlayer = null
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenamePlayer(playerBeingEdited.id, editingName)
                        editingPlayer = null
                    },
                    enabled = editingName.trim().isNotEmpty()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingPlayer = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text("Delete All Players?", fontWeight = FontWeight.Black) },
            text = {
                Text("This permanently removes all ${players.size} saved player(s) from this device. This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAllPlayers()
                        showDeleteAllConfirm = false
                    }
                ) {
                    Text("Delete All", color = ProblemRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val screenshotResult = importResult
    if (screenshotResult != null) {
        AlertDialog(
            onDismissRequest = { importResult = null },
            title = { Text("Import Players", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (screenshotResult.newNames.isEmpty()) {
                        Text(
                            if (screenshotResult.skippedExisting.isNotEmpty()) {
                                "All ${screenshotResult.skippedExisting.size} recognized name(s) are already in your player list."
                            } else {
                                "No player names were recognized in the selected screenshot(s)."
                            }
                        )
                    } else {
                        Text("Found ${screenshotResult.newNames.size} new player(s). Uncheck any that were misread.")
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            screenshotResult.newNames.forEach { name ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedImportNames = if (name in selectedImportNames) {
                                                selectedImportNames - name
                                            } else {
                                                selectedImportNames + name
                                            }
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = name in selectedImportNames,
                                        onCheckedChange = { checked ->
                                            selectedImportNames = if (checked) {
                                                selectedImportNames + name
                                            } else {
                                                selectedImportNames - name
                                            }
                                        }
                                    )
                                    Text(
                                        text = name,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    if (screenshotResult.skippedExisting.isNotEmpty() && screenshotResult.newNames.isNotEmpty()) {
                        Text(
                            text = "Skipped ${screenshotResult.skippedExisting.size} already in your player list.",
                            color = Ink.copy(alpha = 0.62f),
                            fontSize = 12.sp
                        )
                    }
                    if (screenshotResult.failedImageCount > 0) {
                        Text(
                            text = "Could not read text from ${screenshotResult.failedImageCount} image(s).",
                            color = ProblemRed,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                if (screenshotResult.newNames.isNotEmpty()) {
                    Button(
                        onClick = {
                            selectedImportNames.forEach { onAddPlayer(it) }
                            importResult = null
                        },
                        enabled = selectedImportNames.isNotEmpty()
                    ) {
                        Text("Import ${selectedImportNames.size}")
                    }
                } else {
                    TextButton(onClick = { importResult = null }) { Text("OK") }
                }
            },
            dismissButton = {
                if (screenshotResult.newNames.isNotEmpty()) {
                    TextButton(onClick = { importResult = null }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
private fun PlayerListSection(
    title: String,
    players: List<Player>,
    isTabletLayout: Boolean,
    onEdit: (Player) -> Unit,
    onDelete: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SetupSectionLabel(text = title, compact = !isTabletLayout)
        if (players.isEmpty()) {
            Text(
                text = "No players yet",
                color = Ink.copy(alpha = 0.62f),
                fontSize = if (isTabletLayout) 16.sp else 13.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            players.forEach { player ->
                PlayerManagementRow(
                    player = player,
                    isTabletLayout = isTabletLayout,
                    onEdit = { onEdit(player) },
                    onDelete = { onDelete(player.id) }
                )
            }
        }
    }
}

@Composable
private fun PlayerManagementRow(
    player: Player,
    isTabletLayout: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isTabletLayout) 58.dp else 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF3F4F6))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = player.name,
            color = Ink,
            fontSize = if (isTabletLayout) 20.sp else 16.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        OutlinedButton(
            modifier = Modifier
                .height(if (isTabletLayout) 40.dp else 34.dp)
                .width(if (isTabletLayout) 82.dp else 68.dp),
            onClick = onEdit,
            shape = RoundedCornerShape(SetupControlCornerRadius),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
        ) {
            Text("EDIT", color = Ink, fontWeight = FontWeight.Black, fontSize = if (isTabletLayout) 13.sp else 11.sp)
        }
        OutlinedButton(
            modifier = Modifier
                .height(if (isTabletLayout) 40.dp else 34.dp)
                .width(if (isTabletLayout) 88.dp else 76.dp),
            onClick = onDelete,
            shape = RoundedCornerShape(SetupControlCornerRadius),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
        ) {
            Text("DELETE", color = ProblemRed, fontWeight = FontWeight.Black, fontSize = if (isTabletLayout) 13.sp else 10.sp)
        }
    }
}

@Composable
private fun PlayerManagementInput(
    modifier: Modifier,
    value: String,
    hint: String,
    compact: Boolean,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            android.widget.EditText(context).apply {
                setSingleLine(true)
                setTextColor(Ink.toArgb())
                setHintTextColor(android.graphics.Color.rgb(110, 118, 126))
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if (compact) 16f else 18f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(20, 0, 20, 0)
                setBackgroundColor(android.graphics.Color.WHITE)
                filters = arrayOf(InputFilter.AllCaps())
                imeOptions = EditorInfo.IME_ACTION_DONE
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
                    android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                setOnEditorActionListener { _, actionId, event ->
                    val isEnterKey = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                        event.action == KeyEvent.ACTION_UP
                    if (actionId == EditorInfo.IME_ACTION_DONE || isEnterKey) {
                        onDone()
                        true
                    } else {
                        false
                    }
                }
                addTextChangedListener(
                    object : TextWatcher {
                        override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                        ) = Unit

                        override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                        ) {
                            onValueChange(s?.toString().orEmpty())
                        }

                        override fun afterTextChanged(s: Editable?) = Unit
                    }
                )
            }
        },
        update = { editText ->
            editText.hint = hint
            if (editText.text.toString() != value) {
                editText.setText(value)
                editText.setSelection(value.length)
            }
            editText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if (compact) 16f else 18f)
        }
    )
}
