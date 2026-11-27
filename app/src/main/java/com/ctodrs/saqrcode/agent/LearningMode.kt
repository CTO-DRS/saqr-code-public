package com.ctodrs.saqrcode.agent

/** v0.1.17: learning mode. Saqr teaches while it works: every change comes with a line-by-line walkthrough. */
object LearningMode {
    /** The lesson recipe, shared by the /learn command and the system prompt section. */
    const val LESSON = "Walk through the code line by line (group trivial adjacent lines): for each line or block, say what it does and why it is there, quote the line in a code span, explain one concept per bullet, define each new term the first time it appears, and finish with a two-sentence summary and one small exercise the user can try."

    fun section(arabic: Boolean): String = buildString {
        appendLine("# LEARNING MODE (the user is learning: teach while you work)")
        appendLine("- After you finish changing files, add a short walkthrough of the code you wrote or changed. $LESSON")
        appendLine("- Keep the work itself unchanged: same tools, same edits, same verification. The teaching is an addition to your final message, never a reason to skip or delay a tool call.")
        appendLine("- Use simple words and concrete examples; do not paste whole files, only the lines you explain.")
        if (arabic) appendLine("- Explain in clear Arabic (Modern Standard with a friendly tone); keep code, identifiers and quoted lines in English.")
        else appendLine("- Explain in the user's language; keep code and identifiers as written.")
    }
}
