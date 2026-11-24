package com.ctodrs.saqrcode.ui

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.SaqrApp
import com.ctodrs.saqrcode.agent.AgentSession
import com.ctodrs.saqrcode.agent.ChatItem
import com.ctodrs.saqrcode.agent.Providers
import com.ctodrs.saqrcode.agent.Budget
import com.ctodrs.saqrcode.agent.Checkpoints
import com.ctodrs.saqrcode.agent.SlashCommand
import com.ctodrs.saqrcode.agent.SlashCommands
import com.ctodrs.saqrcode.agent.SlashContext
import com.ctodrs.saqrcode.agent.Usage
import com.ctodrs.saqrcode.agent.Tools
import com.ctodrs.saqrcode.ui.chat.ChatContent
import com.ctodrs.saqrcode.ui.chat.CheckpointsSheet
import com.ctodrs.saqrcode.ui.chat.RestoreConfirmDialog
import com.ctodrs.saqrcode.ui.chat.UsageSheet
import com.ctodrs.saqrcode.ui.chat.UsageSummary
import com.ctodrs.saqrcode.ui.chat.formatUsd
import com.ctodrs.saqrcode.ui.chat.ChatUiState
import com.ctodrs.saqrcode.ui.chat.PlanPillContent
import com.ctodrs.saqrcode.ui.components.FalconMark
import com.ctodrs.saqrcode.ui.components.latinDigits
import com.ctodrs.saqrcode.ui.components.GlassIconButton
import com.ctodrs.saqrcode.ui.components.GlassPill
import com.ctodrs.saqrcode.ui.components.PillIcon
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Radius
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.ctodrs.saqrcode.agent.ImagePart
import com.ctodrs.saqrcode.agent.Stack
import com.ctodrs.saqrcode.agent.StackDetector
import com.ctodrs.saqrcode.agent.Vision
import com.ctodrs.saqrcode.ui.chat.ImageAction
import com.ctodrs.saqrcode.ui.chat.ImageCodec
import com.ctodrs.saqrcode.ui.chat.VisionWarningDialog

object Pane { const val AGENT = 0; const val FILES = 1; const val TERMINAL = 2; const val GIT = 3; const val PREVIEW = 4; const val BUILD = 5 }

val TopBarHeight: Dp = 68.dp

@Composable
fun IdeScreen(projectName: String, onBack: () -> Unit, onSettings: () -> Unit, onAccounts: () -> Unit = onSettings, onPaywall: () -> Unit = onSettings) {
    val ws = remember(projectName) { Workspace.get(projectName) }
    if (ws == null) { LaunchedEffect(Unit) { onBack() }; return }
    val app = SaqrApp.instance
    val settings = app.settings
    val version by settings.version.collectAsState()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showCheckpoints by remember { mutableStateOf(false) }
    var showUsage by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf<Pair<Checkpoints.Point, Checkpoints.Plan>?>(null) }
    var budgetPending by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    val arabicUi = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language == "ar"
    val ctx = LocalContext.current
    // ---- image attachments (v0.5)
    val pendingImages = remember(projectName) { mutableStateListOf<ImagePart>() }
    var imageToCode by remember { mutableStateOf(false) }
    var visionPending by remember { mutableStateOf<String?>(null) }
    var previewWhenDone by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    fun addImage(uri: android.net.Uri?, deleteAfter: java.io.File? = null) {
        if (uri == null) { if (pendingImages.isEmpty()) imageToCode = false; return }
        scope.launch {
            val part = withContext(Dispatchers.IO) { ImageCodec.load(ctx, uri).also { deleteAfter?.delete() } }
            if (part != null) pendingImages += part
            else android.widget.Toast.makeText(ctx, R.string.image_load_failed, android.widget.Toast.LENGTH_SHORT).show()
            if (pendingImages.isEmpty()) imageToCode = false
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { addImage(it) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        addImage(if (ok) uri else null, java.io.File(ctx.cacheDir, "camera/shot.jpg"))
    }
    fun onImageAction(a: ImageAction) {
        when (a) {
            ImageAction.IMAGE_TO_CODE -> { imageToCode = true; pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            ImageAction.PICK -> pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            ImageAction.CAMERA -> runCatching {
                val f = java.io.File(ctx.cacheDir, "camera/shot.jpg").apply { parentFile?.mkdirs() }
                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                cameraUri = uri
                takePhoto.launch(uri)
            }.onFailure { android.widget.Toast.makeText(ctx, R.string.image_load_failed, android.widget.Toast.LENGTH_SHORT).show() }
        }
    }
    /** Sends [text] with the pending images ([withImages] = false drops them, e.g. for a text-only model). */
    fun sendWithImages(text: String, withImages: Boolean = true) {
        val images = if (withImages) pendingImages.toList() else emptyList()
        val i2c = imageToCode && withImages
        val stack = if (i2c) StackDetector.detect(ws.dir) else Stack.UNKNOWN
        val prompt = when {
            i2c -> StackDetector.imageToCodePrompt(stack, text)
            text.isBlank() && images.isNotEmpty() -> "(see the attached image)"
            else -> text
        }
        val display = text.ifBlank { if (i2c) ctx.getString(R.string.image_to_code) else "" }
        if (prompt.isBlank() && images.isEmpty()) return
        ws.agent.send(prompt, display = display, images = images)
        previewWhenDone = i2c && stack == Stack.WEB
        pendingImages.clear(); imageToCode = false
    }

    fun askRestore(id: String?) {
        val p = id?.let { ws.agent.checkpoints.get(it) } ?: return
        confirmRestore = p to ws.agent.checkpoints.preview(p)
    }

    /** Run a slash command or send the text as is. */
    fun dispatch(raw: String) {
        val inv = SlashCommands.parse(raw, settings.customCommands)
        when (inv?.command?.action) {
            null -> ws.agent.send(raw)
            SlashCommand.Action.CLEAR -> ws.agent.clear()
            SlashCommand.Action.MODEL -> Unit // handled by the composer (opens the model menu)
            SlashCommand.Action.PROMPT -> {
                val tab = ws.tabs.getOrNull(ws.activeTab)
                val sel = tab?.value?.let { v -> if (v.selection.collapsed) null else v.text.substring(v.selection.min, v.selection.max) }
                ws.agent.send(SlashCommands.expand(inv, SlashContext(tab?.path, sel, arabicUi)), display = raw.trim())
            }
        }
    }

    /** Vision check for attachments, then send (or run a slash command when there are none). */
    fun submit(text: String) {
        if (pendingImages.isEmpty()) { dispatch(text); return }
        val model = settings.config(settings.activeProvider).model
        if (!settings.supportsVision(model)) visionPending = text else sendWithImages(text)
    }

    // ---- v0.6: sessions, voice, background
    var showSessions by remember { mutableStateOf(false) }
    // v0.12: command palette
    var showPalette by remember { mutableStateOf(false) }
    var batteryDialog by remember { mutableStateOf(false) }
    var micRationale by remember { mutableStateOf(false) }
    var micDenied by remember { mutableStateOf(false) }
    val voice = remember { com.ctodrs.saqrcode.ui.chat.VoiceInput(ctx) }
    val speaker = remember { com.ctodrs.saqrcode.ui.chat.Speaker(ctx) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        speaker.onFail = { p ->
            val msg = when (p) {
                com.ctodrs.saqrcode.ui.chat.Speaker.Problem.NO_ENGINE -> R.string.tts_unavailable
                com.ctodrs.saqrcode.ui.chat.Speaker.Problem.NO_ARABIC_VOICE -> R.string.tts_no_arabic
                com.ctodrs.saqrcode.ui.chat.Speaker.Problem.NOTHING_TO_READ -> R.string.tts_nothing
            }
            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
        }
        onDispose { voice.release(); speaker.release() }
    }
    val appLang = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
    val voiceLang = com.ctodrs.saqrcode.agent.VoiceLocale.pick(appLang, settings.voiceLang.also { version })
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val sendRef = remember { arrayOfNulls<(String) -> Unit>(1) }
    fun startVoice() {
        voice.start(voiceLang, input, onText = { input = it }, onFinal = { text ->
            input = text
            if (settings.voiceAutoSend && text.isNotBlank() && !ws.agent.running) sendRef[0]?.invoke(text)
        }, onError = { code ->
            val msg = when (code) {
                -1 -> R.string.voice_no_service
                android.speech.SpeechRecognizer.ERROR_NO_MATCH, android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.voice_no_match
                android.speech.SpeechRecognizer.ERROR_NETWORK, android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> R.string.voice_network
                android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.voice_permission_denied
                else -> R.string.voice_error
            }
            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
        })
    }
    val activity = ctx as? android.app.Activity
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoice()
        else if (activity != null && !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, android.Manifest.permission.RECORD_AUDIO)) micDenied = true
    }
    fun onMic() {
        if (voice.listening) { voice.stop(); return }
        if (!voice.available) { android.widget.Toast.makeText(ctx, R.string.voice_no_service, android.widget.Toast.LENGTH_LONG).show(); return }
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) startVoice() else micRationale = true
    }
    fun onMicLongPress() {
        val next = com.ctodrs.saqrcode.agent.VoiceLocale.toggle(voiceLang)
        settings.voiceLang = next
        android.widget.Toast.makeText(ctx, if (next.startsWith("ar")) R.string.voice_lang_ar else R.string.voice_lang_en, android.widget.Toast.LENGTH_SHORT).show()
        if (voice.listening) { voice.cancel(); voice.start(next, input, { input = it }, { input = it }, { }) }
    }
    /** First turn that may run in the background: explain battery killers once, ask for notifications. */
    fun beforeFirstBackgroundRun() {
        if (!settings.keepInBackground) return
        if (android.os.Build.VERSION.SDK_INT >= 33 && !settings.notificationsAsked &&
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            settings.notificationsAsked = true
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else if (!settings.batteryTipShown) {
            val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            settings.batteryTipShown = true
            if (!pm.isIgnoringBatteryOptimizations(ctx.packageName)) batteryDialog = true
        }
    }

    // "Image → Code" on a web project: show the result in the preview when the turn ends.
    LaunchedEffect(ws.agent.running) { if (!ws.agent.running && previewWhenDone) { previewWhenDone = false; ws.tab = Pane.PREVIEW } }

    // Any agent activity may change files: refresh tree + clean editor tabs.
    LaunchedEffect(ws.agent.items.size, ws.agent.running) { ws.fsVersion++; ws.syncFromDisk() }

    val chatOverflow: @Composable () -> Unit = {
        ChatOverflow(onCheckpoints = { showCheckpoints = true }, onUsage = { showUsage = true }, onNewChat = { ws.agent.clear() },
            onSessions = { showSessions = true })
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            IdeDrawer(
                projectName = projectName, current = ws.tab,
                onPane = { ws.tab = it; scope.launch { drawer.close() } },
                onNewChat = { ws.agent.clear(); ws.tab = Pane.AGENT; scope.launch { drawer.close() } },
                onRestore = { showCheckpoints = true; scope.launch { drawer.close() } },
                onSessions = { showSessions = true; ws.tab = Pane.AGENT; scope.launch { drawer.close() } },
                onSettings = { scope.launch { drawer.close() }; onSettings() },
                onProjects = { scope.launch { drawer.close() }; onBack() },
            )
        },
    ) {
        IdeFrame(
            topBar = {
                IdeTopBar(
                    pane = ws.tab, onDrawer = { scope.launch { drawer.open() } },
                    onPane = { ws.tab = if (ws.tab == it) Pane.AGENT else it },
                    overflow = chatOverflow.takeIf { ws.tab == Pane.AGENT },
                    onSearch = { showPalette = true },
                    center = {
                        if (ws.tab == Pane.AGENT) PlanPillContent(ws.agent.plan, ws.agent.running, onPause = { ws.agent.stop() }, title = projectName)
                        else PaneTitle(ws.tab, projectName)
                    },
                )
            },
        ) { top ->
            AnimatedContent(ws.tab, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "pane") { tab ->
                when (tab) {
                    Pane.AGENT -> {
                        version
                        val def = Providers.byId(settings.activeProvider)
                        val cfg = settings.config(def.id)
                        val prices = remember(version) { settings.prices() }
                        val month = remember(ws.agent.usageVersion) { ws.agent.ledger.month() }
                        val session = ws.agent.sessionUsage.toMap()
                        val sessionTotal = session.values.fold(Usage.ZERO) { a, b -> a + b }
                        val reviewTotal = ws.agent.reviewUsage.values.fold(Usage.ZERO) { a, b -> a + b }
                        val plannerTotal = ws.agent.plannerUsage.values.fold(Usage.ZERO) { a, b -> a + b }
                        val state = ChatUiState(
                            items = ws.agent.items, running = ws.agent.running, step = ws.agent.step, maxSteps = AgentSession.MAX_STEPS,
                            contextTokens = ws.agent.contextTokens, contextWindow = def.contextWindow, providerId = def.id,
                            model = cfg.model, models = (listOf(cfg.model) + def.suggestedModels).filter { it.isNotBlank() }.distinct(),
                            ready = settings.activeReady(),
                            projectFiles = {
                                Tools.listFiles(ws.dir, ".", true, 200).lines().filter { !it.endsWith("/") && !it.contains("(skipped)") && it.isNotBlank() }
                                    .map { it.substringBeforeLast(" (") }
                            },
                            customCommands = settings.customCommands,
                            sessionTokens = sessionTotal.total, sessionCost = com.ctodrs.saqrcode.agent.PriceTable.cost(session, prices),
                            usageEstimated = sessionTotal.estimated,
                            reviewTokens = reviewTotal.total, reviewCost = com.ctodrs.saqrcode.agent.PriceTable.cost(ws.agent.reviewUsage.toMap(), prices),
                            plannerTokens = plannerTotal.total, plannerCost = com.ctodrs.saqrcode.agent.PriceTable.cost(ws.agent.plannerUsage.toMap(), prices),
                            budget = settings.monthlyBudget,
                            monthSpent = com.ctodrs.saqrcode.agent.PriceTable.cost(month, prices) ?: 0.0,
                            pendingImages = pendingImages.toList(), imageToCode = imageToCode,
                            voice = com.ctodrs.saqrcode.ui.chat.VoiceUi(voice.available, voice.listening, voiceLang, voice.level),
                            speakingKey = speaker.speakingKey,
                        )
                        sendRef[0] = { text ->
                            if (voice.listening) voice.cancel()
                            val local = SlashCommands.parse(text, state.customCommands)?.command?.action == SlashCommand.Action.CLEAR
                            if (!local && Budget.needsConfirmation(state.monthSpent, state.budget)) budgetPending = text
                            else { if (!local) beforeFirstBackgroundRun(); submit(text) }
                            input = ""
                        }
                        Box(Modifier.imePadding()) {
                            ChatContent(
                                state, input, { input = it },
                                onSend = { sendRef[0]?.invoke(input) },
                                onStop = { ws.agent.stop() },
                                onDecide = { a, ok -> ws.agent.decide(a, ok) },
                                onPickModel = { settings.setModel(def.id, it) }, onManageProviders = onSettings, topInset = top,
                                onAnswer = { a, skip -> ws.agent.answer(a, skip) },
                                onMeter = { showUsage = true },
                                onRestore = { askRestore(it.checkpointId) },
                                onImageAction = { onImageAction(it) },
                                onRemoveImage = { i -> if (i in pendingImages.indices) pendingImages.removeAt(i); if (pendingImages.isEmpty()) imageToCode = false },
                                onMic = { onMic() }, onMicLongPress = { onMicLongPress() },
                                onSpeak = { a -> speaker.toggle(a.key, a.text) },
                                onResumeTask = { beforeFirstBackgroundRun(); ws.agent.resumeInterrupted() },
                            )
                        }
                    }
                    Pane.FILES -> Box(Modifier.padding(top = top).imePadding()) { FilesPane(ws) }
                    Pane.TERMINAL -> Box(Modifier.padding(top = top).imePadding()) { TerminalPane(ws) }
                    Pane.GIT -> Box(Modifier.padding(top = top).imePadding()) { GitPane(ws, onAccounts) }
                    Pane.BUILD -> Box(Modifier.padding(top = top)) { BuildPane(ws, onPaywall) }
                    else -> Box(Modifier.padding(top = top)) { PreviewPane(ws) }
                }
            }
        }
    }

    if (showCheckpoints) {
        val points = remember(ws.agent.items.size) { ws.agent.checkpoints.list() }
        CheckpointsSheet(points, remember(points) { ws.agent.checkpoints.sizeBytes() },
            onPick = { showCheckpoints = false; askRestore(it.id) }, onDismiss = { showCheckpoints = false })
    }
    if (showSessions) {
        com.ctodrs.saqrcode.ui.chat.SessionsSheet(ws.agent, onOpened = { speaker.stop(); ws.tab = Pane.AGENT }, onDismiss = { showSessions = false })
    }
    // v0.12: the command palette — files, slash commands, settings destinations and sessions.
    if (showPalette) {
        val paletteItems = remember(showPalette) {
            val files = runCatching {
                com.ctodrs.saqrcode.agent.Tools.listFiles(ws.dir, ".", true, 400)
                    .lines().filter { !it.endsWith("/") && !it.contains("(skipped)") && it.isNotBlank() }
                    .map { it.substringBeforeLast(" (") }
            }.getOrDefault(emptyList())
            PaletteLogic.build(files, SlashCommands.all(settings.customCommands))
        }
        CommandPaletteDialog(
            visible = showPalette,
            onDismiss = { showPalette = false },
            items = paletteItems,
            onSelect = { item ->
                showPalette = false
                when (item.kind) {
                    PaletteKind.FILE -> ws.open(item.key)
                    PaletteKind.COMMAND -> input = "/" + item.key + " "
                    PaletteKind.SETTING -> onSettings()
                    PaletteKind.SESSION -> { ws.tab = Pane.AGENT; showSessions = true }
                }
            },
        )
    }
    if (micRationale) {
        val c = Saqr.colors
        AlertDialog(
            onDismissRequest = { micRationale = false }, containerColor = c.surface1,
            icon = { SIcon(Lucide.Mic, size = 24.dp, tint = c.accentText) },
            title = { Text(stringResource(R.string.mic_rationale_title), style = Saqr.type.title2) },
            text = { Text(stringResource(R.string.mic_rationale_body), style = Saqr.type.body) },
            confirmButton = { TextButton(onClick = { micRationale = false; micPermission.launch(android.Manifest.permission.RECORD_AUDIO) }) { Text(stringResource(R.string.allow), color = c.accentText) } },
            dismissButton = { TextButton(onClick = { micRationale = false }) { Text(stringResource(R.string.not_now), color = c.text2) } },
        )
    }
    if (micDenied) {
        val c = Saqr.colors
        AlertDialog(
            onDismissRequest = { micDenied = false }, containerColor = c.surface1,
            icon = { SIcon(Lucide.Mic, size = 24.dp, tint = c.danger) },
            title = { Text(stringResource(R.string.mic_denied_title), style = Saqr.type.title2) },
            text = { Text(stringResource(R.string.mic_denied_body), style = Saqr.type.body) },
            confirmButton = { TextButton(onClick = {
                micDenied = false
                runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + ctx.packageName))) }
            }) { Text(stringResource(R.string.open_app_settings), color = c.accentText) } },
            dismissButton = { TextButton(onClick = { micDenied = false }) { Text(stringResource(R.string.cancel), color = c.text2) } },
        )
    }
    if (batteryDialog) BatteryDialog(onDismiss = { batteryDialog = false })
    if (showUsage) {
        version
        val summary = UsageSummary(
            ws.agent.sessionUsage.toMap(), ws.agent.ledger.day(), ws.agent.ledger.month(), settings.prices(), settings.monthlyBudget,
            review = ws.agent.reviewUsage.toMap(), planner = ws.agent.plannerUsage.toMap(),
        )
        UsageSheet(summary, onDismiss = { showUsage = false })
    }
    confirmRestore?.let { (p, plan) ->
        RestoreConfirmDialog(p, plan, onDismiss = { confirmRestore = null }, onConfirm = {
            if (!ws.agent.running) {
                ws.agent.restoreCheckpoint(p)
                ws.fsVersion++; ws.syncFromDisk()
            }
            confirmRestore = null
        })
    }
    visionPending?.let { text ->
        val def = Providers.byId(settings.activeProvider)
        val model = settings.config(def.id).model
        val suggestion = remember(model, version) { Vision.suggestSwitch(def.suggestedModels.filter { it != model }, settings.visionOverrides) }
        VisionWarningDialog(
            model, suggestion,
            onSwitch = { m -> settings.setModel(def.id, m); visionPending = null; sendWithImages(text) },
            onTextOnly = { visionPending = null; sendWithImages(text, withImages = false) },
            onDismiss = { visionPending = null; input = text },
        )
    }
    budgetPending?.let { text ->
        val c = Saqr.colors
        val budget = settings.monthlyBudget ?: 0.0
        val spent = com.ctodrs.saqrcode.agent.PriceTable.cost(ws.agent.ledger.month(), settings.prices()) ?: 0.0
        AlertDialog(
            onDismissRequest = { budgetPending = null; input = text },
            containerColor = c.surface1,
            icon = { SIcon(Lucide.TriangleAlert, size = 24.dp, tint = c.danger) },
            title = { Text(stringResource(R.string.budget_confirm_title), style = Saqr.type.title2) },
            text = { Text(stringResource(R.string.budget_confirm_body, com.ctodrs.saqrcode.ui.chat.ltr(formatUsd(spent)), com.ctodrs.saqrcode.ui.chat.ltr(formatUsd(budget))), style = Saqr.type.body) },
            confirmButton = { TextButton(onClick = { budgetPending = null; beforeFirstBackgroundRun(); submit(text) }) { Text(stringResource(R.string.send_anyway), color = c.danger) } },
            dismissButton = { TextButton(onClick = { budgetPending = null; input = text }) { Text(stringResource(R.string.cancel), color = c.text2) } },
        )
    }
}

/** One-time explanation of OEM battery killers (Infinix/XOS, Xiaomi…) with shortcuts to the right settings. */
@Composable
fun BatteryDialog(onDismiss: () -> Unit) {
    val c = Saqr.colors
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.surface1,
        icon = { SIcon(Lucide.BatteryCharging, size = 26.dp, tint = c.accentText) },
        title = { Text(stringResource(R.string.battery_title), style = Saqr.type.title2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                Text(stringResource(R.string.battery_body), style = Saqr.type.body, color = c.text1)
                Text(stringResource(R.string.battery_oem), style = Saqr.type.bodySmall, color = c.text2)
                Row(Modifier.fillMaxWidth().padding(top = Sp.xs), horizontalArrangement = Arrangement.spacedBy(Sp.sm)) {
                    com.ctodrs.saqrcode.ui.components.SecondaryButton(stringResource(R.string.battery_settings), {
                        runCatching {
                            ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:" + ctx.packageName)))
                        }
                        onDismiss()
                    }, Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                @android.annotation.SuppressLint("BatteryLife")
                val i = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:" + ctx.packageName))
                runCatching { ctx.startActivity(i) }.onFailure {
                    runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                }
                onDismiss()
            }) { Text(stringResource(R.string.battery_allow), color = c.accentText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.later), color = c.text2) } },
    )
}

@Composable
private fun ChatOverflow(onCheckpoints: () -> Unit, onUsage: () -> Unit, onNewChat: () -> Unit, onSessions: () -> Unit = {}) {
    val c = Saqr.colors
    var open by remember { mutableStateOf(false) }
    Box {
        PillIcon(Lucide.EllipsisVertical, stringResource(R.string.more), { open = true })
        androidx.compose.material3.DropdownMenu(open, { open = false }, Modifier.background(c.surface2)) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.sessions), style = Saqr.type.label, color = c.text1) },
                leadingIcon = { SIcon(Lucide.MessagesSquare, size = 16.dp, tint = c.accentText) },
                onClick = { open = false; onSessions() },
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.checkpoints), style = Saqr.type.label, color = c.text1) },
                leadingIcon = { SIcon(Lucide.History, size = 16.dp, tint = c.accentText) },
                onClick = { open = false; onCheckpoints() },
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.usage_title), style = Saqr.type.label, color = c.text1) },
                leadingIcon = { SIcon(Lucide.Zap, size = 16.dp, tint = c.accentText) },
                onClick = { open = false; onUsage() },
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.clear_chat), style = Saqr.type.label, color = c.text1) },
                leadingIcon = { SIcon(Lucide.MessageSquarePlus, size = 16.dp, tint = c.text2) },
                onClick = { open = false; onNewChat() },
            )
        }
    }
}

/** Content with a floating glass top bar over a soft scrim. `content` receives the top inset to pad by. */
@Composable
fun IdeFrame(topBar: @Composable () -> Unit, content: @Composable (Dp) -> Unit) {
    val c = Saqr.colors
    Box(Modifier.fillMaxSize().background(c.bg)) {
        Box(Modifier.fillMaxSize()) { content(TopBarHeight + statusBarHeight()) }
        Box(Modifier.fillMaxWidth().height(TopBarHeight + statusBarHeight() + 20.dp)
            .background(Brush.verticalGradient(0f to c.bg, 0.62f to c.bg.copy(alpha = 0.92f), 1f to Color.Transparent)))
        Box(Modifier.statusBarsPadding()) { topBar() }
    }
}

@Composable
private fun statusBarHeight(): Dp {
    val insets = androidx.compose.foundation.layout.WindowInsets.statusBars
    val d = androidx.compose.ui.platform.LocalDensity.current
    return with(d) { insets.getTop(d).toDp() }
}

@Composable
fun IdeTopBar(pane: Int, onDrawer: () -> Unit, onPane: (Int) -> Unit, overflow: (@Composable () -> Unit)? = null, center: @Composable () -> Unit, onSearch: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().height(TopBarHeight).padding(horizontal = Sp.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(Lucide.PanelLeft, stringResource(R.string.menu), onDrawer)
        onSearch?.let { GlassIconButton(Lucide.Search, stringResource(R.string.palette_open), it) }
        Spacer(Modifier.weight(1f))
        GlassPill { center() }
        Spacer(Modifier.weight(1f))
        GlassPill {
            PillIcon(Lucide.Globe, stringResource(R.string.tab_preview), { onPane(Pane.PREVIEW) }, active = pane == Pane.PREVIEW)
            PillIcon(Lucide.SquareTerminal, stringResource(R.string.tab_terminal), { onPane(Pane.TERMINAL) }, active = pane == Pane.TERMINAL)
            PillIcon(Lucide.Package, stringResource(R.string.tab_build), { onPane(Pane.BUILD) }, active = pane == Pane.BUILD)
            overflow?.invoke()
        }
    }
}

@Composable
fun PaneTitle(pane: Int, projectName: String) {
    val c = Saqr.colors
    val (icon, label) = paneMeta(pane)
    Row(Modifier.padding(horizontal = Sp.sm), verticalAlignment = Alignment.CenterVertically) {
        SIcon(icon, size = 17.dp, tint = c.accentText)
        Spacer(Modifier.width(Sp.sm))
        Text(stringResource(label), style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1, maxLines = 1)
        Text(" · $projectName", style = Saqr.type.label, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 110.dp))
    }
}

fun paneMeta(pane: Int): Pair<ImageVector, Int> = when (pane) {
    Pane.FILES -> Lucide.FileCode to R.string.tab_files
    Pane.TERMINAL -> Lucide.SquareTerminal to R.string.tab_terminal
    Pane.GIT -> Lucide.GitBranch to R.string.tab_git
    Pane.PREVIEW -> Lucide.Globe to R.string.tab_preview
    Pane.BUILD -> Lucide.Package to R.string.tab_build
    else -> Lucide.Sparkles to R.string.tab_agent
}

@Composable
fun IdeDrawer(
    projectName: String, current: Int, onPane: (Int) -> Unit, onNewChat: () -> Unit, onRestore: () -> Unit,
    onSettings: () -> Unit, onProjects: () -> Unit, onSessions: () -> Unit = {},
) {
    val c = Saqr.colors
    ModalDrawerSheet(
        drawerContainerColor = c.surface1, drawerContentColor = c.text1,
        drawerShape = RoundedCornerShape(topEnd = Radius.xl, bottomEnd = Radius.xl),
        modifier = Modifier.widthIn(max = 316.dp),
    ) {
        Column(Modifier.fillMaxHeight().statusBarsPadding().padding(Sp.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FalconMark(44.dp)
                Spacer(Modifier.width(Sp.md))
                Column(Modifier.weight(1f)) {
                    Text(projectName, style = Saqr.type.title3.copy(fontWeight = FontWeight.SemiBold), color = c.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.app_name), style = Saqr.type.caption, color = c.text3)
                }
            }
            Spacer(Modifier.height(Sp.xl))
            DrawerAction(Lucide.MessageSquarePlus, stringResource(R.string.clear_chat), onNewChat, highlight = true)
            Spacer(Modifier.height(Sp.lg))
            Text(stringResource(R.string.workspace), style = Saqr.type.micro, color = c.text3, modifier = Modifier.padding(horizontal = Sp.md, vertical = Sp.xs))
            listOf(Pane.AGENT, Pane.FILES, Pane.TERMINAL, Pane.GIT, Pane.PREVIEW, Pane.BUILD).forEach { p ->
                val (icon, label) = paneMeta(p)
                DrawerItem(icon, stringResource(label), selected = current == p) { onPane(p) }
            }
            Spacer(Modifier.height(Sp.lg))
            Text(stringResource(R.string.more), style = Saqr.type.micro, color = c.text3, modifier = Modifier.padding(horizontal = Sp.md, vertical = Sp.xs))
            DrawerItem(Lucide.MessagesSquare, stringResource(R.string.sessions), false, onSessions)
            DrawerItem(Lucide.History, stringResource(R.string.checkpoints), false, onRestore)
            DrawerItem(Lucide.Settings, stringResource(R.string.settings), false, onSettings)
            Spacer(Modifier.weight(1f))
            DrawerItem(Lucide.House, stringResource(R.string.all_projects), false, onProjects)
        }
    }
}

@Composable
private fun DrawerAction(icon: ImageVector, label: String, onClick: () -> Unit, highlight: Boolean) {
    val c = Saqr.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(if (highlight) c.accentSoft else c.surface2)
            .border(1.dp, if (highlight) c.accent.copy(alpha = 0.35f) else c.border, RoundedCornerShape(Radius.md))
            .clickable(onClick = onClick).padding(horizontal = Sp.md, vertical = Sp.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SIcon(icon, size = 19.dp, tint = c.accentText)
        Spacer(Modifier.width(Sp.md))
        Text(label, style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1)
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val c = Saqr.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(Radius.sm))
            .background(if (selected) c.surface3 else Color.Transparent).clickable(onClick = onClick).padding(horizontal = Sp.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SIcon(icon, size = 19.dp, tint = if (selected) c.accentText else c.text2)
        Spacer(Modifier.width(Sp.md))
        Text(label, style = Saqr.type.label, color = if (selected) c.text1 else c.text2, modifier = Modifier.weight(1f))
        if (selected) Box(Modifier.size(6.dp).clip(CircleShape).background(c.accent))
    }
}
