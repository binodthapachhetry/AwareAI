import java.time.Instant
import java.util.UUID

data class SessionMetadata(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val date: String = Instant.now().toString(), // ISO 8601
    val messages: MutableList<Message> = mutableListOf(),
    val contextSnapshot: String? = null
 )
