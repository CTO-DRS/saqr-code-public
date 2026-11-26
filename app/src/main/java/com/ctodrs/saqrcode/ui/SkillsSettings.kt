package com.ctodrs.saqrcode.ui

import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.agent.AgentRoles
import com.ctodrs.saqrcode.agent.Risk
import com.ctodrs.saqrcode.agent.ToolRegistry
import com.ctodrs.saqrcode.agent.skills.InstallPlan
import com.ctodrs.saqrcode.agent.skills.InstalledSkill
import com.ctodrs.saqrcode.agent.skills.SkillIssue
import com.ctodrs.saqrcode.agent.skills.SkillManager
import com.ctodrs.saqrcode.agent.skills.SkillRuntime
import com.ctodrs.saqrcode.agent.skills.SkillSource
import com.ctodrs.saqrcode.data.GalleryEntry
import com.ctodrs.saqrcode.data.GalleryKit
import com.ctodrs.saqrcode.data.SettingsStore
import com.ctodrs.saqrcode.ui.chat.SaqrSheet
import com.ctodrs.saqrcode.ui.chat.roleName
import com.ctodrs.saqrcode.ui.components.GhostButton
import com.ctodrs.saqrcode.ui.components.ListRow
import com.ctodrs.saqrcode.ui.components.RowDivider
import com.ctodrs.saqrcode.ui.components.SaqrField
import com.ctodrs.saqrcode.ui.components.SaqrSwitch
import com.ctodrs.saqrcode.ui.components.Segmented
import com.ctodrs.saqrcode.ui.components.Tag
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Radius
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v0.18 «المهارات · Skills» — settings switches, the skills manager sheet, and the one install
 * flow every source shares (gallery, file, paste): plan → refusal reasons or an update dialog
 * with the changelog diff → apply. Everything here acts on real installed data; no button is a
 * placeholder.
 */
class SkillsUi(
    val installed: List<InstalledSkill> = emptyList(),
    val skillsEnabled: Boolean = true,
    val onSkillsEnabled: (Boolean) -> Unit = {},
    val autoAttach: Boolean = false,
    val onAutoAttach: (Boolean) -> Unit = {},
    val topN: Int = SkillRuntime.DEFAULT_TOP_N,
    val onTopN: (Int) -> Unit = {},
    /** The cached gallery entries (for «update available») — parsed only when the manager opens. */
    val gallery: () -> List<GalleryEntry> = { emptyList() },
    val plan: (raw: String, source: SkillSource, sha: String?) -> InstallPlan = { _, _, _ -> InstallPlan.Refused(emptyList()) },
    val apply: (InstallPlan.Ready) -> Unit = {},
    val onEnabled: (String, Boolean) -> Unit = { _, _ -> },
    val onPrivate: (String, Boolean) -> Unit = { _, _ -> },
    val onRate: (String, Int) -> Unit = { _, _ -> },
    val onUninstall: (String) -> Unit = {},
    /** (name, instructions, category, tags) → issues (empty = created). */
    val onCreateLocal: (String, String, String, List<String>) -> List<SkillIssue> = { _, _, _, _ -> emptyList() },
) {
    companion object {
        fun from(settings: SettingsStore): SkillsUi = SkillsUi(
            installed = settings.installedSkills,
            skillsEnabled = settings.skillsEnabled, onSkillsEnabled = { settings.skillsEnabled = it },
            autoAttach = settings.autoAttachSkills, onAutoAttach = { settings.autoAttachSkills = it },
            topN = settings.skillsTopN, onTopN = { settings.skillsTopN = it },
            gallery = { GalleryKit.parseIndex(settings.galleryCache)?.entries ?: emptyList() },
            plan = { raw, src, sha -> SkillManager.plan(raw, src, settings.installedSkills, sha) },
            apply = { p -> applyPlan(settings, p) },
            onEnabled = { id, on -> settings.installedSkills = SkillManager.setEnabled(settings.installedSkills, id, on) },
            onPrivate = { id, on -> settings.installedSkills = SkillManager.setPrivate(settings.installedSkills, id, on) },
            onRate = { id, n -> settings.installedSkills = SkillManager.rate(settings.installedSkills, id, n) },
            onUninstall = { id -> settings.installedSkills = SkillManager.uninstall(settings.installedSkills, id) },
            onCreateLocal = { name, text, cat, tags ->
                val (list, issues) = SkillManager.createLocal(settings.installedSkills, name, text, cat, tags, System.currentTimeMillis())
                if (issues.isEmpty()) settings.installedSkills = list
                issues
            },
        )

        /** One install for every source: skills + the v0.14 pack half (template, commands), stored together. */
        fun applyPlan(settings: SettingsStore, p: InstallPlan.Ready) {
            val now = System.currentTimeMillis()
            if (p.pack.skills.isNotEmpty()) settings.installedSkills = SkillManager.apply(p, settings.installedSkills, now)
            val (cmds, packs) = SkillManager.applyPackPart(p, settings.customCommands,
                com.ctodrs.saqrcode.data.SaqrPackCodec.decodePacks(settings.packsJson), now)
            settings.customCommands = cmds
            if (p.hasProjectPart) settings.packsJson = com.ctodrs.saqrcode.data.SaqrPackCodec.encodePacks(packs)
        }
    }
}

/** The shared install flow: a refusal or update dialog when needed, a direct install otherwise. */
@Stable
class SkillInstaller internal constructor(private val uiRef: () -> SkillsUi) {
    private val ui get() = uiRef()
    internal var pending by mutableStateOf<InstallPlan?>(null)
    internal var done: ((Boolean) -> Unit)? = null

    /**
     * [onResult] gets true once installed (now, or after the user confirms an update) and false
     * when refused or cancelled; [onRefused] runs only for a refusal (its reasons are in the dialog).
     */
    fun install(raw: String, source: SkillSource, sha: String? = null, onRefused: () -> Unit = {}, onResult: (Boolean) -> Unit = {}) {
        when (val p = ui.plan(raw, source, sha?.takeIf { it.isNotBlank() })) {
            is InstallPlan.Refused -> { pending = p; onRefused(); onResult(false) }
            is InstallPlan.Ready -> if (p.updates.isNotEmpty()) { pending = p; done = onResult } else { ui.apply(p); onResult(true) }
        }
    }

    internal fun confirm() {
        val p = pending as? InstallPlan.Ready ?: return
        ui.apply(p); pending = null
        done?.invoke(true); done = null
    }

    internal fun dismiss() { pending = null; done?.invoke(false); done = null }
}

@Composable
fun rememberSkillInstaller(ui: SkillsUi): SkillInstaller {
    // the settings screen rebuilds SkillsUi on every change; the installer (and an open dialog) must survive that
    val latest = androidx.compose.runtime.rememberUpdatedState(ui)
    val inst = remember { SkillInstaller { latest.value } }
    SkillInstallDialogs(inst)
    return inst
}

@Composable
internal fun issueLabel(code: String): String = stringResource(when (code) {
    "json" -> R.string.skill_err_json
    "size" -> R.string.skill_err_size
    "empty" -> R.string.skill_err_empty
    "template" -> R.string.skill_err_template
    "path" -> R.string.skill_err_path
    "command" -> R.string.skill_err_command
    "duplicate" -> R.string.skill_err_duplicate
    "version" -> R.string.skill_err_version
    "checksum" -> R.string.skill_err_checksum
    "conflict" -> R.string.skill_err_conflict
    "tampered" -> R.string.skill_err_tampered
    "same_version_changed" -> R.string.skill_err_same_version_changed
    "downgrade" -> R.string.skill_err_downgrade
    "id" -> R.string.skill_err_id
    "name" -> R.string.skill_err_name
    "category" -> R.string.skill_err_category
    "tags" -> R.string.skill_err_tags
    "role" -> R.string.skill_err_role
    "risk" -> R.string.skill_err_risk
    "permission_unknown" -> R.string.skill_err_permission_unknown
    "permission_high" -> R.string.skill_err_permission_high
    "permission_ceiling" -> R.string.skill_err_permission_ceiling
    "injection" -> R.string.skill_err_injection
    "secret" -> R.string.skill_err_secret
    "internal_url" -> R.string.skill_err_internal_url
    "hidden_unicode" -> R.string.skill_err_hidden_unicode
    "changelog" -> R.string.skill_err_changelog
    else -> R.string.skill_err_skill
})

@Composable
private fun IssueList(issues: List<SkillIssue>) {
    val c = Saqr.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        issues.take(12).forEach { i ->
            Row(verticalAlignment = Alignment.Top) {
                SIcon(Lucide.CircleAlert, size = 14.dp, tint = c.danger)
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(issueLabel(i.code), style = Saqr.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.text1)
                    Text(i.detail, style = Saqr.type.micro.copy(textDirection = TextDirection.Content), color = c.text3)
                }
            }
        }
    }
}

@Composable
private fun SkillInstallDialogs(inst: SkillInstaller) {
    val c = Saqr.colors
    val ctx = LocalContext.current
    when (val p = inst.pending) {
        is InstallPlan.Refused -> AlertDialog(
            onDismissRequest = { inst.dismiss() }, containerColor = c.surface1,
            icon = { SIcon(Lucide.ShieldCheck, size = 24.dp, tint = c.danger) },
            title = { Text(stringResource(R.string.skills_refused_title), style = Saqr.type.title2) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                    Text(stringResource(R.string.skills_refused_body), style = Saqr.type.body)
                    IssueList(p.reasons.ifEmpty { listOf(SkillIssue("json", "—")) })
                }
            },
            confirmButton = { TextButton(onClick = { inst.dismiss() }) { Text(stringResource(android.R.string.ok), color = c.accentText) } },
        )
        is InstallPlan.Ready -> AlertDialog(
            onDismissRequest = { inst.dismiss() }, containerColor = c.surface1,
            icon = { SIcon(Lucide.RefreshCw, size = 24.dp, tint = c.accentText) },
            title = { Text(stringResource(R.string.skills_update_title), style = Saqr.type.title2) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Sp.md)) {
                    p.updates.forEach { u ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.skills_update_body, u.skill.name, u.old.skill.version, u.skill.version),
                                style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold, textDirection = TextDirection.Content), color = c.text1)
                            if (u.changes.isEmpty()) Text(stringResource(R.string.skills_update_no_notes), style = Saqr.type.caption, color = c.text3)
                            u.changes.forEach { ch ->
                                Text("• ${ch.version} — ${ch.notes}", style = Saqr.type.caption.copy(textDirection = TextDirection.Content), color = c.text2)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    inst.confirm()
                    android.widget.Toast.makeText(ctx, R.string.skills_updated_toast, android.widget.Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.skills_update_confirm), color = c.accentText) }
            },
            dismissButton = { TextButton(onClick = { inst.dismiss() }) { Text(stringResource(R.string.cancel), color = c.text2) } },
        )
        null -> Unit
    }
}

// ---------------------------------------------------------------- settings section

@Composable
internal fun SkillsSection(ui: SkillsUi) {
    val c = Saqr.colors
    var open by remember { mutableStateOf(false) }
    Column {
        ListRow(stringResource(R.string.skills_enabled), stringResource(R.string.skills_enabled_desc), icon = Lucide.Sparkles,
            trailing = { SaqrSwitch(ui.skillsEnabled, ui.onSkillsEnabled) })
        RowDivider()
        ListRow(stringResource(R.string.skills_auto), stringResource(R.string.skills_auto_desc), icon = Lucide.Search,
            trailing = { SaqrSwitch(ui.autoAttach, ui.onAutoAttach) })
        if (ui.autoAttach) {
            Column(Modifier.padding(horizontal = Sp.lg, vertical = Sp.sm), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.skills_top_n), style = Saqr.type.caption, color = c.text2)
                val opts = (SkillRuntime.MIN_TOP_N..SkillRuntime.MAX_TOP_N).toList()
                Segmented(opts.map { it.toString() }, opts.indexOf(ui.topN).coerceAtLeast(0), { ui.onTopN(opts[it]) })
            }
        }
        RowDivider()
        ListRow(
            stringResource(R.string.skills_manage),
            stringResource(R.string.skills_manage_desc, ui.installed.size, ui.installed.count { it.enabled }),
            icon = Lucide.Layers, onClick = { open = true },
            trailing = { SIcon(Lucide.ChevronRight, size = 18.dp, tint = c.text3) },
        )
    }
    if (open) SkillsSheet(ui, onDismiss = { open = false })
}

// ---------------------------------------------------------------- the manager sheet

private fun sourceLabel(s: SkillSource) = when (s) {
    SkillSource.GALLERY -> R.string.skills_source_gallery
    SkillSource.FILE -> R.string.skills_source_file
    SkillSource.PASTE -> R.string.skills_source_paste
    SkillSource.LOCAL -> R.string.skills_source_local
}

private fun shareText(ctx: android.content.Context, subject: String, text: String) {
    runCatching {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        ctx.startActivity(android.content.Intent.createChooser(send, null))
    }
}

private fun toastOf(ctx: android.content.Context, res: Int) =
    android.widget.Toast.makeText(ctx, res, android.widget.Toast.LENGTH_SHORT).show()

@Composable
fun SkillsSheet(ui: SkillsUi, onDismiss: () -> Unit) {
    val c = Saqr.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val arabic = LocalLayoutDirection.current == LayoutDirection.Rtl
    val installer = rememberSkillInstaller(ui)
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var detailId by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    // SAF save: the JSON waiting for the user's chosen file
    var toSave by remember { mutableStateOf<String?>(null) }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val text = toSave
        toSave = null
        if (uri == null || text == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null }.getOrDefault(false)
            }
            toastOf(ctx, if (ok) R.string.skills_saved_file else R.string.skills_save_failed)
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { r ->
                        val buf = CharArray(SkillManager.MAX_RAW_CHARS + 1)
                        val n = r.read(buf)
                        if (n <= 0) "" else String(buf, 0, n)
                    }
                }.getOrNull()
            }
            installer.install(text.orEmpty(), SkillSource.FILE) { ok -> if (ok) toastOf(ctx, R.string.skills_installed_toast) }
        }
    }

    val shown = remember(ui.installed, query, category) { SkillManager.search(ui.installed, query, category) }
    val cats = remember(ui.installed) { SkillManager.categories(ui.installed) }
    val gallery = remember { ui.gallery() }
    val galleryNewer: (InstalledSkill) -> GalleryEntry? = { s ->
        gallery.firstOrNull { e -> e.pack?.id == s.packId && e.pack.skills.any { it.id == s.skill.id && SkillManager.newerIn(s, listOf(it)) != null } }
    }

    SaqrSheet(onDismiss) {
        Row(Modifier.padding(bottom = Sp.md), verticalAlignment = Alignment.CenterVertically) {
            SIcon(Lucide.Sparkles, size = 20.dp, tint = c.accentText)
            Spacer(Modifier.width(Sp.sm))
            Text(stringResource(R.string.skills_title), style = Saqr.type.title2, color = c.text1)
            Spacer(Modifier.width(Sp.sm))
            Tag(ui.installed.size.toString(), color = c.text2)
        }
        Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
            SaqrField(query, { query = it }, placeholder = stringResource(R.string.skills_search))
            if (cats.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(stringResource(R.string.skills_all), category == null) { category = null }
                    cats.forEach { cat -> FilterChip(cat, category == cat) { category = if (category == cat) null else cat } }
                }
            }
            if (ui.installed.isEmpty()) Text(stringResource(R.string.skills_none), style = Saqr.type.caption, color = c.text3)
            else if (shown.isEmpty()) Text(stringResource(R.string.skills_no_match), style = Saqr.type.caption, color = c.text3)
            shown.forEach { s ->
                SkillRow(s, arabic, galleryNewer(s) != null, onOpen = { detailId = s.skill.id }, onEnabled = { ui.onEnabled(s.skill.id, it) })
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Sp.sm)) {
                GhostButton(stringResource(R.string.skills_import_file), { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, icon = Lucide.Upload)
                GhostButton(stringResource(R.string.skills_export_all), {
                    val ex = SkillManager.exportBundle(ui.installed)
                    if (ex == null) toastOf(ctx, R.string.skills_nothing_to_export)
                    else {
                        android.widget.Toast.makeText(ctx, ctx.getString(R.string.skills_exported_n, ex.exported, ex.skippedPrivate), android.widget.Toast.LENGTH_SHORT).show()
                        shareText(ctx, "saqr-skills.saqrpack", ex.json)
                    }
                }, icon = Lucide.ArrowUpFromLine)
                GhostButton(stringResource(R.string.skills_new), { creating = true }, icon = Lucide.Plus)
            }
        }
    }

    detailId?.let { id ->
        val s = ui.installed.firstOrNull { it.skill.id == id }
        if (s == null) detailId = null
        else SkillDetails(
            s = s, arabic = arabic, newer = galleryNewer(s), ui = ui, installer = installer,
            onSave = { json, name -> toSave = json; saver.launch(name) },
            onDismiss = { detailId = null },
        )
    }
    if (creating) NewSkillDialog(ui, onDismiss = { creating = false })
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = Saqr.colors
    Box(
        Modifier.clip(RoundedCornerShape(Radius.sm))
            .background(if (selected) c.accentSoft else c.surface2)
            .border(1.dp, if (selected) c.accent else c.border, RoundedCornerShape(Radius.sm))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) { Text(text, style = Saqr.type.micro, color = if (selected) c.accentText else c.text2) }
}

@Composable
private fun SkillRow(s: InstalledSkill, arabic: Boolean, updatable: Boolean, onOpen: () -> Unit, onEnabled: (Boolean) -> Unit) {
    val c = Saqr.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).background(c.surface2)
            .border(1.dp, if (updatable) c.accent.copy(alpha = 0.45f) else c.border, RoundedCornerShape(Radius.sm))
            .clickable(onClick = onOpen).padding(horizontal = Sp.md, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.skill.displayName(arabic), style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (s.private) Tag(stringResource(R.string.skills_private), color = c.warning)
                if (updatable) Tag(stringResource(R.string.skills_update_available), color = c.accentText, dot = true)
            }
            Text(
                buildString {
                    append("v").append(s.skill.version).append("  ·  ").append(s.skill.category)
                    if (s.rating > 0) append("  ·  ").append("★".repeat(s.rating))
                    append("  ·  $").append(s.skill.id)
                },
                style = Saqr.type.micro, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(stringResource(R.string.skills_uses, s.uses), style = Saqr.type.micro, color = c.text3)
        }
        SaqrSwitch(s.enabled, onEnabled)
    }
}

@Composable
private fun SkillDetails(
    s: InstalledSkill, arabic: Boolean, newer: GalleryEntry?, ui: SkillsUi, installer: SkillInstaller,
    onSave: (json: String, fileName: String) -> Unit, onDismiss: () -> Unit,
) {
    val c = Saqr.colors
    val ctx = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    // a private skill's export waits for an explicit yes: "share" or "save"
    var privateExport by remember { mutableStateOf<String?>(null) }
    val k = s.skill

    fun doExport(kind: String, includePrivate: Boolean) {
        val ex = SkillManager.exportOne(s, includePrivate) ?: run { privateExport = kind; return }
        if (kind == "share") shareText(ctx, "${k.id}.saqrpack", ex.json) else onSave(ex.json, "${k.id}.saqrpack")
    }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.surface1,
        icon = { SIcon(Lucide.Sparkles, size = 24.dp, tint = c.accentText) },
        title = { Text(k.displayName(arabic), style = Saqr.type.title2) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                if (k.description.isNotBlank()) Text(k.description, style = Saqr.type.body.copy(textDirection = TextDirection.Content), color = c.text2)
                Text(
                    listOfNotNull(
                        stringResource(R.string.skills_version, k.version),
                        k.author.takeIf { it.isNotBlank() }?.let { stringResource(R.string.skills_by, it) },
                        stringResource(sourceLabel(s.source)),
                        "$" + k.id,
                    ).joinToString("  ·  "),
                    style = Saqr.type.micro.copy(textDirection = TextDirection.Content), color = c.text3,
                )
                Text(AgentRoles.byId(k.role)?.let { stringResource(R.string.skills_role, roleName(it)) } ?: stringResource(R.string.skills_any_role),
                    style = Saqr.type.caption, color = c.text2)
                if (k.tags.isNotEmpty()) Text(k.tags.joinToString(" · "), style = Saqr.type.micro, color = c.text3)

                // permissions with their real risk tier
                Text(stringResource(R.string.skills_permissions, k.risk), style = Saqr.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.text1)
                if (k.permissions.isEmpty()) Text(stringResource(R.string.skills_no_permissions), style = Saqr.type.micro, color = c.text3)
                k.permissions.forEach { p ->
                    val risk = ToolRegistry.get(p)?.risk
                    Text("• $p — " + stringResource(if (risk == Risk.SAFE) R.string.skills_risk_safe else R.string.skills_risk_medium),
                        style = Saqr.type.micro.copy(textDirection = TextDirection.Content), color = c.text2)
                }

                if (k.changelog.isNotEmpty()) {
                    Text(stringResource(R.string.skills_changelog), style = Saqr.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.text1)
                    k.changelog.take(8).forEach { ch ->
                        Text("• ${ch.version} — ${ch.notes}", style = Saqr.type.micro.copy(textDirection = TextDirection.Content), color = c.text2)
                    }
                }

                Text(stringResource(R.string.skills_checksum), style = Saqr.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.text1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.sha256.take(16) + "…" + s.sha256.takeLast(8), style = Saqr.type.mono, color = c.text2, modifier = Modifier.weight(1f))
                    Box(Modifier.minimumInteractiveComponentSize().size(30.dp).clip(RoundedCornerShape(Radius.sm)).clickable(role = Role.Button) {
                        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("sha256", s.sha256))
                        toastOf(ctx, R.string.skills_checksum_copied)
                    }, contentAlignment = Alignment.Center) { SIcon(Lucide.Copy, size = 14.dp, tint = c.text2, contentDescription = stringResource(R.string.skills_checksum)) }
                }

                Text(stringResource(R.string.skills_rating), style = Saqr.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.text1)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { n ->
                        val rateLabel = stringResource(R.string.skills_rate_n, n)
                        Box(Modifier.minimumInteractiveComponentSize().size(34.dp).clip(RoundedCornerShape(Radius.sm)).clickable(role = Role.RadioButton) { ui.onRate(k.id, if (s.rating == n) 0 else n) },
                            contentAlignment = Alignment.Center) {
                            Text(if (n <= s.rating) "★" else "☆", modifier = Modifier.semantics { contentDescription = rateLabel }, style = Saqr.type.title3, color = if (n <= s.rating) c.warning else c.text3)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.skills_enable), style = Saqr.type.body, color = c.text1, modifier = Modifier.weight(1f))
                    SaqrSwitch(s.enabled) { ui.onEnabled(k.id, it) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.skills_private), style = Saqr.type.body, color = c.text1)
                        Text(stringResource(R.string.skills_private_desc), style = Saqr.type.micro, color = c.text3)
                    }
                    SaqrSwitch(s.private) { ui.onPrivate(k.id, it) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Sp.sm)) {
                    if (newer != null) GhostButton(stringResource(R.string.skills_update), {
                        installer.install(newer.packJson, SkillSource.GALLERY, newer.sha256)
                    }, icon = Lucide.RefreshCw)
                    GhostButton(stringResource(R.string.skills_share), { doExport("share", false) }, icon = Lucide.ArrowUpFromLine)
                    GhostButton(stringResource(R.string.skills_save_file), { doExport("save", false) }, icon = Lucide.Save)
                    GhostButton(stringResource(R.string.delete), { confirmDelete = true }, icon = Lucide.Trash2)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok), color = c.accentText) } },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = c.surface1,
        icon = { SIcon(Lucide.Trash2, size = 24.dp, tint = c.danger) },
        text = { Text(stringResource(R.string.skills_delete_q), style = Saqr.type.body) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; ui.onUninstall(k.id); onDismiss() }) { Text(stringResource(R.string.delete), color = c.danger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel), color = c.text2) } },
    )
    privateExport?.let { kind ->
        AlertDialog(
            onDismissRequest = { privateExport = null }, containerColor = c.surface1,
            icon = { SIcon(Lucide.Lock, size = 24.dp, tint = c.warning) },
            text = { Text(stringResource(R.string.skills_export_private_q), style = Saqr.type.body) },
            confirmButton = { TextButton(onClick = { privateExport = null; doExport(kind, true) }) { Text(stringResource(R.string.skills_export_private_yes), color = c.accentText) } },
            dismissButton = { TextButton(onClick = { privateExport = null }) { Text(stringResource(R.string.cancel), color = c.text2) } },
        )
    }
}

@Composable
private fun NewSkillDialog(ui: SkillsUi, onDismiss: () -> Unit) {
    val c = Saqr.colors
    var name by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("general") }
    var tags by remember { mutableStateOf("") }
    var issues by remember { mutableStateOf<List<SkillIssue>>(emptyList()) }
    val valid = name.isNotBlank() && text.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.surface1,
        icon = { SIcon(Lucide.Plus, size = 24.dp, tint = c.accentText) },
        title = { Text(stringResource(R.string.skills_new), style = Saqr.type.title2) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                SaqrField(name, { name = it; issues = emptyList() }, label = stringResource(R.string.skills_new_name))
                SaqrField(text, { text = it; issues = emptyList() }, label = stringResource(R.string.skills_new_instructions), singleLine = false)
                SaqrField(category, { category = it; issues = emptyList() }, label = stringResource(R.string.skills_new_category), placeholder = "code-review")
                SaqrField(tags, { tags = it }, label = stringResource(R.string.skills_new_tags))
                Text(stringResource(R.string.skills_private_desc), style = Saqr.type.micro, color = c.text3)
                if (issues.isNotEmpty()) IssueList(issues)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val res = ui.onCreateLocal(name, text, category, tags.split(',', '،'))
                if (res.isEmpty()) onDismiss() else issues = res
            }) { Text(stringResource(R.string.skills_new_create), color = if (valid) c.accentText else c.text3) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = c.text2) } },
    )
}
