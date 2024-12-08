package cn.lalaki.touch_event;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

public class GetEventService extends IGetEventService.Stub implements Runnable {
    private final ProcessBuilder builder = new ProcessBuilder();
    private boolean isServiceRunning = true;
    private int port;
    private int errCount;

    @Override
    public void destroy() {
        isServiceRunning = false;
    }

    @Override
    public void getEvent(int port) {
        this.port = port;
        new Thread(this).start();
    }

    /**
     * @noinspection BusyWait
     */
    @Override
    public void run() {
        while (isServiceRunning) {
            try (Socket socket = new Socket(InetAddress.getLocalHost(), port)) {
                String[] cmdline = {"getevent", "-t"};
                InputStream in = builder.command(cmdline).start().getInputStream();
                OutputStream out = socket.getOutputStream();
                int ch;
                while (isServiceRunning) {
                    ch = in.read();
                    if (ch == -1) {
                        Thread.sleep(1);
                    } else {
                        out.write(ch);
                    }
                }
            } catch (IOException | InterruptedException ignored) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored1) {
                }
                errCount++;
                if (errCount > 15) {
                    isServiceRunning = false;
                }
            }
        }
    }
}