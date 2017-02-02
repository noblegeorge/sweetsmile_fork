package fr.pchab.androidrtc;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
//import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.View;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import fr.pchab.androidrtc.Adapter.ChatAdapter;
import fr.pchab.androidrtc.Model.ChatMessage;

import io.socket.emitter.Emitter;
import io.socket.client.IO;
import io.socket.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.MediaStream;
//import org.webrtc.RendererCommon;
//import org.webrtc.VideoRenderer;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

import fr.pchab.webrtcclient.PeerConnectionParameters;
import fr.pchab.webrtcclient.WebRtcClient;

import static android.os.SystemClock.sleep;

public class RtcActivity extends Activity implements WebRtcClient.RtcListener {
    private static final String VIDEO_CODEC_VP9 = "VP8";
    private static final String AUDIO_CODEC_OPUS = "opus";
    // Local preview screen position before call is connected.
    private static final int LOCAL_X_CONNECTING = 0;
    private static final int LOCAL_Y_CONNECTING = 0;
    private static final int LOCAL_WIDTH_CONNECTING = 100;
    private static final int LOCAL_HEIGHT_CONNECTING = 100;
    // Local preview screen position after call is connected.
    private static final int LOCAL_X_CONNECTED = 72;
    private static final int LOCAL_Y_CONNECTED = 72;
    private static final int LOCAL_WIDTH_CONNECTED = 25;
    private static final int LOCAL_HEIGHT_CONNECTED = 25;
    // Remote video screen position
    private static final int REMOTE_X = 0;
    private static final int REMOTE_Y = 0;
    private static final int REMOTE_WIDTH = 100;
    private static final int REMOTE_HEIGHT = 100;
 //   private RendererCommon.ScalingType scalingType = RendererCommon.ScalingType.SCALE_ASPECT_FILL;
//    private GLSurfaceView vsv;
   // private VideoRenderer.Callbacks localRender;
  //  private VideoRenderer.Callbacks remoteRender;
    private static WebRtcClient client;
    private String mSocketAddress;
    private EditText mChatEditText;
    private String username;
    private ListView mChatList;
    private ChatAdapter mChatAdapter;
    private String myId;
    private String number="";
    private String callerIdChat="";
    private String callerName="";
    Button hang;
    private Timer mTimer;

    private Socket client2;
    private TextView mCallDuration;
    private TextView mCallerName;
    private TextView mCallState;
    private String mCallId;
    private long mCallStart = 0;
    private UpdateCallDurationTask mDurationTask;
    public String status;
    public int flag = 0;


    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;
    private int field = 0x00000020;



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(
                LayoutParams.FLAG_FULLSCREEN
                        | LayoutParams.FLAG_KEEP_SCREEN_ON
                        | LayoutParams.FLAG_DISMISS_KEYGUARD
                        | LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | LayoutParams.FLAG_TURN_SCREEN_ON);


        try {
            // Yeah, this is hidden field.
            field = PowerManager.class.getClass().getField("PROXIMITY_SCREEN_OFF_WAKE_LOCK").getInt(null);
        } catch (Throwable ignored) {
        }

        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(field, getLocalClassName());


        setContentView(R.layout.main);
//        this.mChatEditText = (EditText) findViewById(R.id.chat_input);
//        this.mChatList = getListView();
//        this.mChatEditText = (EditText) findViewById(R.id.chat_input);

        //Set list chat adapter for the list activity
//        List<ChatMessage> ll = new LinkedList<ChatMessage>();
 //       mChatAdapter = new ChatAdapter(this, ll);
//        mChatList.setAdapter(mChatAdapter);

        hang = (Button) findViewById(R.id.hang_up);

        mCallDuration = (TextView) findViewById(R.id.callDuration);
        mCallerName = (TextView) findViewById(R.id.remoteUser);
        mCallState = (TextView) findViewById(R.id.callState);

        hang.setOnClickListener(new View.OnClickListener() {
                                    public void onClick(View v) {
                                       hangup();
                                    }
        });

        mCallStart = System.currentTimeMillis();


        mSocketAddress = "http://" + getResources().getString(R.string.host);
        mSocketAddress += (":" + getResources().getString(R.string.port) + "/");

        //Set sources for video renderer in view
//        vsv = (GLSurfaceView) findViewById(R.id.glview_call);
//        vsv.setPreserveEGLContextOnPause(true);
//        vsv.setKeepScreenOn(true);
        wakeLock.acquire();
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            myId = extras.getString("id");
            number = extras.getString("number");
            callerIdChat = extras.getString("callerIdChat");
            username = extras.getString("name");
            callerName=extras.getString("callerName");
        }

        mCallerName.setText(callerName);
        mTimer = new Timer();
        mDurationTask = new UpdateCallDurationTask();
        mTimer.schedule(mDurationTask, 0, 500);


        init();

//       VideoRendererGui.setView(vsv, new Runnable() {
//            @Override
//            public void run() {
//            }
//        });

        // local and remote render
//        remoteRender = VideoRendererGui.create(
//                REMOTE_X, REMOTE_Y,
//                REMOTE_WIDTH, REMOTE_HEIGHT, scalingType, false);
//        localRender = VideoRendererGui.create(
//                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
//                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING, scalingType, true);
    }




    private void updateCallDuration() {
        if (mCallStart > 0) {
            mCallDuration.setText(formatTimespan(System.currentTimeMillis() - mCallStart));
        }
    }


    private String formatTimespan(long timespan) {
        long totalSeconds = timespan / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    /**
     * Initialize webrtc client
     * <p/>
     * Set up the peer connection parameters get some video information and then pass these information to Webrtcclient class.
     */
    private void init() {
       // Point displaySize = new Point();
        PeerConnectionParameters params = new PeerConnectionParameters(
                false, false, 0, 0, 0, 0, null, false, 1, AUDIO_CODEC_OPUS, true);
        client = new WebRtcClient(this, mSocketAddress, params, this.myId, MyService.client);
    }

    /**
     * Handle chat messages when people click the send button
     * <p/>
     * Get the message from input then add it to chat adapter
     * Transmit the message to other users except the one who call this function
     *
     *  the view that contain the button
     */
   /* public void sendMessage(View view) {
        String message = mChatEditText.getText().toString();
        if (message.equals("")) return; // Return if empty
        ChatMessage chatMsg = new ChatMessage(username, message, System.currentTimeMillis());
        mChatAdapter.addMessage(chatMsg);

        //Data is being sent under JSON object
        JSONObject messageJSON = new JSONObject();
        try {

            if (number != "" && number != null){
                //Log.d("minthestfinal","come first "+number);
                messageJSON.put("to", number);
            }else{
                //Log.d("minthestfinal","come second "+callerIdChat);
                messageJSON.put("to", callerIdChat);
            }
            //Log.d("minthestfinal","chet ngay day "+ );
            messageJSON.put("user_id", chatMsg.getSender());
            messageJSON.put("msg", chatMsg.getMessage());
            messageJSON.put("time", chatMsg.getTimeStamp());
            client.transmitChat(messageJSON);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Hide keyboard when you send a message.
        View focusView = this.getCurrentFocus();
        if (focusView != null) {
            InputMethodManager inputManager = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }
        mChatEditText.setText("");
    }*/


    private class UpdateCallDurationTask extends TimerTask {

        @Override
        public void run() {
            RtcActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateCallDuration();
                }
            });
        }
    }

    /**
     * Handle when people click hangup button
     * <p/>
     * Destroy all video resources and connection
     *
    // * @param view the view that contain the button
     */
    public void hangup() {
        if(mCallState.getText()=="CONNECTED") {
            String host = "http://" + getResources().getString(R.string.host);
            host += (":" + getResources().getString(R.string.port) + "/");



           // client2.connect();
            try {
                JSONObject message = new JSONObject();
              //  message.put("myId", myId);
                message.put("callerId", number);
                MyService.client.emit("ejectcall", message);
            } catch (JSONException e) {
                e.printStackTrace();
            }
           // client2.disconnect();
        }
//        if (client != null) {
//            client.onDestroy();
   //         onDestroy();
  //          android.os.Process.killProcess(android.os.Process.myPid());


   //            client.removeVideo(number);

        wakeLock.release();

        onDestroy();


 //       closeActivity();


        //       }
    }

    /**
     * Handle when people click stopvideo button
     * <p/>
     * Stop all video resources and connection
     *
     * @param view the view that contain the button
     */
/*    public void stopvideo(View view) {
        if (client != null) {
            client.stopVideo();

        }
    } */

    /**
     * Handle onPause event which is implement by RtcListener class
     * <p/>
     * Pause the video source
     */
    @Override
    public void onPause() {
        super.onPause();
//        vsv.onPause();
//        if (client != null) {
//            client.onPause();
//        }
        if(isAppIsInBackground(getBaseContext())){
            NotificationManager mManager;
            mManager = (NotificationManager) getApplicationContext()
                    .getSystemService(
                            getApplicationContext().NOTIFICATION_SERVICE);
            Intent in = new Intent(getApplicationContext(),
                    RtcActivity.class);
            Notification notification = new Notification(R.drawable.notification_template_icon_bg,
                    "Demo video  ", System.currentTimeMillis());
            RemoteViews notificationView = new RemoteViews(getPackageName(),
                    R.layout.notification_video_calling);
            in.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);

//            Intent hangIntent = new Intent(this, hangButtonListener.class);
//            PendingIntent pendingHangIntent = PendingIntent.getBroadcast(this, 0,
//                    hangIntent, 0);
//            notificationView.setOnClickPendingIntent(R.id.hang_up_noti,
//                    pendingHangIntent);
            Intent stopIntent = new Intent(this, stopButtonListener.class);
            PendingIntent pendingStopIntent = PendingIntent.getBroadcast(this, 0,
                    stopIntent, 0);
            notificationView.setOnClickPendingIntent(R.id.end_call_noti,pendingStopIntent
                    );
            PendingIntent pendingNotificationIntent = PendingIntent.getActivity(
                    getApplicationContext(), 0, in,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            notification.flags |= Notification.FLAG_AUTO_CANCEL;
            notification.contentView = notificationView;
            notification.contentIntent = pendingNotificationIntent;
            mManager.notify(0, notification);
        }
    }

/*    public static class hangButtonListener extends BroadcastReceiver {
        @Override
      //  public void onReceive(Context context, Intent intent) {
            client.stopVideo();
        }
    } */

    public static class stopButtonListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            client.onDestroy();
            Intent mainview =new Intent(context,MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            context.startActivity(mainview);

        }
    }


    /**
     * Handle onResume event which is implement by RtcListener class
     * <p/>
     * Resume the video source
     */



    @Override
    public void onResume() {
        super.onResume();
//        vsv.onResume();
        if (client != null) {
            client.onResume();
        }
    }

    /**
     * Handle onDestroy event which is implement by RtcListener class
     * <p/>
     * Destroy the video source
     */

    /**
     * This function is being call when user have got an id from nodejs server
     * <p/>
     * check if caller id is not null then answer the call
     * if not then start the camera and send id to other user
     *
     * @param callId the id of the user
     */
    @Override
    public void onCallReady(String callId) {
       // this.username = client.client_id();
        if (number != null) {
            try {
                client.startClient(number, "init", null);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            call(callId);
        }
    }

    /**
     * This function is being call when user reject phone call
     */
    @Override
    public void onReject() {
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);


        wakeLock.release();


        onDestroy();
    }

    @Override
    public void onAcceptCall(String callId) {
        try {
            try{ Thread.sleep(500); }catch(InterruptedException e){ }
            answer(callId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

public void closeActivity() {

    RtcActivity.this.runOnUiThread(new Runnable() {
        @Override
        public void run() {
            finishActivity(0);
        }
    });

}
    /**
     * This function is being when the chat event is being triggered
     * <p/>
     * Add the chat message to the chat adapter
     *
  //   * @param id  the id of the user sent the chat
  //   * @param msg the message
     */
  //  @Override
    public void receiveMessage(final String id, final String msg) {

    /*    runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ChatMessage chatMsg = new ChatMessage(id, msg, System.currentTimeMillis());
                mChatAdapter.addMessage(chatMsg);
                if(isAppIsInBackground(getBaseContext())){
                    NotificationManager mManager;
                    mManager = (NotificationManager) getApplicationContext()
                            .getSystemService(
                                    getApplicationContext().NOTIFICATION_SERVICE);
                    Intent in = new Intent(getApplicationContext(),
                            RtcActivity.class);
                    Notification notification = new Notification(R.drawable.notification_template_icon_bg,
                            "New message from "+id, System.currentTimeMillis());
                    notification.sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                    in.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP);

                    PendingIntent pendingNotificationIntent = PendingIntent.getActivity(
                            getApplicationContext(), 0, in,
                            PendingIntent.FLAG_UPDATE_CURRENT);
                    notification.flags |= Notification.FLAG_AUTO_CANCEL;

//                    notification.setLatestEventInfo(getApplicationContext(),
//                            "New message", msg,
//                            pendingNotificationIntent);
                    mManager.notify(0, notification);
                }
            }
        }); */

    }

    /**
     * Check application is in the backgroud or in foreground
     *
     * @param context  the id of the user sent the chat
     */
    private boolean isAppIsInBackground(Context context) {
        boolean isInBackground = true;
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT_WATCH) {
            List<ActivityManager.RunningAppProcessInfo> runningProcesses = am.getRunningAppProcesses();
            for (ActivityManager.RunningAppProcessInfo processInfo : runningProcesses) {
                if (processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    for (String activeProcess : processInfo.pkgList) {
                        if (activeProcess.equals(context.getPackageName())) {
                            isInBackground = false;
                        }
                    }
                }
            }
        } else {
            List<ActivityManager.RunningTaskInfo> taskInfo = am.getRunningTasks(1);
            ComponentName componentInfo = taskInfo.get(0).topActivity;
            if (componentInfo.getPackageName().equals(context.getPackageName())) {
                isInBackground = false;
            }
        }

        return isInBackground;
    }

    /**
     * This function is being call to answer call from other user
     * <p/>
     * send init message to the caller and connect
     * start the camera
     *
     * @param callerId the id of the caler
     */
    public void answer(String callerId) throws JSONException {

        client.sendMessage(callerId, "init", null);
        startCam();
    }

    /**
     * This function is to send message contain id to the other user in order to start a call
     * <p/>
     * Start intent then start the message intent contain url and user id
     *
     * @param callId the id of the user
     */
    public void call(String callId) {
        startCam();
    }


    /**
     * Start camera function
     * <p/>
     * call the webrtc start camera function
     */
    public void startCam() {
        // Camera settings
        client.start(myId);
    }

    /**
     * Being called when call status change
     * <p/>
     * Log message when webrtc status change
     */
    @Override
    public void onStatusChanged(final String newStatus) {


        runOnUiThread(new Runnable() {
            @Override
            public void run(){
            //    sleep(500);
                switch (newStatus){
                    case "CONNECTING":
                        mCallState.setText("CONNECTING");
                        mCallDuration.setText(formatTimespan(System.currentTimeMillis()));
                        break;
                    case "CONNECTED":
                        mCallState.setText("CONNECTED");
                        mCallDuration.setText(formatTimespan(System.currentTimeMillis() - mCallStart));

                        break;
                    case "DISCONNECTED":
                        mCallState.setText("DISCONNECTED");
                        sleep(1000);

                        wakeLock.release();

                        onDestroy();
                        break;
                    default:
                        mCallState.setText("INITIALISING");
                        break;

                }

            }
        });
    }

    /**
     * Being called when local stream is added
     * <p/>
     * Update render view for the local stream in the small window
     */
//    @Override
    public void onLocalStream(MediaStream localStream) {
/*        localStream.videoTracks.get(0).addRenderer(new VideoRenderer(localRender));
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING,
                scalingType,false);
*/    }

    /**
     * Being called when remote stream is added
     * <p/>
     * Update render view for the remote stream in the big window
     */
//    @Override
    public void onAddRemoteStream(MediaStream remoteStream, int endPoint) {
/*        remoteStream.videoTracks.get(0).addRenderer(new VideoRenderer(remoteRender));
        VideoRendererGui.update(remoteRender,
               REMOTE_X, REMOTE_Y,
                REMOTE_WIDTH, REMOTE_HEIGHT, RendererCommon.ScalingType.SCALE_ASPECT_FILL,false);
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTED, LOCAL_Y_CONNECTED,
                LOCAL_WIDTH_CONNECTED, LOCAL_HEIGHT_CONNECTED,
                scalingType,false);
*/    }


    /**
     * Being called when remote stream is removed
     * <p/>
     * make local renderer become the big one again
     */
//    @Override
   public void onRemoveRemoteStream(int endPoint) {
/*        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING,
                scalingType,false);
*/    }


    @Override
    public void onDestroy() {


        if(flag==0) {

            mTimer.cancel();
            mTimer = null;


            try {
                client.onDestroy();
            } catch (Exception E) {

            }
       /* if(client2!=null) {
            client2.disconnect();
        }*/
            client2 = null;
            startService(new Intent(this, MyService.class));
            // client.factory.dispose();
            //  client.audioSource.dispose();


            //  android.os.Process.killProcess(android.os.Process.myPid());
            //closeActivity();




            flag =1;



          //  super.onBackPressed();

         //   super.finish();



        }
        finishActivity(0);

        super.onDestroy();
        super.finish();



        //  android.os.Process.killProcess(android.os.Process.myPid());
    }
}