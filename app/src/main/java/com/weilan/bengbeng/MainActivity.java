package com.weilan.bengbeng;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

/**
 * 全屏沉浸式密码输入界面。
 * - 普通密码 -> HomeActivity
 * - 管理员密码 -> AdminSettingsActivity
 * 像游戏一样全屏，可通过导航键/手势退出。
 */
public class MainActivity extends AppCompatActivity {

    private EditText etPassword;
    private TextView tvError;
    private DatabaseHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = new DatabaseHelper(this);

        etPassword = findViewById(R.id.et_password);
        tvError = findViewById(R.id.tv_error);
        MaterialButton btnEnter = findViewById(R.id.btn_enter);

        btnEnter.setOnClickListener(v -> attemptLogin());
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUI();
    }

    private void hideSystemUI() {
        // 沉浸式全屏：隐藏状态栏和导航栏，但仍可手势唤出
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
        // 允许在刘海屏区域显示
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }
    }

    private void attemptLogin() {
        String input = etPassword.getText().toString().trim();
        String normalPwd = db.get(DatabaseHelper.KEY_NORMAL_PWD, "mf1234s56");
        String adminPwd = db.get(DatabaseHelper.KEY_ADMIN_PWD, "zyf7b65g08");

        if (input.isEmpty()) {
            showError("请输入密码");
            return;
        }

        if (input.equals(adminPwd)) {
            tvError.setVisibility(View.GONE);
            startActivity(new Intent(this, AdminSettingsActivity.class));
        } else if (input.equals(normalPwd)) {
            tvError.setVisibility(View.GONE);
            startActivity(new Intent(this, HomeActivity.class));
        } else {
            showError(getString(R.string.wrong_password));
        }
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }
}
