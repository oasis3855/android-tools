package com.example.android_soramame_receive;

import android.os.AsyncTask;
import      android.os.StrictMode;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Calendar;

import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.os.Build;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.app.AlertDialog;
import android.widget.EditText;
import android.text.InputType;
import android.content.DialogInterface;

import java.io.PrintStream;
import java.io.ByteArrayOutputStream;

import org.apache.http.*;
import org.apache.http.impl.client.*;
import org.apache.http.client.methods.*;
import org.apache.http.util.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends ActionBarActivity {

    // 画面上のテキストエリアのID
    final int ID_TEXTVIEW = 0x1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        setContentView(layout);
        // 画面上のテキストエリアを定義
        TextView text = new TextView(this);
        text.setId(ID_TEXTVIEW);
        text.setText("しばらくお待ちください");
        text.setTextSize((int)(text.getTextSize()*1.3));
        layout.addView(text);

        // プリファレンスから設定値（観測局ID）を読み込む
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        String station_id = pref.getString("keyStationID", "13101010");
        station_id = station_id.replaceAll("\n", "");
        if(station_id.length() != 8){
            text.setText("観測局IDが想定外です");
            return;
        }

        // ネットワークの非同期受信タスク
        Task task = new Task();
        task.execute("http://www.obccbo.com/soramame/v1/weeks/" + station_id + ".json");

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        // メニュー項目による分岐 （メニューは res/menu/main.xml で定義）

        // 「データ受信」メニューが押された場合
        if (id == R.id.action_receive_http) {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
            String station_id = pref.getString("keyStationID", "13101010");
            station_id = station_id.replaceAll("\n", "");
            if(station_id.length() != 8){
                TextView text = (TextView) MainActivity.this.findViewById(ID_TEXTVIEW);
                text.setText("観測局IDが想定外です");
                return true;
            }
            Task task = new Task();
            task.execute("http://www.obccbo.com/soramame/v1/weeks/" + station_id + ".json");
        }
        
        // 「そらまめ君観測局ID設定」メニューが押された場合
        else if (id == R.id.action_edit_station_id) {
            // 現在の「テキスト入力値」をプリファレンスxmlより読みだす
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
            String str = pref.getString("keyStationID", "13101010");
 
            // テキスト入力AlertDialogを表示する
            final EditText editView = new EditText(MainActivity.this);
            editView.setLines(1);   // 1行
            editView.setInputType(InputType.TYPE_CLASS_TEXT);   // 改行を許可しない
            // AlertDialogを構築する
            AlertDialog.Builder dlg = new AlertDialog.Builder(this);
            dlg.setTitle("観測局IDの設定");
            dlg.setView(editView);
            editView.setText(str);
            dlg.setPositiveButton("OK", new DialogInterface.OnClickListener(){
                public void onClick(DialogInterface dialog, int which) {
                    // 入力された文字列をプリファレンスxmlに書き込む
                    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                    SharedPreferences.Editor edit = pref.edit();
                    edit.putString("keyStationID", editView.getText().toString());
                    edit.commit();
                }
            });
            dlg.setNegativeButton("キャンセル", null);
            dlg.show();
            return true;
        }

        // 「プログラム終了」メニューが押された場合
        else if (id == R.id.action_quit) {
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
        }

        return super.onOptionsItemSelected(item);
    }

    // ネットワークの非同期受信処理
    private class Task extends AsyncTask<String, Void, String> {
        protected String doInBackground(String... urls) {
            String str_recv = "";
            BufferedReader bf = null;

            try{
                URL url = new URL(urls[0]);
                URLConnection url_conn = url.openConnection();
                url_conn.setReadTimeout(6000);                  // タイムアウト（ミリ秒）の設定
                Object content = url_conn.getContent();
                if (content instanceof InputStream) {
                    // ネットワークから読み取り、受信した各行を連結する
                    bf = new BufferedReader(new InputStreamReader( (InputStream)content) );
                    String line;
                    while ((line = bf.readLine()) != null) {
                        str_recv += (line + "\n");
                    }
                }
                else{
                    str_recv = content.toString();
                }
            } catch (Exception e) {
                str_recv = "HTTP接続に失敗 : " + e.getLocalizedMessage();
            }
            finally {
                // ネットワーク接続を閉じる
                if(bf != null){
                    try{
                        bf.close();
                    } catch (Exception e) { }
                }
            }

            return(str_recv);   // この値が、onPostExecuteの引数となる
        }

        // ネットワークの非同期送受信後、この関数に制御が移る
        @Override
        protected void onPostExecute(String str_recv)
        {
            // ネットワークから受信した文字列を、JSON解析し画面表示する
            parse_json(str_recv);
            return;
        }

        // 与えられた文字列をJSONデータとして解析し、画面表示する
        protected void parse_json(String str_recv){
            // 画面上のテキストエリア
            TextView text = (TextView) MainActivity.this.findViewById(ID_TEXTVIEW);

            try{
                // JSON内データを日時ソートするときに使う内部変数
                Calendar d_new = Calendar.getInstance();
                Calendar d_tmp = Calendar.getInstance();
                // JSONデータをパースする
                JSONObject jo = new JSONObject(str_recv);

                // JSONデータ先頭にStatus=OKが無ければエラー
                if(!jo.getString("status").equalsIgnoreCase("OK")){
                    throw new Exception();
                }
                // 最新日時のデータを検索し、インデックスをi_newに代入する
                int i_new = 0;
                d_new.set(Integer.valueOf(jo.getJSONArray("data").getJSONObject(0).getString("year")),
                        Integer.valueOf(jo.getJSONArray("data").getJSONObject(0).getString("month")),
                        Integer.valueOf(jo.getJSONArray("data").getJSONObject(0).getString("day")),
                        Integer.valueOf(jo.getJSONArray("data").getJSONObject(0).getString("time").substring(0, 2)),
                        0);

                for(int i=0; i<jo.getJSONArray("data").length(); i++){
                    d_tmp.set(Integer.valueOf(jo.getJSONArray("data").getJSONObject(i).getString("year")),
                            Integer.valueOf(jo.getJSONArray("data").getJSONObject(i).getString("month")),
                            Integer.valueOf(jo.getJSONArray("data").getJSONObject(i).getString("day")),
                            Integer.valueOf(jo.getJSONArray("data").getJSONObject(i).getString("time").substring(0, 2)),
                            0);
                    if(d_tmp.getTimeInMillis() > d_new.getTimeInMillis()){
                        d_new = (Calendar)d_tmp.clone();
                        i_new = i;
                    }
                }
                // 最新日時のデータを文字列strに整形・代入する
                PrintStream ps = new PrintStream(new ByteArrayOutputStream());
                String str = String.format("日時 %d/%02d/%02d %02d:00\n温度 = %s ℃\n湿度 = %s %%\n風速 = %s m/sec\nNOx = %s ppm\nPM2.5 = %s ug/m3",
                        Integer.valueOf(jo.getJSONArray("data").getJSONObject(i_new).getString("year")),
                        Integer.valueOf(jo.getJSONArray("data").getJSONObject(i_new).getString("month")),
                        Integer.valueOf(jo.getJSONArray("data").getJSONObject(i_new).getString("day")),
                        Integer.valueOf(jo.getJSONArray("data").getJSONObject(i_new).getString("time").substring(0, 2)),
                        jo.getJSONArray("data").getJSONObject(i_new).getString("temp"),
                        jo.getJSONArray("data").getJSONObject(i_new).getString("hum"),
                        jo.getJSONArray("data").getJSONObject(i_new).getString("ws"),
                        jo.getJSONArray("data").getJSONObject(i_new).getString("nox"),
                        jo.getJSONArray("data").getJSONObject(i_new).getString("pm2.5")
                        );
                // 画面表示
                text.setText(str);
            } catch (Exception e){
                text.setText("json解析エラー");
                return;
            }
        }
    }

}
