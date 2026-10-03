package com.weilan.bengbeng;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.button.MaterialButton;

/**
 * 管理员设置界面（iOS 26 风格）。
 * 修改后直接写入 SQLite 数据库，应用内的悬浮窗等会读取这些设置。
 */
public class AdminSettingsActivity extends AppCompatActivity {

    private DatabaseHelper db;
    private EditText etNormalPwd, etAdminPwd, etFloatTitle;
    private SwitchCompat swGod, swHp, swRecoil, swSpeed;
    private String selectedColor = "blue";
    private TextView tvSaveHint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        db = new DatabaseHelper(this);

        etNormalPwd = findViewById(R.id.et_normal_pwd);
        etAdminPwd = findViewById(R.id.et_admin_pwd);
        etFloatTitle = findViewById(R.id.et_float_title);
        swGod = findViewById(R.id.sw_god);
        swHp = findViewById(R.id.sw_infinite_hp);
        swRecoil = findViewById(R.id.sw_no_recoil);
        swSpeed = findViewById(R.id.sw_speed);
        tvSaveHint = findViewById(R.id.tv_save_hint);
        MaterialButton btnSave = findViewById(R.id.btn_save);
        TextView btnBack = findViewById(R.id.btn_back);

        btnBack.setOnClickListener(v -> finish());

        // 从数据库加载当前设置
        loadSettings();

        // Neon 数据异步加载完成后，刷新界面为云端最新值
        db.setOnLoadedListener(this::loadSettings);

        // 颜色选择
        setupColorPickers();

        btnSave.setOnClickListener(v -> saveSettings());
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }

    private void hideSystemUI() {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }
    }

    private void loadSettings() {
        etNormalPwd.setText(db.get(DatabaseHelper.KEY_NORMAL_PWD, "mf1234s56"));
        etAdminPwd.setText(db.get(DatabaseHelper.KEY_ADMIN_PWD, "zyf7b65g08"));
        etFloatTitle.setText(db.get(DatabaseHelper.KEY_FLOAT_TITLE, "蔚蓝绷绷"));
        swGod.setChecked(db.getBoolean(DatabaseHelper.KEY_SW_GOD, true));
        swHp.setChecked(db.getBoolean(DatabaseHelper.KEY_SW_HP, true));
        swRecoil.setChecked(db.getBoolean(DatabaseHelper.KEY_SW_RECOIL, false));
        swSpeed.setChecked(db.getBoolean(DatabaseHelper.KEY_SW_SPEED, false));
        selectedColor = db.get(DatabaseHelper.KEY_ACCENT_COLOR, "blue");
        highlightColor(selectedColor);
    }

    private void setupColorPickers() {
        int[] ids = {R.id.color_blue, R.id.color_cyan, R.id.color_pink, R.id.color_green};
        for (int id : ids) {
            View v = findViewById(id);
            if (v != null) {
                v.setOnClickListener(view -> {
                    selectedColor = (String) view.getTag();
                    highlightColor(selectedColor);
                });
            }
        }
    }

    private void highlightColor(String color) {
        int[] ids = {R.id.color_blue, R.id.color_cyan, R.id.color_pink, R.id.color_green};
        int[] colors = {R.color.ios_blue, R.color.accent_cyan, R.color.accent_pink, R.color.ios_green};
        String[] tags = {"blue", "cyan", "pink", "green"};
        for (int i = 0; i < ids.length; i++) {
            View v = findViewById(ids[i]);
            if (v != null) {
                // 每次都新建 GradientDrawable，避免 ColorDrawable 强转崩溃
                GradientDrawable d = new GradientDrawable();
                d.setShape(GradientDrawable.OVAL);
                d.setColor(getResources().getColor(colors[i]));
                if (tags[i].equals(color)) {
                    d.setStroke(6, Color.WHITE);
                }
                v.setBackground(d);
            }
        }
    }

    private void saveSettings() {
        db.set(DatabaseHelper.KEY_NORMAL_PWD, etNormalPwd.getText().toString().trim());
        db.set(DatabaseHelper.KEY_ADMIN_PWD, etAdminPwd.getText().toString().trim());
        db.set(DatabaseHelper.KEY_FLOAT_TITLE, etFloatTitle.getText().toString().trim());
        db.set(DatabaseHelper.KEY_ACCENT_COLOR, selectedColor);
        db.setBoolean(DatabaseHelper.KEY_SW_GOD, swGod.isChecked());
        db.setBoolean(DatabaseHelper.KEY_SW_HP, swHp.isChecked());
        db.setBoolean(DatabaseHelper.KEY_SW_RECOIL, swRecoil.isChecked());
        db.setBoolean(DatabaseHelper.KEY_SW_SPEED, swSpeed.isChecked());

        tvSaveHint.setText("设置已保存并更新到数据库");
        tvSaveHint.setVisibility(View.VISIBLE);
        tvSaveHint.postDelayed(() -> tvSaveHint.setVisibility(View.GONE), 2500);
    }
}
