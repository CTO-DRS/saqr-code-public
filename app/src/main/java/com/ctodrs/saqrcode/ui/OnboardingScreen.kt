package com.ctodrs.saqrcode.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.ui.components.FalconMark
import com.ctodrs.saqrcode.ui.components.GhostButton
import com.ctodrs.saqrcode.ui.components.PrimaryButton
import com.ctodrs.saqrcode.ui.icons.FileTypeIcon
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.ProviderBadge
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp
import kotlinx.coroutines.launch

private data class Page(val title: Int, val body: Int, val art: @Composable () -> Unit)

/** First-run welcome: four short pages, then straight into the app. */
@Composable
fun OnboardingScreen(onDone: (openSettings: Boolean) -> Unit, initialPage: Int = 0) {
    val c = Saqr.colors
    val pages = listOf(
        Page(R.string.onb1_title, R.string.onb1_body) { ArtWelcome() },
        Page(R.string.onb2_title, R.string.onb2_body) { ArtKeys() },
        Page(R.string.onb3_title, R.string.onb3_body) { ArtTerminal() },
        Page(R.string.onb4_title, R.string.onb4_body) { ArtPrompts() },
    )
    val pager = rememberPagerState(initialPage) { pages.size }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(c.bg)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.13f), Color.Transparent), center = Offset(size.width / 2, size.height * 0.3f), radius = size.width),
                radius = size.width, center = Offset(size.width / 2, size.height * 0.3f))
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Sp.sm, vertical = Sp.xs), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                GhostButton(stringResource(R.string.skip), { onDone(false) }, tint = c.text2)
            }
            HorizontalPager(pager, Modifier.weight(1f)) { i ->
                val p = pages[i]
                Column(Modifier.fillMaxSize().padding(horizontal = Sp.xxl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.height(240.dp), contentAlignment = Alignment.Center) { p.art() }
                    Spacer(Modifier.height(Sp.xxl))
                    Text(stringResource(p.title), style = Saqr.type.display.copy(fontSize = Saqr.type.title1.fontSize * 1.15f), color = c.text1, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(Sp.md))
                    Text(stringResource(p.body), style = Saqr.type.body, color = c.text2, textAlign = TextAlign.Center)
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = Sp.lg), horizontalArrangement = Arrangement.Center) {
                repeat(pages.size) { i ->
                    val w by animateDpAsState(if (pager.currentPage == i) 22.dp else 7.dp, tween(220), label = "dot")
                    Box(Modifier.padding(horizontal = 3.dp).height(7.dp).width(w).clip(CircleShape)
                        .background(if (pager.currentPage == i) c.accent else c.borderStrong))
                }
            }
            val last = pager.currentPage == pages.lastIndex
            PrimaryButton(
                stringResource(if (last) R.string.onb_start else R.string.next),
                onClick = { if (last) onDone(true) else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Sp.xxl).height(54.dp),
                icon = if (last) Lucide.KeyRound else null,
            )
            Spacer(Modifier.height(Sp.xl))
        }
    }
}

@Composable
private fun ArtWelcome() {
    Box(contentAlignment = Alignment.Center) {
        FalconMark(120.dp, glow = true)
        Bubble("سوّ لي صفحة هبوط", Modifier.offset(x = (-78).dp, y = (-96).dp))
        Bubble("Fix the failing test", Modifier.offset(x = 82.dp, y = 92.dp))
    }
}

@Composable
private fun Bubble(text: String, modifier: Modifier) {
    val c = Saqr.colors
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(c.surface2).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 7.dp)) {
        Text(text, style = Saqr.type.caption, color = c.text1, maxLines = 1)
    }
}

@Composable
private fun ArtPrompts() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Bubble("سوّ لي موقع لمطعمي", Modifier)
        Bubble("Add a dark mode toggle", Modifier)
        Bubble("اشرح لي هذا الكود", Modifier)
        Bubble("Write tests for utils.kt", Modifier)
    }
}

@Composable
private fun ArtKeys() {
    val c = Saqr.colors
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(132.dp).clip(RoundedCornerShape(38.dp)).background(c.surface1).border(1.dp, c.accent.copy(alpha = 0.4f), RoundedCornerShape(38.dp)),
            contentAlignment = Alignment.Center) { SIcon(Lucide.ShieldCheck, size = 58.dp, tint = c.accentText) }
        listOf("anthropic" to Offset(-108f, -54f), "openai" to Offset(108f, -54f), "gemini" to Offset(-108f, 54f), "deepseek" to Offset(108f, 54f), "openrouter" to Offset(0f, -104f))
            .forEach { (id, o) -> Box(Modifier.offset(o.x.dp, o.y.dp)) { ProviderBadge(id, 44.dp) } }
    }
}

@Composable
private fun ArtTerminal() {
    val c = Saqr.colors
    Box(contentAlignment = Alignment.Center) {
        Column(Modifier.width(250.dp).clip(RoundedCornerShape(20.dp)).background(c.termBg).border(1.dp, c.border, RoundedCornerShape(20.dp)).padding(16.dp)) {
            Row { listOf(Color(0xFFF2555A), Color(0xFFF0A93B), Color(0xFF46C98B)).forEach { Box(Modifier.size(9.dp).clip(CircleShape).background(it)); Spacer(Modifier.width(6.dp)) } }
            Spacer(Modifier.height(14.dp))
            Text("❯ apk add nodejs python3", style = Saqr.type.monoSmall, color = Color(0xFFEDEEF0))
            Text("OK: 58 MiB in 31 packages", style = Saqr.type.monoSmall, color = Color(0xFF6CCBB6))
            Text("❯ node index.js", style = Saqr.type.monoSmall, color = Color(0xFFEDEEF0))
            Text("http://127.0.0.1:3000", style = Saqr.type.monoSmall, color = c.accent)
        }
        Row(Modifier.offset(y = 104.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("app.kt", "main.py", "index.js", "style.css").forEach { f ->
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.surface2).border(1.dp, c.border, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    FileTypeIcon(f, 22.dp)
                }
            }
        }
    }
}


