package com.riyaz.rssmoneymanager

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.TextView

/** CSS-like motion implemented with native Android animations for a lightweight native app. */
object MagicNavigation {
    private const val DURATION = 260L
    private var indicatorRef: View? = null
    private var itemsRef: Array<out TextView>? = null

    fun setup(indicator: View, vararg items: TextView, onSelected: (Int) -> Unit) {
        indicatorRef = indicator
        itemsRef = items
        items.forEachIndexed { index, item ->
            if (index == 2) {
                item.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        select(indicator, items, index)
                        onSelected(index)
                    }
                    false
                }
            } else {
                item.setOnClickListener {
                    select(indicator, items, index)
                    showDestination(item, index)
                    if (index == 0 || index == 1 || index == 3 || index == 4) onSelected(index)
                }
            }
        }
        indicator.post { select(indicator, items, 0, animate = false) }
    }

    fun selectTab(index: Int) {
        val indicator = indicatorRef ?: return
        val items = itemsRef ?: return
        if (index !in items.indices) return
        select(indicator, items, index)
        if (index != 2) showDestination(items[index], index)
    }

    private fun showDestination(source: View, index: Int) {
        val root = source.rootView
        val home = root.findViewById<View>(R.id.home_screen)
        val transactions = root.findViewById<View>(R.id.transactions_screen)
        val accounts = root.findViewById<View>(R.id.accounts_screen)
        val more = root.findViewById<View>(R.id.more_screen)
        val destination = when (index) { 0 -> home; 1 -> transactions; 3 -> accounts; 4 -> more; else -> null } ?: return
        listOf(home, transactions, accounts, more).forEach { if (it != destination) it.visibility = View.GONE }
        destination.visibility = View.VISIBLE
        destination.alpha = 0f
        destination.translationY = 18f
        destination.animate().alpha(1f).translationY(0f).setDuration(DURATION).start()
    }

    private fun select(indicator: View, items: Array<out TextView>, selected: Int, animate: Boolean = true) {
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
            val targetWidth = target.width
            val targetLeft = target.left
            if (!animate) {
                indicator.layoutParams = indicator.layoutParams.apply { width = targetWidth }
                indicator.x = targetLeft.toFloat()
                indicator.requestLayout()
                return@post
            }
            val oldX = indicator.x
            indicator.layoutParams = indicator.layoutParams.apply { width = targetWidth }
            indicator.requestLayout()
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(indicator, View.X, oldX, targetLeft.toFloat()),
                    ObjectAnimator.ofFloat(indicator, View.SCALE_X, .82f, 1.04f, 1f)
                )
                duration = DURATION
                interpolator = OvershootInterpolator(1.0f)
                start()
            }
        }
    }
}
