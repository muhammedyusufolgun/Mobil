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
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.WindowManager;
import java.util.Locale;

public class MainActivity extends Activity {
 private WebView web;
 private final Handler handler = new Handler(Looper.getMainLooper());
 private TextToSpeech tts;
 private boolean turkishVoiceReady = false;
 private ToneGenerator currentTone;
 private int alarmSequence = 0;

 private final Runnable stopTone = () -> {
  if (currentTone != null) {
   currentTone.stopTone();
   currentTone.release();
   currentTone = null;
  }
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

  tts = new TextToSpeech(getApplicationContext(), status -> {
   if (status != TextToSpeech.SUCCESS || tts == null) return;
   int language = tts.setLanguage(new Locale("tr", "TR"));
   turkishVoiceReady = language != TextToSpeech.LANG_MISSING_DATA
       && language != TextToSpeech.LANG_NOT_SUPPORTED;
   if (turkishVoiceReady) {
    tts.setSpeechRate(0.9f);
    tts.setPitch(1.0f);
   }
  });
 }

 private void beepFallback(int sequence) {
  if (sequence != alarmSequence) return;
  handler.removeCallbacks(stopTone);
  stopTone.run();
  try {
   currentTone = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
   final ToneGenerator tone = currentTone;
   for (int i = 0; i < 6; i++) {
    handler.postDelayed(() -> {
     if (sequence == alarmSequence && currentTone == tone) {
      tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 350);
     }
    }, i * 500L);
   }
   handler.postDelayed(stopTone, 3000L);
  } catch (Exception ignored) {
   stopTone.run();
  }
 }

 private void announceFinished() {
  int sequence = ++alarmSequence;
  stopTone.run();
  if (!turkishVoiceReady || tts == null) {
   beepFallback(sequence);
   return;
  }
  try {
   tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
    @Override public void onStart(String utteranceId) {}
    @Override public void onDone(String utteranceId) {}
    @Override public void onError(String utteranceId) {
     handler.post(() -> beepFallback(sequence));
    }
   });
   int result = tts.speak("Zaman doldu", TextToSpeech.QUEUE_FLUSH, null, "pomodoro_" + sequence);
   if (result == TextToSpeech.ERROR) beepFallback(sequence);
  } catch (Exception ignored) {
   beepFallback(sequence);
  }
 }

 public class Bridge {
  @JavascriptInterface public void alarm() {
   handler.post(() -> announceFinished());
  }
 }

 @Override public void onDestroy() {
  alarmSequence++;
  handler.removeCallbacksAndMessages(null);
  stopTone.run();
  if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
  web.destroy();
  super.onDestroy();
 }
}
