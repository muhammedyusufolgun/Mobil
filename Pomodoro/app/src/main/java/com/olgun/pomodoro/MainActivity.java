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
 private ToneGenerator currentTone;
 private final Runnable stopAlarm = () -> {
  if (currentTone != null) { currentTone.stopTone(); currentTone.release(); currentTone = null; }
 };
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
    handler.removeCallbacks(stopAlarm);
    stopAlarm.run();
    currentTone = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
    final ToneGenerator tone = currentTone;
    for (int i = 0; i < 6; i++) {
     handler.postDelayed(() -> { if (currentTone == tone) tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 350); }, i * 500L);
    }
    handler.postDelayed(stopAlarm, 3000L);
   });
  }
 }
 @Override public void onDestroy() { handler.removeCallbacksAndMessages(null); stopAlarm.run(); web.destroy(); super.onDestroy(); }
}