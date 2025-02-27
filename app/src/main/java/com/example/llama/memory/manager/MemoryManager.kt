class MemoryManager(private val storage: Storage) {                                                                              
     private val cache = LruCache<String, String>(100)                                                                            
     private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())                                                         
                                                                                                                                  
     suspend fun setMemory(key: String, value: String, sessionId: String) {                                                       
         cache.put(key, value)                                                                                                    
         storage.store("memory_${sessionId}_$key", value)                                                                         
     }                                                                                                                            
                                                                                                                                  
     suspend fun getMemory(key: String, sessionId: String): String? {                                                             
         return cache.get(key) ?: storage.retrieve("memory_${sessionId}_$key")?.also {                                            
             cache.put(key, it)                                                                                                   
         }                                                                                                                        
     }                                                                                                                            
                                                                                                                                  
     suspend fun clearSessionMemory(sessionId: String) {                                                                          
         storage.deleteByPrefix("memory_${sessionId}_")                                                                           
         cache.evictAll()                                                                                                         
     }                                                                                                                            
                                                                                                                                  
     suspend fun getRelatedMemories(query: String, sessionId: String, count: Int = 5): List<String> {                             
         // Implement semantic search or keyword matching                                                                         
         return storage.searchByPrefix("memory_${sessionId}_")                                                                    
             .sortedByDescending { it.second.timestamp }                                                                          
             .take(count)                                                                                                         
             .map { it.second.value }                                                                                             
     }                                                                                                                            
 } 