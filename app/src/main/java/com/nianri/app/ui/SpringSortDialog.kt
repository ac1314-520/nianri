package com.nianri.app.ui

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nianri.app.MainActivity
import com.nianri.app.model.Anniversary
import com.nianri.app.model.dateLine

/**
 * 跟手拖拽排序：长按行开始拖，拖起的行放大、抬起、加投影；
 * ItemTouchHelper 负责让其他行用平滑位移让出位置。
 */
fun showSpringSortDialog(
    host: MainActivity,
    events: List<Anniversary>,
    onSave: (List<String>) -> Unit,
) {
    if (events.size < 2) return
    val ordered = events.toMutableList()
    val dialog = Dialog(host, android.R.style.Theme_Translucent_NoTitleBar).apply {
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        window?.setGravity(Gravity.BOTTOM)
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window?.setDimAmount(0.34f)
    }

    val wrapper = FrameLayout(host).apply {
        setBackgroundColor(Color.TRANSPARENT)
    }
    val card = LinearLayout(host).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(host.dp(18), host.dp(18), host.dp(18), host.dp(18))
        background = roundedDrawableWithRadii(
            host,
            AppColors.cream,
            floatArrayOf(28f, 28f, 28f, 28f, 0f, 0f, 0f, 0f),
        )
        elevation = host.dpf(20f)
    }
    card.addView(newText(host, "调整顺序", 23f, AppColors.ink, AppFont.serif))
    card.addView(newText(host, "长按一行拖动；松手后会保存新的首页顺序。", 10.5f, AppColors.muted, AppFont.sans).apply {
        setPadding(0, host.dp(5), 0, host.dp(12))
    })

    val recycler = RecyclerView(host).apply {
        layoutManager = LinearLayoutManager(host)
        itemAnimator = DefaultItemAnimator().apply {
            addDuration = 220L
            moveDuration = 220L
            changeDuration = 220L
        }
        overScrollMode = View.OVER_SCROLL_NEVER
    }
    val adapter = SortAdapter(host, ordered)
    recycler.adapter = adapter
    val touch = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        0,
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
            ordered.add(to, ordered.removeAt(from))
            adapter.notifyItemMoved(from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                viewHolder.itemView.animate()
                    .scaleX(1.035f).scaleY(1.035f)
                    .translationZ(host.dpf(18f))
                    .setDuration(120L).start()
                viewHolder.itemView.alpha = 0.98f
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.animate()
                .scaleX(1f).scaleY(1f)
                .translationZ(0f)
                .setDuration(220L).start()
            viewHolder.itemView.alpha = 1f
        }
    }).apply { attachToRecyclerView(recycler) }

    card.addView(recycler, LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        0,
        1f,
    ))
    val save = newText(host, "保存顺序", 14f, AppColors.cream, AppFont.sansMedium).apply {
        gravity = Gravity.CENTER
        background = roundedDrawable(host, AppColors.ink, 24f)
        isClickable = true
        isFocusable = true
        setOnClickListener {
            onSave(ordered.map { it.id })
            dialog.dismiss()
        }
    }
    card.addView(save, LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        host.dp(50),
    ).apply { topMargin = host.dp(12) })

    val height = (host.resources.displayMetrics.heightPixels * 0.68f).toInt()
        .coerceAtLeast(host.dp(360))
    wrapper.addView(card, FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        height,
        Gravity.BOTTOM,
    ).apply {
        leftMargin = host.dp(14)
        rightMargin = host.dp(14)
        bottomMargin = host.dp(16)
    })
    dialog.setContentView(wrapper)
    card.translationY = host.dpf(420f)
    dialog.setOnShowListener {
        card.animate().translationY(0f).setDuration(380L)
            .setInterpolator(android.view.animation.OvershootInterpolator(0.72f)).start()
    }
    dialog.show()
}

private class SortAdapter(
    private val host: MainActivity,
    private val items: MutableList<Anniversary>,
) : RecyclerView.Adapter<SortAdapter.Holder>() {

    class Holder(val view: LinearLayout) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val row = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(host.dp(12), host.dp(10), host.dp(12), host.dp(10))
            background = roundedDrawable(host, AppColors.paperDeep, 17f, 0.8f, AppColors.line)
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = host.dp(8) }
        }
        row.addView(newText(host, "≡", 22f, AppColors.muted, AppFont.sans), LinearLayout.LayoutParams(
            host.dp(26),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        val labels = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(5), 0, host.dp(8), 0)
        }
        labels.addView(newText(host, "", 14f, AppColors.ink, AppFont.sansMedium), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        labels.addView(newText(host, "", 9.5f, AppColors.muted, AppFont.sans).apply {
            setPadding(0, host.dp(3), 0, 0)
        })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return Holder(row)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val event = items[position]
        val labels = holder.view.getChildAt(1) as LinearLayout
        (labels.getChildAt(0) as android.widget.TextView).text = event.title
        (labels.getChildAt(1) as android.widget.TextView).text = event.dateLine()
    }

    override fun getItemCount(): Int = items.size
}
