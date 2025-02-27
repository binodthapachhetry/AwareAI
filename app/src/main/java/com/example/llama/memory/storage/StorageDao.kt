@Dao
interface StorageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: StorageEntry)

    @Query("SELECT * FROM StorageEntry WHERE `key` = :key")
    suspend fun get(key: String): StorageEntry?

    @Query("DELETE FROM StorageEntry WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM StorageEntry WHERE `key` LIKE :prefix || '%'")
    suspend fun deleteByPrefix(prefix: String)

    @Query("SELECT * FROM StorageEntry WHERE `key` LIKE :prefix || '%'")
    suspend fun searchByPrefix(prefix: String): List<StorageEntry>
}
