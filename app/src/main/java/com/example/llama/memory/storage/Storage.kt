interface Storage {                                                                                                              
     suspend fun store(key: String, value: Any)                                                                                   
     suspend fun <T> retrieve(key: String): T?                                                                                    
     suspend fun delete(key: String)                                                                                              
     suspend fun deleteByPrefix(prefix: String)                                                                                   
     suspend fun searchByPrefix(prefix: String): List<Pair<String, StorageEntry>>                                                 
 }  