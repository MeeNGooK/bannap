package com.meengook.bannap;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.Html;
import android.view.View;
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
        views.setTextViewText(R.id.widget_summary, books.isEmpty() ? "0권" : books.size() + "권");
        views.setTextViewText(R.id.widget_subtitle, books.isEmpty() ? "등록된 반납 일정이 없어요" : "가까운 반납 일정");
        int[] rows = { R.id.widget_row_one, R.id.widget_row_two, R.id.widget_row_three };
        int[] badges = { R.id.widget_badge_one, R.id.widget_badge_two, R.id.widget_badge_three };
        int[] titles = { R.id.widget_title_one, R.id.widget_title_two, R.id.widget_title_three };
        int[] libraries = { R.id.widget_library_one, R.id.widget_library_two, R.id.widget_library_three };
        for (int index = 0; index < rows.length; index++) {
            if (index >= books.size()) { views.setViewVisibility(rows[index], View.GONE); continue; }
            Reminder book = books.get(index);
            views.setViewVisibility(rows[index], View.VISIBLE);
            views.setTextViewText(badges[index], book.badge());
            views.setTextViewText(titles[index], book.title);
            views.setTextViewText(libraries[index], book.library);
        }
        views.setViewVisibility(R.id.widget_empty, books.isEmpty() ? View.VISIBLE : View.GONE);
        if (books.isEmpty()) views.setTextViewText(R.id.widget_empty, "앱에서 책을 추가해 보세요.");
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
                if (!book.optBoolean("done", false)) result.add(new Reminder(readable(book.optString("title", "제목 없는 책")), readable(book.optString("library", "어느 도서관")), daysUntil(book.optString("due", ""))));
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

    private static String readable(String text) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
            ? Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString()
            : Html.fromHtml(text).toString();
    }

    private static class Reminder {
        final String title, library; final long days;
        Reminder(String title, String library, long days) { this.title = title; this.library = library; this.days = days; }
        String badge() {
            return days < 0 ? "D+" + Math.abs(days) : days == 0 ? "D-DAY" : "D-" + days;
        }
    }
}
