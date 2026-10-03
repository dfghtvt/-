package com.weilan.bengbeng;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;

/**
 * 悬浮窗服务（外挂风格）：
 * - 先显示一个液态玻璃质感的悬浮球（应用图标）
 * - 点击悬浮球展开/收起功能面板
 * - 悬浮球可拖动；面板可关闭（仅收起，悬浮球仍在）
 * - Android 12+ 使用 RenderEffect 实现真实毛玻璃模糊
 */
public class FloatingWindowService extends Service {

    private static final String CHANNEL_ID = "weilan_float_channel";
    private static final int NOTIF_ID = 0xB9;

    private WindowManager windowManager;
    private View ballView;
    private View panelView;
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

        // 悬浮球本身就是根 ImageView，触摸监听直接挂在上面
        ballView.setOnTouchListener(new BallTouchListener());

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

            // 液态玻璃模糊效果（Android 12+）
            applyGlassBlur(panelView);

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
            panelParams.x = ballParams.x + 70;
            panelParams.y = ballParams.y;

            // 液态玻璃：窗口背后模糊（Android 12+），透出的桌面/应用产生毛玻璃
            applyWindowBlur(panelParams);

            applyDbSettings();

            TextView btnClose = panelView.findViewById(R.id.btn_float_close);
            btnClose.setOnClickListener(v -> hidePanel());

            View dragHandle = panelView.findViewById(R.id.drag_handle);
            dragHandle.setOnTouchListener(new PanelDragListener());
        }

        applyDbSettings();
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

    /**
     * 应用液态玻璃模糊效果。
     * 1. 窗口背后模糊（Android 12+）：让透出的桌面/应用产生真实毛玻璃
     * 2. 面板背景图模糊（RenderEffect）：叠加在玻璃层上
     * 低版本退化为半透明渐变（在 XML 中定义）。
     */
    private void applyWindowBlur(WindowManager.LayoutParams params) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                params.setBlurBehindRadius(40);
            } catch (Exception ignored) {
            }
        }
    }

    private void applyGlassBlur(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                View bg = view.findViewById(R.id.float_glass_bg);
                if (bg != null) {
                    bg.setRenderEffect(
                            RenderEffect.createBlurEffect(30f, 30f, Shader.TileMode.CLAMP));
                }
            } catch (Exception ignored) {
            }
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

        View.OnClickListener dummy = v -> {};
        if (swGod != null) swGod.setOnClickListener(dummy);
        if (swHp != null) swHp.setOnClickListener(dummy);
        if (swRecoil != null) swRecoil.setOnClickListener(dummy);
        if (swSpeed != null) swSpeed.setOnClickListener(dummy);
        if (swWall != null) swWall.setOnClickListener(dummy);
    }

    /**
     * 悬浮球触摸：手动识别点击（ACTION_UP 时若未拖动则触发）+ 拖动。
     * 比 GestureDetector 更可靠，避免轻微移动导致点击丢失。
     */
    private class BallTouchListener implements View.OnTouchListener {
        private int initialX, initialY;
        private float initialTouchX, initialTouchY;
        private boolean dragging = false;
        private static final int TOUCH_SLOP = 15; // 像素，超过则视为拖动

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = ballParams.x;
                    initialY = ballParams.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int) (event.getRawX() - initialTouchX);
                    int dy = (int) (event.getRawY() - initialTouchY);
                    if (!dragging
                            && (Math.abs(dx) > TOUCH_SLOP || Math.abs(dy) > TOUCH_SLOP)) {
                        dragging = true;
                    }
                    if (dragging) {
                        ballParams.x = initialX + dx;
                        ballParams.y = initialY + dy;
                        if (windowManager != null && ballView != null) {
                            windowManager.updateViewLayout(ballView, ballParams);
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    // 未拖动 -> 视为点击，切换面板
                    if (!dragging) {
                        togglePanel();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
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
