package com.ctodrs.saqrcode.data

import android.content.Context
import java.io.File

data class Project(val name: String, val dir: File, val modified: Long)

class ProjectRepository(context: Context) {
    val root: File = File(context.filesDir, "projects").apply { mkdirs() }

    fun list(): List<Project> = (root.listFiles() ?: emptyArray())
        .filter { it.isDirectory }
        .map { Project(it.name, it, lastModifiedDeep(it)) }
        .sortedByDescending { it.modified }

    fun get(name: String): Project? = File(root, name).takeIf { it.isDirectory }?.let { Project(name, it, it.lastModified()) }

    fun sanitize(name: String): String =
        name.trim().replace(Regex("""[\\/:*?"<>|\s]+"""), "-").trim('-', '.').take(60)

    fun create(rawName: String, template: Template): Result<Project> = create(rawName, template.files(sanitize(rawName)))

    /** v0.14 skill packs: create from an explicit files map — same sanitize/existence rules. */
    fun create(rawName: String, files: Map<String, String>): Result<Project> = runCatching {
        val name = sanitize(rawName)
        require(name.isNotEmpty()) { "Invalid name" }
        val dir = File(root, name)
        require(!dir.exists()) { "Project already exists" }
        dir.mkdirs()
        files.forEach { (path, content) ->
            File(dir, path).apply { parentFile?.mkdirs(); writeText(content) }
        }
        Project(name, dir, dir.lastModified())
    }

    fun delete(p: Project) { p.dir.deleteRecursively() }

    private fun lastModifiedDeep(dir: File): Long {
        var m = dir.lastModified()
        dir.listFiles()?.forEach { if (it.lastModified() > m) m = it.lastModified() }
        return m
    }
}

enum class Template {
    EMPTY, WEB, PYTHON, NODE, COMPOSE, IME, LITE_WEB;

    fun files(name: String): Map<String, String> = when (this) {
        EMPTY -> mapOf(
            "README.md" to "# $name\n",
            "AGENTS.md" to AGENTS_MD,
        )
        WEB -> mapOf(
            "index.html" to """<!doctype html>
<html lang="ar" dir="rtl">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>$name</title>
  <link rel="stylesheet" href="style.css">
</head>
<body>
  <main>
    <h1>مرحبًا من صقر كود 🦅</h1>
    <p id="msg">Hello from Saqr Code</p>
    <button id="btn">اضغط هنا</button>
  </main>
  <script src="app.js"></script>
</body>
</html>
""",
            "style.css" to """body { font-family: system-ui, sans-serif; background: #0e1116; color: #e6e6e6; margin: 0; }
main { max-width: 640px; margin: 10vh auto; padding: 24px; text-align: center; }
button { background: #d9a441; border: 0; padding: 12px 20px; border-radius: 12px; font-size: 16px; }
""",
            "app.js" to """document.getElementById('btn').addEventListener('click', () => {
  document.getElementById('msg').textContent = 'يا هلا! 👋';
});
""",
            "AGENTS.md" to AGENTS_MD,
        )
        PYTHON -> mapOf(
            "main.py" to """def greet(name: str) -> str:
    return f"مرحبا {name}! Hello {name}!"


if __name__ == "__main__":
    print(greet("صقر"))
""",
            "README.md" to "# $name\n\nRun: `apk add python3 && python3 main.py`\n",
            "AGENTS.md" to AGENTS_MD,
        )
        NODE -> mapOf(
            "package.json" to """{
  "name": "${name.lowercase()}",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": { "start": "node index.js" }
}
""",
            "index.js" to """import http from 'node:http';

const server = http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' });
  res.end('مرحبا من صقر كود 🦅\n');
});
server.listen(3000, '127.0.0.1', () => console.log('http://127.0.0.1:3000'));
""",
            "README.md" to "# $name\n\nRun: `apk add nodejs && npm start`\n",
            "AGENTS.md" to AGENTS_MD,
        )
        COMPOSE -> TemplateFiles.compose(name)
        IME -> TemplateFiles.ime(name)
        LITE_WEB -> TemplateFiles.liteWeb(name)
    }

    companion object {
        const val AGENTS_MD = """# Project rules for the Saqr agent
- Explain in the user's language; write code, identifiers and commit messages in English.
- Keep files small and focused.
"""
    }
}
