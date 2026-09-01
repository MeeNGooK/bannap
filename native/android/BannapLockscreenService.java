package com.meengook.bannap;

import android.app.KeyguardManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

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
    private static final String PREFS = "bannap_lockscreen";
    private static final String CHANNEL = "bannap_lockscreen_service";
    private static final int NOTIFICATION_ID = 9471;
    private final Handler handler = new Handler();
    private WindowManager windowManager;
    private View card;
    private BroadcastReceiver screenReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createChannel();
        startForeground(NOTIFICATION_ID, new NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("반납 잠금화면 카드 사용 중")
            .setContentText("반납 예정 도서를 잠금화면에 표시합니다.")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build());
        screenReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) showIfNeeded();
            }
        };
        registerReceiver(screenReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("enabled", false)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "반납 잠금화면 카드", NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    private void showIfNeeded() {
        if (!Settings.canDrawOverlays(this)) return;
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (keyguard == null || !keyguard.isKeyguardLocked()) return;
        List<Reminder> reminders = upcomingReminders();
        if (reminders.isEmpty()) return;
        showCard(reminders);
    }

    private List<Reminder> upcomingReminders() {
        List<Reminder> result = new ArrayList<>();
        String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString("books", "[]");
        try {
            JSONArray books = new JSONArray(raw);
            for (int i = 0; i < books.length(); i++) {
                JSONObject book = books.getJSONObject(i);
                if (book.optBoolean("done", false)) continue;
                long days = daysUntil(book.optString("due", ""));
                if (days <= 7) result.add(new Reminder(book.optString("title", "제목 없는 책"), book.optString("library", "어느 도서관"), days));
            }
        } catch (Exception ignored) { }
        Collections.sort(result, Comparator.comparingLong(reminder -> reminder.days));
        return result;
    }

    private long daysUntil(String due) throws Exception {
        Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).parse(due);
        return TimeUnit.MILLISECONDS.toDays(date.getTime() - startOfToday());
    }

    private long startOfToday() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private void showCard(List<Reminder> reminders) {
        dismissCard();
        Reminder first = reminders.get(0);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(16), dp(14));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(49, 39, 92));
        background.setCornerRadius(dp(24));
        root.setBackground(background);

        TextView eyebrow = text("반납", 12, Color.rgb(220, 213, 255));
        eyebrow.setLetterSpacing(0.12f);
        root.addView(eyebrow);
        TextView title = text(labelFor(first.days) + " · " + first.title, 20, Color.WHITE);
        title.setPadding(0, dp(5), 0, 0);
        root.addView(title);
        TextView detail = text(first.library + (reminders.size() > 1 ? " · 반납 예정 " + reminders.size() + "권" : ""), 14, Color.rgb(221, 216, 239));
        detail.setPadding(0, dp(5), 0, 0);
        root.addView(detail);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(10), 0, 0);
        TextView dismiss = text("닫기", 14, Color.rgb(220, 213, 255));
        dismiss.setPadding(dp(12), dp(7), dp(12), dp(7));
        dismiss.setOnClickListener(view -> dismissCard());
        TextView open = text("일정 보기", 14, Color.WHITE);
        open.setPadding(dp(12), dp(7), dp(2), dp(7));
        open.setOnClickListener(view -> openApp());
        actions.addView(dismiss);
        actions.addView(open);
        root.addView(actions);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = dp(76);
        params.width = getResources().getDisplayMetrics().widthPixels - dp(24);
        card = root;
        windowManager.addView(card, params);
        handler.postDelayed(this::dismissCard, 15000);
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setMaxLines(1);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return view;
    }

    private String labelFor(long days) {
        if (days < 0) return Math.abs(days) + "일 지남";
        if (days == 0) return "오늘 반납";
        if (days == 1) return "내일 반납";
        return days + "일 후 반납";
    }

    private void openApp() {
        dismissCard();
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        }
    }

    private void dismissCard() {
        handler.removeCallbacksAndMessages(null);
        if (card != null) {
            try { windowManager.removeView(card); } catch (Exception ignored) { }
            card = null;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override public void onDestroy() {
        dismissCard();
        if (screenReceiver != null) unregisterReceiver(screenReceiver);
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    private static class Reminder {
        final String title;
        final String library;
        final long days;
        Reminder(String title, String library, long days) {
            this.title = title;
            this.library = library;
            this.days = days;
        }
    }
}
