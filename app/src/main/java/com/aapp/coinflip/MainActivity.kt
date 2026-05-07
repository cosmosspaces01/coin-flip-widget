package com.aapp.coinflip

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

/**
 * Full-screen coin flip activity — launched from the app icon.
 * Features a 3D coin flip animation, stats tracking, and history.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var coinImage: ImageView
    private lateinit var resultText: TextView
    private lateinit var headsCountText: TextView
    private lateinit var tailsCountText: TextView
    private lateinit var totalCountText: TextView
    private lateinit var streakText: TextView
    private lateinit var flipButton: Button
    private lateinit var resetButton: Button
    private lateinit var instructionText: TextView

    private var isFlipping = false

    companion object {
        private const val PREFS_NAME = "coin_flip_widget_prefs"
        private const val KEY_HEADS_COUNT = "heads_count"
        private const val KEY_TAILS_COUNT = "tails_count"
        private const val KEY_LAST_RESULT = "last_result"
        private const val KEY_STREAK = "streak"
        private const val KEY_STREAK_SIDE = "streak_side"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind views
        coinImage = findViewById(R.id.coinImageMain)
        resultText = findViewById(R.id.resultTextMain)
        headsCountText = findViewById(R.id.headsCountMain)
        tailsCountText = findViewById(R.id.tailsCountMain)
        totalCountText = findViewById(R.id.totalCountMain)
        streakText = findViewById(R.id.streakTextMain)
        flipButton = findViewById(R.id.flipButtonMain)
        resetButton = findViewById(R.id.resetButtonMain)
        instructionText = findViewById(R.id.instructionText)

        // Load saved state
        refreshUI()

        // Tap coin image to flip
        coinImage.setOnClickListener { performFlip() }
        flipButton.setOnClickListener { performFlip() }

        resetButton.setOnClickListener { resetStats() }
    }

    /**
     * Performs the coin flip with a 3D rotation animation.
     */
    private fun performFlip() {
        if (isFlipping) return
        isFlipping = true
        flipButton.isEnabled = false
        instructionText.visibility = View.GONE

        val isHeads = Random.nextBoolean()

        // Haptic feedback at start
        triggerHaptic(30)

        // Animate: scale up + rotate Y (simulated via scaleX flip)
        val duration = 800L
        val totalRotations = 6  // Number of "half-flips"

        // Phase 1: Fly up and spin
        val scaleUpX = ObjectAnimator.ofFloat(coinImage, "scaleX", 1f, 1.2f).apply {
            this.duration = duration / 3
        }
        val scaleUpY = ObjectAnimator.ofFloat(coinImage, "scaleY", 1f, 1.2f).apply {
            this.duration = duration / 3
        }
        val flyUp = ObjectAnimator.ofFloat(coinImage, "translationY", 0f, -200f).apply {
            this.duration = duration / 2
            interpolator = AccelerateDecelerateInterpolator()
        }

        // Simulated 3D rotation using scaleX oscillation
        val rotateAnim = ObjectAnimator.ofFloat(coinImage, "rotationY", 0f, 360f * 2).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
        }

        // Phase 2: Come back down
        val flyDown = ObjectAnimator.ofFloat(coinImage, "translationY", -200f, 0f).apply {
            this.duration = duration / 2
            startDelay = duration / 2
            interpolator = OvershootInterpolator(1.2f)
        }
        val scaleDownX = ObjectAnimator.ofFloat(coinImage, "scaleX", 1.2f, 1f).apply {
            this.duration = duration / 3
            startDelay = duration * 2 / 3
        }
        val scaleDownY = ObjectAnimator.ofFloat(coinImage, "scaleY", 1.2f, 1f).apply {
            this.duration = duration / 3
            startDelay = duration * 2 / 3
        }

        val animSet = AnimatorSet()
        animSet.playTogether(scaleUpX, scaleUpY, flyUp, rotateAnim, flyDown, scaleDownX, scaleDownY)

        animSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                // Set final image
                coinImage.setImageResource(
                    if (isHeads) R.drawable.coin_heads else R.drawable.coin_tails
                )
                coinImage.rotationY = 0f

                // Update prefs
                updatePrefs(isHeads)

                // Refresh UI
                refreshUI()

                // Haptic on land
                triggerHaptic(50)

                // Notify widget to refresh
                notifyWidgets()

                isFlipping = false
                flipButton.isEnabled = true
            }
        })

        // Swap image mid-animation to create flip illusion
        coinImage.postDelayed({
            coinImage.setImageResource(
                if (isHeads) R.drawable.coin_tails else R.drawable.coin_heads
            )
        }, duration / 3)

        coinImage.postDelayed({
            coinImage.setImageResource(
                if (isHeads) R.drawable.coin_heads else R.drawable.coin_tails
            )
        }, duration * 2 / 3)

        animSet.start()
    }

    private fun updatePrefs(isHeads: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
    }

    private fun refreshUI() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastResult = prefs.getString(KEY_LAST_RESULT, null)
        val headsCount = prefs.getInt(KEY_HEADS_COUNT, 0)
        val tailsCount = prefs.getInt(KEY_TAILS_COUNT, 0)
        val streak = prefs.getInt(KEY_STREAK, 0)
        val streakSide = prefs.getString(KEY_STREAK_SIDE, null)
        val total = headsCount + tailsCount

        when (lastResult) {
            "H" -> {
                coinImage.setImageResource(R.drawable.coin_heads)
                resultText.text = "HEADS!"
                resultText.setTextColor(getColor(R.color.gold_400))
            }
            "T" -> {
                coinImage.setImageResource(R.drawable.coin_tails)
                resultText.text = "TAILS!"
                resultText.setTextColor(getColor(R.color.blue_glow))
            }
            else -> {
                coinImage.setImageResource(R.drawable.coin_heads)
                resultText.text = "Tap to Flip!"
                resultText.setTextColor(getColor(R.color.text_secondary))
                instructionText.visibility = View.VISIBLE
            }
        }

        headsCountText.text = "👑 Heads: $headsCount"
        tailsCountText.text = "🦅 Tails: $tailsCount"
        totalCountText.text = "Total: $total"

        if (streak > 1 && streakSide != null) {
            val sideLabel = if (streakSide == "H") "Heads" else "Tails"
            streakText.text = "🔥 Streak: ${streak}× $sideLabel"
            streakText.visibility = View.VISIBLE
        } else {
            streakText.visibility = View.GONE
        }
    }

    private fun resetStats() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        refreshUI()
        notifyWidgets()
        triggerHaptic(20)
    }

    private fun notifyWidgets() {
        val intent = Intent(this, CoinFlipWidgetProvider::class.java).apply {
            action = CoinFlipWidgetProvider.ACTION_FLIP_COMPLETE
        }
        sendBroadcast(intent)
    }

    private fun triggerHaptic(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            }
        } catch (_: Exception) { }
    }
}
