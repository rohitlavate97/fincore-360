package com.fincore.feature.notifications.data.repository

import com.fincore.core.database.dao.NotificationDao
import com.fincore.core.database.entity.NotificationEntity
import com.fincore.feature.notifications.data.remote.NotificationApi
import com.fincore.feature.notifications.domain.model.NotificationItem
import com.fincore.feature.notifications.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val notificationApi: NotificationApi,
    private val notificationDao: NotificationDao
) : NotificationRepository {

    override fun observeNotifications(): Flow<List<NotificationItem>> {
        return notificationDao.observeNotifications().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeUnreadCount(): Flow<Int> {
        return notificationDao.observeUnreadCount()
    }

    override suspend fun refreshNotifications(): Result<Unit> {
        return runCatching {
            val response = notificationApi.getNotifications()
            if (!response.isSuccessful || response.body() == null) {
                throw RuntimeException("Failed to fetch notifications: HTTP ${response.code()}")
            }

            val dtos = response.body()!!.items
            val entities = dtos.map { dto ->
                val createdEpoch = runCatching { Instant.parse(dto.createdAt).toEpochMilli() }.getOrDefault(System.currentTimeMillis())
                val readEpoch = dto.readAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

                NotificationEntity(
                    id = dto.id,
                    customerId = "",
                    title = dto.title,
                    body = dto.body,
                    type = dto.type,
                    deepLinkUri = dto.deepLinkUri,
                    status = dto.status,
                    createdAt = createdEpoch,
                    readAt = readEpoch
                )
            }
            notificationDao.upsertAll(entities)
        }
    }

    override suspend fun markAsRead(id: String): Result<Unit> {
        return runCatching {
            notificationDao.markAsRead(id, System.currentTimeMillis())
            val response = notificationApi.markAsRead(id)
            if (!response.isSuccessful) {
                throw RuntimeException("Failed to mark read on server: HTTP ${response.code()}")
            }
        }
    }

    private fun NotificationEntity.toDomain(): NotificationItem {
        return NotificationItem(
            id = id,
            title = title,
            body = body,
            type = type,
            deepLinkUri = deepLinkUri,
            isRead = status.equals("READ", ignoreCase = true),
            createdAt = createdAt,
            readAt = readAt
        )
    }
}
