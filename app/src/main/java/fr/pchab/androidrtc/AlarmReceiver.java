package fr.pchab.androidrtc;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.widget.Toast;


import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import static fr.pchab.androidrtc.MyService.userId;


public class AlarmReceiver extends BroadcastReceiver
{

    @Override
    public void onReceive( Context context, Intent intent)
    {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "");
        wl.acquire();

        // Put here YOUR code.
    //    Toast.makeText(context, "Alarm !!!!!!!!!!", Toast.LENGTH_LONG).show(); // For example
        setAlarm(context);
        Thread t = pollingThread(context);
        t.interrupt();
        if (!isMyServiceRunning(MyService.class,context)) {

            Intent updateIntent = new Intent();
            updateIntent.setClass(context, MyService.class);
            context.startService(updateIntent);
        }



      /*  Intent updateIntent = new Intent();
        updateIntent.setClass(context, MyService.class);
        context.startService(intent);*/


        /*try {

            HttpClient httpClient = new DefaultHttpClient();
            //  String host = "http://" + MyService.getResources().getString(R.string.host);
            //  host += (":" + MyService.getResources().getString(R.string.port) + "/");
            HttpGet request = new HttpGet(host + ":" + port + "/status/" + userId);
            Toast.makeText(context, host, Toast.LENGTH_LONG).show();
            JSONObject message = new JSONObject();
            String model = Build.MODEL;





            int status = (new JSONObject(EntityUtils.toString((httpClient.execute(request)).getEntity()))).getInt("status");
            if (status != 1) {

                client.disconnect();
                client.connect();

                //   message.put("myId", userId);
                //   client.emit("resetId", message);

            }

            message=null;
            message.put("myId", model);
            client.emit("poll", message);



        } catch (Exception E) {

        }
        finally
        {

        }*/

        wl.release();
    }

    public void setAlarm(Context context)
    {
        AlarmManager am =( AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, AlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
      //  am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), 1000 * 60 , pi); // Millisec * Second * Minute

        /*   // TODO: Update copile SDK to latest and enable setExactAndAllowWhileIdle for API>=23. Depreciated httpClients needs to be fixed.

        if (Build.VERSION.SDK_INT >= 23) {

            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 500, pi);
        } else */ if (Build.VERSION.SDK_INT >= 19) {
            am.setExact(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()+60000, pi);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()+60000, pi);
        }

    }

    public void cancelAlarm(Context context)
    {
        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent, 0);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(sender);
    }

    public Thread pollingThread(final Context context) {



        final Thread t = new Thread() {
            @Override
            public void run() {
                    try {


                        HttpClient httpClient = new DefaultHttpClient();
                        String host = "http://" + context.getString(R.string.host);
                        host += (":" + context.getString(R.string.port) + "/");
                        HttpGet request = new HttpGet(host + "status/" + userId);
                        JSONObject message = new JSONObject();
                        String model = Build.MODEL;


                        int status = (new JSONObject(EntityUtils.toString((httpClient.execute(request)).getEntity()))).getInt("status");
                        if (status != 1) {
                            MyService.client.close();
                            MyService.client.disconnect();
                            MyService.client.connect();

                            //   message.put("myId", userId);
                            //   client.emit("resetId", message);

                        }

                        message = new JSONObject();
                        message.put("myId", model);
                        MyService.client.emit("poll", message);


                    } catch (Exception E) {

                    } finally {

                    }

            }
        };
        t.start();
        return t;
    }


    private boolean isMyServiceRunning(Class<?> serviceClass,Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

}


/*
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import static fr.pchab.androidrtc.MyService.client;
import static fr.pchab.androidrtc.MyService.userId;
import static fr.pchab.androidrtc.R.string.host;
import static fr.pchab.androidrtc.R.string.port;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context,Intent intent){
        Intent startServiceIntent = new Intent(context, MyService.class);



        try {

            HttpClient httpClient = new DefaultHttpClient();
          //  String host = "http://" + MyService.getResources().getString(R.string.host);
          //  host += (":" + MyService.getResources().getString(R.string.port) + "/");
            HttpGet request = new HttpGet(host + ":" + port + "/status/" + userId);
            JSONObject message = new JSONObject();
            String model = Build.MODEL;





                int status = (new JSONObject(EntityUtils.toString((httpClient.execute(request)).getEntity()))).getInt("status");
                if (status != 1) {

                    client.disconnect();
                    client.connect();

                    //   message.put("myId", userId);
                    //   client.emit("resetId", message);

                }

                        message=null;
                        message.put("myId", model);
                        client.emit("poll", message);



        } catch (Exception E) {

        }
        finally
        {

        }
    }




}*/
