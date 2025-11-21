
import kotlinx.serialization.Serializable


@Serializable
data class RepairJob(
    val id: Int,
    val customerName: String,
    val phoneModel: String,
    val issue: String,
    val status: String,
    val priceEstimate: Double? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)


@Serializable
data class RepairJobCreateRequest(
    val customerName: String,
    val phoneModel: String,
    val issue: String,
    val status: String,
    val priceEstimate: Double? = null
)

@Serializable
data class RepairJobUpdateRequest(
    val customerName: String? = null,
    val phoneModel: String? = null,
    val issue: String? = null,
    val status: String? = null,
    val priceEstimate: Double? = null
)
