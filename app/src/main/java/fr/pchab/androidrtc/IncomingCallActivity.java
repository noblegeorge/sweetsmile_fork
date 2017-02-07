package fr.pchab.androidrtc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.PowerManager;
import android.os.Vibrator;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import io.socket.client.IO;
import io.socket.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;

public class IncomingCallActivity extends Activity {
    private String callerName;
    private TextView mCallerID;
    private TextView cd;
    private String userName;
    private String userId;
    private Socket client;
    private String callerId;
    private Vibrator vib;
    private MediaPlayer mMediaPlayer;
    public int secs = 0;
    protected PowerManager.WakeLock mWakeLock;
    countDown timer;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD|
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON|
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_incoming_call);
        final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        this.mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
        this.mWakeLock.acquire();

        Bundle extras = getIntent().getExtras();
        callerId = extras.getString("CALLER_ID");
        userId = extras.getString("USER_ID");
        callerName = extras.getString("CALLER_NAME");
        userName= extras.getString("USER_NAME");


        this.mCallerID = (TextView) findViewById(R.id.caller_id);
        this.cd = (TextView) findViewById(R.id.timer);
        this.mCallerID.setText(this.callerName);
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer = MediaPlayer.create(this, R.raw.skype_call);
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mMediaPlayer.setLooping(true);
        mMediaPlayer.start();
        vib= (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        long[] pattern = {0, 0, 1000};
        vib.vibrate(pattern,0);
        timer = new countDown(22000, 1000);

    }

    private class countDown extends CountDownTimer {
        public countDown(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
            start();
        }

        @Override
        public void onFinish() {

            client=MyService.client;

          /*  String host = "http://" + getResources().getString(R.string.host);
            host += (":" + getResources().getString(R.string.port) + "/");
            try {
                client = IO.socket(host);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }*/
          //  client.connect();
            try {
                JSONObject message = new JSONObject();
                message.put("myId", userId);
                message.put("callerId", callerId);
                MyService.client.emit("ejectcall", message);
            } catch (JSONException e) {
                e.printStackTrace();
            }
           // client.close();
            userName = userId = null;
            finish();
         //   onDestroy();
        }

        @Override
        public void onTick(long duration) {
            cd.setText(String.valueOf(secs));
            secs = secs + 1;

        }
    }

    private void getService(){
        startService(new Intent(this, MyService.class));


    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void acceptCall(View view) {


        Intent intent = new Intent(IncomingCallActivity.this, RtcIncomingActivity.class);
        intent.putExtra("id", this.userId);
        intent.putExtra("name",this.userName);
        intent.putExtra("callerId", callerId);
        intent.putExtra("callerName",callerName);
        String host = "http://" + getResources().getString(R.string.host);
        host += (":" + getResources().getString(R.string.port) + "/");

        client=MyService.client;

        /*try {
            client = IO.socket(host);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        client.connect();*/
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        //  client.close();
        startActivity(intent);
        userName = userId = null;
        finish();
        closeActivity();
    }

    /**
     * Publish a hangup command if rejecting call.
     *
     * @param view
     */
    public void rejectCall(View view) {
        getService();
        /*String host = "http://" + getResources().getString(R.string.host);
        host += (":" + getResources().getString(R.string.port) + "/");
        try {
            client = IO.socket(host);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        client.connect();*/
        client=MyService.client;

        try {
            JSONObject message = new JSONObject();
            message.put("myId", userId);
            message.put("callerId", callerId);
            MyService.client.emit("ejectcall", message);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        //client.close();
        userName = userId = null;

        finish();
        closeActivity();





    }

    public void closeActivity() {

        IncomingCallActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                finishActivity(0);
            }
        });

    }

    public void onDestroy(){

        timer.cancel();
        timer=null;

        client=null;

        secs = 0;
        // I have an Intent you might not need one
        vib.cancel();
        mMediaPlayer.reset();
        mMediaPlayer.release();
        mWakeLock.release();

        super.onDestroy();
        super.finish();

    }
}
