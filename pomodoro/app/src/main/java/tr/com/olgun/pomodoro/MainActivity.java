package tr.com.olgun.pomodoro;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
public class MainActivity extends Activity {
  private WebView web;
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    web = new WebView(this);
    web.getSettings().setJavaScriptEnabled(true);
    web.getSettings().setDomStorageEnabled(true);
    web.setWebViewClient(new WebViewClient());
    setContentView(web);
    web.loadUrl("file:///android_asset/index.html");
  }
  @Override protected void onPause() {
    if(web!=null) web.evaluateJavascript("typeof save==='function'&&save()",null);
    super.onPause();
  }
  @Override protected void onDestroy() {
    if(web!=null){web.destroy();web=null;}
    super.onDestroy();
  }
}
