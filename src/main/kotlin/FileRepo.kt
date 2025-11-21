import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class FileRepo (filePath: String): RepairJobRepository {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val fileName = "repairs.json"

    // Render: read from classpath
    private fun loadFromResource(): String {
        val stream = this::class.java.classLoader.getResourceAsStream(fileName)
            ?: throw IllegalStateException("$fileName not found in resources")
        return stream.bufferedReader().readText()
    }

    // LOCAL: also allow reading from file during development
    private val localFile = File("repairs.json") // stored next to jar

    override fun readAll(): MutableList<RepairJob> {
        val content = if (localFile.exists()) {
            localFile.readText()
        } else {
            loadFromResource()
        }

        return try {
            if (content.isBlank()) mutableListOf()
            else json.decodeFromString<List<RepairJob>>(content).toMutableList()
        } catch (e: Exception) {
            println("Failed to parse JSON: ${e.message}")
            mutableListOf()
        }
    }

    private fun writeAll(items: List<RepairJob>) {
        // Render cannot write inside resources, so write next to jar instead
        localFile.writeText(json.encodeToString(items))
    }

    override fun create(job: RepairJob): RepairJob {
        val list = readAll()
        val nextId = (list.maxOfOrNull { it.id } ?: 0) + 1
        val now = Instant.now()
        val localDateTime = now.atZone(ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")
        val formatted = formatter.format(localDateTime).toString()
        val newJob = job.copy(id = nextId, createdAt = formatted, updatedAt = formatted)
        list.add(newJob)
        writeAll(list)
        return newJob
    }

    override fun update(id: Int, update: RepairJob): RepairJob? {
        val list = readAll()
        val idx = list.indexOfFirst { it.id == id }
        if (idx == -1) return null
        val now = Instant.now()
        val localDateTime = now.atZone(ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")
        val formatted = formatter.format(localDateTime).toString()
        val updated = update.copy(id = id, updatedAt = formatted, createdAt = list[idx].createdAt)
        list[idx] = updated
        writeAll(list)
        return updated
    }

    override fun delete(id: Int): Boolean {
        val list = readAll()
        val removed = list.removeIf { it.id == id }
        if (removed) writeAll(list)
        return removed
    }
}
