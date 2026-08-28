package com.fincore.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.fincore.core.database.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts WHERE customer_id = :customerId ORDER BY created_at DESC")
    fun getAccountsByCustomer(customerId: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY created_at DESC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun getAccountById(id: String): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun findAccountById(id: String): AccountEntity?

    @Upsert
    suspend fun upsertAll(accounts: List<AccountEntity>)

    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()
}
