package com.sakinah.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.json.JSONArray

class SakinahWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> updateOne(context, manager, id) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = android.content.ComponentName(context, SakinahWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { updateOne(context, manager, it) }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
            val prefs = context.getSharedPreferences("sakinah_data", Context.MODE_PRIVATE)
            val arr = try { JSONArray(prefs.getString("friends", "[]")) } catch (_: Exception) { JSONArray() }
            val friend = if (arr.length() > 0) arr.optJSONObject(0) else null
            val person = friend?.optString("name", "أصحابك في دعائك") ?: "أصحابك في دعائك"
            val custom = friend?.optString("dua", "").orEmpty()
            val defaultDua = "اللهم احفظ من نحب، واكتب لهم الخير والطمأنينة، ووفقهم لما تحب وترضى."
            val views = RemoteViews(context.packageName, R.layout.sakinah_widget)
            views.setTextViewText(R.id.widget_person, "🤍 $person")
            views.setTextViewText(R.id.widget_dua, custom.takeIf { it.isNotBlank() } ?: defaultDua)
            val open = PendingIntent.getActivity(context, 5050, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_open, open)
            views.setOnClickPendingIntent(R.id.widget_title, open)
            manager.updateAppWidget(id, views)
        }
    }
}
