package com.aapp.coinflip

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import kotlin.random.Random

/**
 * Lightweight transparent Activity launched when the widget coin is tapped.
 * Shows a quick flip animation overlay and then finishes, updating the widget.
 *
 * NOTE: This is an optional alternative for immersive widget flip experience.
 *       The widget can also flip directly via the BroadcastReceiver (see CoinFlipWidgetProvider).
 */
class CoinFlipActivity : Activity() {

    companion object {
        private const val PREFS_NAME = "coin_flip_widget_prefs"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coin_flip)

        val coinImage = findViewById<ImageView>(R.id.coinFlipImage)
        val resultLabel = findViewById<TextView>(R.id.coinFlipResult)

        val isHeads = Random.nextBoolean()

        // Start flip animation
        val flipAnim = AnimationUtils.loadAnimation(this, R.anim.coin_flip_anim)
        coinImage.startAnimation(flipAnim)

        // Haptic
        triggerHaptic()

        // After animation, show result and close
        Handler(Looper.getMainLooper()).postDelayed({
            coinImage.setImageResource(
                if (isHeads) R.drawable.coin_heads else R.drawable.coin_tails
            )
            resultLabel.text = if (isHeads) "HEADS!" else "TAILS!"

            // Update shared prefs (same as widget provider)
            updatePrefs(isHeads)

            // Notify widget
            val intent = Intent(this, CoinFlipWidgetProvider::class.java).apply {
                action = CoinFlipWidgetProvider.ACTION_FLIP_COMPLETE
            }
            sendBroadcast(intent)

            // Close after a short delay
            Handler(Looper.getMainLooper()).postDelayed({
                finish()
                overridePendingTransition(0, android.R.anim.fade_out)
            }, 800)
        }, 600)
    }

    private fun updatePrefs(isHeads: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val headsCount = prefs.getInt("heads_count", 0)
        val tailsCount = prefs.getInt("tails_count", 0)
        val lastStreakSide = prefs.getString("streak_side", null)
        val lastStreak = prefs.getInt("streak", 0)

        val currentSide = if (isHeads) "H" else "T"
        val newStreak = if (currentSide == lastStreakSide) lastStreak + 1 else 1

        prefs.edit().apply {
            putInt("heads_count", if (isHeads) headsCount + 1 else headsCount)
            putInt("tails_count", if (!isHeads) tailsCount + 1 else tailsCount)
            putString("last_result", currentSide)
            putInt("streak", newStreak)
            putString("streak_side", currentSide)
            apply()
        }
    }

    private fun triggerHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(
                    VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            }
        } catch (_: Exception) { }
    }
}
