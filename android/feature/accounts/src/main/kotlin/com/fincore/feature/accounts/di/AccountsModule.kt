package com.fincore.feature.accounts.di

import com.fincore.core.database.dao.AccountDao
import com.fincore.feature.accounts.data.remote.AccountApi
import com.fincore.feature.accounts.data.repository.AccountRepositoryImpl
import com.fincore.feature.accounts.domain.repository.AccountRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AccountsModule {

    @Provides
    @Singleton
    fun provideAccountApi(retrofit: Retrofit): AccountApi {
        return retrofit.create(AccountApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAccountRepository(
        accountApi: AccountApi,
        accountDao: AccountDao
    ): AccountRepository {
        return AccountRepositoryImpl(accountApi, accountDao)
    }
}
