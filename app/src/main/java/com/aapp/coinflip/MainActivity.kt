package com.aapp.coinflip

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

/**
 * Full-screen coin flip activity — launched from the app icon.
 * Features a 3D coin flip animation, stats tracking, and live Nothing-style widget customizer.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var coinBackground: ImageView
    private lateinit var coinImage: ImageView
    private lateinit var resultText: TextView
    private lateinit var headsCountText: TextView
    private lateinit var tailsCountText: TextView
    private lateinit var totalCountText: TextView
    private lateinit var streakText: TextView
    private lateinit var flipButton: Button
    private lateinit var resetButton: Button
    private lateinit var instructionText: TextView

    private lateinit var coinStyleGroup: RadioGroup
    private lateinit var radioDefaultCoin: RadioButton
    private lateinit var radioNothingCoin: RadioButton
    private lateinit var radioNothingGlassCoin: RadioButton

    private lateinit var widgetFrameGroup: RadioGroup
    private lateinit var radioFrameSolid: RadioButton
    private lateinit var radioFrameLucent: RadioButton
    private lateinit var radioFrameDial: RadioButton

    private lateinit var opacityValueText: TextView
    private lateinit var opacitySeekBar: SeekBar

    private lateinit var bgTintGroup: RadioGroup
    private lateinit var radioTintSlate: RadioButton
    private lateinit var radioTintOled: RadioButton
    private lateinit var radioTintRed: RadioButton
    private lateinit var radioTintFrost: RadioButton

    private var isFlipping = false

    companion object {
        private const val PREFS_NAME = "coin_flip_widget_prefs"
        private const val KEY_HEADS_COUNT = "heads_count"
        private const val KEY_TAILS_COUNT = "tails_count"
        private const val KEY_LAST_RESULT = "last_result"
        private const val KEY_STREAK = "streak"
        private const val KEY_STREAK_SIDE = "streak_side"

        const val KEY_COIN_STYLE = "coin_style"
        const val STYLE_DEFAULT = "default"
        const val STYLE_NOTHING = "nothing"
        const val STYLE_NOTHING_GLASS = "nothing_glass"

        const val KEY_WIDGET_FRAME = "widget_frame"
        const val FRAME_SOLID = "solid"
        const val FRAME_LUCENT = "lucent"
        const val FRAME_DIAL = "dial"

        const val KEY_BG_OPACITY = "bg_opacity"
        const val DEFAULT_BG_OPACITY = 20

        const val KEY_BG_TINT = "bg_tint"
        const val TINT_SLATE = "#1B1B1D"
        const val TINT_OLED = "#000000"
        const val TINT_RED = "#330D0E"
        const val TINT_FROST = "#FFFFFF"
        const val DEFAULT_BG_TINT = TINT_SLATE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind views
        coinBackground = findViewById(R.id.coinBackgroundMain)
        coinImage = findViewById(R.id.coinImageMain)
        resultText = findViewById(R.id.resultTextMain)
        headsCountText = findViewById(R.id.headsCountMain)
        tailsCountText = findViewById(R.id.tailsCountMain)
        totalCountText = findViewById(R.id.totalCountMain)
        streakText = findViewById(R.id.streakTextMain)
        flipButton = findViewById(R.id.flipButtonMain)
        resetButton = findViewById(R.id.resetButtonMain)
        instructionText = findViewById(R.id.instructionText)

        coinStyleGroup = findViewById(R.id.coinStyleGroup)
        radioDefaultCoin = findViewById(R.id.radioDefaultCoin)
        radioNothingCoin = findViewById(R.id.radioNothingCoin)
        radioNothingGlassCoin = findViewById(R.id.radioNothingGlassCoin)

        widgetFrameGroup = findViewById(R.id.widgetFrameGroup)
        radioFrameSolid = findViewById(R.id.radioFrameSolid)
        radioFrameLucent = findViewById(R.id.radioFrameLucent)
        radioFrameDial = findViewById(R.id.radioFrameDial)

        opacityValueText = findViewById(R.id.opacityValueText)
        opacitySeekBar = findViewById(R.id.opacitySeekBar)

        bgTintGroup = findViewById(R.id.bgTintGroup)
        radioTintSlate = findViewById(R.id.radioTintSlate)
        radioTintOled = findViewById(R.id.radioTintOled)
        radioTintRed = findViewById(R.id.radioTintRed)
        radioTintFrost = findViewById(R.id.radioTintFrost)

        // Load saved preferences
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        when (prefs.getString(KEY_COIN_STYLE, STYLE_DEFAULT)) {
            STYLE_NOTHING_GLASS -> radioNothingGlassCoin.isChecked = true
            STYLE_NOTHING -> radioNothingCoin.isChecked = true
            else -> radioDefaultCoin.isChecked = true
        }

        when (prefs.getString(KEY_WIDGET_FRAME, FRAME_LUCENT)) {
            FRAME_SOLID -> radioFrameSolid.isChecked = true
            FRAME_DIAL -> radioFrameDial.isChecked = true
            else -> radioFrameLucent.isChecked = true
        }

        val savedOpacity = prefs.getInt(KEY_BG_OPACITY, DEFAULT_BG_OPACITY)
        opacitySeekBar.progress = savedOpacity
        opacityValueText.text = "$savedOpacity%"

        when (prefs.getString(KEY_BG_TINT, DEFAULT_BG_TINT)) {
            TINT_OLED -> radioTintOled.isChecked = true
            TINT_RED -> radioTintRed.isChecked = true
            TINT_FROST -> radioTintFrost.isChecked = true
            else -> radioTintSlate.isChecked = true
        }

        // Listen for coin design changes
        coinStyleGroup.setOnCheckedChangeListener { _, checkedId ->
            val style = when (checkedId) {
                R.id.radioNothingGlassCoin -> STYLE_NOTHING_GLASS
                R.id.radioNothingCoin -> STYLE_NOTHING
                else -> STYLE_DEFAULT
            }
            prefs.edit().putString(KEY_COIN_STYLE, style).apply()
            refreshUI()
            updateBackgroundPreview()
            notifyWidgets()
        }

        // Listen for widget frame changes
        widgetFrameGroup.setOnCheckedChangeListener { _, checkedId ->
            val frame = when (checkedId) {
                R.id.radioFrameSolid -> FRAME_SOLID
                R.id.radioFrameDial -> FRAME_DIAL
                else -> FRAME_LUCENT
            }
            prefs.edit().putString(KEY_WIDGET_FRAME, frame).apply()
            updateBackgroundPreview()
            notifyWidgets()
        }

        // Listen for opacity changes
        opacitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                opacityValueText.text = "$progress%"
                if (fromUser) {
                    prefs.edit().putInt(KEY_BG_OPACITY, progress).apply()
                    updateBackgroundPreview()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                notifyWidgets()
            }
        })

        // Listen for tint changes
        bgTintGroup.setOnCheckedChangeListener { _, checkedId ->
            val tint = when (checkedId) {
                R.id.radioTintOled -> TINT_OLED
                R.id.radioTintRed -> TINT_RED
                R.id.radioTintFrost -> TINT_FROST
                else -> TINT_SLATE
            }
            prefs.edit().putString(KEY_BG_TINT, tint).apply()
            updateBackgroundPreview()
            notifyWidgets()
        }

        // Load saved state and render preview
        refreshUI()
        updateBackgroundPreview()

        // Tap coin image to flip
        coinImage.setOnClickListener { performFlip() }
        flipButton.setOnClickListener { performFlip() }

        resetButton.setOnClickListener { resetStats() }
    }

    /**
     * Updates the live preview background and coin padding to match widget settings.
     */
    private fun updateBackgroundPreview() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val frame = prefs.getString(KEY_WIDGET_FRAME, FRAME_LUCENT) ?: FRAME_LUCENT
        val tintStr = prefs.getString(KEY_BG_TINT, DEFAULT_BG_TINT) ?: DEFAULT_BG_TINT
        val opacity = prefs.getInt(KEY_BG_OPACITY, DEFAULT_BG_OPACITY)
        val coinStyle = prefs.getString(KEY_COIN_STYLE, STYLE_DEFAULT) ?: STYLE_DEFAULT

        val tintColor = try {
            Color.parseColor(tintStr)
        } catch (_: Exception) {
            Color.parseColor(DEFAULT_BG_TINT)
        }

        val bitmap = CoinFlipWidgetProvider.generateWidgetBackground(
            context = this,
            widthPx = 400,
            heightPx = 400,
            frame = frame,
            tintColor = tintColor,
            opacityPercent = opacity,
            coinStyle = coinStyle
        )
        coinBackground.setImageBitmap(bitmap)

        // Dynamic padding on coin image inside the 200dp container
        val paddingDp = if (frame == FRAME_DIAL) 28 else 16
        val paddingPx = (paddingDp * resources.displayMetrics.density).toInt()
        coinImage.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
    }

    /**
     * Returns true if a Nothing-styled coin (Solid or Glass) is currently selected.
     */
    private fun isNothingCoin(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val style = prefs.getString(KEY_COIN_STYLE, STYLE_DEFAULT)
        return style == STYLE_NOTHING || style == STYLE_NOTHING_GLASS
    }

    /**
     * Returns the appropriate heads drawable resource for the current coin style.
     */
    private fun headsDrawable(): Int {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return when (prefs.getString(KEY_COIN_STYLE, STYLE_DEFAULT)) {
            STYLE_NOTHING_GLASS -> R.drawable.nothing_coin_heads_glass
            STYLE_NOTHING -> R.drawable.nothing_coin_heads
            else -> R.drawable.coin_heads
        }
    }

    /**
     * Returns the appropriate tails drawable resource for the current coin style.
     */
    private fun tailsDrawable(): Int {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return when (prefs.getString(KEY_COIN_STYLE, STYLE_DEFAULT)) {
            STYLE_NOTHING_GLASS -> R.drawable.nothing_coin_tails_glass
            STYLE_NOTHING -> R.drawable.nothing_coin_tails
            else -> R.drawable.coin_tails
        }
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
                    if (isHeads) headsDrawable() else tailsDrawable()
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
                if (isHeads) tailsDrawable() else headsDrawable()
            )
        }, duration / 3)

        coinImage.postDelayed({
            coinImage.setImageResource(
                if (isHeads) headsDrawable() else tailsDrawable()
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
                coinImage.setImageResource(headsDrawable())
                resultText.text = "HEADS!"
                resultText.setTextColor(getColor(R.color.gold_400))
            }
            "T" -> {
                coinImage.setImageResource(tailsDrawable())
                resultText.text = "TAILS!"
                resultText.setTextColor(getColor(R.color.blue_glow))
            }
            else -> {
                coinImage.setImageResource(headsDrawable())
                resultText.text = "Tap to Flip!"
                resultText.setTextColor(getColor(R.color.text_secondary))
                instructionText.visibility = View.VISIBLE
            }
        }

        headsCountText.text = "Heads: $headsCount"
        tailsCountText.text = "Tails: $tailsCount"
        totalCountText.text = "Total: $total"

        if (streak > 1 && streakSide != null) {
            val sideLabel = if (streakSide == "H") "Heads" else "Tails"
            streakText.text = "Streak: ${streak}x $sideLabel"
            streakText.visibility = View.VISIBLE
        } else {
            streakText.visibility = View.GONE
        }
    }

    private fun resetStats() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val coinStyle = prefs.getString(KEY_COIN_STYLE, STYLE_DEFAULT)
        val widgetFrame = prefs.getString(KEY_WIDGET_FRAME, FRAME_LUCENT)
        val bgOpacity = prefs.getInt(KEY_BG_OPACITY, DEFAULT_BG_OPACITY)
        val bgTint = prefs.getString(KEY_BG_TINT, DEFAULT_BG_TINT)

        prefs.edit().clear().apply()
        prefs.edit().putString(KEY_COIN_STYLE, coinStyle)
            .putString(KEY_WIDGET_FRAME, widgetFrame)
            .putInt(KEY_BG_OPACITY, bgOpacity)
            .putString(KEY_BG_TINT, bgTint)
            .apply()

        refreshUI()
        updateBackgroundPreview()
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
