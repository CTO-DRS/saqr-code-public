package com.ctodrs.saqrcode

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.1.20: source scan. A clickable that wraps only an icon needs a label and a 48dp target.
 * Flags `.clickable` lines whose body is a Box that holds a lone SIcon without contentDescription.
 */
class V0123IconA11yScanTest {
    private fun sources(): List<File> {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        return root.walkTopDown().filter { it.extension == "kt" }.toList()
    }

    @Test fun `no icon-only clickable box without a content description`() {
        val bad = ArrayList<String>()
        for (f in sources()) {
            val lines = f.readLines()
            for (i in lines.indices) {
                if (!lines[i].contains(".clickable")) continue
                val window = lines.subList(i, minOf(lines.size, i + 4)).joinToString("\n")
                val iconOnly = window.contains("{ SIcon(") && !window.contains("Text(")
                if (iconOnly && !window.contains("contentDescription")) bad += "${f.name}:${i + 1}"
            }
        }
        assertTrue("icon-only clickables lacking a label: $bad", bad.isEmpty())
    }

    @Test fun `memory delete target is 48dp`() {
        val src = sources().first { it.name == "MemorySheet.kt" }.readText()
        assertTrue(!src.contains("Modifier.size(30.dp).clip(RoundedCornerShape(Radius.sm)).clickable"))
        assertTrue(src.contains("Role.Button"))
    }
}
