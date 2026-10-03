package com.weilan.bengbeng;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 云端 Neon (PostgreSQL) 数据库，通过 Neon Data API (HTTP) 访问。
 * 以 key-value 形式存储应用设置。
 * 内存缓存保证读取即时返回；写入时先更新缓存，再异步同步到 Neon。
 */
public class DatabaseHelper {

    // Neon Data API 端点
    private static final String NEON_SQL_URL =
            "https://ep-broad-morning-b3bvdfvh.c-4.ap-southeast-1.aws.neon.tech/sql";
    // Neon 连接串（含数据库账号密码），通过 neon-connection-string 请求头发送
    private static final String NEON_CONNECTION_STRING =
            "postgresql://neondb_owner:npg_TKw9qNFE5yYD@ep-broad-morning-b3bvdfvh.c-4.ap-southeast-1.aws.neon.tech/neondb?sslmode=require";

    // 设置键名（与原 SQLite 版本保持一致）
    public static final String KEY_NORMAL_PWD = "normal_pwd";
    public static final String KEY_ADMIN_PWD = "admin_pwd";
    public static final String KEY_FLOAT_TITLE = "float_title";
    public static final String KEY_ACCENT_COLOR = "accent_color";
    public static final String KEY_SW_GOD = "sw_god";
    public static final String KEY_SW_HP = "sw_hp";
    public static final String KEY_SW_RECOIL = "sw_recoil";
    public static final String KEY_SW_SPEED = "sw_speed";

    // 内存缓存：保证读取即时返回，无需等待网络
    private final Map<String, String> cache = new HashMap<>();
    // 单线程执行器，保证 Neon 读写顺序执行
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    // 主线程 Handler，用于在加载完成后回调
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile boolean loaded = false;
    private Runnable onLoadedCallback;

    public DatabaseHelper(Context context) {
        // 先用默认值填充缓存，确保 Neon 不可达时应用仍可运行
        cache.put(KEY_NORMAL_PWD, "mf1234s56");
        cache.put(KEY_ADMIN_PWD, "zyf7b65g08");
        cache.put(KEY_FLOAT_TITLE, "蔚蓝绷绷");
        cache.put(KEY_ACCENT_COLOR, "blue");
        cache.put(KEY_SW_GOD, "1");
        cache.put(KEY_SW_HP, "1");
        cache.put(KEY_SW_RECOIL, "0");
        cache.put(KEY_SW_SPEED, "0");

        // 异步从 Neon 拉取最新设置
        loadFromNeon();
    }

    /**
     * 注册一个回调，当 Neon 数据加载完成后在主线程执行。
     */
    public void setOnLoadedListener(Runnable callback) {
        if (loaded) {
            mainHandler.post(callback);
        } else {
            this.onLoadedCallback = callback;
        }
    }

    /** 从 Neon 异步加载全部设置到缓存 */
    private void loadFromNeon() {
        executor.execute(() -> {
            try {
                String resp = postQuery("SELECT key, value FROM settings", null);
                JSONObject json = new JSONObject(resp);
                JSONArray rows = json.getJSONArray("rows");
                synchronized (cache) {
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject row = rows.getJSONObject(i);
                        cache.put(row.getString("key"), row.getString("value"));
                    }
                    loaded = true;
                }
                if (onLoadedCallback != null) {
                    mainHandler.post(onLoadedCallback);
                }
            } catch (Exception e) {
                // 网络异常：保留缓存中的默认值
            }
        });
    }

    /** 读取设置（即时返回，来自内存缓存） */
    public String get(String key, String defaultValue) {
        synchronized (cache) {
            String v = cache.get(key);
            return v != null ? v : defaultValue;
        }
    }

    /** 写入设置：立即更新缓存，后台同步到 Neon */
    public void set(String key, String value) {
        synchronized (cache) {
            cache.put(key, value);
        }
        // 异步 upsert 到 Neon（使用参数化查询，避免 SQL 注入）
        executor.execute(() -> {
            try {
                String sql = "INSERT INTO settings (key, value) VALUES ($1, $2) "
                        + "ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value";
                String[] params = {key, value};
                postQuery(sql, params);
            } catch (Exception ignored) {
                // 同步失败不影响本次会话（缓存仍是最新值）
            }
        });
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String v = get(key, defaultValue ? "1" : "0");
        return "1".equals(v);
    }

    public void setBoolean(String key, boolean value) {
        set(key, value ? "1" : "0");
    }

    /** 向 Neon Data API 发送 SQL 请求 */
    private String postQuery(String sql, String[] params) throws Exception {
        JSONObject body = new JSONObject();
        body.put("query", sql);
        if (params != null) {
            JSONArray arr = new JSONArray();
            for (String p : params) arr.put(p);
            body.put("params", arr);
        }
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);

        HttpURLConnection conn = (HttpURLConnection) new URL(NEON_SQL_URL).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("neon-connection-string", NEON_CONNECTION_STRING);
            conn.setFixedLengthStreamingMode(payload.length);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload);
            }

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300)
                    ? conn.getInputStream() : conn.getErrorStream();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }
}
