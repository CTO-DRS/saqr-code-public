package com.ctodrs.saqrcode

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guard: a clickable box smaller than 48dp must opt in to minimumInteractiveComponentSize. */
class V0102TouchTargetGuardTest {
    @Test fun `no clickable under 48dp without a minimum touch target`() {
        val root = listOf("src/main/java", "app/src/main/java").map(::File).first { it.exists() }
        val bad = root.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, l ->
                val m = Regex("""Modifier\.(?:padding\([^)]*\)\.)?size\((\d+)\.dp\)""").find(l)
                val small = m != null && m.groupValues[1].toInt() < 48
                if (small && (".clickable(" in l || ".pressable" in l) && "minimumInteractiveComponentSize" !in l) "${f.name}:${i + 1}" else null
            }
        }.toList()
        assertTrue("under-48dp targets: $bad", bad.isEmpty())
    }
}
