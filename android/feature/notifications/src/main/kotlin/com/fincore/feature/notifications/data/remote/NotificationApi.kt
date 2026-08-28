package com.fincore.feature.notifications.data.remote

import com.fincore.feature.notifications.data.remote.dto.NotificationDto
import com.fincore.feature.notifications.data.remote.dto.PagedNotificationDto
import com.fincore.feature.notifications.data.remote.dto.UnreadCountDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

interface NotificationApi {

    @GET("api/v1/notifications")
    suspend fun getNotifications(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50
    ): Response<PagedNotificationDto>

    @PATCH("api/v1/notifications/{id}/read")
    suspend fun markAsRead(
        @Path("id") id: String
    ): Response<NotificationDto>

    @GET("api/v1/notifications/unread-count")
    suspend fun getUnreadCount(): Response<UnreadCountDto>
}
