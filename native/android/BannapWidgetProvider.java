package com.meengook.bannap;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class BannapWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) update(context, manager, id);
    }

    static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, BannapWidgetProvider.class);
        for (int id : manager.getAppWidgetIds(provider)) update(context, manager, id);
    }

    private static void update(Context context, AppWidgetManager manager, int id) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.bannap_widget);
        List<Reminder> books = upcoming(context);
        views.setTextViewText(R.id.widget_summary, books.isEmpty() ? "반납 일정이 없어요" : "가장 가까운 반납 3권");
        int[] rows = { R.id.widget_row_one, R.id.widget_row_two, R.id.widget_row_three };
        for (int index = 0; index < rows.length; index++) {
            views.setTextViewText(rows[index], index < books.size() ? books.get(index).label() : "");
        }
        Intent open = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (open != null) {
            PendingIntent pending = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_root, pending);
        }
        manager.updateAppWidget(id, views);
    }

    private static List<Reminder> upcoming(Context context) {
        List<Reminder> result = new ArrayList<>();
        String raw = context.getSharedPreferences("bannap_lockscreen", Context.MODE_PRIVATE).getString("books", "[]");
        try {
            JSONArray books = new JSONArray(raw);
            for (int i = 0; i < books.length(); i++) {
                JSONObject book = books.getJSONObject(i);
                if (!book.optBoolean("done", false)) result.add(new Reminder(book.optString("title", "제목 없는 책"), daysUntil(book.optString("due", ""))));
            }
        } catch (Exception ignored) { }
        Collections.sort(result, Comparator.comparingLong(item -> item.days));
        return result.subList(0, Math.min(3, result.size()));
    }

    private static long daysUntil(String due) throws Exception {
        Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).parse(due);
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0); calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0); calendar.set(java.util.Calendar.MILLISECOND, 0);
        return TimeUnit.MILLISECONDS.toDays(date.getTime() - calendar.getTimeInMillis());
    }

    private static class Reminder {
        final String title; final long days;
        Reminder(String title, long days) { this.title = title; this.days = days; }
        String label() {
            String when = days < 0 ? Math.abs(days) + "일 지남" : days == 0 ? "오늘" : days == 1 ? "내일" : days + "일 후";
            return when + " · " + title;
        }
    }
}
