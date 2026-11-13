package com.remotemix.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

object RemoteAppsDialog {
    fun show(activity: Activity, apps: List<RemoteApp>, onLaunch: (RemoteApp) -> Unit) {
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        val filtered = apps.toMutableList()
        val icons = mutableMapOf<String, Bitmap?>()
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(16))
            setBackgroundColor(UiPalette.background)
        }
        val handle = View(activity).apply {
            background = GradientDrawable().apply { setColor(UiPalette.border); cornerRadius = dp(3).toFloat() }
        }
        container.addView(handle, LinearLayout.LayoutParams(dp(40), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(12) })
        val title = TextView(activity).apply {
            text = "电视应用 (${apps.size})"; textSize = 22f; setTextColor(UiPalette.ink)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        container.addView(title, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val search = EditText(activity).apply {
            hint = "搜索应用或包名"
            textSize = 16f
            setSingleLine(true)
            minHeight = dp(52)
            setPadding(dp(14), 0, dp(14), 0)
            background = GradientDrawable().apply { setColor(UiPalette.secondary); cornerRadius = dp(12).toFloat() }
        }
        container.addView(search)
        val note = TextView(activity).apply {
            text = if (apps.any { it.iconPng != null }) "名称和图标来自电视 · 点击即可打开"
                   else "电视未提供应用图标 · 点击即可打开"
            textSize = 13f
            setTextColor(UiPalette.muted)
            setPadding(0, 0, 0, dp(6))
        }
        container.addView(note)
        val list = ListView(activity).apply {
            dividerHeight = dp(1)
            isTextFilterEnabled = false
        }
        val adapter = object : BaseAdapter() {
            override fun getCount() = filtered.size
            override fun getItem(position: Int) = filtered[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val app = filtered[position]
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(4), dp(8), dp(4), dp(8))
                    minimumHeight = dp(64)
                }
                row.addView(ImageView(activity).apply {
                    val icon = icons.getOrPut(app.packageName) {
                        app.iconPng?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    }
                    if (icon != null) {
                        setImageBitmap(icon)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setPadding(dp(4), dp(4), dp(4), dp(4))
                    } else {
                        setImageDrawable(RemoteGlyphDrawable(activity, RemoteGlyph.APPS, UiPalette.accent))
                        setPadding(dp(10), dp(10), dp(10), dp(10))
                    }
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(dp(48), dp(48)))
                val titles = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                titles.addView(TextView(activity).apply {
                    text = app.label
                    textSize = 16f
                    setTextColor(UiPalette.ink)
                    maxLines = 1
                })
                if (app.label != app.packageName) titles.addView(TextView(activity).apply {
                    text = app.packageName
                    textSize = 13f
                    setTextColor(UiPalette.muted)
                    maxLines = 1
                })
                row.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
                return row
            }
        }
        list.adapter = adapter
        val listHeight = minOf(dp(420), (activity.resources.displayMetrics.heightPixels * 0.55f).toInt())
        container.addView(list, LinearLayout.LayoutParams(-1, listHeight))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim().orEmpty()
                filtered.clear()
                filtered += apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
                adapter.notifyDataSetChanged()
            }
        })
        val dialog = AlertDialog.Builder(activity).setView(container).create()
        list.setOnItemClickListener { _, _, position, _ ->
            val selected = filtered.getOrNull(position) ?: return@setOnItemClickListener
            dialog.dismiss()
            onLaunch(selected)
        }
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        dialog.window?.setGravity(Gravity.BOTTOM)
        dialog.window?.setBackgroundDrawable(GradientDrawable().apply {
            setColor(UiPalette.background)
            cornerRadii = floatArrayOf(dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(), 0f, 0f, 0f, 0f)
        })
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    }
}
