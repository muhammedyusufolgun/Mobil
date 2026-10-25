package com.olgun.pomodoro;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
public class MainActivity extends Activity {
 private WebView web;
 private final Handler handler = new Handler(Looper.getMainLooper());
 @Override public void onCreate(Bundle b) {
  super.onCreate(b);
  getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  web = new WebView(this);
  web.getSettings().setJavaScriptEnabled(true);
  web.getSettings().setDomStorageEnabled(true);
  web.setWebViewClient(new WebViewClient());
  web.addJavascriptInterface(new Bridge(), "Android");
  setContentView(web);
  web.loadUrl("file:///android_asset/index.html");
 }
 public class Bridge {
  @JavascriptInterface public void alarm() {
   handler.post(() -> {
    final ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_ALARM, 75);
    for (int i=0;i<6;i++) handler.postDelayed(() -> tone.startTone(ToneGenerator.TONE_PROP_BEEP, 220), i*500L);
    handler.postDelayed(tone::release, 3000L);
   });
  }
 }
 @Override public void onDestroy() { web.destroy(); super.onDestroy(); }
}
