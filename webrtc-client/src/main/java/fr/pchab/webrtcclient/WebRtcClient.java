package fr.pchab.webrtcclient;

import android.hardware.Camera;
import android.opengl.EGLContext;
import android.util.Log;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

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
    private PeerConnectionFactory factory;
    private HashMap<String, Peer> peers = new HashMap<>();
    private LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<>();
    private PeerConnectionParameters pcParams;
    private MediaConstraints pcConstraints = new MediaConstraints();
    private MediaStream localMS;
    private PeerConnection pc;
   // private VideoSource videoSource;
    private RtcListener mListener;
    private Socket client;
    private String myId;

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
            Log.d(TAG, "CreateOfferCommand");
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
            Log.d(TAG, "CreateAnswerCommand");
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

            Log.d(TAG, "SetRemoteSDPCommand");
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


            Log.d(TAG, "AddIceCandidateCommand");

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


        JSONObject message = new JSONObject();
        message.put("to", to);
        message.put("type", type);
        message.put("payload", payload);
        client.emit("message", message);

    }

    private class MessageHandler {
        private HashMap<String, Command> commandMap;

        private MessageHandler() {
            this.commandMap = new HashMap<>();
            commandMap.put("init", new CreateOfferCommand());
            commandMap.put("offer", new CreateAnswerCommand());
            commandMap.put("answer", new SetRemoteSDPCommand());
            commandMap.put("candidate", new AddIceCandidateCommand());
        }

        private Emitter.Listener onMessage = new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject data = (JSONObject) args[0];
                try {
                    String from = data.getString("from");
                    String type = data.getString("type");
                    JSONObject payload = null;
                    Log.d("minhhere ", from + " " + type);
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
                mListener.onReject();
            }
        };

        private Emitter.Listener onRemoveCall = new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.d("minhhangup","test");
            }
        };

        private Emitter.Listener onAccept = new Emitter.Listener() {
            @Override
            public void call(Object... args) {
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
                String id = (String) args[0];
                mListener.onCallReady(id);
            }
        };

        //listen to the chat event from the server and then chat adapter add message to view
        private Emitter.Listener onChat = new Emitter.Listener() {
            @Override
            public void call(Object... args) {
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
        }

        @Override
        public void onCreateFailure(String s) {
        }

        @Override
        public void onSetFailure(String s) {
        }

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            Log.d("minhfinal","ice conenction change "+iceConnectionState);
            if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
                mListener.onStatusChanged("DISCONNECTED");
                // sleep(2000);
                removePeer(id);
                onDestroy();



            }
        }

        public void onIceConnectionReceivingChange(boolean b) {

        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
        }

        @Override
        public void onIceCandidate(final IceCandidate candidate) {
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
            Log.d(TAG, "onAddStream " + mediaStream.label());
            // remote streams are displayed from 1 to MAX_PEER (0 is localStream)
            mListener.onAddRemoteStream(mediaStream, endPoint + 1);
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            mListener.onStatusChanged("DISCONNECTED");

            Log.d(TAG, "onRemoveStream " + mediaStream.label());
            removePeer(id);
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
        }

        @Override
        public void onRenegotiationNeeded() {

        }

        public Peer(String id, int endPoint) {

            Log.d("anhminh", "new Peer: " + id + " " + endPoint);
            this.pc = factory.createPeerConnection(iceServers, pcConstraints, this);
            this.id = id;
            this.endPoint = endPoint;

            pc.addStream(localMS); //, new MediaConstraints()

        }
    }

    private Peer addPeer(String id, int endPoint) {
        Peer peer = new Peer(id, endPoint);
        peers.put(id, peer);

        endPoints[endPoint] = true;
        return peer;

    }

    public void removePeer(String id) {
        Peer peer = peers.get(id);
        mListener.onRemoveRemoteStream(peer.endPoint);
        peer.pc.close();
        peers.remove(peer.id);
        endPoints[peer.endPoint] = false;
    }

    public WebRtcClient(RtcListener listener, String host, PeerConnectionParameters params, String myId) {
        mListener = listener;
        pcParams = params;
        this.myId = myId;
        PeerConnectionFactory.initializeAndroidGlobals(listener, true, true,
                params.videoCodecHwAcceleration);
        factory = new PeerConnectionFactory();
        MessageHandler messageHandler = new MessageHandler();

        try {
            client = IO.socket(host);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
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
        iceServers.add(new PeerConnection.IceServer("turn:numb.viagenie.ca:3478","noble@live.in", "Nogetch12!"));
        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));
        pcConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
    }

    /**
     * Call this method in Activity.onPause()
     */
    public void onPause() {
       // if (videoSource != null) videoSource.stop();
    }

    /**
     * Call this method in Activity.onResume()
     */
    public void onResume() {
       // if (videoSource != null) videoSource.restart();
    }

    /**
     * Call this method in Activity.onDestroy()
     */
    public void onDestroy() {
        for (Peer peer : peers.values()) {
            peer.pc.dispose();
        }
// videoSource.dispose();
        factory.dispose();
        if(pc!=null)pc.dispose();

        if(localMS!=null)localMS.dispose();
        if(client!=null) {
            client.disconnect();
            client.close();
        }
        android.os.Process.killProcess(android.os.Process.myPid());

    }

    public void stopVideo() {
        //videoSource.stop();
    }

    private int findEndPoint() {
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
        setCamera();
        try {
            JSONObject message = new JSONObject();
            message.put("name", name);
            client.emit("readyToStream", message);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void transmitChat(JSONObject message) {
        client.emit("chat", message);
    }

    public void removeVideo(String otherID) throws JSONException{
        JSONObject message = new JSONObject();
        message.put("other", otherID);
        client.emit("removeVideo", message);
    }

    public String client_id() {
        return client.id();
    }

    public void startClient(String to, String type, JSONObject payload) throws JSONException {
        Log.d("minhminh", "vao ham moi tao");
        JSONObject message = new JSONObject();
        message.put("to", to);
        message.put("type", type);
        message.put("payload", payload);
        client.emit("startclient", message);
    }

    private void setCamera() {
        localMS = factory.createLocalMediaStream("ARDAMS");
        /*if (pcParams.videoCallEnabled) {
            MediaConstraints videoConstraints = new MediaConstraints();
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxHeight", Integer.toString(pcParams.videoHeight)));
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxWidth", Integer.toString(pcParams.videoWidth)));
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxFrameRate", Integer.toString(pcParams.videoFps)));
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("minFrameRate", Integer.toString(pcParams.videoFps)));

         //   videoSource = factory.createVideoSource(getVideoCapturer(), videoConstraints);
         //   localMS.addTrack(factory.createVideoTrack("ARDAMSv0", videoSource));
        }*/

        AudioSource audioSource = factory.createAudioSource(new MediaConstraints());
        localMS.addTrack(factory.createAudioTrack("ARDAMSa0", audioSource));

        mListener.onLocalStream(localMS);
    }
/*
    private VideoCapturer getVideoCapturer() {
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
}