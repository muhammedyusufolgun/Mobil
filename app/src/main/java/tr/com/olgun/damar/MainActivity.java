package tr.com.olgun.damar;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.LifecycleCameraController;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends ComponentActivity {
    private static final int GREEN_BG = Color.rgb(217, 247, 222);
    private static final int GREEN_DARK = Color.rgb(28, 111, 61);
    private PreviewView previewView;
    private ImageView filteredView;
    private TextView status;
    private LifecycleCameraController cameraController;
    private ExecutorService analysisExecutor;
    private volatile boolean enhance = false;
    private volatile float strength = 1.7f;
    private ActivityResultLauncher<String> cameraPermission;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(GREEN_BG);
        getWindow().setNavigationBarColor(GREEN_BG);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        analysisExecutor = Executors.newSingleThreadExecutor();
        buildScreen();
        cameraPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) startCamera();
            else showPermissionMessage();
        });
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(GREEN_BG);
        root.setPadding(dp(16), dp(10), dp(16), dp(10));

        TextView title = new TextView(this);
        title.setText("Damar Netleştirme");
        title.setTextColor(GREEN_DARK);
        title.setTextSize(23);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(38)));

        TextView subtitle = new TextView(this);
        subtitle.setText("Canlı görüntüde yüzeysel damar görünümünü belirginleştirmeyi dener.");
        subtitle.setTextColor(Color.rgb(58, 91, 68));
        subtitle.setTextSize(13);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(38)));

        FrameLayout cameraFrame = new FrameLayout(this);
        cameraFrame.setBackgroundColor(Color.rgb(24, 42, 30));
        LinearLayout.LayoutParams cameraParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        cameraParams.topMargin = dp(8);
        cameraParams.bottomMargin = dp(10);
        root.addView(cameraFrame, cameraParams);

        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        cameraFrame.addView(previewView, new FrameLayout.LayoutParams(-1, -1));
        filteredView = new ImageView(this);
        filteredView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        filteredView.setVisibility(View.GONE);
        cameraFrame.addView(filteredView, new FrameLayout.LayoutParams(-1, -1));

        status = new TextView(this);
        status.setText("Kamera başlatılıyor…");
        status.setTextColor(GREEN_DARK);
        status.setTextSize(13);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(28)));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button original = makeButton("Normal Görüntü");
        Button filter = makeButton("Damarı Netleştir");
        actions.addView(original, new LinearLayout.LayoutParams(0, dp(48), 1f));
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        filterParams.leftMargin = dp(8);
        actions.addView(filter, filterParams);
        root.addView(actions, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView strengthLabel = new TextView(this);
        strengthLabel.setText("Filtre gücü");
        strengthLabel.setTextColor(GREEN_DARK);
        strengthLabel.setTextSize(13);
        root.addView(strengthLabel, new LinearLayout.LayoutParams(-1, dp(22)));
        SeekBar strengthBar = new SeekBar(this);
        strengthBar.setMax(100);
        strengthBar.setProgress(35);
        strengthBar.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN_DARK));
        strengthBar.setThumbTintList(android.content.res.ColorStateList.valueOf(GREEN_DARK));
        strengthBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                strength = 0.5f + (progress / 100f) * 2.5f;
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        root.addView(strengthBar, new LinearLayout.LayoutParams(-1, dp(36)));

        TextView note = new TextView(this);
        note.setText("Sadece görüntü filtresidir; damar yolu, tanı veya tedavi için kullanılmaz.");
        note.setTextColor(Color.rgb(74, 99, 80));
        note.setTextSize(11);
        note.setGravity(Gravity.CENTER);
        root.addView(note, new LinearLayout.LayoutParams(-1, dp(32)));

        original.setOnClickListener(v -> {
            enhance = false;
            filteredView.setVisibility(View.GONE);
            previewView.setVisibility(View.VISIBLE);
            status.setText("Normal kamera görüntüsü");
        });
        filter.setOnClickListener(v -> {
            enhance = true;
            filteredView.setVisibility(View.VISIBLE);
            previewView.setVisibility(View.GONE);
            status.setText("Filtreli görüntü • yaklaşık görünüm");
        });
        setContentView(root);
    }

    private Button makeButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GREEN_DARK));
        return b;
    }

    private void startCamera() {
        try {
            cameraController = new LifecycleCameraController(this);
            cameraController.setCameraSelector(CameraSelector.DEFAULT_BACK_CAMERA);
            cameraController.setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST);
            cameraController.setImageAnalysisResolutionSelector(new androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                    .setResolutionStrategy(new androidx.camera.core.resolutionselector.ResolutionStrategy(
                            new android.util.Size(640, 480), androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                    .build());
            cameraController.setEnabledUseCases(LifecycleCameraController.USE_CASE_PREVIEW | LifecycleCameraController.USE_CASE_IMAGE_ANALYSIS);
            cameraController.setImageAnalysisAnalyzer(analysisExecutor, this::analyzeFrame);
            previewView.setController(cameraController);
            cameraController.bindToLifecycle(this);
            status.setText("Kamera hazır • yalnızca cihazda işlenir");
        } catch (Exception e) {
            status.setText("Kamera başlatılamadı. İzinleri ve kamera kullanımını kontrol edin.");
        }
    }

    private void analyzeFrame(ImageProxy image) {
        try {
            if (enhance) {
                Bitmap bitmap = yuvToBitmap(image);
                if (bitmap != null) {
                    Bitmap result = VeinFilter.enhance(bitmap, strength);
                    runOnUiThread(() -> {
                        if (enhance && !isFinishing()) filteredView.setImageBitmap(result);
                        else result.recycle();
                    });
                    if (bitmap != result && !bitmap.isRecycled()) bitmap.recycle();
                }
            }
        } catch (RuntimeException ignored) {
            // Drop a bad frame and allow the next camera frame to be processed.
        } finally {
            image.close();
        }
    }

    private Bitmap yuvToBitmap(ImageProxy image) {
        int width = image.getWidth(), height = image.getHeight();
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        if (planes.length < 3) return null;
        ByteBuffer y = planes[0].getBuffer(), u = planes[1].getBuffer(), v = planes[2].getBuffer();
        int yRow = planes[0].getRowStride(), uRow = planes[1].getRowStride(), vRow = planes[2].getRowStride();
        int yPixel = planes[0].getPixelStride(), uPixel = planes[1].getPixelStride(), vPixel = planes[2].getPixelStride();
        int[] pixels = new int[width * height];
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int yy = y.get(row * yRow + col * yPixel) & 0xff;
                int uIndex = (row >> 1) * uRow + (col >> 1) * uPixel;
                int vIndex = (row >> 1) * vRow + (col >> 1) * vPixel;
                int uu = (u.get(uIndex) & 0xff) - 128;
                int vv = (v.get(vIndex) & 0xff) - 128;
                int c = Math.max(0, yy - 16);
                int r = clamp((298 * c + 409 * vv + 128) >> 8);
                int g = clamp((298 * c - 100 * uu - 208 * vv + 128) >> 8);
                int b = clamp((298 * c + 516 * uu + 128) >> 8);
                pixels[row * width + col] = Color.rgb(r, g, b);
            }
        }
        Bitmap raw = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        Matrix rotation = new Matrix();
        rotation.postRotate(image.getImageInfo().getRotationDegrees());
        Bitmap rotated = Bitmap.createBitmap(raw, 0, 0, width, height, rotation, true);
        if (rotated != raw) raw.recycle();
        int targetWidth = Math.min(480, rotated.getWidth());
        if (targetWidth < rotated.getWidth()) {
            int targetHeight = Math.max(1, (int) (rotated.getHeight() * (targetWidth / (float) rotated.getWidth())));
            Bitmap small = Bitmap.createScaledBitmap(rotated, targetWidth, targetHeight, true);
            rotated.recycle();
            return small;
        }
        return rotated;
    }

    private int clamp(int value) { return Math.max(0, Math.min(255, value)); }

    private void showPermissionMessage() {
        status.setText("Canlı görüntü için kamera izni gerekir.");
        Button retry = makeButton("Kamera izni ver");
        retry.setOnClickListener(v -> cameraPermission.launch(Manifest.permission.CAMERA));
        ((LinearLayout) ((android.view.ViewGroup) status.getParent())).addView(retry);
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onDestroy() {
        if (cameraController != null) cameraController.clearImageAnalysisAnalyzer();
        if (analysisExecutor != null) analysisExecutor.shutdown();
        super.onDestroy();
    }
}
