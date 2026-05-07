package com.aapp.coinflip

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
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
 * Full-screen transparent overlay launched when the home screen widget is tapped.
 * Features a realistic 3D coin flip animation using the scaleX phase technique:
 *   Phase 1: toss up + scaleX 1→0 (coin turns edge-on)
 *   [image swap at midpoint]
 *   Phase 2: scaleX 0→1 + fall down + bounce (new face revealed)
 */
class CoinFlipActivity : Activity() {

    companion object {
        private const val PREFS_NAME = "coin_flip_widget_prefs"
        private const val FLIP_HALF_MS = 280L   // each phase of the flip
        private const val TOSS_MS      = 320L   // vertical movement duration
        private const val RESULT_DELAY = FLIP_HALF_MS * 2 + 80L
        private const val CLOSE_DELAY  = RESULT_DELAY + 1200L
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coin_flip)

        val coinImage   = findViewById<ImageView>(R.id.coinFlipImage)
        val resultLabel = findViewById<TextView>(R.id.coinFlipResult)
        val titleLabel  = findViewById<TextView>(R.id.overlayTitle)
        val hintLabel   = findViewById<TextView>(R.id.overlayHint)

        // Set camera distance for convincing 3D perspective
        val density = resources.displayMetrics.density
        coinImage.cameraDistance = 12_000 * density

        val isHeads = Random.nextBoolean()

        // Fade in title
        ObjectAnimator.ofFloat(titleLabel, "alpha", 0f, 1f).apply {
            duration = 300
            start()
        }

        // Initial haptic
        triggerHaptic(25)

        // ── Phase 1: Toss up + scaleX shrink to 0 ─────────────────────────
        val tossUp = ObjectAnimator.ofFloat(coinImage, "translationY", 0f, -180f).apply {
            duration = TOSS_MS
            interpolator = DecelerateInterpolator(1.8f)
        }
        // Slight Z rotation wobble for realism
        val wobble = ObjectAnimator.ofFloat(coinImage, "rotation", 0f, -8f, 6f, -3f, 0f).apply {
            duration = FLIP_HALF_MS * 2
        }
        val shrink = ObjectAnimator.ofFloat(coinImage, "scaleX", 1f, 0f).apply {
            duration = FLIP_HALF_MS
            interpolator = AccelerateInterpolator(1.5f)
        }

        shrink.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // ── Midpoint: swap image while coin is edge-on ──────────────
                coinImage.setImageResource(
                    if (isHeads) R.drawable.coin_heads else R.drawable.coin_tails
                )

                // ── Phase 2: scaleX expand + fall down + bounce ─────────────
                val expand = ObjectAnimator.ofFloat(coinImage, "scaleX", 0f, 1f).apply {
                    duration = FLIP_HALF_MS
                    interpolator = DecelerateInterpolator(1.5f)
                }
                val fallDown = ObjectAnimator.ofFloat(coinImage, "translationY", -180f, 0f).apply {
                    duration = TOSS_MS
                    interpolator = OvershootInterpolator(1.6f)
                }
                // Slight scale pulse on landing
                val punchX = ObjectAnimator.ofFloat(coinImage, "scaleX", 1f, 1.12f, 1f).apply {
                    startDelay = FLIP_HALF_MS
                    duration = 200
                }
                val punchY = ObjectAnimator.ofFloat(coinImage, "scaleY", 1f, 1.12f, 1f).apply {
                    startDelay = FLIP_HALF_MS
                    duration = 200
                }

                AnimatorSet().apply {
                    playTogether(expand, fallDown, punchX, punchY)
                    start()
                }

                // Haptic on land
                handler.postDelayed({ triggerHaptic(60) }, FLIP_HALF_MS)

                // Show result after landing
                handler.postDelayed({
                    showResult(isHeads, resultLabel, hintLabel)
                    updatePrefs(isHeads)
                    notifyWidget()
                }, FLIP_HALF_MS + 120)
            }
        })

        // Start phase 1
        AnimatorSet().apply {
            playTogether(tossUp, wobble, shrink)
            start()
        }

        // Auto-close
        handler.postDelayed({
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, android.R.anim.fade_out)
        }, CLOSE_DELAY)
    }

    private fun showResult(isHeads: Boolean, resultLabel: TextView, hintLabel: TextView) {
        val resultText  = if (isHeads) "HEADS" else "TAILS"
        val resultColor = if (isHeads) 0xFFFFCC1A.toInt() else 0xFF6BAFD4.toInt()

        resultLabel.text = resultText
        resultLabel.setTextColor(resultColor)

        // Pop-in animation
        resultLabel.scaleX = 0.4f
        resultLabel.scaleY = 0.4f
        val fadeIn  = ObjectAnimator.ofFloat(resultLabel, "alpha", 0f, 1f).apply { duration = 250 }
        val popX    = ObjectAnimator.ofFloat(resultLabel, "scaleX", 0.4f, 1.08f, 1f).apply { duration = 350; interpolator = OvershootInterpolator(2f) }
        val popY    = ObjectAnimator.ofFloat(resultLabel, "scaleY", 0.4f, 1.08f, 1f).apply { duration = 350; interpolator = OvershootInterpolator(2f) }
        AnimatorSet().apply { playTogether(fadeIn, popX, popY); start() }

        // Hint text
        hintLabel.text = if (isHeads) "The lion prevails! 🦁" else "The moon rises! 🌙"
        ObjectAnimator.ofFloat(hintLabel, "alpha", 0f, 0.8f).apply {
            duration = 400
            startDelay = 200
            start()
        }
    }

    private fun updatePrefs(isHeads: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val headsCount    = prefs.getInt("heads_count", 0)
        val tailsCount    = prefs.getInt("tails_count", 0)
        val lastStreakSide = prefs.getString("streak_side", null)
        val lastStreak    = prefs.getInt("streak", 0)
        val currentSide   = if (isHeads) "H" else "T"
        val newStreak     = if (currentSide == lastStreakSide) lastStreak + 1 else 1

        prefs.edit().apply {
            putInt("heads_count", if (isHeads) headsCount + 1 else headsCount)
            putInt("tails_count", if (!isHeads) tailsCount + 1 else tailsCount)
            putString("last_result", currentSide)
            putInt("streak", newStreak)
            putString("streak_side", currentSide)
            apply()
        }
    }

    private fun notifyWidget() {
        val intent = Intent(this, CoinFlipWidgetProvider::class.java).apply {
            action = CoinFlipWidgetProvider.ACTION_FLIP_COMPLETE
        }
        sendBroadcast(intent)
    }

    private fun triggerHaptic(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            }
        } catch (_: Exception) { }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
