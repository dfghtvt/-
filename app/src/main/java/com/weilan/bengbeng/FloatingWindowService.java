package com.weilan.bengbeng;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;

/**
 * 悬浮窗服务：显示一个"外挂"形式的 UI 面板（仅 UI 演示，无实际功能）。
 * 悬浮窗可拖动、可关闭，UI 为 iOS 26 风格玻璃拟态。
 */
public class FloatingWindowService extends Service {

    private static final String CHANNEL_ID = "weilan_float_channel";
    private static final int NOTIF_ID = 0xB9;

    private WindowManager windowManager;
    private View floatView;
    private WindowManager.LayoutParams params;
    private DatabaseHelper db;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        db = new DatabaseHelper(this);
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());
        showFloatingWindow();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "悬浮窗服务", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("蔚蓝绷绷悬浮窗");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent launchIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setContentTitle("蔚蓝绷绷")
                .setContentText("悬浮窗运行中")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void showFloatingWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatView = View.inflate(this, R.layout.floating_window, null);

        int type;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            type = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 300;

        // 从数据库读取设置并应用到悬浮窗
        applyDbSettings();

        // Neon 数据异步加载完成后，刷新悬浮窗显示
        db.setOnLoadedListener(this::applyDbSettings);

        // 关闭按钮
        TextView btnClose = floatView.findViewById(R.id.btn_float_close);
        btnClose.setOnClickListener(v -> stopSelf());

        // 拖拽
        View dragHandle = floatView.findViewById(R.id.drag_handle);
        dragHandle.setOnTouchListener(new DragTouchListener());

        windowManager.addView(floatView, params);
    }

    private void applyDbSettings() {
        String title = db.get(DatabaseHelper.KEY_FLOAT_TITLE, "蔚蓝绷绷");
        TextView tvTitle = floatView.findViewById(R.id.tv_float_title);
        if (tvTitle != null) tvTitle.setText(title);

        SwitchCompat swGod = floatView.findViewById(R.id.fsw_god);
        SwitchCompat swHp = floatView.findViewById(R.id.fsw_hp);
        SwitchCompat swRecoil = floatView.findViewById(R.id.fsw_recoil);
        SwitchCompat swSpeed = floatView.findViewById(R.id.fsw_speed);
        SwitchCompat swWall = floatView.findViewById(R.id.fsw_wall);

        if (swGod != null) swGod.setChecked(db.getBoolean(DatabaseHelper.KEY_SW_GOD, true));
        if (swHp != null) swHp.setChecked(db.getBoolean(DatabaseHelper.KEY_SW_HP, true));
        if (swRecoil != null) swRecoil.setChecked(db.getBoolean(DatabaseHelper.KEY_SW_RECOIL, false));
        if (swSpeed != null) swSpeed.setChecked(db.getBoolean(DatabaseHelper.KEY_SW_SPEED, false));
        if (swWall != null) swWall.setChecked(false);

        // 点击开关仅作 UI 演示
        View.OnClickListener dummy = v -> {};
        if (swGod != null) swGod.setOnClickListener(dummy);
        if (swHp != null) swHp.setOnClickListener(dummy);
        if (swRecoil != null) swRecoil.setOnClickListener(dummy);
        if (swSpeed != null) swSpeed.setOnClickListener(dummy);
        if (swWall != null) swWall.setOnClickListener(dummy);
    }

    private class DragTouchListener implements View.OnTouchListener {
        private int initialX, initialY;
        private float initialTouchX, initialTouchY;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = params.x;
                    initialY = params.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    params.x = initialX + (int) (event.getRawX() - initialTouchX);
                    params.y = initialY + (int) (event.getRawY() - initialTouchY);
                    if (windowManager != null && floatView != null) {
                        windowManager.updateViewLayout(floatView, params);
                    }
                    return true;
            }
            return false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatView != null && windowManager != null) {
            windowManager.removeView(floatView);
            floatView = null;
        }
    }
}
