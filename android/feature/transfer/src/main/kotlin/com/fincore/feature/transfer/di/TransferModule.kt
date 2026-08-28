package com.fincore.feature.transfer.di

import com.fincore.core.database.dao.TransactionDao
import com.fincore.feature.transfer.data.remote.TransferApi
import com.fincore.feature.transfer.data.repository.TransferRepositoryImpl
import com.fincore.feature.transfer.domain.repository.TransferRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TransferModule {

    @Provides
    @Singleton
    fun provideTransferApi(retrofit: Retrofit): TransferApi {
        return retrofit.create(TransferApi::class.java)
    }

    @Provides
    @Singleton
    fun provideTransferRepository(
        transferApi: TransferApi,
        transactionDao: TransactionDao
    ): TransferRepository {
        return TransferRepositoryImpl(transferApi, transactionDao)
    }
}
