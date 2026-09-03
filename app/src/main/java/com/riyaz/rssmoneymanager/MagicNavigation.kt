package com.riyaz.rssmoneymanager

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.TextView

/** CSS-like motion implemented with native Android animations for a lightweight native app. */
object MagicNavigation {
    private const val DURATION = 260L

    fun setup(
        indicator: View,
        vararg items: TextView,
        onSelected: (Int) -> Unit
    ) {
        items.forEachIndexed { index, item ->
            item.setOnClickListener {
                select(indicator, items, index)
                onSelected(index)
            }
        }
        select(indicator, items, 0, animate = false)
    }

    private fun select(
        indicator: View,
        items: Array<out TextView>,
        selected: Int,
        animate: Boolean = true
    ) {
        items.forEachIndexed { index, item ->
            val active = index == selected
            item.isSelected = active
            item.alpha = if (active) 1f else .72f
            if (animate && active) {
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(item, View.SCALE_X, .94f, 1.08f, 1f),
                        ObjectAnimator.ofFloat(item, View.SCALE_Y, .94f, 1.08f, 1f),
                        ObjectAnimator.ofFloat(item, View.TRANSLATION_Y, 6f, -4f, 0f)
                    )
                    duration = DURATION
                    interpolator = OvershootInterpolator(1.2f)
                    start()
                }
            }
        }

        indicator.post {
            val target = items[selected]
            val parent = indicator.parent as View
            val targetWidth = target.width
            val targetLeft = target.left
            if (!animate) {
                indicator.layoutParams = indicator.layoutParams.apply { width = targetWidth }
                indicator.x = targetLeft.toFloat()
                return@post
            }
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(indicator, View.X, indicator.x, targetLeft.toFloat()),
                    ObjectAnimator.ofInt(indicator, "width", indicator.width, targetWidth)
                )
                duration = DURATION
                interpolator = OvershootInterpolator(1.0f)
                start()
            }
        }
    }
}
