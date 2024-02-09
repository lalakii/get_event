package cn.lalaki.touch_event;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity implements View.OnClickListener, Shizuku.OnRequestPermissionResultListener {
    private boolean permissionIsGranted = false;
    private TextView te_data;
    private File rish;
    private ScrollView sc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        sc = findViewById(R.id.scroll);
        te_data = findViewById(R.id.touch_data);
        File external = getFilesDir();
        if (external != null) {
            String path = external.getAbsolutePath() + "/private/";
            if (new File(path).mkdirs()) {
                Toast.makeText(this, "create private dir is done.", Toast.LENGTH_SHORT).show();
            }
            rish = new File(path + "rish");
            if (!rish.exists()) {
                String[] files = {"rish_shizuku.dex", "rish"};
                for (String name : files) {
                    try (InputStream rish_dex = getAssets().open(name)) {
                        try (FileOutputStream out = new FileOutputStream(path + name)) {
                            ByteStreams.copy(rish_dex, out);
                        }
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

    @Override
    protected void onResume() {
        Intent shizukuIntent = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
        if (shizukuIntent != null) {
            if (Shizuku.pingBinder()) {
                permissionIsGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
                if (permissionIsGranted) {
                    findViewById(R.id.tips).setAlpha(0F);
                } else {
                    Shizuku.addRequestPermissionResultListener(this);
                    Shizuku.requestPermission(0);
                }
            }
        }
        super.onResume();
    }

    @Override
    public void onRequestPermissionResult(int requestCode, int grantResult) {
        permissionIsGranted = grantResult == 0;
    }
    private boolean isRunning = false;
    private final ProcessBuilder builder = new ProcessBuilder();
    @Override
    public void onClick(View view) {
        if (!permissionIsGranted) return;
        if (isRunning || !((Switch) view).isChecked()) {
            Toast.makeText(this, R.string.end, Toast.LENGTH_SHORT).show();
            finish();
            System.exit(0);
        }
        if (rish.exists()) {
            isRunning = !isRunning;
            Toast.makeText(this, R.string.touch_tips, Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                try {
                    String[] cmdline = {"sh", rish.getAbsolutePath(), "-c", "getevent -t"};
                    builder.environment().put("RISH_APPLICATION_ID", getPackageName());
                    InputStream in = builder.command(cmdline).start().getInputStream();
                    StringBuilder sb = new StringBuilder();
                    byte[] data = new byte[64];
                    while (isRunning) {
                        try {
                            ByteStreams.readFully(in, data);
                            onData(sb, data);
                        } catch (IOException e) {
                            runOnUiThread(() -> te_data.setText(e.getLocalizedMessage()));
                        }
                    }
                } catch (IOException e) {
                    runOnUiThread(() -> te_data.setText(e.getLocalizedMessage()));
                }
            }).start();
        }
    }

    @SuppressLint("SetTextI18n")
    public void onData(StringBuilder sb, byte[] data) {
        sb.append(new String(data));
        int len = sb.length();
        if (len > 2048) {
            sb.delete(0, 1024);
            runOnUiThread(() -> {
                te_data.setText(sb + "\n\n\n\n\n\n");
                sc.fullScroll(View.FOCUS_DOWN);
            });
        }
    }
}