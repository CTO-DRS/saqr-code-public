package com.ctodrs.saqrcode.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.agent.AgentRole
import com.ctodrs.saqrcode.agent.AgentRoles
import com.ctodrs.saqrcode.data.SettingsStore
import com.ctodrs.saqrcode.ui.chat.roleName
import com.ctodrs.saqrcode.ui.components.ListRow
import com.ctodrs.saqrcode.ui.components.RowDivider
import com.ctodrs.saqrcode.ui.components.SaqrSwitch
import com.ctodrs.saqrcode.ui.components.SectionLabel
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp

/**
 * v0.16 «فريق الوكلاء · Agent team» settings: Chief mode (off by default — the classic
 * Planner → Coder → Reviewer team stays exactly as it was), parallel read-only roles (1 or 2),
 * the before-start cost estimate, and one switch per optional role. The Chief and the Developer
 * are always on, so they have no switch. A plain holder so previews/screenshot tests can build it.
 */
class TeamUi(
    val chiefMode: Boolean = false,
    val onChiefMode: (Boolean) -> Unit = {},
    val maxConcurrent: Int = 1,
    val onMaxConcurrent: (Int) -> Unit = {},
    val costEstimate: Boolean = true,
    val onCostEstimate: (Boolean) -> Unit = {},
    val disabledRoles: Set<String> = emptySet(),
    val onRoleEnabled: (AgentRole, Boolean) -> Unit = { _, _ -> },
) {
    fun enabled(role: AgentRole) = role in AgentRoles.core || role.id !in disabledRoles

    companion object {
        fun from(settings: SettingsStore) = TeamUi(
            chiefMode = settings.chiefMode, onChiefMode = { settings.chiefMode = it },
            maxConcurrent = settings.teamMaxConcurrent, onMaxConcurrent = { settings.teamMaxConcurrent = it },
            costEstimate = settings.teamCostEstimate, onCostEstimate = { settings.teamCostEstimate = it },
            disabledRoles = settings.teamDisabledRoles,
            onRoleEnabled = { role, on ->
                settings.teamDisabledRoles = if (on) settings.teamDisabledRoles - role.id else settings.teamDisabledRoles + role.id
            },
        )
    }
}

@Composable
internal fun TeamSection(ui: TeamUi) {
    val c = Saqr.colors
    SectionLabel(stringResource(R.string.team_section), Modifier.padding(top = Sp.md))
    Group {
        Column {
            ListRow(stringResource(R.string.chief_mode), stringResource(R.string.chief_mode_desc), icon = Lucide.Users,
                trailing = { SaqrSwitch(ui.chiefMode, ui.onChiefMode) })
            if (ui.chiefMode) {
                RowDivider()
                ListRow(stringResource(R.string.team_parallel_roles), stringResource(R.string.team_parallel_roles_desc), icon = Lucide.Layers,
                    trailing = { SaqrSwitch(ui.maxConcurrent >= 2) { ui.onMaxConcurrent(if (it) 2 else 1) } })
                RowDivider()
                ListRow(stringResource(R.string.team_cost_estimate), stringResource(R.string.team_cost_estimate_desc), icon = Lucide.TrendingDown,
                    trailing = { SaqrSwitch(ui.costEstimate, ui.onCostEstimate) })
            }
        }
    }
    if (ui.chiefMode) {
        Group {
            Column {
                Text(stringResource(R.string.team_roles_desc), style = Saqr.type.caption, color = c.text3,
                    modifier = Modifier.padding(horizontal = Sp.lg, vertical = Sp.sm))
                AgentRoles.toggleable.forEach { role ->
                    RowDivider()
                    ListRow(roleName(role), stringResource(roleDesc(role)), icon = Lucide.User,
                        trailing = { SaqrSwitch(ui.enabled(role)) { ui.onRoleEnabled(role, it) } })
                }
            }
        }
    }
}

private fun roleDesc(role: AgentRole): Int = when (role) {
    AgentRole.CHIEF -> R.string.role_chief_desc
    AgentRole.PLANNER -> R.string.role_planner_desc
    AgentRole.RESEARCH -> R.string.role_research_desc
    AgentRole.ARCHITECT -> R.string.role_architect_desc
    AgentRole.DEVELOPER -> R.string.role_developer_desc
    AgentRole.DEBUGGER -> R.string.role_debugger_desc
    AgentRole.SECURITY -> R.string.role_security_desc
    AgentRole.TESTER -> R.string.role_tester_desc
    AgentRole.REVIEWER -> R.string.role_reviewer_desc
    AgentRole.OPTIMIZER -> R.string.role_optimizer_desc
    AgentRole.DOCUMENTATION -> R.string.role_documentation_desc
    AgentRole.DEPLOYMENT -> R.string.role_deployment_desc
}
