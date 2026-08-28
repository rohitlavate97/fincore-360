package com.fincore.feature.accounts.data.repository

import com.fincore.core.database.dao.AccountDao
import com.fincore.core.database.entity.AccountEntity
import com.fincore.feature.accounts.data.remote.AccountApi
import com.fincore.feature.accounts.data.remote.dto.AccountDto
import com.fincore.feature.accounts.data.remote.dto.CreateAccountRequestDto
import com.fincore.feature.accounts.domain.model.Account
import com.fincore.feature.accounts.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class AccountRepositoryImpl @Inject constructor(
    private val accountApi: AccountApi,
    private val accountDao: AccountDao
) : AccountRepository {

    override fun getAccounts(): Flow<List<Account>> {
        return accountDao.getAllAccounts().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAccountById(id: String): Flow<Account?> {
        return accountDao.getAccountById(id).map { it?.toDomain() }
    }

    override suspend fun refreshAccounts(): Result<Unit> = runCatching {
        val pagedResponse = accountApi.getAccounts()
        val entities = pagedResponse.items.map { it.toEntity() }
        accountDao.upsertAll(entities)
    }

    override suspend fun createAccount(
        accountType: String,
        currency: String,
        initialDeposit: String
    ): Result<Account> = runCatching {
        val response = accountApi.createAccount(
            CreateAccountRequestDto(
                accountType = accountType,
                currency = currency,
                initialDeposit = initialDeposit
            )
        )
        val entity = response.toEntity()
        accountDao.upsert(entity)
        entity.toDomain()
    }

    private fun AccountDto.toEntity(): AccountEntity {
        val parsedTime = runCatching { Instant.parse(createdAt).toEpochMilli() }.getOrDefault(System.currentTimeMillis())
        return AccountEntity(
            id = id,
            customerId = customerId,
            accountNumber = accountNumber,
            accountType = accountType,
            status = status,
            currency = currency,
            ledgerBalance = ledgerBalance,
            availableBalance = availableBalance,
            createdAt = parsedTime,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun AccountEntity.toDomain(): Account = Account(
        id = id,
        customerId = customerId,
        accountNumber = accountNumber,
        accountType = accountType,
        status = status,
        currency = currency,
        ledgerBalance = ledgerBalance,
        availableBalance = availableBalance,
        createdAt = createdAt
    )
}
