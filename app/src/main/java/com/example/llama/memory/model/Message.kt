import java.util.UUID

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val sender: SenderType,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: MessageMetadata,
    val versions: MutableList<Message> = mutableListOf()
 ) {
     enum class SenderType { USER, AI }

     data class MessageMetadata(
         val tokens: Int,
         val contextReferences: List<String>,
         val modelParams: ModelParams? = null
     )

     data class ModelParams(
         val temperature: Float,
         val topP: Float
     )
 }
