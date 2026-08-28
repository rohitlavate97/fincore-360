package com.fincore.feature.notifications.manager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinCoreNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    companion object {
        const val TRANSACTIONS_CHANNEL_ID = "fincore_transactions"
        const val TRANSACTIONS_CHANNEL_NAME = "Transactions & Transfers"
        const val DEEP_LINK_SCHEME = "fincore"
        const val DEEP_LINK_HOST = "transactions"

        fun createTransactionDeepLinkUri(transactionId: String): String {
            return "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST/$transactionId"
        }

        fun createDeepLinkIntent(transactionId: String): Intent {
            return Intent(Intent.ACTION_VIEW, Uri.parse(createTransactionDeepLinkUri(transactionId))).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
    }

    init {
        createNotificationChannels()
    }

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                TRANSACTIONS_CHANNEL_ID,
                TRANSACTIONS_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time alerts for money transfers and transactions"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun buildTransactionNotification(
        transactionId: String,
        title: String,
        body: String
    ): NotificationCompat.Builder {
        val deepLinkIntent = createDeepLinkIntent(transactionId)
        val pendingIntent = PendingIntent.getActivity(
            context,
            transactionId.hashCode(),
            deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, TRANSACTIONS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
    }

    fun showTransactionNotification(
        transactionId: String,
        title: String,
        body: String
    ) {
        val builder = buildTransactionNotification(transactionId, title, body)
        try {
            NotificationManagerCompat.from(context).notify(transactionId.hashCode(), builder.build())
        } catch (e: SecurityException) {
            // Android 13+ POST_NOTIFICATIONS permission handled gracefully
        }
    }
}
