package com.example.amedasjsontest01;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private final Map<String, String> stationMap = new HashMap<>(); // 観測地点名 → 観測地点番号のマッピング
    private final List<String> stationNames = new ArrayList<>(); // 観測地点名のリスト
    private List<String> filteredNames = new ArrayList<>(); // フィルタリング後の地点名
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 受信データ表示用のテキストエリアを縦スクロール可能にする
        TextView textData = findViewById(R.id.textView_Data);
        textData.setMovementMethod(new ScrollingMovementMethod());

        findViewById(R.id.button_setStation).setOnClickListener(this);
        findViewById(R.id.button_receiveJson).setOnClickListener(this);
        findViewById(R.id.button_close).setOnClickListener(this);

        // 観測地点名・観測地点番号のリストを気象庁Webサイトよりダウンロードする
        fetchStations();
    }


    /**
     * Called when a view has been clicked.
     *
     * @param view The view that was clicked.
     */
    @Override
    public void onClick(View view) {
        if (view != null) {
            // 「終了」ボタンが押された場合の処理 : プログラムを終了する
            if (view.getId() == R.id.button_close) {
                finish();
            }
            // 「観測地点選択」ボタンが押された場合の処理
            else if (view.getId() == R.id.button_setStation) {
                showSetStationDialog();
            }
            // 「JSON受信」ボタンが押された場合の処理
            else if (view.getId() == R.id.button_receiveJson) {
                EditText editText_StationNo = findViewById(R.id.editText_StationNo);
                String stringStationId = editText_StationNo.getText().toString();
                if (!stringStationId.isEmpty()) {
                    fetchWeatherData(stringStationId);
                }
            }
        }

    }


    /****
     * 観測地点データを取得するメソッド
     * 観測地点名を stationNames リストに格納
     * 観測地点番号・観測地点名のセットを stationMap 連想配列に格納
     */
    private void fetchStations() {
        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                // データ受信スレッドが動作中は、ネット接続系のボタンを無効化（グレーアウト）する
                Change_Button_State(false);

                HttpURLConnection connection = null;
                InputStream inputStream = null;
                BufferedReader reader = null;

                try {
                    URL url = new URL("https://www.jma.go.jp/bosai/amedas/const/amedastable.json");
                    connection = (HttpURLConnection) url.openConnection();
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
                            new InputStreamReader(inputStream, StandardCharsets.UTF_8)
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
                            TextView textData = findViewById(R.id.textView_Data);
                            textData.setText("観測地点データベースのダウンロード完了.\n観測地点数 : " + Integer.valueOf(stationNames.size()).toString());
                        }
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    // UI更新（メインスレッド）
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @SuppressLint("SetTextI18n")
                        @Override
                        public void run() {
                            TextView textData = findViewById(R.id.textView_Data);
                            textData.setText("観測地点データベース /bosai/amedas/const/amedastable.json\nError !\n" + Objects.toString(e.getMessage(), "(No Message)"));
                        }
                    });
                } finally {
                    try {
                        if (reader != null) reader.close();
                        if (inputStream != null) inputStream.close();
                        if (connection != null) connection.disconnect();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    // データ受信スレッドが終了すれば、ネット接続系のボタンを有効化する
                    Change_Button_State(true);
                }
            }
        });
    }


    /***
     * 気象庁のWebサーバより、指定した観測地点Noの最新時間帯JSONデータをダウンロードし、最新データを画面表示する
     *
     * @param stationId : 気象庁の観測地点No
     */
    private void fetchWeatherData(String stationId) {
        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                // データ受信スレッドが動作中は、ネット接続系のボタンを無効化（グレーアウト）する
                Change_Button_State(false);

                // **
                // JDK1.1以降で非推奨となったgetHours()を使う方法
                // 現在の日時を取得
                // SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
                // String yyyymmdd = dateFormat.format(new Date());
                // 最新の3時間区分を計算
                // int h3 = (new Date().getHours() / 3) * 3;

                // **
                // Android API 26以降の日時取得方法
                // 現在の日時を取得
                // LocalDateTime now = LocalDateTime.now();
                // String yyyymmdd = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                // 最新の3時間区分（0, 3, 6, ... 21）を計算し、ゼロパディング
                // String h3Formatted = String.format("%02d", (now.getHour() / 3) * 3);

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
                    double minTemperature = latestData.isNull("minTemp") ? -255.0 : latestData.getJSONArray("minTemp").getDouble(0);
                    double maxTemperature = latestData.isNull("maxTemp") ? -255.0 : latestData.getJSONArray("maxTemp").getDouble(0);
                    int humidity = latestData.isNull("humidity") ? -255 : latestData.getJSONArray("humidity").getInt(0);
                    double pressure = latestData.isNull("normalPressure") ? -255.0 : latestData.getJSONArray("normalPressure").getDouble(0);
                    double precip1h = latestData.isNull("precipitation1h") ? -255.0 : latestData.getJSONArray("precipitation1h").getDouble(0);
                    double precip24h = latestData.isNull("precipitation24h") ? -255.0 : latestData.getJSONArray("precipitation24h").getDouble(0);
                    double wind = latestData.isNull("wind") ? -255.0 : latestData.getJSONArray("wind").getDouble(0);

                    // UI更新（メインスレッド）
                    String finalLatestTime = latestTime;
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            TextView textData = findViewById(R.id.textView_Data);

                            String result = "ファイル: " + urlCoreStr + "\n"
                                    + "最新の観測データ\n"
                                    + "日時JSON key: " + finalLatestTime + "\n"
                                    + "気温: " + temperature + "℃\n"
                                    + "最低気温: " + minTemperature + "℃\n"
                                    + "最高気温: " + maxTemperature + "℃\n"
                                    + "湿度: " + humidity + "%\n"
                                    + "1時間雨量: " + precip1h + "mm\n"
                                    + "24時間雨量: " + precip24h + "mm\n"
                                    + "風速: " + wind + "m/s\n"
                                    + "海面気圧: " + pressure + " hPa";
                            textData.setText(result);
                        }
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    // UI更新（メインスレッド）
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @SuppressLint("SetTextI18n")
                        @Override
                        public void run() {
                            TextView textData = findViewById(R.id.textView_Data);
                            textData.setText(String.format("file : %s\nError !\n%s", urlCoreStr, Objects.toString(e.getMessage(), "(No Message)")));
                        }
                    });
                } finally {
                    try {
                        if (reader != null) reader.close();
                        if (inputStream != null) inputStream.close();
                        if (connection != null) connection.disconnect();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    // データ受信スレッドが終了すれば、ネット接続系のボタンを有効化する
                    Change_Button_State(true);
                }
            }
        });
    }

    /***
     * AlertDialogの表示
     * Spinnerに表示された観測地点No・観測地点名のデータベース（リスト）より、ユーザが観測地点名を指定すると、
     * 呼び出し側ActivityMainのEditTextに観測地点No・観測地点名が書き込まれる
     */
    private void showSetStationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("観測地点を選択してください");

        // 「観測地点名フィルタリング用文字列」
        EditText textSearchBox = new EditText(this);
        // 「観測地点名フィルタリング実行ボタン」
        Button buttonFilter = new Button(this);
        buttonFilter.setText("フィルター");
        // 「観測地点名選択」スピナー（ドロップダウンリスト）
        Spinner spinner = new Spinner(this);
        filteredNames = new ArrayList<>(stationNames);  // 元のListが破壊されないよう、ディープコピーする
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, filteredNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // 「フィルター」ボタンを押したとき、textSearchBox に入力された文字列を含む観測地点名を抽出し、スピナーにセットする
        buttonFilter.setOnClickListener(view -> updateFilteredList(textSearchBox.getText().toString(), spinner));
// ラムダ式に置換した
//        buttonFilter.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                updateFilteredList(textSearchBox.getText().toString(), spinner);
//            }
//        });

        // AlertDialogに 「観測地点名フィルタリング用文字列」EditText と 「観測地点名選択」Spinner を追加
        LinearLayout layout = new LinearLayout(MainActivity.this);
        layout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout layoutSearchBox = new LinearLayout(MainActivity.this);
        layoutSearchBox.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layoutSearchBox.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams paramsTextSearchBox =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2);
        LinearLayout.LayoutParams paramsButtonFilter =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        textSearchBox.setLayoutParams(paramsTextSearchBox);
        buttonFilter.setLayoutParams(paramsButtonFilter);
        layoutSearchBox.addView(textSearchBox);
        layoutSearchBox.addView(buttonFilter);
        layout.addView(layoutSearchBox);
        layout.addView(spinner);
        builder.setView(layout);

        // AlertDialogのOKボタンを押したときの処理
        // 呼び出し側ActivityMainのEditTextに観測地点名・観測地点Noを書き込む
        builder.setPositiveButton("OK", (dialog, which) -> {
            // スピナーで選択された「観測地点名」を取り出す。また、観測地点名に紐付けられた「観測地点No」を得る
            String selectedStationName = (String) spinner.getSelectedItem();
            String selectedStationNo = stationMap.get(selectedStationName);
            // 「観測地点名」と「観測地点No」を呼び出し側Main ActivityのEditTextにセットする
            EditText editText_StationNo = findViewById(R.id.editText_StationNo);
            editText_StationNo.setText(selectedStationNo);
            EditText editText_StationName = findViewById(R.id.editText_StationName);
            editText_StationName.setText(selectedStationName);
        });

        builder.setNegativeButton("キャンセル", (dialog, which) -> dialog.dismiss());

        builder.create().show();

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


    /***
     * 非同期タスクを起動するためのボタンを有効化(グレーアウト)/無効化する
     *
     * @param boolEnable : trueはボタン有効, falseはボタン無効(グレーアウト)
     */
    private void Change_Button_State(boolean boolEnable) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Button button_ReceiveJson = findViewById(R.id.button_receiveJson);
                button_ReceiveJson.setEnabled(boolEnable);
                Button button_SetStation = findViewById(R.id.button_setStation);
                button_SetStation.setEnabled(boolEnable);
            }
        });
    }

}