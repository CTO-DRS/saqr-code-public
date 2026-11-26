package com.ctodrs.saqrcode.ui

import androidx.compose.ui.semantics.Role
import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.SaqrApp
import com.ctodrs.saqrcode.agent.CoachSnapshot
import com.ctodrs.saqrcode.agent.SaqrCoach
import com.ctodrs.saqrcode.billing.PaywallDialog
import com.ctodrs.saqrcode.data.Project
import com.ctodrs.saqrcode.data.SaqrPack
import com.ctodrs.saqrcode.data.SaqrPackCodec
import com.ctodrs.saqrcode.data.Template
import com.ctodrs.saqrcode.ui.components.FalconMark
import com.ctodrs.saqrcode.ui.components.latinDigits
import com.ctodrs.saqrcode.ui.components.GlassIconButton
import com.ctodrs.saqrcode.ui.components.PrimaryButton
import com.ctodrs.saqrcode.ui.components.SaqrCard
import com.ctodrs.saqrcode.ui.components.SaqrField
import com.ctodrs.saqrcode.ui.components.SectionLabel
import com.ctodrs.saqrcode.ui.components.pressable
import com.ctodrs.saqrcode.ui.icons.FileTypeIcon
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Radius
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp

data class ProjectCard(val name: String, val modified: Long, val files: Int, val mainFile: String, val branch: String? = null, val lastBuild: com.ctodrs.saqrcode.data.BuildRecord? = null)

data class TemplateInfo(val template: Template, val title: Int, val desc: Int, val icon: String, val accent: Color)

val templateGallery = listOf(
    TemplateInfo(Template.WEB, R.string.tpl_web, R.string.tpl_web_desc, "index.html", Color(0xFFEF7B4D)),
    TemplateInfo(Template.PYTHON, R.string.tpl_python, R.string.tpl_python_desc, "main.py", Color(0xFF4B9CE2)),
    TemplateInfo(Template.NODE, R.string.tpl_node, R.string.tpl_node_desc, "index.js", Color(0xFF8CC84B)),
    TemplateInfo(Template.COMPOSE, R.string.tpl_compose, R.string.tpl_compose_desc, "MainActivity.kt", Color(0xFF8A6FE8)),
    TemplateInfo(Template.IME, R.string.tpl_ime, R.string.tpl_ime_desc, "ArabicImeService.kt", Color(0xFF3BA9A0)),
    TemplateInfo(Template.LITE_WEB, R.string.tpl_lite, R.string.tpl_lite_desc, "index.html", Color(0xFF4F8CFF)),
    TemplateInfo(Template.EMPTY, R.string.tpl_empty, R.string.tpl_empty_desc, "README.md", Color(0xFF8FA3B8)),
)

fun Project.toCard(): ProjectCard {
    val files = dir.walkTopDown().onEnter { it.name !in setOf(".git", "node_modules", ".saqr") }.filter { it.isFile }.take(2000).toList()
    val main = listOf("index.html", "main.py", "index.js", "package.json", "README.md").firstOrNull { java.io.File(dir, it).isFile }
        ?: files.firstOrNull()?.name ?: "README.md"
    return ProjectCard(name, modified, files.size, main)
}

/** v0.12: the project's current git branch, read straight from .git/HEAD (null when not a repo). */
fun branchOf(dir: java.io.File): String? = runCatching {
    val head = java.io.File(dir, ".git/HEAD")
    if (!head.isFile) return null
    val s = head.readText().trim()
    when {
        s.startsWith("ref: refs/heads/") -> s.removePrefix("ref: refs/heads/").take(60)
        s.length >= 7 -> s.take(7)
        else -> null
    }
}.getOrNull()

@Composable
fun HomeScreen(onOpen: (String) -> Unit, onSettings: () -> Unit, onPaywall: () -> Unit = {}) {
    val app = SaqrApp.instance
    val version by app.settings.version.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    val projects = remember(refresh) {
        val builds = app.builds
        app.projects.list().map { p -> p.toCard().copy(branch = branchOf(p.dir), lastBuild = builds.last(p.name)) }
    }
    var create by remember { mutableStateOf<Template?>(null) }
    // v0.14 skill packs: installed .saqrpack bundles appear as home-screen templates
    var createPack by remember { mutableStateOf<SaqrPack?>(null) }
    val packs = remember(version, refresh) { SaqrPackCodec.decodePacks(app.settings.packsJson) }
    var toDelete by remember { mutableStateOf<String?>(null) }
    var gate by remember { mutableStateOf(false) }
    val c = Saqr.colors

    // v0.11: free tier caps the project count — attempts to create beyond it open the paywall.
    val limits = app.settings.limits()
    val capReached = !limits.unlimitedProjects && projects.size >= limits.maxProjects

    // v0.13.3 Saqr Coach: real signals only — a stored key, a project, the first command, a build.
    val coachSnap = remember(version, refresh) {
        val s = app.settings
        CoachSnapshot(
            hasKey = s.activeReady(),
            hasProject = projects.isNotEmpty(),
            hasFirstMessage = s.coachFirstMessageAt > 0L,
            hasBuildAttempt = app.builds.anyAttempt(),
            hasSuccessfulBuild = app.builds.anySuccess(),
        )
    }
    val coachProgress = remember(coachSnap) { app.settings.coachProgress() }
    var coachOpen by remember { mutableStateOf(false) }
    // stamp newly-completed steps with the time they first happened (the coach remembers its story)
    androidx.compose.runtime.LaunchedEffect(coachSnap) {
        val cur = app.settings.coachProgress()
        val merged = com.ctodrs.saqrcode.agent.CoachProgressCodec.merge(
            cur, SaqrCoach.steps(coachSnap).filter { it.done }.map { it.id }, System.currentTimeMillis())
        if (merged != cur) app.settings.coachProgressJson = com.ctodrs.saqrcode.agent.CoachProgressCodec.encode(merged)
    }
    // starter prompts follow the newest project's template family
    val coachTemplate = remember(refresh) {
        val latest = app.projects.list().firstOrNull()
        if (latest == null) "WEB"
        else SaqrCoach.guessTemplate(
            latest.dir.walkTopDown().onEnter { it.name !in setOf(".git", "node_modules", ".saqr") }
                .filter { it.isFile }.take(60).map { it.name }.toList())
    }

    HomeContent(
        projects = projects, needsProvider = version.let { !app.settings.activeReady() },
        projectCapReached = capReached,
        packs = packs,
        coachSnap = coachSnap, coachProgress = coachProgress, coachDismissed = app.settings.coachDismissed,
        onOpen = onOpen, onSettings = onSettings, onPaywall = onPaywall,
        onCreate = { if (capReached) gate = true else create = it ?: Template.WEB },
        onCreatePack = { if (capReached) gate = true else createPack = it },
        onDelete = { toDelete = it },
        onCoach = { coachOpen = true },
    )

    if (coachOpen) CoachSheet(
        snap = coachSnap, progress = coachProgress, template = coachTemplate,
        onDismiss = { coachOpen = false },
        onHide = { app.settings.coachDismissed = true; coachOpen = false },
    )

    PaywallDialog(show = gate, onDismiss = { gate = false }, onOpenPaywall = onPaywall)

    create?.let { tpl ->
        CreateProjectDialog(
            initial = tpl,
            onDismiss = { create = null },
            onCreate = { name, t -> app.projects.create(name, t).onSuccess { create = null; refresh++; onOpen(it.name) } },
        )
    }
    // v0.14 skill packs: creating from an installed pack materializes its template files + rules
    createPack?.let { pk ->
        CreatePackDialog(
            pack = pk,
            onDismiss = { createPack = null },
            onCreate = { name, p ->
                app.projects.create(name, SaqrPackCodec.packFiles(p, name))
                    .onSuccess { createPack = null; refresh++; onOpen(it.name) }
            },
        )
    }
    toDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { toDelete = null }, containerColor = c.surface1,
            icon = { SIcon(Lucide.Trash2, size = 24.dp, tint = c.danger) },
            text = { Text(stringResource(R.string.delete_project_q, name), style = Saqr.type.body) },
            confirmButton = {
                TextButton(onClick = { app.projects.get(name)?.let { app.projects.delete(it) }; app.builds.clear(name); toDelete = null; refresh++ }) {
                    Text(stringResource(R.string.delete), color = c.danger)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel), color = c.text2) } },
        )
    }
}

@Composable
fun HomeContent(
    projects: List<ProjectCard>, needsProvider: Boolean,
    projectCapReached: Boolean = false,
    packs: List<SaqrPack> = emptyList(),
    coachSnap: CoachSnapshot? = null,
    coachProgress: Map<String, Long> = emptyMap(),
    coachDismissed: Boolean = false,
    onOpen: (String) -> Unit, onSettings: () -> Unit, onPaywall: () -> Unit = {}, onCreate: (Template?) -> Unit,
    onCreatePack: (SaqrPack) -> Unit = {},
    onDelete: (String) -> Unit, onCoach: () -> Unit = {},
    now: Long = System.currentTimeMillis(),
) {
    val c = Saqr.colors
    LazyColumn(
        Modifier.fillMaxSize().background(c.bg),
        contentPadding = PaddingValues(start = Sp.lg, end = Sp.lg, bottom = Sp.xxxl),
        verticalArrangement = Arrangement.spacedBy(Sp.md),
    ) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = Sp.md, bottom = Sp.xs), verticalAlignment = Alignment.CenterVertically) {
                FalconMark(42.dp)
                Spacer(Modifier.width(Sp.md))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_name), style = Saqr.type.title2.copy(fontWeight = FontWeight.Bold), color = c.text1)
                    Text(stringResource(R.string.tagline), style = Saqr.type.caption, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                GlassIconButton(Lucide.Star, stringResource(R.string.paywall_title), onPaywall)
                Spacer(Modifier.width(Sp.sm))
                GlassIconButton(Lucide.Settings, stringResource(R.string.settings), onSettings)
            }
        }
        item { Hero(onNew = { onCreate(null) }) }
        if (needsProvider) item { SetupBanner(onSettings) }
        if (projectCapReached) item { UpgradeBanner(onPaywall) }
        // v0.13.3: the learning path to the first successful build (hidden once dismissed)
        if (coachSnap != null && SaqrCoach.visible(coachDismissed)) item { CoachCard(coachSnap, coachProgress, onCoach) }
        if (projects.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.recent), Modifier.padding(top = Sp.md)) { Text("${projects.size}", style = Saqr.type.monoMicro, color = c.text3) } }
            items(projects, key = { it.name }) { p -> ProjectRow(p, now, { onOpen(p.name) }, { onDelete(p.name) }) }
        }
        item { SectionLabel(stringResource(R.string.templates), Modifier.padding(top = Sp.md)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Sp.md)) {
                templateGallery.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Sp.md)) {
                        row.forEach { t -> TemplateCard(t, Modifier.weight(1f)) { onCreate(t.template) } }
                    }
                }
            }
        }
        // v0.14 skill packs: installed .saqrpack bundles create full projects in one tap
        if (packs.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.pack_packs_label), Modifier.padding(top = Sp.md)) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Sp.md)) {
                    packs.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Sp.md)) {
                            row.forEach { p -> PackCard(p, Modifier.weight(1f)) { onCreatePack(p) } }
                        }
                    }
                }
            }
        }
        if (projects.isEmpty()) item {
            Text(stringResource(R.string.no_projects), style = Saqr.type.caption, color = c.text3, modifier = Modifier.fillMaxWidth().padding(top = Sp.sm),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        item { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun Hero(onNew: () -> Unit) {
    val c = Saqr.colors
    val shape = RoundedCornerShape(Radius.xl)
    Box(Modifier.fillMaxWidth().clip(shape).background(c.surface1).border(1.dp, c.border, shape)) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Brush.radialGradient(listOf(c.accent.copy(alpha = if (c.isDark) 0.20f else 0.22f), Color.Transparent),
                center = Offset(size.width * 0.12f, size.height * 0.05f), radius = size.width * 0.75f),
                radius = size.width * 0.75f, center = Offset(size.width * 0.12f, size.height * 0.05f))
            // faint code grid lines
            val step = 22.dp.toPx()
            var y = step
            while (y < size.height) { drawLine(c.border.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), 1f); y += step }
        }
        Column(Modifier.padding(Sp.xl)) {
            Row(Modifier.clip(RoundedCornerShape(50)).background(c.accentSoft).padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                SIcon(Lucide.Sparkles, size = 13.dp, tint = c.accentText)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.hero_badge), style = Saqr.type.micro, color = c.accentText)
            }
            Spacer(Modifier.height(Sp.md))
            Text(stringResource(R.string.hero_title), style = Saqr.type.title1, color = c.text1)
            Spacer(Modifier.height(Sp.xs))
            Text(stringResource(R.string.hero_body), style = Saqr.type.bodySmall, color = c.text2)
            Spacer(Modifier.height(Sp.lg))
            PrimaryButton(stringResource(R.string.new_project), onNew, icon = Lucide.Plus)
        }
    }
}

@Composable
private fun SetupBanner(onSettings: () -> Unit) {
    val c = Saqr.colors
    SaqrCard(Modifier.fillMaxWidth(), color = c.warningBg, border = c.warningBorder, onClick = onSettings, padding = PaddingValues(Sp.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SIcon(Lucide.KeyRound, size = 20.dp, tint = c.warning)
            Spacer(Modifier.width(Sp.md))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.setup_title), style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1)
                Text(stringResource(R.string.setup_body), style = Saqr.type.caption, color = c.text2)
            }
            SIcon(Lucide.ChevronRight, size = 18.dp, tint = c.text3)
        }
    }
}

/** v0.11: shown when the free tier's project cap is reached — taps open the paywall. */
@Composable
private fun UpgradeBanner(onPaywall: () -> Unit) {
    val c = Saqr.colors
    SaqrCard(Modifier.fillMaxWidth(), color = c.accentSoft, onClick = onPaywall, padding = PaddingValues(Sp.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SIcon(Lucide.Star, size = 20.dp, tint = c.accentText)
            Spacer(Modifier.width(Sp.md))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.upgrade_banner_title), style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1)
                Text(stringResource(R.string.upgrade_banner_body), style = Saqr.type.caption, color = c.text2)
            }
            SIcon(Lucide.ChevronRight, size = 18.dp, tint = c.text3)
        }
    }
}

@Composable
private fun ProjectRow(p: ProjectCard, now: Long, onOpen: () -> Unit, onDelete: () -> Unit) {
    val c = Saqr.colors
    SaqrCard(Modifier.fillMaxWidth(), onClick = onOpen, padding = PaddingValues(start = Sp.lg, end = Sp.xs, top = Sp.md, bottom = Sp.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(c.surface2).border(1.dp, c.border, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                FileTypeIcon(p.mainFile, 24.dp)
            }
            Spacer(Modifier.width(Sp.md))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = Saqr.type.title3.copy(fontWeight = FontWeight.SemiBold, textDirection = TextDirection.Content), color = c.text1,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SIcon(Lucide.Clock, size = 12.dp, tint = c.text3)
                    Spacer(Modifier.width(4.dp))
                    Text(DateUtils.getRelativeTimeSpanString(p.modified, now, DateUtils.MINUTE_IN_MILLIS).toString().latinDigits(), style = Saqr.type.caption, color = c.text2, maxLines = 1)
                    Text("  ·  ", style = Saqr.type.caption, color = c.text3)
                    Text(stringResource(R.string.n_files, p.files.toString()), style = Saqr.type.caption, color = c.text2, maxLines = 1)
                }
                // v0.12: live card — last on-device build and the git branch, when they exist.
                if (p.lastBuild != null || p.branch != null) {
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        p.lastBuild?.let { b ->
                            SIcon(if (b.ok) Lucide.CircleCheck else Lucide.CircleAlert, size = 12.dp, tint = if (b.ok) c.success else c.danger)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(if (b.ok) R.string.build_last_ok else R.string.build_last_failed, DateUtils.getRelativeTimeSpanString(b.time, now, DateUtils.MINUTE_IN_MILLIS).toString().latinDigits()),
                                style = Saqr.type.caption, color = c.text2, maxLines = 1,
                            )
                        }
                        p.branch?.let { br ->
                            if (p.lastBuild != null) Text("  ·  ", style = Saqr.type.caption, color = c.text3)
                            SIcon(Lucide.GitBranch, size = 12.dp, tint = c.text3)
                            Spacer(Modifier.width(4.dp))
                            Text(br, style = Saqr.type.monoMicro, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onDelete), contentAlignment = Alignment.Center) {
                SIcon(Lucide.Trash2, size = 17.dp, tint = c.text3, contentDescription = stringResource(R.string.delete))
            }
        }
    }
}

@Composable
private fun TemplateCard(t: TemplateInfo, modifier: Modifier, onClick: () -> Unit) {
    val c = Saqr.colors
    SaqrCard(modifier.heightIn(min = 132.dp), onClick = onClick, padding = PaddingValues(Sp.lg)) {
        Column {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(t.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                FileTypeIcon(t.icon, 24.dp)
            }
            Spacer(Modifier.height(Sp.md))
            Text(stringResource(t.title), style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1, maxLines = 1)
            Text(stringResource(t.desc), style = Saqr.type.caption, color = c.text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun CreateProjectDialog(initial: Template, onDismiss: () -> Unit, onCreate: (String, Template) -> Result<*>) {
    val c = Saqr.colors
    var name by remember { mutableStateOf("") }
    var tpl by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.surface1,
        title = { Text(stringResource(R.string.new_project), style = Saqr.type.title2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Sp.md)) {
                SaqrField(name, { name = it; error = null }, label = stringResource(R.string.project_name), placeholder = "my-app",
                    isError = error != null, supporting = error)
                Text(stringResource(R.string.template), style = Saqr.type.caption.copy(fontWeight = FontWeight.Medium), color = c.text2)
                templateGallery.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Sp.sm)) {
                        row.forEach { t ->
                            val sel = tpl == t.template
                            Row(
                                Modifier.weight(1f).heightIn(min = 46.dp).clip(RoundedCornerShape(Radius.sm))
                                    .background(if (sel) c.accentSoft else c.surface2)
                                    .border(1.dp, if (sel) c.accent.copy(alpha = 0.55f) else c.border, RoundedCornerShape(Radius.sm))
                                    .pressable { tpl = t.template }.padding(horizontal = Sp.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FileTypeIcon(t.icon, 18.dp)
                                Spacer(Modifier.width(Sp.sm))
                                Text(stringResource(t.title), style = Saqr.type.caption.copy(fontWeight = FontWeight.Medium),
                                    color = if (sel) c.text1 else c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            PrimaryButton(stringResource(R.string.create), enabled = name.isNotBlank(), onClick = { onCreate(name, tpl).onFailure { error = it.message } })
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = c.text2) } },
    )
}

/** v0.14 skill pack: a home-screen template card for an installed .saqrpack bundle. */
@Composable
private fun PackCard(p: SaqrPack, modifier: Modifier, onClick: () -> Unit) {
    val c = Saqr.colors
    SaqrCard(modifier.heightIn(min = 132.dp), onClick = onClick, padding = PaddingValues(Sp.lg)) {
        Column {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                SIcon(Lucide.Package, size = 22.dp, tint = c.accentText)
            }
            Spacer(Modifier.height(Sp.md))
            Text(p.name, style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1, maxLines = 1)
            Text(p.description.ifBlank { p.nameAr }, style = Saqr.type.caption, color = c.text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** v0.14: create a project from an installed skill pack — the pack files (template + rules) are written as-is. */
@Composable
fun CreatePackDialog(pack: SaqrPack, onDismiss: () -> Unit, onCreate: (String, SaqrPack) -> Result<*>) {
    val c = Saqr.colors
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.surface1,
        title = { Text(stringResource(R.string.new_project), style = Saqr.type.title2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Sp.md)) {
                SaqrField(name, { name = it; error = null }, label = stringResource(R.string.project_name), placeholder = "my-app",
                    isError = error != null, supporting = error)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).clip(RoundedCornerShape(Radius.sm)).background(c.accentSoft), contentAlignment = Alignment.Center) {
                        SIcon(Lucide.Package, size = 16.dp, tint = c.accentText)
                    }
                    Spacer(Modifier.width(Sp.sm))
                    Column {
                        Text(pack.description.ifBlank { pack.name }, style = Saqr.type.caption, color = c.text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (pack.templateRef.isNotBlank()) {
                            Text(stringResource(R.string.pack_template_of, pack.templateRef), style = Saqr.type.micro, color = c.text3)
                        }
                    }
                }
            }
        },
        confirmButton = {
            PrimaryButton(stringResource(R.string.create), enabled = name.isNotBlank(), onClick = { onCreate(name, pack).onFailure { error = it.message } })
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = c.text2) } },
    )
}
