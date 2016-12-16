package fr.pchab.androidrtc;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.widget.Toast;
import android.support.v4.app.NotificationCompat;


import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.SocketAddress;
import java.net.URISyntaxException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;



public class MyService extends Service {
    public static Socket client;
    public static String userName;
    public static String userId;

    public MyService() {
    }
    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");

    }
    @Override
    public void onCreate() {
        Toast.makeText(this, "Service was Created", Toast.LENGTH_LONG).show();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (!preferences.contains("USER_ID")) {

            userId = MainActivity.userId;
            userName = MainActivity.userName;
            SharedPreferences.Editor edit = preferences.edit();
            edit.putString("USER_ID", userId);
            edit.putString("USER_NAME", userName);
            edit.apply();

            return;
        }
        else {

            userId = preferences.getString("USER_ID", "");
            userName = preferences.getString("USER_NAME", "");

        }

        Intent showTaskIntent = new Intent(getApplicationContext(), MainActivity.class);
        showTaskIntent.setAction(Intent.ACTION_MAIN);
        showTaskIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        showTaskIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent contentIntent = PendingIntent.getActivity(
                getApplicationContext(),
                0,
                showTaskIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);


        NotificationCompat.Builder notificationB = new NotificationCompat.Builder(getApplicationContext())
                .setContentTitle("Helo is connected.")
                .setContentText("Removing this notification will cause connection issues.")
                .setSmallIcon(R.drawable.fab_background)
                .setContentIntent(contentIntent);
        Notification notification = notificationB.build();
        startForeground(101,notification);
        // Perform your long running operations here.
        Toast.makeText(this, "Service Started", Toast.LENGTH_LONG).show();

        //Receive call callback from when other people call you
        String host = "http://" + getResources().getString(R.string.host);
        host += (":" + getResources().getString(R.string.port) + "/");
        try {
            client = IO.socket(host);
            Toast.makeText(this, client.toString(), Toast.LENGTH_LONG).show();

        } catch (URISyntaxException e) {
            e.printStackTrace();
        }



        client.connect();
        try {
            JSONObject message = new JSONObject();
            message.put("myId", userId);
            client.emit("resetId", message);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        pollingThread();


    }

    @Override
    public int onStartCommand(Intent intent,int flags, int startId){
        PowerManager.WakeLock wakelock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyService");
        wakelock.acquire();
        super.onStartCommand(intent, flags, startId);



                        client.on("receiveCall", onReceiveCall);



        return this.START_STICKY;
    }

    @Override
    public void onDestroy() {
        Toast.makeText(this, "Service Destroyed", Toast.LENGTH_LONG).show();
        super.onDestroy();

    }

    public static Thread pollingThread() {

        final Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    for(int i=1;i>0;i++) {
                        JSONObject message = new JSONObject();
                        String model = Build.MODEL;

                        message.put("myId", model);

                        client.emit("poll", message);
                        Thread.sleep(1000);
                    }


                } catch (Exception E) {

                }
                finally
                 {

                }
            }
        };
        t.start();
        return t;
    }

    private Emitter.Listener onReceiveCall = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            String from = "";
            String name  = "";
            JSONObject data = (JSONObject) args[0];
            try {
                from = data.getString("from");
                name = data.getString("name");
            } catch (JSONException e) {
                e.printStackTrace();
            }

                Intent intent = new Intent(getApplicationContext(), IncomingCallActivity.class);
                //intent.setComponent(new ComponentName(getPackageName(), IncomingCallActivity.class.getName()));
                intent.setAction(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("CALLER_ID", from);
                intent.putExtra("USER_ID", userId);
                intent.putExtra("CALLER_NAME", name);
                intent.putExtra("USER_NAME", userName);
                intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            client.close();

            getApplicationContext().startActivity(intent);


        }
    };



}
