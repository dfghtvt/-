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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;

/**
 * 悬浮窗服务：先显示一个悬浮球（应用图标），点击后展开/收起功能面板。
 * 悬浮球可拖动，面板可关闭（仅收起，悬浮球仍在）。
 */
public class FloatingWindowService extends Service {

    private static final String CHANNEL_ID = "weilan_float_channel";
    private static final int NOTIF_ID = 0xB9;

    private WindowManager windowManager;
    private View ballView;          // 悬浮球
    private View panelView;         // 功能面板
    private WindowManager.LayoutParams ballParams;
    private WindowManager.LayoutParams panelParams;
    private boolean panelShowing = false;
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
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        showBall();
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
                .setContentText("悬浮窗运行中 · 点击通知关闭")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    /** 显示悬浮球 */
    private void showBall() {
        ballView = View.inflate(this, R.layout.floating_ball, null);
        ImageView ball = ballView.findViewById(R.id.float_ball);

        int type;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            type = WindowManager.LayoutParams.TYPE_PHONE;
        }

        ballParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        ballParams.gravity = Gravity.TOP | Gravity.START;
        ballParams.x = 100;
        ballParams.y = 300;

        ball.setOnTouchListener(new BallTouchListener());

        windowManager.addView(ballView, ballParams);
    }

    /** 显示/隐藏功能面板 */
    private void togglePanel() {
        if (panelShowing) {
            hidePanel();
        } else {
            showPanel();
        }
    }

    private void showPanel() {
        if (panelView == null) {
            panelView = View.inflate(this, R.layout.floating_window, null);

            int type;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                type = WindowManager.LayoutParams.TYPE_PHONE;
            }

            panelParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            panelParams.gravity = Gravity.TOP | Gravity.START;
            // 面板显示在悬浮球旁边
            panelParams.x = ballParams.x + 70;
            panelParams.y = ballParams.y;

            applyDbSettings();

            // 关闭按钮：收起面板（保留悬浮球）
            TextView btnClose = panelView.findViewById(R.id.btn_float_close);
            btnClose.setOnClickListener(v -> hidePanel());

            // 面板拖拽
            View dragHandle = panelView.findViewById(R.id.drag_handle);
            dragHandle.setOnTouchListener(new PanelDragListener());
        }

        // 每次显示前刷新设置
        applyDbSettings();
        // 面板位置跟随悬浮球
        panelParams.x = ballParams.x + 70;
        panelParams.y = ballParams.y;

        windowManager.addView(panelView, panelParams);
        panelShowing = true;
    }

    private void hidePanel() {
        if (panelView != null && panelShowing) {
            windowManager.removeView(panelView);
            panelShowing = false;
        }
    }

    private void applyDbSettings() {
        if (panelView == null) return;

        String title = db.get(DatabaseHelper.KEY_FLOAT_TITLE, "蔚蓝绷绷");
        TextView tvTitle = panelView.findViewById(R.id.tv_float_title);
        if (tvTitle != null) tvTitle.setText(title);

        SwitchCompat swGod = panelView.findViewById(R.id.fsw_god);
        SwitchCompat swHp = panelView.findViewById(R.id.fsw_hp);
        SwitchCompat swRecoil = panelView.findViewById(R.id.fsw_recoil);
        SwitchCompat swSpeed = panelView.findViewById(R.id.fsw_speed);
        SwitchCompat swWall = panelView.findViewById(R.id.fsw_wall);

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

    /** 悬浮球触摸：拖动 + 点击切换面板 */
    private class BallTouchListener implements View.OnTouchListener {
        private int initialX, initialY;
        private float initialTouchX, initialTouchY;
        private boolean moved = false;
        private long downTime = 0;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = ballParams.x;
                    initialY = ballParams.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    moved = false;
                    downTime = System.currentTimeMillis();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int) (event.getRawX() - initialTouchX);
                    int dy = (int) (event.getRawY() - initialTouchY);
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) moved = true;
                    ballParams.x = initialX + dx;
                    ballParams.y = initialY + dy;
                    if (windowManager != null && ballView != null) {
                        windowManager.updateViewLayout(ballView, ballParams);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    // 未拖动且按下时间短 -> 视为点击
                    if (!moved && System.currentTimeMillis() - downTime < 300) {
                        togglePanel();
                    }
                    return true;
            }
            return false;
        }
    }

    /** 面板拖动 */
    private class PanelDragListener implements View.OnTouchListener {
        private int initialX, initialY;
        private float initialTouchX, initialTouchY;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = panelParams.x;
                    initialY = panelParams.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    panelParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                    panelParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                    if (windowManager != null && panelView != null) {
                        windowManager.updateViewLayout(panelView, panelParams);
                    }
                    return true;
            }
            return false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (ballView != null && windowManager != null) {
            windowManager.removeView(ballView);
            ballView = null;
        }
        if (panelView != null && windowManager != null) {
            windowManager.removeView(panelView);
            panelView = null;
        }
        panelShowing = false;
    }
}
