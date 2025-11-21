import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RepairService(private val repo: RepairJobRepository) {
    suspend fun getAll(): List<RepairJob> = withContext(Dispatchers.IO) {
        repo.readAll()
    }

    suspend fun getById(id: Int): RepairJob? = withContext(Dispatchers.IO) { repo.readAll().find { it.id == id } }

    suspend fun create(request: RepairJobCreateRequest): RepairJob = withContext(Dispatchers.IO) {
        val job = RepairJob(
            id = 0,
            customerName = request.customerName,
            phoneModel = request.phoneModel,
            issue = request.issue,
            status = request.status,
            priceEstimate = request.priceEstimate,
            createdAt = "",
            updatedAt = ""
        )
        repo.create(job)
    }

    suspend fun update(id: Int, request: RepairJobUpdateRequest): RepairJob? = withContext(Dispatchers.IO) {
        val current = getById(id) ?: return@withContext null
        val next = current.copy(
            customerName = request.customerName ?: current.customerName,
            phoneModel = request.phoneModel ?: current.phoneModel,
            issue = request.issue ?: current.issue,
            status = request.status ?: current.status,
            priceEstimate = request.priceEstimate ?: current.priceEstimate
        )
        if (next == current) {
            return@withContext current
        } else {
            repo.update(id, next)
        }
    }

    suspend fun delete(id: Int): Boolean = withContext(Dispatchers.IO) { repo.delete(id) }
}
