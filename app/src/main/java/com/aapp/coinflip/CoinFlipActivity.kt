package com.aapp.coinflip

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import kotlin.random.Random

/**
 * Transparent overlay Activity launched when the home screen widget is tapped.
 * Shows a full coin flip animation (3D rotation + toss), updates stats,
 * notifies the widget, and auto-closes after showing the result.
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

        // Initial haptic
        triggerHaptic(30)

        // ---------- Animation Sequence ----------

        val totalDuration = 1200L

        // Phase 1: Toss up + scale up
        val tossUp = ObjectAnimator.ofFloat(coinImage, "translationY", 0f, -300f).apply {
            duration = totalDuration / 2
            interpolator = DecelerateInterpolator(1.5f)
        }
        val scaleUpX = ObjectAnimator.ofFloat(coinImage, "scaleX", 1f, 1.3f).apply {
            duration = totalDuration / 3
        }
        val scaleUpY = ObjectAnimator.ofFloat(coinImage, "scaleY", 1f, 1.3f).apply {
            duration = totalDuration / 3
        }

        // 3D-style rotation (rotationY simulates the flip)
        val spin = ObjectAnimator.ofFloat(coinImage, "rotationY", 0f, 1080f).apply {
            duration = totalDuration
            interpolator = AccelerateInterpolator(0.6f)
        }

        // Also add a slight tilt on X axis for realism
        val tiltX = ObjectAnimator.ofFloat(coinImage, "rotationX", 0f, 15f, -10f, 5f, 0f).apply {
            duration = totalDuration
        }

        // Phase 2: Fall back down + scale back + bounce
        val fallDown = ObjectAnimator.ofFloat(coinImage, "translationY", -300f, 0f).apply {
            duration = totalDuration / 2
            startDelay = totalDuration / 2
            interpolator = OvershootInterpolator(1.4f)
        }
        val scaleDownX = ObjectAnimator.ofFloat(coinImage, "scaleX", 1.3f, 1f).apply {
            duration = totalDuration / 3
            startDelay = totalDuration * 2 / 3
        }
        val scaleDownY = ObjectAnimator.ofFloat(coinImage, "scaleY", 1.3f, 1f).apply {
            duration = totalDuration / 3
            startDelay = totalDuration * 2 / 3
        }

        // Fade-in for result text
        resultLabel.alpha = 0f

        val animSet = AnimatorSet()
        animSet.playTogether(tossUp, scaleUpX, scaleUpY, spin, tiltX, fallDown, scaleDownX, scaleDownY)

        // Swap images mid-spin to create the illusion of flipping between sides
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            coinImage.setImageResource(if (isHeads) R.drawable.coin_tails else R.drawable.coin_heads)
        }, totalDuration / 4)

        handler.postDelayed({
            coinImage.setImageResource(if (isHeads) R.drawable.coin_heads else R.drawable.coin_tails)
        }, totalDuration / 2)

        handler.postDelayed({
            coinImage.setImageResource(if (isHeads) R.drawable.coin_tails else R.drawable.coin_heads)
        }, totalDuration * 3 / 4)

        animSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                // Set final result image
                coinImage.setImageResource(
                    if (isHeads) R.drawable.coin_heads else R.drawable.coin_tails
                )
                coinImage.rotationY = 0f
                coinImage.rotationX = 0f

                // Show result text with a pop animation
                resultLabel.text = if (isHeads) "HEADS!" else "TAILS!"
                resultLabel.setTextColor(
                    if (isHeads) getColor(R.color.gold_400) else getColor(R.color.blue_glow)
                )
                ObjectAnimator.ofFloat(resultLabel, "alpha", 0f, 1f).apply {
                    duration = 300
                    start()
                }
                val popX = ObjectAnimator.ofFloat(resultLabel, "scaleX", 0.5f, 1.1f, 1f).apply { duration = 400 }
                val popY = ObjectAnimator.ofFloat(resultLabel, "scaleY", 0.5f, 1.1f, 1f).apply { duration = 400 }
                AnimatorSet().apply { playTogether(popX, popY); start() }

                // Haptic on land
                triggerHaptic(60)

                // Update shared prefs
                updatePrefs(isHeads)

                // Notify widget to refresh
                val intent = Intent(this@CoinFlipActivity, CoinFlipWidgetProvider::class.java).apply {
                    action = CoinFlipWidgetProvider.ACTION_FLIP_COMPLETE
                }
                sendBroadcast(intent)

                // Auto-close after showing result
                handler.postDelayed({
                    finish()
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, android.R.anim.fade_out)
                }, 1000)
            }
        })

        animSet.start()
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

    private fun triggerHaptic(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(
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
