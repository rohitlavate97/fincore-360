package com.fincore.feature.notifications.di

import com.fincore.core.database.dao.NotificationDao
import com.fincore.feature.notifications.data.remote.NotificationApi
import com.fincore.feature.notifications.data.repository.NotificationRepositoryImpl
import com.fincore.feature.notifications.domain.repository.NotificationRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NotificationModule {

    @Provides
    @Singleton
    fun provideNotificationApi(retrofit: Retrofit): NotificationApi {
        return retrofit.create(NotificationApi::class.java)
    }

    @Provides
    @Singleton
    fun provideNotificationRepository(
        notificationApi: NotificationApi,
        notificationDao: NotificationDao
    ): NotificationRepository {
        return NotificationRepositoryImpl(notificationApi, notificationDao)
    }
}
