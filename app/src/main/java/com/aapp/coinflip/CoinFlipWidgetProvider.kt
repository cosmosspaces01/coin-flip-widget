package com.aapp.coinflip

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.RemoteViews
import kotlin.random.Random

/**
 * AppWidgetProvider for the Coin Flip home screen widget.
 *
 * The widget displays a coin image (heads/tails) and result text.
 * Tapping the widget triggers a coin flip — the result is randomized
 * and the widget updates in-place on the home screen.
 */
class CoinFlipWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_FLIP = "com.aapp.coinflip.ACTION_FLIP"
        const val ACTION_FLIP_COMPLETE = "com.aapp.coinflip.ACTION_FLIP_COMPLETE"
        private const val PREFS_NAME = "coin_flip_widget_prefs"
        private const val KEY_HEADS_COUNT = "heads_count"
        private const val KEY_TAILS_COUNT = "tails_count"
        private const val KEY_LAST_RESULT = "last_result"
        private const val KEY_STREAK = "streak"
        private const val KEY_STREAK_SIDE = "streak_side"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_FLIP -> {
                performFlip(context)
            }
            ACTION_FLIP_COMPLETE -> {
                // Refresh all widget instances
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val ids = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, CoinFlipWidgetProvider::class.java)
                )
                for (id in ids) {
                    updateWidget(context, appWidgetManager, id)
                }
            }
        }
    }

    /**
     * Performs the coin flip: randomizes result, updates prefs, triggers haptic,
     * and refreshes all widget instances.
     */
    private fun performFlip(context: Context) {
        val isHeads = Random.nextBoolean()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val headsCount = prefs.getInt(KEY_HEADS_COUNT, 0)
        val tailsCount = prefs.getInt(KEY_TAILS_COUNT, 0)
        val lastStreakSide = prefs.getString(KEY_STREAK_SIDE, null)
        val lastStreak = prefs.getInt(KEY_STREAK, 0)

        val currentSide = if (isHeads) "H" else "T"
        val newStreak = if (currentSide == lastStreakSide) lastStreak + 1 else 1

        prefs.edit().apply {
            putInt(KEY_HEADS_COUNT, if (isHeads) headsCount + 1 else headsCount)
            putInt(KEY_TAILS_COUNT, if (!isHeads) tailsCount + 1 else tailsCount)
            putString(KEY_LAST_RESULT, currentSide)
            putInt(KEY_STREAK, newStreak)
            putString(KEY_STREAK_SIDE, currentSide)
            apply()
        }

        // Haptic feedback
        triggerHaptic(context)

        // Refresh widgets
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(
            ComponentName(context, CoinFlipWidgetProvider::class.java)
        )
        for (id in ids) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    /**
     * Builds and pushes the RemoteViews for a single widget instance.
     */
    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastResult = prefs.getString(KEY_LAST_RESULT, null)
        val headsCount = prefs.getInt(KEY_HEADS_COUNT, 0)
        val tailsCount = prefs.getInt(KEY_TAILS_COUNT, 0)
        val streak = prefs.getInt(KEY_STREAK, 0)
        val streakSide = prefs.getString(KEY_STREAK_SIDE, null)

        val views = RemoteViews(context.packageName, R.layout.widget_coin_flip)

        // Set coin image based on last result
        when (lastResult) {
            "H" -> {
                views.setImageViewResource(R.id.coinImage, R.drawable.coin_heads)
                views.setTextViewText(R.id.resultText, "HEADS!")
                views.setTextColor(R.id.resultText, context.getColor(R.color.gold_400))
            }
            "T" -> {
                views.setImageViewResource(R.id.coinImage, R.drawable.coin_tails)
                views.setTextViewText(R.id.resultText, "TAILS!")
                views.setTextColor(R.id.resultText, context.getColor(R.color.blue_glow))
            }
            else -> {
                views.setImageViewResource(R.id.coinImage, R.drawable.coin_heads)
                views.setTextViewText(R.id.resultText, "Tap to Flip!")
                views.setTextColor(R.id.resultText, context.getColor(R.color.text_secondary))
            }
        }

        // Stats
        views.setTextViewText(R.id.headsCountText, "👑 $headsCount")
        views.setTextViewText(R.id.tailsCountText, "🦅 $tailsCount")

        // Streak
        if (streak > 1 && streakSide != null) {
            val sideLabel = if (streakSide == "H") "Heads" else "Tails"
            views.setTextViewText(R.id.streakText, "🔥 ${streak}× $sideLabel")
        } else {
            views.setTextViewText(R.id.streakText, "")
        }

        // Set click intent — tapping the widget launches the animated flip overlay
        val flipIntent = Intent(context, CoinFlipActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flipPendingIntent = PendingIntent.getActivity(
            context,
            0,
            flipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, flipPendingIntent)

        // Long-press opens the full activity
        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val activityPendingIntent = PendingIntent.getActivity(
            context,
            1,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.openAppButton, activityPendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    /**
     * Triggers a short haptic vibration to give tactile feedback on flip.
     */
    private fun triggerHaptic(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(
                    VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(
                    VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            }
        } catch (_: Exception) {
            // Vibration not available — fail silently
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // Clean up prefs if no widgets remain
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val remaining = appWidgetManager.getAppWidgetIds(
            ComponentName(context, CoinFlipWidgetProvider::class.java)
        )
        if (remaining.isEmpty()) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().apply()
        }
    }
}
