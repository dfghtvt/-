package com.weilan.bengbeng;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * 本地 SQLite 数据库，存储应用设置。
 * 管理员修改的设置会写入此数据库并应用到应用。
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "weilan_bengbeng.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE_SETTINGS = "settings";

    public static final String KEY_NORMAL_PWD = "normal_pwd";
    public static final String KEY_ADMIN_PWD = "admin_pwd";
    public static final String KEY_FLOAT_TITLE = "float_title";
    public static final String KEY_ACCENT_COLOR = "accent_color";
    public static final String KEY_SW_GOD = "sw_god";
    public static final String KEY_SW_HP = "sw_hp";
    public static final String KEY_SW_RECOIL = "sw_recoil";
    public static final String KEY_SW_SPEED = "sw_speed";

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_SETTINGS + " ("
                + "key TEXT PRIMARY KEY, "
                + "value TEXT NOT NULL)");

        // 初始化默认值
        put(db, KEY_NORMAL_PWD, "mf1234s56");
        put(db, KEY_ADMIN_PWD, "zyf7b65g08");
        put(db, KEY_FLOAT_TITLE, "蔚蓝绷绷");
        put(db, KEY_ACCENT_COLOR, "blue");
        put(db, KEY_SW_GOD, "1");
        put(db, KEY_SW_HP, "1");
        put(db, KEY_SW_RECOIL, "0");
        put(db, KEY_SW_SPEED, "0");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SETTINGS);
        onCreate(db);
    }

    private static void put(SQLiteDatabase db, String key, String value) {
        ContentValues cv = new ContentValues();
        cv.put("key", key);
        cv.put("value", value);
        db.insertWithOnConflict(TABLE_SETTINGS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void set(String key, String value) {
        SQLiteDatabase db = getWritableDatabase();
        put(db, key, value);
        db.close();
    }

    public String get(String key, String defaultValue) {
        SQLiteDatabase db = getReadableDatabase();
        String value = defaultValue;
        Cursor cursor = db.query(TABLE_SETTINGS, new String[]{"value"},
                "key=?", new String[]{key}, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                value = cursor.getString(0);
            }
            cursor.close();
        }
        db.close();
        return value;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String v = get(key, defaultValue ? "1" : "0");
        return "1".equals(v);
    }

    public void setBoolean(String key, boolean value) {
        set(key, value ? "1" : "0");
    }
}
