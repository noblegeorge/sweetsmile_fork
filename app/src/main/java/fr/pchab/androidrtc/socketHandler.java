package fr.pchab.androidrtc;

/**
 * Created by noblegeorge on 07/02/17.
 */

import io.socket.client.Socket;


public class socketHandler {
    private static Socket socket;



    public static synchronized Socket getSocket(){
        return socket;
    }

    public static synchronized void setSocket(Socket socket){
        socketHandler.socket = socket;
    }
}