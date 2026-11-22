package com.ctodrs.saqrcode.ui

import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.SaqrApp
import com.ctodrs.saqrcode.data.Project
import com.ctodrs.saqrcode.data.Template

@Composable
fun FalconLogo(size: Int = 40) {
    Box(
        Modifier.size(size.dp).background(Color(0xFF10151C), RoundedCornerShape((size / 4).dp)),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size((size * 1.5).dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val app = SaqrApp.instance
    var refresh by remember { mutableIntStateOf(0) }
    val projects = remember(refresh) { app.projects.list() }
    var showCreate by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Project?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FalconLogo(36)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.new_project)) },
                containerColor = Gold, contentColor = Color(0xFF1A1206),
            )
        },
    ) { pad ->
        if (projects.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad).padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FalconLogo(96)
                    Spacer(Modifier.padding(8.dp))
                    Text(stringResource(R.string.no_projects), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { Text(stringResource(R.string.projects), style = MaterialTheme.typography.titleMedium) }
                items(projects, key = { it.name }) { p ->
                    Card(
                        Modifier.fillMaxWidth().clickable { onOpen(p.name) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.Medium)
                                Text(
                                    DateUtils.getRelativeTimeSpanString(p.modified).toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { toDelete = p }) {
                                Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) CreateProjectDialog(
        onDismiss = { showCreate = false },
        onCreate = { name, tpl ->
            app.projects.create(name, tpl).onSuccess { showCreate = false; refresh++; onOpen(it.name) }
        },
    )
    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            text = { Text(stringResource(R.string.delete_project_q, p.name)) },
            confirmButton = {
                TextButton(onClick = { app.projects.delete(p); toDelete = null; refresh++ }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun CreateProjectDialog(onDismiss: () -> Unit, onCreate: (String, Template) -> Result<*>) {
    var name by remember { mutableStateOf("") }
    var tpl by remember { mutableStateOf(Template.WEB) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_project)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it; error = null }, label = { Text(stringResource(R.string.project_name)) },
                    singleLine = true, isError = error != null, supportingText = error?.let { { Text(it) } })
                Text(stringResource(R.string.template), style = MaterialTheme.typography.labelLarge)
                val labels = listOf(
                    Template.WEB to R.string.tpl_web, Template.PYTHON to R.string.tpl_python,
                    Template.NODE to R.string.tpl_node, Template.EMPTY to R.string.tpl_empty,
                )
                labels.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (t, l) -> FilterChip(selected = tpl == t, onClick = { tpl = t }, label = { Text(stringResource(l)) }) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onCreate(name, tpl).onFailure { error = it.message }
            }) { Text(stringResource(R.string.create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
