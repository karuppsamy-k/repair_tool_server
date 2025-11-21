import io.netty.handler.codec.http.HttpMessage

sealed class RepairResult {
    data class NotFound(val message: String)
}