import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// SessionManager.kt
 class SessionManager(
     private val storage: Storage,
     private val memoryManager: MemoryManager
 ) {
     private var activeSessionId: String? = null
     private val activeSession: StateFlow<SessionMetadata?> = MutableStateFlow(null)

     suspend fun createSession(title: String): SessionMetadata {
         val session = SessionMetadata(title = title)
         storage.store("session_${session.id}", session)
         setActiveSession(session.id)
         return session
     }

     suspend fun getSession(id: String): SessionMetadata? {
         return storage.retrieve("session_$id")
     }

     suspend fun addMessage(message: Message) {
         val session = requireActiveSession()
         session.messages.add(message)
         storage.store("session_${session.id}", session)
     }

     suspend fun requireActiveSession(): SessionMetadata {
         return activeSession.value ?: throw IllegalStateException("No active session")
     }
 }
