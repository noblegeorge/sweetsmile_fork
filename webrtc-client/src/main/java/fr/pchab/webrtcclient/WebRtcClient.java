package fr.pchab.webrtcclient;

import android.hardware.Camera;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.opengl.EGLContext;
import android.util.Log;

import io.socket.emitter.Emitter;
import io.socket.client.IO;
import io.socket.client.Socket;
/*import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;*/

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoCapturerAndroid;
import org.webrtc.VideoSource;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedList;


import static android.os.SystemClock.sleep;

public class WebRtcClient {
    private final static String TAG = WebRtcClient.class.getCanonicalName();
    private final static int MAX_PEER = 2;
    private boolean[] endPoints = new boolean[MAX_PEER];
    public PeerConnectionFactory factory;
    private HashMap<String, Peer> peers = new HashMap<>();
    private LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<>();
    private PeerConnectionParameters pcParams;
    private MediaConstraints pcConstraints = new MediaConstraints();
    private MediaStream localMS;
    private PeerConnection pc;
    private VideoSource videoSource;
    private RtcListener mListener;
    public Socket client;
    private String myId;
    public AudioSource audioSource;
    public org.webrtc.AudioTrack track;
    public MediaConstraints mc;
    public int Flag = 0;

    /**
     * Implement this interface to be notified of events.
     */
    public interface RtcListener {
        void onCallReady(String callId);

        void onAcceptCall(String callId);

        void onStatusChanged(String newStatus);

        void receiveMessage(String id, String msg);

        void onLocalStream(MediaStream localStream);

        void onAddRemoteStream(MediaStream remoteStream, int endPoint);

        void onRemoveRemoteStream(int endPoint);

        void onReject();
    }

    private interface Command {
        void execute(String peerId, JSONObject payload) throws JSONException;
    }

    /**
     * create an offer and send it to the server
     * <p/>
     * If you are the one initiating the call,
     * you would use navigator.getUserMedia() to get a video stream, then add the stream to the RTCPeerConnection
     * create and send offer to the server
     */
    private class CreateOfferCommand implements Command {
        public void execute(String peerId, JSONObject payload) throws JSONException {
            Log.d("Test : ","CreateOfferCommand");
            peers.values();
            mListener.onStatusChanged("CONNECTING");
            Peer peer = peers.get(peerId);
            peer.pc.createOffer(peer, pcConstraints);

        }
    }

    /**
     * create an answer and send it to the server
     * <p/>
     * On the opposite end, the friend will receive the offer from the server
     * Once the offer arrives, navigator.getUserMedia() is once again used to create the stream
     * An RTCSessionDescription object is created and set up as the remote description by calling
     * Then an answer is created using RTCPeerConnection.createAnswer() and sent back to the server, which forwards it to the caller.
     */
    private class CreateAnswerCommand implements Command {

        public void execute(String peerId, JSONObject payload) throws JSONException {
            Log.d("Test : ","CreateAnswerCommand");
            mListener.onStatusChanged("CONNECTING");
            Peer peer = peers.get(peerId);
            SessionDescription sdp = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(payload.getString("type")),
                    payload.getString("sdp")
            );

            peer.pc.setRemoteDescription(peer, sdp);
            peer.pc.createAnswer(peer, pcConstraints);
        }
    }

    private class SetRemoteSDPCommand implements Command {
        public void execute(String peerId, JSONObject payload) throws JSONException {

            Log.d("Test : ","SetRemoteSDPCommand");
            Peer peer = peers.get(peerId);
            SessionDescription sdp = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(payload.getString("type")),
                    payload.getString("sdp")
            );
            peer.pc.setRemoteDescription(peer, sdp);
        }
    }

    private class AddIceCandidateCommand implements Command {
        public void execute(String peerId, JSONObject payload) throws JSONException {
            mListener.onStatusChanged("CONNECTED");


            Log.d("Test : ","AddIceCandidateCommand");

            pc = peers.get(peerId).pc;
            if (pc.getRemoteDescription() != null) {
                IceCandidate candidate = new IceCandidate(
                        payload.getString("id"),
                        payload.getInt("label"),
                        payload.getString("candidate")
                );
                pc.addIceCandidate(candidate);
            }
        }
    }

    /**
     * Send a message through the signaling server
     *
     * @param to      id of recipient
     * @param type    type of message
     * @param payload payload of message
     * @throws JSONException
     */
    public void sendMessage(String to, String type, JSONObject payload) throws JSONException {

        Log.d("Test : ","sendMessage");



        JSONObject message = new JSONObject();
        message.put("to", to);
        message.put("type", type);
        message.put("payload", payload);
        client.emit("message", message);

    }

    private class MessageHandler {

        private HashMap<String, Command> commandMap;

        private MessageHandler() {
            Log.d("Test : ","MessageHandler");

            this.commandMap = new HashMap<>();
            JSONObject message = new JSONObject();

            commandMap.put("init", new CreateOfferCommand());
            commandMap.put("offer", new CreateAnswerCommand());
            commandMap.put("answer", new SetRemoteSDPCommand());
            commandMap.put("candidate", new AddIceCandidateCommand());
        }

        private Emitter.Listener onMessage = new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.d("Test : ","onMessage");

                JSONObject data = (JSONObject) args[0];
                try {
                    String from = data.getString("from");
                    String type = data.getString("type");
                    JSONObject payload = null;
                    Log.d("Test : onMessage ", from + " " + type);
                    if (!type.equals("init")) {
                        payload = data.getJSONObject("payload");
                    }
                    // if peer is unknown, try to add him
                    if (!peers.containsKey(from)) {
                        // if MAX_PEER is reach, ignore the call
                        int endPoint = findEndPoint();
                        if (endPoint != MAX_PEER) {
                            Peer peer = addPeer(from, endPoint);
                           peer.pc.addStream(localMS);
                            commandMap.get(type).execute(from, payload);
                        }
                    } else {
                        commandMap.get(type).execute(from, payload);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        };

        private Emitter.Listener onEject = new Emitter.Listener() {



            @Override
            public void call(Object... args) {
                Log.d("Test : ","onEject");

                mListener.onReject();
            }
        }
                ;

        private Emitter.Listener onRemoveCall = new Emitter.Listener() {




            @Override
            public void call(Object... args) {
                Log.d("Test : ","onRemoveCall");

            }
        };

        private Emitter.Listener onAccept = new Emitter.Listener() {
            @Override
            public void call(Object... args) {

                Log.d("Test : ","onAccept");


                JSONObject data = (JSONObject) args[0];
                try {
                    String from = data.getString("myId");
                    mListener.onAcceptCall(from);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
        };

        //listen to the id event from the server to get the user id
        private Emitter.Listener onId = new Emitter.Listener() {
            @Override
            public void call(Object... args) {

                Log.d("Test : ","onId");


                String id = (String) args[0];
                mListener.onCallReady(id);
            }
        };

        //listen to the chat event from the server and then chat adapter add message to view
        private Emitter.Listener onChat = new Emitter.Listener() {
            @Override
            public void call(Object... args) {

                Log.d("Test : ","onChat");

                try {
                    JSONObject obj = (JSONObject) args[0];
                    String id = obj.getString("user_id");
                    String msg = obj.getString("msg");
                    mListener.receiveMessage(id, msg);
                } catch (JSONException e) {
                }
            }
        };
    }

    private class Peer implements SdpObserver, PeerConnection.Observer {



        private PeerConnection pc;
        private String id;
        private int endPoint;

        @Override
        public void onCreateSuccess(final SessionDescription sdp) {

            Log.d("Test : ","Peer.onCreateSuccess");


            // TODO: modify sdp to use pcParams prefered codecs
            try {
                JSONObject payload = new JSONObject();
                payload.put("type", sdp.type.canonicalForm());
                payload.put("sdp", sdp.description);
                sendMessage(id, sdp.type.canonicalForm(), payload);
                pc.setLocalDescription(Peer.this, sdp);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onSetSuccess() {
            Log.d("Test : ","Peer.onSetSuccess");

        }

        @Override
        public void onCreateFailure(String s) {
            Log.d("Test : ","Peer.onCreateFailure");

        }

        @Override
        public void onSetFailure(String s) {
            Log.d("Test : ","Peer.onSetFailure");

        }

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.d("Test : ","Peer.onSignalingChange");

        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            Log.d("Test : ","onIceConnectionChange"+iceConnectionState);
            if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
                removePeer(id);
                mListener.onStatusChanged("DISCONNECTED");
                // sleep(2000);
                onDestroy();



            }
        }

        public void onIceConnectionReceivingChange(boolean b) {
            Log.d("Test : ","Peer.onIceConnectionReceivingChange");


        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
            Log.d("Test : ","Peer.onIceGatheringChange");

        }

        @Override
        public void onIceCandidate(final IceCandidate candidate) {
            Log.d("Test : ","Peer.onIceCandidate");

            try {
                JSONObject payload = new JSONObject();
                payload.put("label", candidate.sdpMLineIndex);
                payload.put("id", candidate.sdpMid);
                payload.put("candidate", candidate.sdp);
                sendMessage(id, "candidate", payload);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            mListener.onStatusChanged("CONNECTED");
            Log.d("Test : ","Peer.onAddStream" + mediaStream.label());

            // remote streams are displayed from 1 to MAX_PEER (0 is localStream)
            mListener.onAddRemoteStream(mediaStream, endPoint + 1);
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            Log.d("Test : ","Peer.onRemoveStream"+ mediaStream.label());
            mListener.onStatusChanged("DISCONNECTED");

            removePeer(id);
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
            Log.d("Test : ","Peer.onDataChannel");

        }

        @Override
        public void onRenegotiationNeeded() {

            Log.d("Test : ","Peer.onRenegotiationNeeded");


        }

        public Peer(String id, int endPoint) {


            Log.d("Test : ", "Peer | new Peer: " + id + " " + endPoint);
            this.pc = factory.createPeerConnection(iceServers, pcConstraints, this);
            this.id = id;
            this.endPoint = endPoint;

            pc.addStream(localMS); //, new MediaConstraints()

        }
    }

    private Peer addPeer(String id, int endPoint) {
        Log.d("Test : ","addPeer");

        Peer peer = new Peer(id, endPoint);
        peers.put(id, peer);

        endPoints[endPoint] = true;
        return peer;

    }

    public void removePeer(String id) {
        Log.d("Test : ","removePeer");

        Peer peer = peers.get(id);
        mListener.onRemoveRemoteStream(peer.endPoint);
        peer.pc.close();
        peers.remove(peer.id);
        peers.clear();
        //mListener.onRemoveRemoteStream(localMS,peer.pc);

        endPoints[peer.endPoint] = false;
    }

    public WebRtcClient(RtcListener listener, String host, PeerConnectionParameters params, String myId, Socket clientArg) {
        Log.d("Test : ","WebRtcClient");
        mListener = listener;
        pcParams = params;
        this.myId = myId;
        PeerConnectionFactory.initializeAndroidGlobals(listener, true, false,
                false);
        factory = new PeerConnectionFactory();
        MessageHandler messageHandler = new MessageHandler();
        client = clientArg;
      /*  try {
            client = IO.socket(host);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }*/
        client.on("id", messageHandler.onId);
        client.on("message", messageHandler.onMessage);
        client.on("chat", messageHandler.onChat);
        client.on("ejectcall", messageHandler.onEject);
        client.on("acceptcall", messageHandler.onAccept);
        client.on("removeVideo", messageHandler.onRemoveCall);
        client.connect();
        try {
            JSONObject message = new JSONObject();
            message.put("myId", this.myId);
            client.emit("resetId", message);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));
        iceServers.add(new PeerConnection.IceServer("turn:54.169.144.32:3478","Tesseract", "DF34rFef44fref"));
        iceServers.add(new PeerConnection.IceServer("stun:54.169.144.32:3478"));
        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));
        pcConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
    }

    /**
     * Call this method in Activity.onPause()
     */
    public void onPause() {
        Log.d("Test : ","onPause");

        // if (videoSource != null) videoSource.stop();
    }

    /**
     * Call this method in Activity.onResume()
     */
    public void onResume() {
        Log.d("Test : ","onResume");

        // if (videoSource != null) videoSource.restart();
    }

    /**
     * Call this method in Activity.onDestroy()
     */


    public void stopVideo() {
        Log.d("Test : ","stopVideo");

        //videoSource.stop();
    }

    private int findEndPoint() {
        Log.d("Test : ","findEndPoint");

        for (int i = 0; i < MAX_PEER; i++) if (!endPoints[i]) return i;
        return MAX_PEER;
    }

    /**
     * Start the client.
     * <p/>
     * Set up the local stream and notify the signaling server.
     * Call this method after onCallReady.
     *
     * @param name client name
     */
    public void start(String name) {
        Log.d("Test : ","start");

        setCamera();
       /* try {
            JSONObject message = new JSONObject();
            message.put("name", name);
            client.emit("readyToStream", message);
        } catch (JSONException e) {
            e.printStackTrace();
        }*/
    }

    public void transmitChat(JSONObject message) {
        client.emit("chat", message);
    }

    public void removeVideo(String otherID) throws JSONException{
        Log.d("Test : ","removeVideo");

        JSONObject message = new JSONObject();
        message.put("other", otherID);
        client.emit("removeVideo", message);
    }

    public String client_id() {
        Log.d("Test : ","client_id");

        return client.id();
    }

    public void startClient(String to, String type, JSONObject payload) throws JSONException {
        Log.d("Test : ","startClient");

        JSONObject message = new JSONObject();
        message.put("to", to);
        message.put("type", type);
        message.put("payload", payload);
        client.emit("startclient", message);
    }

    private void setCamera() {
        Log.d("Test : ","setCamera");

        localMS = factory.createLocalMediaStream("ARDAMS");


        /* if (pcParams.videoCallEnabled) {
            MediaConstraints videoConstraints = new MediaConstraints();
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxHeight", Integer.toString(pcParams.videoHeight)));
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxWidth", Integer.toString(pcParams.videoWidth)));
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxFrameRate", Integer.toString(pcParams.videoFps)));
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("minFrameRate", Integer.toString(pcParams.videoFps)));

            videoSource = factory.createVideoSource(getVideoCapturer(), videoConstraints);
            localMS.addTrack(factory.createVideoTrack("ARDAMSv0", videoSource));
        } */
        mc = new MediaConstraints();
        audioSource = factory.createAudioSource(mc);
        track = factory.createAudioTrack("ARDAMSa0", audioSource);
        localMS.addTrack(track);
        mListener.onLocalStream(localMS);
    }

  /*  private VideoCapturer getVideoCapturer() {
        String frontCameraDeviceName = getNameOfFrontFacingDevice();
        return VideoCapturerAndroid.create(frontCameraDeviceName);
    }

    public static String getNameOfFrontFacingDevice() {
        for (int i = 0; i < Camera.getNumberOfCameras(); ++i) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
                return getDeviceName(i);
        }
        throw new RuntimeException("Front facing camera does not exist.");
    }

    public static String getDeviceName(int index) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(index, info);
        String facing =
                (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) ? "front" : "back";
        return "Camera " + index + ", Facing " + facing
                + ", Orientation " + info.orientation;
    }*/

    public void onDestroy() {
        Log.d("Test : ","onDestroy");

        if (Flag == 0) {

/*            if(pc!=null) {
                //       pc.dispose();
                      pc.close();

              pc.dispose();
                //           pc.dispose();
            }*/

            for (Peer peer : peers.values()) {
                peer.pc.removeStream(localMS);
                peer.pc.close();
                peer.pc.dispose();
            }
           // pc.removeStream(localMS);

 //         pc.close();
 //         pc.removeStream(localMS);
 //         pc.dispose();

         /* pc.close();
            pc.removeStream(localMS);
            pc.dispose(); */



            localMS.removeTrack(track);

            // localMS.dispose();
            track.dispose();
            audioSource.dispose();

           // pcConstraints.mandatory.clear();
           // pcConstraints.optional.clear();


            try {
                JSONObject message = new JSONObject();
                message.put("myId", this.myId);
                client.emit("resetId", message);
            } catch (JSONException e) {
                e.printStackTrace();
            }

          //  factory.stopAecDump();
         //   localMS=null;
          //  factory=null;

            //      videoSource.stop();
            //     localMS.dispose();
            //
            //  if(audioSource!=null)audioSource.dispose();
            //     if(factory!=null)factory.dispose();
            /*if (client != null) client.disconnect();
            if (client != null) client.close();*/

          //  client=null;
        }
        Flag=1;

    }
}