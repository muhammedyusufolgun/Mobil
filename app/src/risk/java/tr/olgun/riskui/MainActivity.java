package tr.olgun.riskui;

import android.app.Activity;
import android.print.PrintManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {
    private static final int PICK_FILE=2041, SAVE_JSON=2042;
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private String pendingJson;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(2,132,199));
        getWindow().setNavigationBarColor(Color.rgb(248,250,252));
        web=new WebView(this);
        web.setBackgroundColor(Color.rgb(248,250,252));
        setContentView(web);
        WebSettings settings=web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setBlockNetworkLoads(true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params) {
                if(fileCallback!=null)fileCallback.onReceiveValue(null);
                fileCallback=callback;
                try {
                    Intent intent=params.createIntent();
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(intent,PICK_FILE);
                    return true;
                } catch(Exception ex) {
                    fileCallback=null;
                    Toast.makeText(MainActivity.this,"Dosya seçici açılamadı",Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });
        web.addJavascriptInterface(new RiskBridge(),"RiskAndroid");
        web.loadUrl("file:///android_asset/index.html");
    }

    private final class RiskBridge {
        @JavascriptInterface public void saveJson(String json,String filename) {
            runOnUiThread(()->{
                pendingJson=json;
                try {
                    Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    intent.putExtra(Intent.EXTRA_TITLE,filename);
                    startActivityForResult(intent,SAVE_JSON);
                } catch(Exception ex) {
                    Toast.makeText(MainActivity.this,"JSON kaydı açılamadı",Toast.LENGTH_LONG).show();
                }
            });
        }
        @JavascriptInterface public void printPdf() {
            runOnUiThread(()->web.postDelayed(()->{
                try {
                    PrintManager manager=(PrintManager)getSystemService(Context.PRINT_SERVICE);
                    PrintAttributes attributes=new PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setColorMode(PrintAttributes.COLOR_MODE_COLOR).build();
                    manager.print("Risk_UI_L_Tipi_Matris",
                        web.createPrintDocumentAdapter("Risk_UI_L_Tipi_Matris"),attributes);
                } catch(Exception ex) {
                    Toast.makeText(MainActivity.this,"PDF yazdırma başlatılamadı",Toast.LENGTH_LONG).show();
                }
            },450));
        }
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==PICK_FILE) {
            if(fileCallback!=null) {
                Uri[] selected=resultCode==RESULT_OK ? WebChromeClient.FileChooserParams.parseResult(resultCode,data):null;
                fileCallback.onReceiveValue(selected);
                fileCallback=null;
            }
        } else if(requestCode==SAVE_JSON) {
            if(resultCode==RESULT_OK&&data!=null&&data.getData()!=null&&pendingJson!=null) {
                try(OutputStream out=getContentResolver().openOutputStream(data.getData())) {
                    if(out==null)throw new IllegalStateException("Dosya açılamadı");
                    out.write(pendingJson.getBytes(StandardCharsets.UTF_8));
                    Toast.makeText(this,"JSON dosyası kaydedildi",Toast.LENGTH_SHORT).show();
                } catch(Exception ex) {
                    Toast.makeText(this,"Kaydetme hatası: "+ex.getMessage(),Toast.LENGTH_LONG).show();
                }
            }
            pendingJson=null;
        }
    }
    @Override public void onBackPressed() {
        if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if(fileCallback!=null){fileCallback.onReceiveValue(null);fileCallback=null;}
        if(web!=null){web.removeJavascriptInterface("RiskAndroid");web.destroy();}
        super.onDestroy();
    }
}
