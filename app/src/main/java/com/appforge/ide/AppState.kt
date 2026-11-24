package com.appforge.ide

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Project(
    val id: String,
    val name: String,
    val type: String,
    val pkg: String,
    val repo: String = "",
    val status: String = "",
    val apk: String = "",
    val updated: Long = System.currentTimeMillis()
)

data class BrowserRequest(val url: String, val title: String, val code: String?)

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(c: Context) {
        sp = c.getSharedPreferences("appforge", Context.MODE_PRIVATE)
    }

    var token: String
        get() = sp.getString("token", "") ?: ""
        set(v) { sp.edit().putString("token", v).apply() }

    var login: String
        get() = sp.getString("login", "") ?: ""
        set(v) { sp.edit().putString("login", v).apply() }

    var clientId: String
        get() = sp.getString("client_id", "") ?: ""
        set(v) { sp.edit().putString("client_id", v).apply() }

    var isPrivate: Boolean
        get() = sp.getBoolean("private", true)
        set(v) { sp.edit().putBoolean("private", v).apply() }
}

object ProjectStore {
    lateinit var root: File
    private lateinit var index: File
    private val ignoredDirs = setOf(".git", "build", ".gradle", ".dart_tool", ".idea")

    fun init(c: Context) {
        root = File(c.filesDir, "projects").also { it.mkdirs() }
        index = File(c.filesDir, "projects.json")
    }

    fun load(): List<Project> {
        if (!index.exists()) return emptyList()
        return try {
            val arr = JSONArray(index.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Project(
                    o.getString("id"), o.getString("name"), o.getString("type"),
                    o.optString("pkg"), o.optString("repo"), o.optString("status"),
                    o.optString("apk"), o.optLong("updated")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(list: List<Project>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("name", it.name).put("type", it.type)
                    .put("pkg", it.pkg).put("repo", it.repo).put("status", it.status)
                    .put("apk", it.apk).put("updated", it.updated)
            )
        }
        index.writeText(arr.toString())
    }

    fun dir(p: Project): File = File(root, p.id)

    fun files(p: Project): List<String> {
        val d = dir(p)
        if (!d.exists()) return emptyList()
        return d.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(d).path.replace('\\', '/') }
            .filter { rel ->
                val parts = rel.split('/')
                parts.dropLast(1).none { it in ignoredDirs } && parts.last() != "local.properties"
            }
            .sorted()
            .toList()
    }

    fun read(p: Project, rel: String): String = try {
        File(dir(p), rel).readText()
    } catch (e: Exception) {
        ""
    }

    fun write(p: Project, rel: String, text: String) {
        val f = File(dir(p), rel)
        f.parentFile?.mkdirs()
        f.writeText(text)
        AppState.fileTick++
    }

    fun delete(p: Project, rel: String) {
        File(dir(p), rel).delete()
        AppState.fileTick++
    }
}

object AppState {
    var tab by mutableStateOf(0)
    var openProjectId by mutableStateOf<String?>(null)
    var editing by mutableStateOf<String?>(null)
    var browser by mutableStateOf<BrowserRequest?>(null)
    var login by mutableStateOf("")
    var message by mutableStateOf("")
    var building by mutableStateOf(false)
    var fileTick by mutableIntStateOf(0)
    val logs = mutableStateListOf<String>()
    val projects = mutableStateListOf<Project>()

    fun init(c: Context) {
        Prefs.init(c)
        ProjectStore.init(c)
        projects.clear()
        projects.addAll(ProjectStore.load())
        login = Prefs.login
    }

    fun persist() = ProjectStore.save(projects.toList())

    fun update(id: String, f: (Project) -> Project) {
        val i = projects.indexOfFirst { it.id == id }
        if (i >= 0) {
            projects[i] = f(projects[i]).copy(updated = System.currentTimeMillis())
            persist()
        }
    }

    fun createProject(name: String, type: String): Project {
        val clean = sanitize(name).ifEmpty { "MyApp" }
        val p = Project(newId(), clean, type, pkgOf(clean))
        ProjectStore.dir(p).mkdirs()
        Templates.create(p).forEach { (k, v) -> ProjectStore.write(p, k, v) }
        projects.add(0, p)
        persist()
        return p
    }

    fun deleteProject(id: String) {
        val p = projects.firstOrNull { it.id == id } ?: return
        ProjectStore.dir(p).deleteRecursively()
        projects.remove(p)
        persist()
        if (openProjectId == id) openProjectId = null
    }
}

fun newId(): String = UUID.randomUUID().toString().replace("-", "").substring(0, 8)

/** Removes characters that could break XML / Kotlin / Dart string literals. */
fun sanitize(s: String): String = s.replace(Regex("[\"'\\\\\$<>&%]"), "").trim()

fun slug(s: String): String =
    s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(30).trim('-').ifEmpty { "app" }

fun pkgOf(name: String): String =
    "com.appforge.app" + name.lowercase().replace(Regex("[^a-z0-9]"), "").take(20)

fun isTextFile(path: String): Boolean {
    val name = path.substringAfterLast('/')
    if (!name.contains('.')) return true
    val ext = name.substringAfterLast('.').lowercase()
    return ext in setOf(
        "kt", "kts", "java", "xml", "gradle", "properties", "json", "yaml", "yml", "dart",
        "html", "htm", "css", "js", "md", "txt", "pro", "toml", "gitignore", "svg", "cfg", "sh", "bat"
    )
}
