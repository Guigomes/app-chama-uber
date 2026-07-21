package com.defy.notivault.service

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.defy.notivault.data.AppDatabase
import com.defy.notivault.data.NotificationEntity
import com.defy.notivault.data.UberCallEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.time.LocalTime

class NotificationCaptureService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastUberTriggerAt: Long = 0L

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()

        if (title.isBlank() && text.isBlank()) return

        val entity = NotificationEntity(
            packageName = sbn.packageName,
            title = title,
            content = text,
            postedAt = sbn.postTime,
            notificationKey = sbn.key
        )

        serviceScope.launch {
            AppDatabase
                .getInstance(applicationContext)
                .notificationDao()
                .insert(entity)
        }

        if (shouldTriggerUberRide(sbn.packageName, title, text)) {
            serviceScope.launch {
                val route = resolveRouteByTime()
                openUberForRoute(route)
                AppDatabase
                    .getInstance(applicationContext)
                    .uberCallDao()
                    .insert(
                        UberCallEntity(
                            contactName = TARGET_CONTACT,
                            keyword = TRIGGER_KEYWORD,
                            pickupAddress = route.pickupAddress,
                            dropoffAddress = route.dropoffAddress,
                            calledAt = System.currentTimeMillis(),
                            sourceTitle = title,
                            sourceContent = text
                        )
                    )
                UberRideNotifier.notifyRideRequested(applicationContext, null)
            }
        }
    }

    private fun shouldTriggerUberRide(packageName: String, title: String, text: String): Boolean {
        val isWhatsApp = packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b"
        if (!isWhatsApp) return false

        val normalizedTitle = normalizeForComparison(title)
        val normalizedText = normalizeForComparison(text)

        val isContactMatch = normalizedTitle.contains("suely ferias")
        val hasKeyword = normalizedText.contains("uber")
        if (!isContactMatch || !hasKeyword) return false

        val now = System.currentTimeMillis()
        val elapsed = now - lastUberTriggerAt
        if (elapsed in 0 until UBER_TRIGGER_COOLDOWN_MS) return false

        lastUberTriggerAt = now
        return true
    }

    private fun normalizeForComparison(input: String): String {
        if (input.isBlank()) return ""
        return Normalizer
            .normalize(input.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
    }

    private fun resolveRouteByTime(): UberRoute {
        val noon = LocalTime.NOON
        val now = LocalTime.now()
        return if (!now.isAfter(noon)) {
            UberRoute(
                pickupAddress = MORNING_PICKUP_ADDRESS,
                dropoffAddress = MORNING_DROPOFF_ADDRESS
            )
        } else {
            UberRoute(
                pickupAddress = AFTERNOON_PICKUP_ADDRESS,
                dropoffAddress = AFTERNOON_DROPOFF_ADDRESS
            )
        }
    }

    private fun openUberForRoute(route: UberRoute) {
        val deeplink = Uri.parse(
            "https://m.uber.com/ul/?action=setPickup" +
                "&pickup[formatted_address]=${Uri.encode(route.pickupAddress)}" +
                "&dropoff[formatted_address]=${Uri.encode(route.dropoffAddress)}"
        )

        val uberIntent = Intent(Intent.ACTION_VIEW, deeplink).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage(UBER_PACKAGE)
        }

        try {
            startActivity(uberIntent)
        } catch (_: ActivityNotFoundException) {
            startActivity(
                Intent(Intent.ACTION_VIEW, deeplink).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    companion object {
        private const val UBER_PACKAGE = "com.ubercab"
        private const val UBER_TRIGGER_COOLDOWN_MS = 30_000L
        private const val TARGET_CONTACT = "Suely Férias"
        private const val TRIGGER_KEYWORD = "uber"
        private const val MORNING_PICKUP_ADDRESS = "Rua Serra Azul, 780, Campo Grande"
        private const val MORNING_DROPOFF_ADDRESS = "Rua Guararapes, 174, Coophamat, Campo Grande"
        private const val AFTERNOON_PICKUP_ADDRESS = "Rua Guararapes, 174, Coophamat, Campo Grande"
        private const val AFTERNOON_DROPOFF_ADDRESS = "Rua Serra Azul, 780, Campo Grande"
    }

    private data class UberRoute(
        val pickupAddress: String,
        val dropoffAddress: String
    )
}
