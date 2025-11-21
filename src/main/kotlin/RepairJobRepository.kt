interface RepairJobRepository {
    fun readAll(): MutableList<RepairJob>
    fun create(job: RepairJob): RepairJob
    fun update(id: Int, update: RepairJob): RepairJob?
    fun delete(id: Int): Boolean
}