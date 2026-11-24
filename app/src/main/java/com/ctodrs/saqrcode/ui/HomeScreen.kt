package com.ctodrs.saqrcode.ui

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
import com.ctodrs.saqrcode.billing.PaywallDialog
import com.ctodrs.saqrcode.data.Project
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

data class ProjectCard(val name: String, val modified: Long, val files: Int, val mainFile: String)

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

@Composable
fun HomeScreen(onOpen: (String) -> Unit, onSettings: () -> Unit, onPaywall: () -> Unit = {}) {
    val app = SaqrApp.instance
    val version by app.settings.version.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    val projects = remember(refresh) { app.projects.list().map { it.toCard() } }
    var create by remember { mutableStateOf<Template?>(null) }
    var toDelete by remember { mutableStateOf<String?>(null) }
    var gate by remember { mutableStateOf(false) }
    val c = Saqr.colors

    // v0.11: free tier caps the project count — attempts to create beyond it open the paywall.
    val limits = app.settings.limits()
    val capReached = !limits.unlimitedProjects && projects.size >= limits.maxProjects

    HomeContent(
        projects = projects, needsProvider = version.let { !app.settings.activeReady() },
        projectCapReached = capReached,
        onOpen = onOpen, onSettings = onSettings, onPaywall = onPaywall,
        onCreate = { if (capReached) gate = true else create = it ?: Template.WEB }, onDelete = { toDelete = it },
    )

    PaywallDialog(show = gate, onDismiss = { gate = false }, onOpenPaywall = onPaywall)

    create?.let { tpl ->
        CreateProjectDialog(
            initial = tpl,
            onDismiss = { create = null },
            onCreate = { name, t -> app.projects.create(name, t).onSuccess { create = null; refresh++; onOpen(it.name) } },
        )
    }
    toDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { toDelete = null }, containerColor = c.surface1,
            icon = { SIcon(Lucide.Trash2, size = 24.dp, tint = c.danger) },
            text = { Text(stringResource(R.string.delete_project_q, name), style = Saqr.type.body) },
            confirmButton = {
                TextButton(onClick = { app.projects.get(name)?.let { app.projects.delete(it) }; toDelete = null; refresh++ }) {
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
    onOpen: (String) -> Unit, onSettings: () -> Unit, onPaywall: () -> Unit = {}, onCreate: (Template?) -> Unit, onDelete: (String) -> Unit,
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
            }
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(50)).clickable(onClick = onDelete), contentAlignment = Alignment.Center) {
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
