package com.example.simpledeskclock;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, SensorEventListener {

    // 画面の明るさ設定値 0.0-1.0（バックライトON）
    private final float LCD_ON_BRIGHTNESS_DEFAULT = 0.1f;
    // 画面の明るさ設定値 0.0-1.0（バックライトOFF ... 0.0の場合、タップ操作ができない機種があるため、0.0ではない値）
    private final float LCD_OFF_BRIGHTNESS_DEFAULT = 1.0f / 125.0f;
    // バックライトOFFにする照度センサー値
    private final float LUX_MIN_DEFAULT = 3.0f;
    // タイマーで1秒毎に画面を書き換えるため
    private final Handler handler = new Handler(Looper.getMainLooper());
    // 照度測定・画面輝度設定 インターバル（秒）
    private final int CHECK_ENV_INTERVAL = 5;
    // バックライト制御 無効化開始時刻（0:00からの「分」数）デフォルト値は7時（7*60）
    private final int TIME_BACKLIGHT_ON_START_DEFAULT = 7 * 60;
    // バックライト制御 無効化終了時刻（0:00からの「分」数）デフォルト値は21時（21*60）
    private final int TIME_BACKLIGHT_ON_END_DEFAULT = 21 * 60;
    // フォントサイズ
    private final int FONT_SIZE_DATE_DEFAULT = 90;
    private final int FONT_SIZE_TIME_DEFAULT = 90;
    // 画面タップしたときのバックライトON持続時間（秒）（デフォルト 30秒）
    private final int TAP_SCREEN_ON_DURATION = 30;
    // AMEDAS 観測地点No、観測地点名 データベース
    private final Map<String, String> stationMap = new HashMap<>(); // 観測地点名 → 観測地点番号のマッピング
    private final List<String> stationNames = new ArrayList<>(); // 観測地点名のリスト
    // バックライト制御 無効化時刻範囲 Start 〜 End
    private int timeBacklightOnStart = TIME_BACKLIGHT_ON_START_DEFAULT;
    private int timeBacklightOnEnd = TIME_BACKLIGHT_ON_END_DEFAULT;
    // 画面タップしたときのバックライトONを終了する時刻
    private Calendar timeBacklightTapOn = Calendar.getInstance();
    // 画面の明るさ設定値 0.0-1.0（バックライトON）
    private float settingLcdOnBrightness = LCD_ON_BRIGHTNESS_DEFAULT;
    // 画面の明るさ設定値 0.0-1.0（バックライトOFF）
    private float settingLcdOffBrightness = LCD_OFF_BRIGHTNESS_DEFAULT;
    // バックライトOFFにする照度センサー値
    private float settingLuxMin = LUX_MIN_DEFAULT;
    // 画面スリープ制御（WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON）ON/OFF設定
    private boolean enableKeepScreenOnCtrl = false;
    // フォントサイズ
    private int settingFontsizeDate = FONT_SIZE_DATE_DEFAULT;
    private int settingFontsizeTime = FONT_SIZE_TIME_DEFAULT;
    private Runnable runnable;
    private int checkEnvInterval = 0;
    // 照度センサー読み出し用
    private SensorManager sensorManager;
    private float lux;
    // Logcatにログを書き込む
    private boolean enableLogcat = false;
    // 画面上に1行ログを表示する
    private boolean enableScreenLog = true;
    private List<String> filteredNames = new ArrayList<>(); // フィルタリング後の地点名
    private ArrayAdapter<String> adapter;
    // 選択済み AMEDAS 観測地点No、観測地点名
    private String stationNo_Selected = "";
    private String stationName_Selected = "";
    // AMEDASデータ受信・表示を有効化するか
    private boolean enableAmedasReceive = false;
    // AMEDASデータを表示用1行文字列にしたもの
    private String amedasString = "";
    // 前回、AMEDASデータにアクセスした時刻
    private Calendar calendarLastAmedasReceive = Calendar.getInstance();

    /**
     * 時刻文字列を、int 時・分に分離するメソッド
     *
     * @param time 時刻（時・分）を表す文字列で、例えば12時34分なら "12:34" が格納される
     * @return int [] 時、分 のint 値が格納される。"12:34"が入力された場合は、int配列 [12,34] が戻り値となる
     */
    private static int[] parseTime(String time) {
        // 前後の空白をトリム
        time = time.trim();
        // コロンで文字列を分割
        String[] parts = time.split(":");
        // 2桁数値の確認（正規表現"2桁数値:2桁数値"に一致）
        if (!time.matches("\\d{2}:\\d{2}")) {
            return new int[]{0, 0};
        }
        // 文字列からintに変換
        int[] ret = new int[2];
        ret[0] = Integer.parseInt(parts[0]);    // hour
        ret[1] = Integer.parseInt(parts[1]);    // minutes
        // 時刻として成り立つ値か検証
        if (ret[0] < 0 || ret[0] > 23) {
            ret[0] = 0;
        }
        if (ret[1] < 0 || ret[1] > 59) {
            ret[1] = 0;
        }

        // 結果を返す
        return ret;
    }

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 共有環境設定ファイル(SharedPreferences)から設定値を復元する
        LoadPreference();

        // バックライトを最小にする（バックライトを最小輝度でONにする）
        BacklightTurnOn();
        // スリープを阻止する
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 照度センサー値取得のため
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        // スクリーンOFFでもバックグラウンドでセンサーを使う場合は、ここでregisterListenerを実行。
        // そうでなければ、OnResume()内でregisterListenerを、onPause()内でunregisterListenerを実行する
        sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_UI);
        lux = 1000;    // 照度初期値は1000とする

        // ボタンのクリックイベントを受け取る
        findViewById(R.id.button_quit).setOnClickListener(this);
        findViewById(R.id.button_config).setOnClickListener(this);

        // AMEDAS 観測地点データベースを気象庁Webサイトより読み込む
        fetchStations();

        // 前回、AMEDASデータ受信の時刻を11分前にし、（10分ごと受信の条件で）初回受信できるようにする
        calendarLastAmedasReceive.add(Calendar.MINUTE, -11);

        runnable = new Runnable() {
            @SuppressLint({"DefaultLocale", "SetTextI18n"})
            @Override
            public void run() {
                TextView txt_1 = findViewById(R.id.textView_1);
                TextView txt_2 = findViewById(R.id.textView_2);
                TextView txt_3 = findViewById(R.id.textView_3);
                TextView txt_info = findViewById(R.id.textView_info);

                // 1秒毎に日時表示を更新する
                Calendar calendar = Calendar.getInstance();
                SimpleDateFormat formatter_1 = new SimpleDateFormat("MM'月'dd'日('E')'", Locale.JAPANESE);
                txt_1.setText(formatter_1.format(calendar.getTime()));
                SimpleDateFormat formatter_2 = new SimpleDateFormat("k':'mm':'ss", Locale.JAPANESE);
                txt_2.setText(formatter_2.format(calendar.getTime()));

                // 5秒ごとにプロパティ表示更新、照度センサーおよび充電状態によってバックライトON・OFF制御をする
                if (checkEnvInterval % CHECK_ENV_INTERVAL == 0) {
                    // AMEDASデータ受信(10分ごと)
                    long dulationLastAmedasReceive = Calendar.getInstance().getTimeInMillis() - calendarLastAmedasReceive.getTimeInMillis();
                    if (enableAmedasReceive && dulationLastAmedasReceive / 1000 / 60 >= 10 && !stationNo_Selected.isEmpty()) {
                        calendarLastAmedasReceive = Calendar.getInstance();
                        fetchWeatherData(stationNo_Selected);
                    }
                    txt_info.setText(amedasString);

                    float brightness = getWindow().getAttributes().screenBrightness;
                    int batteryLevel = GetBatteryLevel();
                    int currentTimeMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);

                    boolean isTapOn = (calendar.compareTo(timeBacklightTapOn) <= 0);
                    boolean isTimeBacklightOn = (currentTimeMinutes >= timeBacklightOnStart && currentTimeMinutes < timeBacklightOnEnd);
                    boolean isBatteryCharging = (GetBatteryCharged() != BatteryManager.BATTERY_STATUS_DISCHARGING);
                    boolean isLuxHigh = (GetLux() >= settingLuxMin);

                    // バックライトのON/OFF制御
                    if (isTapOn) {
                        // 現在時刻（calendar）がtimeBacklightTapOnより前の場合、強制的にバックライトON
                        BacklightTurnOn();
                    } else if (!isBatteryCharging) {
                        // バッテリーが充電中でない場合は、バックライトOFFとする
                        BacklightTurnOff();
                    } else if (isTimeBacklightOn) {
                        // バッテリーが充電中で、バックライト強制ON時間帯の場合は、バックライトONとする
                        BacklightTurnOn();
                    } else if (isLuxHigh) {
                        // バッテリーが充電中で、バックライト強制ON時間帯ではなく、照度が高い場合、バックライトONとする
                        BacklightTurnOn();
                    } else {
                        BacklightTurnOff();
                    }

                    // バックライト制御を行ったあとに、その状態での輝度を再度得る（ログ、画面デバッグ表示用）
                    brightness = getWindow().getAttributes().screenBrightness;

                    // 画面表示用 および LogCat 出力用 ログ文字列
                    String strLog = String.format("%d TapON:%s 常時ON時:%s 充電:%s(%d%% status=%d) Lux:%.1f 輝度:%.3f",
                            checkEnvInterval,
                            isTapOn ? "○" : "×",
                            isTimeBacklightOn ? "○" : "×",
                            isBatteryCharging ? "○" : "×", batteryLevel, GetBatteryCharged(),
                            GetLux(),
                            brightness);
                    if (enableAmedasReceive) {
                        // AMEDAS前回受信からの経過時間をログ文字列に加える
                        strLog += String.format(" Amedas:%02d:%02d", dulationLastAmedasReceive / 1000 / 60, dulationLastAmedasReceive / 1000 % 60);
                    }
                    // ログを画面表示
                    if (enableScreenLog) {
                        txt_3.setText(strLog);
                    } else {
                        txt_3.setText("");
                    }

                    // ログをLogCatに出力
                    if (enableLogcat) {
                        Log.d("SimpleDeskClock", strLog);
                    }

                    // フォントサイズの変更
                    float sp = txt_1.getTextSize() / getResources().getDisplayMetrics().scaledDensity;
                    if (sp < settingFontsizeDate - 1 || settingFontsizeDate + 1 < sp)
                        txt_1.setTextSize(TypedValue.COMPLEX_UNIT_SP, settingFontsizeDate);
                    sp = txt_2.getTextSize() / getResources().getDisplayMetrics().scaledDensity;
                    if (sp < settingFontsizeTime - 1 || settingFontsizeDate + 1 < sp)
                        txt_2.setTextSize(TypedValue.COMPLEX_UNIT_SP, settingFontsizeTime);

                }
                checkEnvInterval++;
                if (checkEnvInterval > 60) {
                    checkEnvInterval = 0;
                }
                // 残っているスレッドを削除
                handler.removeCallbacks(runnable);
                // スレッドを1秒後に再帰呼出し
                handler.postDelayed(runnable, 1000);
            }
        };
        // スレッド初回実行
        handler.post(runnable);

    }

    /**
     * ボタンが押された場合の処理
     *
     * @param view : The view that was clicked
     */
    @Override
    public void onClick(View view) {
        if (view != null) {
            if (view.getId() == R.id.button_quit) {
                // *****
                // 「終了」ボタンが押された場合の処理
                // プログラムを終了する
                // *****
                finish();
            } else if (view.getId() == R.id.button_config) {
                // *****
                // 「設定」ボタンが押された場合の処理
                // バックライト制御の無効化時間、照度の設定AlertDialogの表示
                // *****
                ShowSettingDialog();

            }
        }
    }

    /**
     * プログラム終了時に次の処理を行う。「1秒毎実行スレッドの削除」「センサーイベントリスナーの開放」
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 残っているスレッドを削除
        if (runnable != null) {
            handler.removeCallbacks(runnable);
        }
        // （プログラムが終了するときには）センサー イベント リスナーを解除する
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

//    @Override
//    protected void onResume() {
//        super.onResume();
//
//        lux = 1000;    // 照度初期値は1000とする
//        // センサー イベント リスナーを登録する
//        sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_UI);
//    }
//
//    @Override
//    protected void onPause() {
//        super.onPause();
//        // （プログラムが終了するときには）センサー イベント リスナーを解除する
//        if (sensorManager != null) {
//            sensorManager.unregisterListener(this);
//        }
//    }

    /**
     * 画面のタッチイベントが通知されたら、バックライトをONにする
     *
     * @param event : The touch screen event being processed
     * @return : Return true if you have consumed the event
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            // タップ後、現在時刻より、settingTapOnDurationで指定した秒数バックライトONを継続する
            timeBacklightTapOn = Calendar.getInstance();
            timeBacklightTapOn.add(Calendar.SECOND, TAP_SCREEN_ON_DURATION);
            BacklightTurnOn();
        }
        return super.onTouchEvent(event);
    }

    /**
     * 照度センサー値の更新がシステムから通知された場合、ローカル変数 (float) lux にその値を格納する
     * ( sensorManager.registerListener により必要とされるメソッド )
     *
     * @param sensorEvent : the SensorEvent
     */
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        // 照度センサーの値が通知された場合
        if (sensorEvent.sensor.getType() == Sensor.TYPE_LIGHT) {
            lux = sensorEvent.values[0];
        }
    }

    /**
     * ( sensorManager.registerListener により必要とされるメソッド )
     *
     * @param sensor : Sensor
     * @param i      : The new accuracy of this sensor, one of SensorManager.SENSOR_STATUS_*
     */
    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    /**
     * バックライトを「消灯」（最も暗く）し、「画面をONのまま」設定を無効化するメソッド
     * <p>
     * バックライトの明るさ設定については次の説明を参照
     * ( https: //developer.android.com/reference/android/view/WindowManager.LayoutParams#screenBrightness )
     * 「画面をONのまま」設定については次の説明を参照
     * ( https: //developer.android.com/develop/background-work/background-tasks/awake/screen-on?hl=ja )
     */
    private void BacklightTurnOff() {
        // バックライト自動消灯を有効化する
        if (enableKeepScreenOnCtrl) {
//            if ((getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0) {
//                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//            }
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        // 現在のバックライトの輝度を得る
        float brightness = getWindow().getAttributes().screenBrightness;

        // バックライト輝度が、設定値settingLcdOffBrightnessであれば、消灯している
        if (brightness == settingLcdOffBrightness) return;

        WindowManager.LayoutParams lp = getWindow().getAttributes();
        // バックライト輝度をゼロ（ゼロにするとタッチ操作も不能となる場合があるので、1/256あたりまで下げる）
        lp.screenBrightness = settingLcdOffBrightness;
        getWindow().setAttributes(lp);

        // デバッグ用
        if (enableLogcat) {
            Log.d("SimpleDeskClock", "BacklightTurnOff()");
        }
    }

    /**
     * バックライトを「点灯」し、「画面をONのまま」設定を行うメソッド
     * <p>
     * バックライトの明るさ設定については次の説明を参照
     * ( https: //developer.android.com/reference/android/view/WindowManager.LayoutParams#screenBrightness )
     * 「画面をONのまま」設定については次の説明を参照
     * ( https: //developer.android.com/develop/background-work/background-tasks/awake/screen-on?hl=ja )
     */
    private void BacklightTurnOn() {
        // バックライト自動消灯を無効化する（常時バックライトON）
        if (enableKeepScreenOnCtrl) {
//            if ((getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) == 0) {
//                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//            }
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        // 現在のバックライトの輝度を得る
        float brightness = getWindow().getAttributes().screenBrightness;

        // バックライト輝度が、設定値settingLcdOnBrightnessであれば、点灯している
        if (brightness == settingLcdOnBrightness) return;

        WindowManager.LayoutParams lp = getWindow().getAttributes();
        // バックライト輝度 最小（0.0 から 1.0 の間で指定する）
        lp.screenBrightness = settingLcdOnBrightness;
        getWindow().setAttributes(lp);

        // デバッグ用
        if (enableLogcat) {
            Log.d("SimpleDeskClock", "BacklightTurnOn()");
        }
    }

    /*****
     * バッテリーの充電率を得る
     * @return int 充電率 (0 - 100)
     * ※ 注意 : Android Studioのエミュレーター Extended Controlsで値を変更しても、
     *          仮想マシンを再起動するまで値は反映されない
     */
    private int GetBatteryLevel() {
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = this.registerReceiver(null, intentFilter);

        int ret = -1;
        if (batteryStatus != null) ret = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
//        int percentLevel = (int) ((float) batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) /
//                (float) batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1) * 100.0f);
        return ret;
    }

    /**
     * バッテリーの充電状態を得る
     *
     * @return 充電状態（下記の値）
     * BATTERY_STATUS_UNKNOWN = 1
     * BATTERY_STATUS_CHARGING = 2
     * BATTERY_STATUS_DISCHARGING = 3 ← ACアダプタが接続されていないことが「間接的」に分かる
     * BATTERY_STATUS_NOT_CHARGING = 4
     * BATTERY_STATUS_FULL = 5
     */
    private int GetBatteryCharged() {
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = this.registerReceiver(null, intentFilter);
        int ret = -1;
        if (batteryStatus != null) ret = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return ret;
    }

    private float GetLux() {
        return lux;
    }

    /**
     * 「設定」ボタンが押されたときに表示するAlertDialogの機能を実装するメソッド
     */
    @SuppressLint({"ClickableViewAccessibility", "DefaultLocale"})
    private void ShowSettingDialog() {

        if (stationNames.isEmpty()) {
            fetchStations();
        }

        LayoutInflater inflater = getLayoutInflater();
        View dlgView = inflater.inflate(R.layout.setting_layout, null);

        // 各項目の初期値設定
        final EditText textTimeBklightOnStart = dlgView.findViewById(R.id.editText_TimeBklightOnStart);
        textTimeBklightOnStart.setText(String.format("%02d:%02d  ", timeBacklightOnStart / 60, timeBacklightOnStart % 60));

        final EditText textTimeBklightOnEnd = dlgView.findViewById(R.id.editText_TimeBklightOnEnd);
        textTimeBklightOnEnd.setText(String.format("%02d:%02d  ", timeBacklightOnEnd / 60, timeBacklightOnEnd % 60));

        final EditText textLightSensor = dlgView.findViewById(R.id.editText_LightSensorValue);
        textLightSensor.setText(String.format("%1.4f", settingLuxMin));

        final EditText textLcdOnBrightness = dlgView.findViewById(R.id.editText_LcdOnBrightness);
        textLcdOnBrightness.setText(String.format("%1.4f", settingLcdOnBrightness));

        final EditText textLcdOffBrightness = dlgView.findViewById(R.id.editText_LcdOffBrightness);
        textLcdOffBrightness.setText(String.format("%1.4f", settingLcdOffBrightness));

        final SwitchMaterial switchKeepScreenOn = dlgView.findViewById(R.id.switch_KeepScreenOn);
        switchKeepScreenOn.setChecked(enableKeepScreenOnCtrl);

        final SwitchMaterial switchDebugScreen = dlgView.findViewById(R.id.switch_DebugScreen);
        switchDebugScreen.setChecked(enableScreenLog);

        final SwitchMaterial switchDebugLog = dlgView.findViewById(R.id.switch_DebugLog);
        switchDebugLog.setChecked(enableLogcat);

        final EditText textFontsizeDate = dlgView.findViewById(R.id.editText_FontSize_Date);
        textFontsizeDate.setText(String.format("%d", settingFontsizeDate));

        final EditText textFontsizeTime = dlgView.findViewById(R.id.editText_FontSize_Time);
        textFontsizeTime.setText(String.format("%d", settingFontsizeTime));

        final SwitchMaterial switchAmedasReceive = dlgView.findViewById(R.id.switch_AmadasDisp);
        switchAmedasReceive.setChecked(enableAmedasReceive);

        final EditText editText_StationNo = dlgView.findViewById(R.id.editText_StationNo);
        editText_StationNo.setText(stationNo_Selected);

        final EditText editText_StationName = dlgView.findViewById(R.id.editText_StationName);
        editText_StationName.setText(stationName_Selected);

        // テキストボックスをクリックしたときの時刻設定ダイアログの表示
        CustomizeEditText_TimePickerDialog(textTimeBklightOnStart);
        CustomizeEditText_TimePickerDialog(textTimeBklightOnEnd);

        // AMEDAS観測地点 設定ボタン
        Button buttonSetStation = dlgView.findViewById(R.id.button_setStation);
        buttonSetStation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (stationNames.isEmpty()) {
                    new AlertDialog.Builder(dlgView.getContext())
                            .setTitle("ネットワークエラー")
                            .setMessage("AMEDAS観測地点データベースJSONファイルを気象庁Webからのダウンロードに失敗")
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {

                                }
                            })
                            .setCancelable(false)
                            .create()
                            .show();
                    return;
                }

                LayoutInflater inflaterSetAmedasStation = getLayoutInflater();
                View dlgView_Amedas = inflaterSetAmedasStation.inflate(R.layout.amedas_station_layout, null);


                filteredNames = new ArrayList<>(stationNames);  // 元のListが破壊されないよう、ディープコピーする
                adapter = new ArrayAdapter<>(dlgView_Amedas.getContext(), android.R.layout.simple_spinner_item, filteredNames);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                Spinner spinner = dlgView_Amedas.findViewById(R.id.spinner_station);
                spinner.setAdapter(adapter);

                // 「フィルター」ボタンを押したとき、textSearchBox に入力された文字列を含む観測地点名を抽出し、スピナーにセットする
                EditText editText_AmedasFilter = dlgView_Amedas.findViewById(R.id.editText_AmedasFilter);
                Button button_AmedasFilter = dlgView_Amedas.findViewById(R.id.button_AmedasFilter);
                button_AmedasFilter.setOnClickListener(viewFilter -> updateFilteredList(editText_AmedasFilter.getText().toString(), spinner));

                AlertDialog.Builder builderSetAmedasStation = new AlertDialog.Builder(dlgView_Amedas.getContext());
                builderSetAmedasStation.setView(dlgView_Amedas)
                        .setTitle("観測地点を選択してください")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                String selectedStationName = (String) spinner.getSelectedItem();
                                String selectedStationNo = stationMap.get(selectedStationName);
                                EditText editText_StationNo = dlgView.findViewById(R.id.editText_StationNo);
                                editText_StationNo.setText(selectedStationNo);
                                EditText editText_StationName = dlgView.findViewById(R.id.editText_StationName);
                                editText_StationName.setText(selectedStationName);
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {

                            }
                        })
                        .show();

            }
        });

        // AlertDialogの表示
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dlgView)
                .setTitle("機能設定")
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    /*
                     * OKボタンを押したときに、テキストボックスに入力されている文字列を読み込み、各設定値に上書き・格納する
                     */
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        timeBacklightOnStart = parseTime(String.valueOf(textTimeBklightOnStart.getText()))[0] * 60 + parseTime(String.valueOf(textTimeBklightOnStart.getText()))[1];
                        timeBacklightOnEnd = parseTime(String.valueOf(textTimeBklightOnEnd.getText()))[0] * 60 + parseTime(String.valueOf(textTimeBklightOnEnd.getText()))[1];
                        if (timeBacklightOnStart > timeBacklightOnEnd) {
                            // 開始時刻が終了時刻より遅い場合、デフォルト値に戻す
                            timeBacklightOnStart = TIME_BACKLIGHT_ON_START_DEFAULT;
                            timeBacklightOnEnd = TIME_BACKLIGHT_ON_END_DEFAULT;
                        }
                        settingLuxMin = Float.parseFloat(String.valueOf(textLightSensor.getText()));
                        if (settingLuxMin <= 0) {
                            // 照度センサーの値が0を下回ることは考えられないので、デフォルト値に戻す
                            settingLuxMin = LUX_MIN_DEFAULT;
                        }
                        settingLcdOnBrightness = Float.parseFloat(String.valueOf(textLcdOnBrightness.getText()));
                        if (settingLcdOnBrightness < 0.0f) {
                            // 0.0を下回る値は取れないので、輝度を最大値の0.0とする
                            settingLcdOnBrightness = 0.0f;
                        } else if (settingLcdOnBrightness > 1.0f) {
                            // 1.0を超える値は取れないので、輝度を最大値の1.0とする
                            settingLcdOnBrightness = 1.0f;
                        }
                        settingLcdOffBrightness = Float.parseFloat(String.valueOf(textLcdOffBrightness.getText()));
                        if (settingLcdOffBrightness < 0.0f) {
                            // 0.0を下回る値は取れないので、輝度を最大値の0.0とする
                            settingLcdOffBrightness = 0.0f;
                        } else if (settingLcdOffBrightness > 1.0f) {
                            // 1.0を超える値は取れないので、輝度を最大値の1.0とする
                            settingLcdOffBrightness = 1.0f;
                        }
                        if (settingLcdOnBrightness <= settingLcdOffBrightness) {
                            // ON照度がOFF照度より小さい設定はありえないので、デフォルト値に戻す
                            settingLcdOffBrightness = LCD_OFF_BRIGHTNESS_DEFAULT;
                            settingLcdOnBrightness = LCD_ON_BRIGHTNESS_DEFAULT;
                        }
                        enableKeepScreenOnCtrl = switchKeepScreenOn.isChecked();
                        enableScreenLog = switchDebugScreen.isChecked();
                        enableLogcat = switchDebugLog.isChecked();
                        settingFontsizeDate = Integer.parseInt(String.valueOf(textFontsizeDate.getText()));
                        if (settingFontsizeDate < 10 || settingFontsizeDate > 200)
                            settingFontsizeDate = FONT_SIZE_DATE_DEFAULT;
                        settingFontsizeTime = Integer.parseInt(String.valueOf(textFontsizeTime.getText()));
                        if (settingFontsizeTime < 10 || settingFontsizeTime > 200)
                            settingFontsizeTime = FONT_SIZE_TIME_DEFAULT;
                        enableAmedasReceive = switchAmedasReceive.isChecked();
                        stationNo_Selected = String.valueOf(editText_StationNo.getText());
                        stationName_Selected = String.valueOf(editText_StationName.getText());

                        // 共有環境設定ファイル(SharedPreferences)に設定値を保存する
                        SavePreference();

                        // 設定完了時に、AMEDASデータを受信する
                        if (enableAmedasReceive && !stationNo_Selected.isEmpty()) {
                            calendarLastAmedasReceive = Calendar.getInstance();
                            fetchWeatherData(stationNo_Selected);
                        }

                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Toast.makeText(MainActivity.this, "キャンセルしました", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("Reset", new DialogInterface.OnClickListener() {
                    /*
                     * RESETボタンを押したときに、デフォルト値に上書き・格納する
                     */
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        timeBacklightOnStart = TIME_BACKLIGHT_ON_START_DEFAULT;
                        timeBacklightOnEnd = TIME_BACKLIGHT_ON_END_DEFAULT;
                        settingLuxMin = LUX_MIN_DEFAULT;
                        settingLcdOnBrightness = LCD_ON_BRIGHTNESS_DEFAULT;
                        settingLcdOffBrightness = LCD_OFF_BRIGHTNESS_DEFAULT;
                        settingFontsizeDate = FONT_SIZE_DATE_DEFAULT;
                        settingFontsizeTime = FONT_SIZE_TIME_DEFAULT;
                        enableKeepScreenOnCtrl = false;
                        enableScreenLog = true;
                        enableLogcat = false;
                        enableAmedasReceive = false;
                        stationNo_Selected = "";
                        stationName_Selected = "";

                        // 共有環境設定ファイル(SharedPreferences)に設定値を保存する
                        SavePreference();
                    }
                })
                .show();
    }

    /**
     * 時刻「時・分」が格納されているUIがクリックされたときの機能設定（時間選択ツール ダイアログの表示と、キーボード表示の抑制）を行うメソッド
     *
     * @param editText 設定する時刻「時・分」が格納されているUI
     */
    @SuppressLint("ClickableViewAccessibility")
    private void CustomizeEditText_TimePickerDialog(EditText editText) {
        editText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showTimePickerDialog(editText);
            }
        });
        editText.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    // ソフトキーボードを表示しないようにする
                    // editText_TimeDialog.setInputType(InputType.TYPE_NULL);
                    editText.requestFocus();
                    editText.performClick(); // TimePickerDialogを表示
                }
                return true;
            }
        });
    }


    /***
     * 気象庁のWebサーバより、指定した観測地点Noの最新時間帯JSONデータをダウンロードし、
     * 最新データをString amedasString に格納する
     *
     * @param stationId : 気象庁の観測地点No
     */
    private void fetchWeatherData(String stationId) {
        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(new Runnable() {
            @SuppressLint("DefaultLocale")
            @Override
            public void run() {
                // **
                // Android API 25以前の日時取得方法
                // 現在の日時を取得
                Calendar calendar = Calendar.getInstance();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
                String yyyymmdd = dateFormat.format(calendar.getTime());
                // 最新の3時間区分（0, 3, 6, ... 21）を計算し、ゼロパディング
                String h3Formatted = String.format("%02d", (calendar.get(Calendar.HOUR_OF_DAY) / 3) * 3);

                // JSONデータのURL
                String urlCoreStr = stationId + "/" + yyyymmdd + "_" + h3Formatted + ".json";
                String urlStr = "https://www.jma.go.jp/bosai/amedas/data/point/" + urlCoreStr;

                HttpURLConnection connection = null;
                InputStream inputStream = null;
                BufferedReader reader = null;

                try {

                    URL url = new URL(urlStr);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(5000);     // timeout 5 sec
                    connection.setReadTimeout(5000);        // timeout 5 sec
                    connection.setRequestMethod("GET");

                    // connect() の記述は必須ではないが、HTTPレスポンスコードで200(HTTP OK)以外をエラーとするために実施する
                    connection.connect();
                    int responseCode = connection.getResponseCode();
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        throw new Exception(String.format("Error HTTP response code : %d", responseCode));
                    }

                    // HTTP connection よりデータを1行ずつ全てダウンロードし、文字列 response に逐次追加する
                    inputStream = connection.getInputStream();
                    reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject jsonObject = new JSONObject(response.toString());

                    // String latestTime = jsonObject.keys().next(); // 最初のキーはファイル中で最も古いデータ

                    // yyyymmddHHMMSSのフォーマット(14桁の数字)を確認する正規表現
                    Pattern timePattern = Pattern.compile("\\d{14}");
                    // 最新時刻のキーを得る
                    Iterator<String> keysIterator = jsonObject.keys();
                    String latestTime = null;
                    // すべてのキーを走査して最大値を取得（最新の時刻）
                    while (keysIterator.hasNext()) {
                        String key = keysIterator.next();
                        // キーがyyyymmddHHMMSSのフォーマットに適合するかのチェック
                        if (!timePattern.matcher(key).matches()) {
                            continue; // 一致しないキーはスキップ
                        }
                        if (latestTime == null || key.compareTo(latestTime) > 0) {
                            latestTime = key;
                        }
                    }
                    // 有効なキーが見つからなかった場合
                    if (latestTime == null) {
                        throw new Exception("yyyymmddHHMMSSフォーマットのキーが存在しません");
                    }

                    // 最新の観測データを取得
                    JSONObject latestData = jsonObject.getJSONObject(latestTime);


                    // JSONデータより観測値を取り出す
                    // JSONに当該項目が無い（観測していない）場合は、-255 が代入される
                    double temperature = latestData.isNull("temp") ? -255.0 : latestData.getJSONArray("temp").getDouble(0);
//                    double minTemperature = latestData.isNull("minTemp") ? -255.0 : latestData.getJSONArray("minTemp").getDouble(0);
//                    double maxTemperature = latestData.isNull("maxTemp") ? -255.0 : latestData.getJSONArray("maxTemp").getDouble(0);
                    int humidity = latestData.isNull("humidity") ? -255 : latestData.getJSONArray("humidity").getInt(0);
//                    double pressure = latestData.isNull("normalPressure") ? -255.0 : latestData.getJSONArray("normalPressure").getDouble(0);
                    double precip1h = latestData.isNull("precipitation1h") ? -255.0 : latestData.getJSONArray("precipitation1h").getDouble(0);
//                    double precip24h = latestData.isNull("precipitation24h") ? -255.0 : latestData.getJSONArray("precipitation24h").getDouble(0);
//                    double wind = latestData.isNull("wind") ? -255.0 : latestData.getJSONArray("wind").getDouble(0);

                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss", Locale.ENGLISH);
                    Date dateKey = sdf.parse(latestTime);
                    sdf = new SimpleDateFormat("HH:mm", Locale.ENGLISH);

                    // UTF絵文字コード表
                    // UTF32->UTF16変換 https: //www.antun.net/tool/encoding/utfConverter.html
                    // thermometer = 1F321 , droplet = 1F4A7 , umbrella = 1F302
                    amedasString = String.format("%s \uD83C\uDF21 %3.1f ℃ \uD83D\uDCA7 %d %% \uD83C\uDF02 %3.1f mm", sdf.format(Objects.requireNonNull(dateKey)), temperature, humidity, precip1h);

                } catch (Exception e) {
                    Log.d("SimpleDeskClock", e.toString());
                } finally {
                    try {
                        if (reader != null) reader.close();
                        if (inputStream != null) inputStream.close();
                        if (connection != null) connection.disconnect();
                    } catch (Exception e) {
                        Log.d("SimpleDeskClock", e.toString());
                    }
                }
            }
        });
    }

    /**
     * 時間選択ツール（時・分）のダイアログを表示するメソッド
     * ( EditTextをクリックしたときに、ソフトキーボードの表示を抑制する手法とともに使うことが好ましい )
     *
     * @param editText 設定する時刻「時・分」が格納されているUI
     *                 <p>
     *                 時間選択ツール ダイアログについての説明はこちら
     *                 (https: //developer.android.com/develop/ui/compose/components/time-pickers-dialogs?hl=ja)
     */
    private void showTimePickerDialog(EditText editText) {

        TimePickerDialog timePickerDialog = new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                // 選択された時刻をEditTextに表示
                editText.setText(String.format(Locale.JAPANESE, "%02d:%02d", hourOfDay, minute));
            }
        },
                parseTime(String.valueOf(editText.getText()))[0],
                parseTime(String.valueOf(editText.getText()))[1],
                true);

        // 時刻選択ダイアログの表示
        timePickerDialog.show();
    }


    /****
     * 観測地点データを取得するメソッド
     * 観測地点名を stationNames リストに格納
     * 観測地点番号・観測地点名のセットを stationMap 連想配列に格納
     */
    private void fetchStations() {


//        Thread thread = new Thread(new Runnable() {
//            @Override
//            public void run() {

//        Executor executor = Executors.newSingleThreadExecutor();
//        executor.execute(new Runnable() {
//            @Override
//            public void run() {

        Executors.newSingleThreadExecutor().execute(() -> {

            HttpURLConnection connection = null;
            InputStream inputStream = null;
            BufferedReader reader = null;

            try {
                URL url = new URL("https://www.jma.go.jp/bosai/amedas/const/amedastable.json");
                connection = (HttpURLConnection) url.openConnection();
//                if(android.os.Build.VERSION.SDK_INT <= 19) {
//                    // TLS 1.2 まで対応可。HttpURLConnectionをHttpsURLConnectionに変更する
//                    SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
//                    sslContext.init(null, null, null);
//                    connection.setSSLSocketFactory(sslContext.getSocketFactory());
//                }
                connection.setConnectTimeout(5000); // timeout 5 sec
                connection.setReadTimeout(5000);    // timeout 5 sec
                connection.setRequestMethod("GET");

                // connect() の記述は必須ではないが、HTTPレスポンスコードで200(HTTP OK)以外をエラーとするために実施する
                connection.connect();
                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new Exception(String.format("Error HTTP response code : %d", responseCode));
                }

                inputStream = connection.getInputStream();
                reader = new BufferedReader(
                        new InputStreamReader(inputStream)
                );
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject jsonObject = new JSONObject(response.toString());

                Iterator<String> keysIterator = jsonObject.keys();
                while (keysIterator.hasNext()) {
                    String stationId = keysIterator.next();
                    JSONObject stationInfo = jsonObject.getJSONObject(stationId);
                    String stationName = stationInfo.optString("kjName", "");
                    stationMap.put(stationName, stationId);
                    stationNames.add(stationName);
                }
                // UI更新（メインスレッド）
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @SuppressLint("SetTextI18n")
                    @Override
                    public void run() {
                        // UIを更新するときはここに記述する
                    }
                });

            } catch (Exception e) {
                Log.d("SimpleDeskClock", e.toString());
            } finally {
                try {
                    if (reader != null) reader.close();
                    if (inputStream != null) inputStream.close();
                    if (connection != null) connection.disconnect();
                } catch (Exception e) {
                    Log.d("SimpleDeskClock", e.toString());
                }
            }
        });
//            }
//        });
//        thread.start();

    }

    /***
     * stationNamesリストから、指定した文字列（query）を含む地点名を抽出し、filteredNamesに代入する
     * （query=nullの場合は、filteredNamesにstationNamesをすべて代入する）
     * AlertDialogのSpinnerにfilteredNamesに結び付けられているため、filteredNamesを更新するとSpinnerも更新される
     *
     * @param query : 観測地点名にこの文字列が含まれるものが抽出される
     * @param spinner : filteredNamesが結び付けられているSpinnerコントロール
     */
    private void updateFilteredList(String query, Spinner spinner) {
        filteredNames.clear();
        // 検索文字列欄に何も入力されていない場合、スピナーに接続されたarrayAdapterに「初期値の観測地点名リスト」stationNamesを書き戻す
        if (query.isEmpty()) {
            adapter.addAll(stationNames);
            adapter.notifyDataSetChanged(); // リスト更新
            spinner.setSelection(0); // 先頭に移動
            return;
        }
        // 検索文字列欄の文字列が含まれるもののみを抽出した「抽出版 観測地点名リスト」filteredNamesを作成する
        for (String name : stationNames) {
            if (name.contains(query)) { // 部分一致で検索
                filteredNames.add(name);
            }
        }
        if (!filteredNames.isEmpty()) {
            // filteredNames はすでにadapterに紐付けられているため、clearするとfilteredNamesもクリアされてしまう
//            adapter.clear();
//            adapter.addAll(filteredNames);
            adapter.notifyDataSetChanged(); // リスト更新
            spinner.setSelection(0); // 先頭に移動
        }
    }

    /**
     * 共有環境設定ファイル(SharedPreferences)に設定値を保存する
     */
    private void SavePreference() {
        SharedPreferences pref = getSharedPreferences("SimpleDeskClock", MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();

        editor.putInt("timeBacklightOnStart", timeBacklightOnStart);
        editor.putInt("timeBacklightOnEnd", timeBacklightOnEnd);
        editor.putFloat("settingLuxMin", settingLuxMin);
        editor.putFloat("settingLcdOnBrightness", settingLcdOnBrightness);
        editor.putFloat("settingLcdOffBrightness", settingLcdOffBrightness);
        editor.putInt("settingFontsizeDate", settingFontsizeDate);
        editor.putInt("settingFontsizeTime", settingFontsizeTime);
        editor.putBoolean("enableKeepScreenOnCtrl", enableKeepScreenOnCtrl);
        editor.putBoolean("enableScreenLog", enableScreenLog);
        editor.putBoolean("enableLogcat", enableLogcat);
        editor.putBoolean("enableAmedasReceive", enableAmedasReceive);
        editor.putString("stationNo_Selected", stationNo_Selected);
        editor.putString("stationName_Selected", stationName_Selected);

        // 非同期保存
        editor.apply();
    }

    /**
     * 共有環境設定ファイル(SharedPreferences)から設定値を復元する
     */
    private void LoadPreference() {
        SharedPreferences pref = getSharedPreferences("SimpleDeskClock", MODE_PRIVATE);

        timeBacklightOnStart = pref.getInt("timeBacklightOnStart", TIME_BACKLIGHT_ON_START_DEFAULT);
        timeBacklightOnEnd = pref.getInt("timeBacklightOnEnd", TIME_BACKLIGHT_ON_END_DEFAULT);
        settingLuxMin = pref.getFloat("settingLuxMin", LUX_MIN_DEFAULT);
        settingLcdOnBrightness = pref.getFloat("settingLcdOnBrightness", LCD_ON_BRIGHTNESS_DEFAULT);
        settingLcdOffBrightness = pref.getFloat("settingLcdOffBrightness", LCD_OFF_BRIGHTNESS_DEFAULT);
        settingFontsizeDate = pref.getInt("settingFontsizeDate", FONT_SIZE_DATE_DEFAULT);
        settingFontsizeTime = pref.getInt("settingFontsizeTime", FONT_SIZE_TIME_DEFAULT);
        enableKeepScreenOnCtrl = pref.getBoolean("enableKeepScreenOnCtrl", false);
        enableScreenLog = pref.getBoolean("enableScreenLog", true);
        enableLogcat = pref.getBoolean("enableLogcat", false);
        enableAmedasReceive = pref.getBoolean("enableAmedasReceive", false);
        stationNo_Selected = pref.getString("stationNo_Selected", "");
        stationName_Selected = pref.getString("stationName_Selected", "");

    }

}
