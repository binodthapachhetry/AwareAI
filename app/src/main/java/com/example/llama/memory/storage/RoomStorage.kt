

 class RoomStorage(private val dao: StorageDao) : Storage {
     private val json = Json {
         ignoreUnknownKeys = true
         isLenient = true
     }

     override suspend fun store(key: String, value: Any) {
         dao.insert(StorageEntry(key, json.encodeToString(value)))
     }

     override suspend fun <T> retrieve(key: String): T? {
         return dao.get(key)?.value?.let { json.decodeFromString(it) }
     }

     // Implement other methods...
 }
