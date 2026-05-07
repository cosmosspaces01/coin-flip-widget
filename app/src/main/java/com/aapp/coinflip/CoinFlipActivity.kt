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
 * Full-screen overlay with multi-flip coin toss animation.
 *
 * Animation stages:
 *   1. Toss up (coin goes airborne)
 *   2. Rapid flips × 5 — fast at first, progressively slowing down
 *   3. Landing with overshoot bounce
 *   4. Result text pop-in
 */
class CoinFlipActivity : Activity() {

    companion object {
        private const val PREFS_NAME = "coin_flip_widget_prefs"
    }

    private val handler = Handler(Looper.getMainLooper())

    // Flip durations: start fast, end slow (ms per half-flip)
    private val flipHalfDurations = longArrayOf(70, 80, 100, 140, 200, 280)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coin_flip)

        val coinImage   = findViewById<ImageView>(R.id.coinFlipImage)
        val resultLabel = findViewById<TextView>(R.id.coinFlipResult)
        val titleLabel  = findViewById<TextView>(R.id.overlayTitle)
        val hintLabel   = findViewById<TextView>(R.id.overlayHint)

        // Set camera distance for 3D perspective depth
        coinImage.cameraDistance = 12_000 * resources.displayMetrics.density

        val isHeads = Random.nextBoolean()

        // Fade in title immediately
        ObjectAnimator.ofFloat(titleLabel, "alpha", 0f, 1f).apply {
            duration = 250; start()
        }

        // Initial toss haptic
        triggerHaptic(30)

        // Total airborne time = sum of all flip durations × 2
        val totalFlipMs = flipHalfDurations.sumOf { it * 2 }

        // ── Vertical toss: up then down ───────────────────────────────────
        val tossUp = ObjectAnimator.ofFloat(coinImage, "translationY", 0f, -220f).apply {
            duration = totalFlipMs / 2
            interpolator = DecelerateInterpolator(1.8f)
        }
        val fallDown = ObjectAnimator.ofFloat(coinImage, "translationY", -220f, 0f).apply {
            duration = totalFlipMs / 2
            startDelay = totalFlipMs / 2
            interpolator = AccelerateInterpolator(1.5f)
        }
        // Bounce overshoot after landing
        val bounce = ObjectAnimator.ofFloat(coinImage, "translationY", 0f, 18f, -8f, 4f, 0f).apply {
            startDelay = totalFlipMs
            duration = 320
            interpolator = DecelerateInterpolator()
        }

        AnimatorSet().apply { playTogether(tossUp, fallDown, bounce); start() }

        // ── Sequential multi-flip ─────────────────────────────────────────
        // Determine which face to show on each intermediate flip
        // Alternate starting from opposite of result so final flip lands on result
        val totalFlips = flipHalfDurations.size
        var showHeadsOnFlip = if (isHeads) false else true  // last flip reveals result

        runFlipChain(
            coinImage     = coinImage,
            flipIndex     = 0,
            showHeads     = showHeadsOnFlip,
            isHeads       = isHeads,
            totalFlips    = totalFlips,
            onAllDone     = {
                // Landing haptic
                triggerHaptic(55)

                // Scale punch on landing
                val punchX = ObjectAnimator.ofFloat(coinImage, "scaleX", 1f, 1.14f, 1f).apply { duration = 220 }
                val punchY = ObjectAnimator.ofFloat(coinImage, "scaleY", 1f, 1.14f, 1f).apply { duration = 220 }
                AnimatorSet().apply { playTogether(punchX, punchY); start() }

                // Show result after punch settles
                handler.postDelayed({
                    showResult(isHeads, resultLabel, hintLabel)
                    updatePrefs(isHeads)
                    notifyWidget()
                }, 180)
            }
        )

        // Auto-close after result is visible
        val closeDelay = totalFlipMs + 320L + 180L + 1400L
        handler.postDelayed({
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, android.R.anim.fade_out)
        }, closeDelay)
    }

    /**
     * Recursively executes each flip in the chain with its own duration.
     * Each flip = scaleX 1→0 (edge), swap image, scaleX 0→1 (new face).
     */
    private fun runFlipChain(
        coinImage:  ImageView,
        flipIndex:  Int,
        showHeads:  Boolean,
        isHeads:    Boolean,
        totalFlips: Int,
        onAllDone:  () -> Unit
    ) {
        if (flipIndex >= totalFlips) {
            onAllDone()
            return
        }

        val halfMs = flipHalfDurations[flipIndex]
        val isFinalFlip = flipIndex == totalFlips - 1

        // Phase 1: shrink to edge
        ObjectAnimator.ofFloat(coinImage, "scaleX", 1f, 0f).apply {
            duration = halfMs
            interpolator = AccelerateInterpolator(1.4f)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Swap image at the edge (invisible moment)
                    val faceToShow = if (isFinalFlip) {
                        // Final flip: show the actual result
                        if (isHeads) R.drawable.coin_heads else R.drawable.coin_tails
                    } else {
                        if (showHeads) R.drawable.coin_heads else R.drawable.coin_tails
                    }
                    coinImage.setImageResource(faceToShow)

                    // Phase 2: expand from edge
                    ObjectAnimator.ofFloat(coinImage, "scaleX", 0f, 1f).apply {
                        duration = halfMs
                        interpolator = DecelerateInterpolator(1.4f)
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                // Tick haptic on each flip (lighter for rapid ones)
                                if (halfMs < 150) triggerHaptic(8) else triggerHaptic(15)
                                // Next flip
                                runFlipChain(coinImage, flipIndex + 1, !showHeads, isHeads, totalFlips, onAllDone)
                            }
                        })
                        start()
                    }
                }
            })
            start()
        }
    }

    private fun showResult(isHeads: Boolean, resultLabel: TextView, hintLabel: TextView) {
        val resultColor = if (isHeads) 0xFFFFCC1A.toInt() else 0xFF6BAFD4.toInt()
        resultLabel.text  = if (isHeads) "HEADS" else "TAILS"
        resultLabel.setTextColor(resultColor)
        resultLabel.scaleX = 0.3f
        resultLabel.scaleY = 0.3f

        val fadeIn = ObjectAnimator.ofFloat(resultLabel, "alpha", 0f, 1f).apply { duration = 300 }
        val popX   = ObjectAnimator.ofFloat(resultLabel, "scaleX", 0.3f, 1.1f, 1f).apply {
            duration = 420; interpolator = OvershootInterpolator(2.2f)
        }
        val popY   = ObjectAnimator.ofFloat(resultLabel, "scaleY", 0.3f, 1.1f, 1f).apply {
            duration = 420; interpolator = OvershootInterpolator(2.2f)
        }
        AnimatorSet().apply { playTogether(fadeIn, popX, popY); start() }

        hintLabel.text = if (isHeads) "The lion prevails! 🦁" else "The moon rises! 🌙"
        ObjectAnimator.ofFloat(hintLabel, "alpha", 0f, 0.85f).apply {
            duration = 400; startDelay = 250; start()
        }
    }

    private fun updatePrefs(isHeads: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentSide = if (isHeads) "H" else "T"
        val lastSide    = prefs.getString("streak_side", null)
        val newStreak   = if (currentSide == lastSide) prefs.getInt("streak", 0) + 1 else 1
        prefs.edit().apply {
            putInt("heads_count", prefs.getInt("heads_count", 0) + if (isHeads) 1 else 0)
            putInt("tails_count", prefs.getInt("tails_count", 0) + if (!isHeads) 1 else 0)
            putString("last_result", currentSide)
            putInt("streak", newStreak)
            putString("streak_side", currentSide)
            apply()
        }
    }

    private fun notifyWidget() {
        sendBroadcast(Intent(this, CoinFlipWidgetProvider::class.java).apply {
            action = CoinFlipWidgetProvider.ACTION_FLIP_COMPLETE
        })
    }

    private fun triggerHaptic(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                    .defaultVibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
                    .vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) { }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
