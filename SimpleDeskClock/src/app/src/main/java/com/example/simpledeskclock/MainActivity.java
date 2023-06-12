package com.example.simpledeskclock;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, SensorEventListener {

    // 画面の明るさ設定値（バックライトON）
    private final float LCDON_BRIGHTNESS_VAL = 0.1f;
    // 画面の明るさ設定値（バックライトOFF ... 0.0の場合、タップ操作ができない機種があるため、0.0ではない値）
    private final float LCDOFF_BRIGHTNESS_VAL = 1.0f / 125.0f;
    private final float SCREENOFF_LIGHT_SENSOR_VAL = 3.0f;
    // タイマーで1秒毎に画面を書き換えるため
    private final Handler handler = new Handler(Looper.myLooper());
    private Runnable runnable;
    private int checkEnvInterval = 0;
    // 照度センサー読み出し用
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private float lightSensorValue;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // バックライトを最小にする（バックライトを最小輝度でONにする）
        BacklightTurnOn();
        // スリープを阻止する
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 照度センサー値取得のため
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        lightSensorValue = 1000;    // 照度初期値は1000とする

        // ボタンのクリックイベントを受け取る
        findViewById(R.id.button_quit).setOnClickListener(this);

        runnable = new Runnable() {
            @Override
            public void run() {
                TextView txt_1 = findViewById(R.id.textView_1);
                TextView txt_2 = findViewById(R.id.textView_2);
                TextView txt_3 = findViewById(R.id.textView_3);

                // 1秒毎に日時表示を更新する
                Calendar calendar = Calendar.getInstance();
                SimpleDateFormat formatter_1 = new SimpleDateFormat("MM'月'dd'日('E')'");
                txt_1.setText(formatter_1.format(calendar.getTime()));
                SimpleDateFormat formatter_2 = new SimpleDateFormat("k':'mm':'ss");
                txt_2.setText(formatter_2.format(calendar.getTime()));

                // 20秒ごとにプロパティ表示更新、照度センサーおよび充電状態によってバックライトON・OFF制御をする
                if (checkEnvInterval == 0 || checkEnvInterval == 20 || checkEnvInterval == 40) {
                    float brightness = getWindow().getAttributes().screenBrightness;
                    int batteryLevel = GetBatteryLevel();
                    int batteryCharged = GetBatteryCharged();
                    txt_3.setText(String.format("照度 %.1f , 画面輝度 %.3f , 充電 %d%% (%s)", GetLightSensor(), brightness, batteryLevel, batteryCharged == BatteryManager.BATTERY_STATUS_DISCHARGING ? "放電中" : "充電中"));

                    if (brightness >= 0.1 && GetLightSensor() <= SCREENOFF_LIGHT_SENSOR_VAL) {
                        BacklightTurnOff();
                    } else if (brightness >= LCDON_BRIGHTNESS_VAL && batteryCharged == BatteryManager.BATTERY_STATUS_DISCHARGING) {
                        BacklightTurnOff();
                    } else if (brightness < LCDON_BRIGHTNESS_VAL && batteryCharged != BatteryManager.BATTERY_STATUS_DISCHARGING && GetLightSensor() > SCREENOFF_LIGHT_SENSOR_VAL) {
                        BacklightTurnOn();
                    }
                } else if (checkEnvInterval == 5 || checkEnvInterval == 25 || checkEnvInterval == 45) {
                    // 3行目プロパティ表示は、5秒間表示後に消去（空白文字列に）する
                    txt_3.setText("     ");
                }
                checkEnvInterval++;
                if (checkEnvInterval > 60) {
                    checkEnvInterval = 0;
                }

                handler.removeCallbacks(runnable);
                // 1秒毎に再帰呼出し
                handler.postDelayed(runnable, 1000);
            }
        };
        handler.postDelayed(runnable, 1000);

    }

    @Override
    public void onClick(View view) {
        if (view != null) {
            // 終了ボタンが押された場合、プログラムを終了する
            if (view.getId() == R.id.button_quit) {
                finish();
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            BacklightTurnOn();
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();

        lightSensorValue = 1000;    // 照度初期値は1000とする
        // センサー イベント リスナーを登録する
        sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // （プログラムが終了するときには）センサー イベント リスナーを解除する
        sensorManager.unregisterListener(this);
    }

    // sensorManager.registerListener により必要とされるメソッド
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        // 照度センサーの値が通知された場合
        if (sensorEvent.sensor.getType() == Sensor.TYPE_LIGHT) {
            lightSensorValue = sensorEvent.values[0];
        }
    }

    // sensorManager.registerListener により必要とされるメソッド
    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private void BacklightTurnOff() {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        // バックライト輝度をゼロ（ゼロにするとタッチ操作も不能となる場合があるので、1/256あたりまで下げる）
        lp.screenBrightness = LCDOFF_BRIGHTNESS_VAL;
        getWindow().setAttributes(lp);

    }

    private void BacklightTurnOn() {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        // バックライト輝度 最小（0.0 から 1.0 の間で指定する）
        lp.screenBrightness = LCDON_BRIGHTNESS_VAL;
        getWindow().setAttributes(lp);

    }

    private int GetBatteryLevel() {
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = this.registerReceiver(null, intentFilter);

        return batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
    }

    private int GetBatteryCharged() {
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = this.registerReceiver(null, intentFilter);
        // BATTERY_STATUS_CHARGING = 2
        // BATTERY_STATUS_DISCHARGING = 3
        return batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
    }

    private float GetLightSensor() {
        return lightSensorValue;
    }

}