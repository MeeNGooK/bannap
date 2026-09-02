package com.meengook.bannap;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

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

public class BannapLockscreenService extends Service {
    static final String PREFS = "bannap_lockscreen";
    static final String CHANNEL = "bannap_return_reminders";
    static final int NOTIFICATION_ID = 9471;
    static final String ACTION_STOP = "com.meengook.bannap.STOP_RETURN_NOTIFICATIONS";

    @Override public void onCreate() { super.onCreate(); createChannel(); }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("enabled", false)) {
            stopSelf(); return START_NOT_STICKY;
        }
        startForeground(NOTIFICATION_ID, notification().build());
        return START_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "반납 상시 알림", NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    private NotificationCompat.Builder notification() {
        List<Reminder> books = upcoming();
        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
        if (books.isEmpty()) style.addLine("등록된 반납 일정이 없어요.");
        for (Reminder book : books) style.addLine(book.label());
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent open = launch == null ? null : PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stopIntent = new Intent(this, BannapNotificationActionReceiver.class).setAction(ACTION_STOP);
        PendingIntent stop = PendingIntent.getBroadcast(this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(books.isEmpty() ? "반납 일정 없음" : "가까운 반납 " + books.size() + "권")
            .setContentText(books.isEmpty() ? "앱에서 대출 책을 추가해 보세요." : books.get(0).label())
            .setStyle(style)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "알림 중지", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setPriority(NotificationCompat.PRIORITY_LOW);
    }

    private List<Reminder> upcoming() {
        List<Reminder> result = new ArrayList<>();
        String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString("books", "[]");
        try {
            JSONArray books = new JSONArray(raw);
            for (int i = 0; i < books.length(); i++) {
                JSONObject book = books.getJSONObject(i);
                if (!book.optBoolean("done", false)) result.add(new Reminder(book.optString("title", "제목 없는 책"), book.optString("library", "어느 도서관"), daysUntil(book.optString("due", ""))));
            }
        } catch (Exception ignored) { }
        Collections.sort(result, Comparator.comparingLong(item -> item.days));
        return result.subList(0, Math.min(3, result.size()));
    }

    private long daysUntil(String due) throws Exception {
        Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).parse(due);
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0); calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0); calendar.set(java.util.Calendar.MILLISECOND, 0);
        return TimeUnit.MILLISECONDS.toDays(date.getTime() - calendar.getTimeInMillis());
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    static class Reminder {
        final String title, library; final long days;
        Reminder(String title, String library, long days) { this.title = title; this.library = library; this.days = days; }
        String label() {
            String when = days < 0 ? Math.abs(days) + "일 지남" : days == 0 ? "오늘" : days == 1 ? "내일" : days + "일 후";
            return when + " · " + title + " · " + library;
        }
    }
}
