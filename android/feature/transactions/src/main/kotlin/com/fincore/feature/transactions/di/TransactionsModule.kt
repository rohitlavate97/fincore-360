package com.fincore.feature.transactions.di

import com.fincore.core.database.dao.TransactionDao
import com.fincore.feature.transactions.data.remote.TransactionsApi
import com.fincore.feature.transactions.data.repository.TransactionRepositoryImpl
import com.fincore.feature.transactions.domain.repository.TransactionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TransactionsModule {

    @Provides
    @Singleton
    fun provideTransactionsApi(retrofit: Retrofit): TransactionsApi {
        return retrofit.create(TransactionsApi::class.java)
    }

    @Provides
    @Singleton
    fun provideTransactionRepository(
        api: TransactionsApi,
        dao: TransactionDao
    ): TransactionRepository {
        return TransactionRepositoryImpl(api, dao)
    }
}
