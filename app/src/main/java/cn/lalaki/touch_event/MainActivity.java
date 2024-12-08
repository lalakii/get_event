package cn.lalaki.touch_event;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuProvider;

public class MainActivity extends Activity implements Runnable, View.OnClickListener, Shizuku.OnRequestPermissionResultListener, ServiceConnection {
    private ScrollView sc;
    private TextView showData;
    private Intent shizukuIntent;
    private boolean isRunning = false;
    private boolean permissionIsGranted = false;
    private Shizuku.UserServiceArgs mUserServiceArgs;
    private final int port = 34567;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        sc = findViewById(R.id.scroll);
        showData = findViewById(R.id.touch_data);
        shizukuIntent = getPackageManager().getLaunchIntentForPackage(ShizukuProvider.MANAGER_APPLICATION_ID);
        mUserServiceArgs = new Shizuku.UserServiceArgs(new ComponentName(getPackageName(), GetEventService.class.getName()))
                .daemon(false)
                .debuggable(false)
                .processNameSuffix("lalaki_getevent")
                .version(1);
    }

    @Override
    public void onRequestPermissionResult(int requestCode, int grantResult) {
        permissionIsGranted = grantResult == 0;
    }

    @Override
    protected void onResume() {
        if (shizukuIntent != null && Shizuku.pingBinder()) {
            permissionIsGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
            if (permissionIsGranted) {
                findViewById(R.id.tips).setAlpha(0F);
                Shizuku.removeRequestPermissionResultListener(this);
            } else {
                Shizuku.addRequestPermissionResultListener(this);
                Shizuku.requestPermission(0);
            }
        }
        super.onResume();
    }

    @Override
    public void onClick(View view) {
        if (isRunning) {
            Toast.makeText(this, R.string.end, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (!permissionIsGranted) return;
        isRunning = true;
        bindService();
        ((Button) view).setText(R.string.stop);
        Toast.makeText(this, R.string.touch_tips, Toast.LENGTH_SHORT).show();
    }

    // 绑定服务
    private void bindService() {
        new Thread(this).start();// Socket服务端
        Shizuku.bindUserService(mUserServiceArgs, this);
    }

    // 进程间通信，如果不理解此处为什么要用到socket，请百度
    @Override
    public void run() {
        try (ServerSocket server = new ServerSocket(port)) {
            while (isRunning) {
                BufferedReader br = new BufferedReader(new InputStreamReader(server.accept().getInputStream()));
                StringBuilder sb = new StringBuilder();
                while (isRunning) {
                    String line = br.readLine();
                    sb.append(line).append('\n');
                    while (isRunning && sb.length() > 4096) {
                        sb.delete(0, 1);
                    }
                    runOnUiThread(() -> {
                        showData.setText(sb);
                        sc.fullScroll(View.FOCUS_DOWN);
                    });
                    Log.d(getPackageName(), line);
                }
            }
        } catch (IOException ignored) {
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        if (service != null && service.pingBinder()) {
            IGetEventService iUserService = IGetEventService.Stub.asInterface(service);
            // getevent 进程
            try {
                iUserService.getEvent(port);
            } catch (RemoteException ignored) {
            }
        }
    }

    @Override
    protected void onDestroy() {
        isRunning = false;
        Shizuku.unbindUserService(mUserServiceArgs, this, true);
        super.onDestroy();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        isRunning = false;
    }
}