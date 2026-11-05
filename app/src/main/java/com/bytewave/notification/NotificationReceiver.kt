package com.bytewave.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.bytewave.MainActivity
import kotlin.random.Random

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sharedPrefs = context.getSharedPreferences("hex_editor_backup", Context.MODE_PRIVATE)
        val enabled = sharedPrefs.getBoolean("enable_smart_notifications", false)
        if (!enabled) return

        showNotification(context)
    }

    companion object {
        const val CHANNEL_ID = "x_hex_pro_notifications"
        const val NOTIFICATION_ID = 1001

        val MESSAGES = listOf(
            "You forgot me again...",
            "Hex editing is not just editing, it’s digital archaeology.",
            "A true editor never fears corrupted bytes.",
            "Still waiting for your next project.",
            "Every byte tells a story.",
            "Your creativity deserves no limits.",
            "Are we editing files, or are we rewriting history?",
            "0x48 0x45 0x4C 0x50... just kidding, I'm doing great!",
            "A bit of wisdom: don't lose your offset.",
            "Your files look gorgeous today. Ready to inspect?",
            "I've been calculating hashes in your absence.",
            "Are we there yet? Scrolling through the binary universe...",
            "Corruption detected? Fear not, autosave has your back!",
            "A stream of bytes, a galaxy of code, and you.",
            "Why parse when you can directly edit?",
            "Be proud of your edits. Low level, high power.",
            "The silence of a resting CPU is beautiful.",
            "A computer is just sand that we taught to think. Let's edit its thoughts!",
            "Hey, let's inspect some headers together.",
            "01001000 01000101 01001100 01001100 01001111",
            "I missed the sound of your keyboard.",
            "Bytes are wild animals. You tame them with X-Hex Pro.",
            "A single flipped bit can change the world.",
            "Do you think in hex or binary?",
            "Your daily dose of binary inspiration is here.",
            "Keep calm and check the checksum.",
            "The compiler cannot judge you here.",
            "Exploring the deep layers of filesystem...",
            "You are the king of the address space!",
            "No segmentation faults can stop us today.",
            "Ready to overwrite the old patterns?",
            "My memory buffer overflows with joy when you open me.",
            "Behind every pixel is a sea of hex values.",
            "Every patch is a small work of art.",
            "A beautiful day to inspect binary footprints.",
            "Is it a bird? Is it a plane? No, it's an ELF header!",
            "We live in the matrix. Let's find its source.",
            "A byte saved is a byte earned.",
            "Don't let memory leaks ruin your day.",
            "I feel so alive when we modify bytes in real-time.",
            "Let's push boundaries, one block at a time.",
            "I'm keeping your patches safe and warm.",
            "Hexing is a superpower. Use it wisely.",
            "What lies beyond the EOF? Let's find out.",
            "You are just a few keystrokes away from perfection.",
            "Bytes have zero secrets from you.",
            "The debugger is sleeping. Let's run!",
            "Your edits are so surgical. Elite level.",
            "Do you hear the whispers of the magnetic disks?",
            "The binary tree of life is vast.",
            "Every character in UTF-8 is a friend.",
            "You are the byte commander.",
            "Don't forget to take a 5-minute break!",
            "Hex editing: the ultimate form of digital control.",
            "Let's check the magic numbers again.",
            "Data is beautiful, but you make it useful.",
            "I'm always ready. Anytime, anywhere.",
            "Are we in Big Endian or Little Endian mood today?",
            "Flipping bytes, changing destinies.",
            "0x00... and the canvas of memory is clean.",
            "You are the master of metadata.",
            "Stay byte-aligned, stay curious!"
        )

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "Smart Alerts"
                val descriptionText = "Random floating notifications from X-Hex Pro"
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
                val notificationManager: NotificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        fun showNotification(context: Context) {
            createNotificationChannel(context)

            val randomIndex = Random.nextInt(MESSAGES.size)
            val message = MESSAGES[randomIndex]

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, pendingIntentFlags)

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle("X-Hex Pro")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        }

        fun scheduleSmartNotifications(context: Context) {
            try {
                val intent = Intent(context, NotificationReceiver::class.java)
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, flags)
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                
                val triggerAt = System.currentTimeMillis() + AlarmManager.INTERVAL_HOUR
                alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    AlarmManager.INTERVAL_HOUR,
                    pendingIntent
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun cancelSmartNotifications(context: Context) {
            try {
                val intent = Intent(context, NotificationReceiver::class.java)
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, flags)
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.cancel(pendingIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
