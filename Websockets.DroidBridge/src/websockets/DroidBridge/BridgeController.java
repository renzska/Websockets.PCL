
/**
 * Created by Nicholas Ventimiglia on 11/27/2015.
 * nick@avariceonline.com
 * <p/>
 * Android Websocket bridge application. Beacause Mono Networking sucks.
 * Unity talks with BridgeClient (java) and uses a C Bridge to raise events.
 * .NET Methods <-->  BridgeClient (Java / NDK) <--->  Websocket (Java)
 */
package websockets.DroidBridge;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import okhttp3.*;
import okio.ByteString;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.TrustManager;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static okhttp3.OkHttpClient.*;

public class BridgeController {

    private static final int NORMAL_CLOSURE_STATUS = 1000;

    private WebSocket mConnection;
    private static String TAG = "[Android.Bridge.Websockets]";

    //MUST BE SET
    public BridgeProxy proxy;
    Handler mainHandler;


    public BridgeController() {
        Log.d(TAG, "ctor");
        mainHandler   = new Handler(Looper.getMainLooper());
    }

    // connect websocket
    public void Open(final String wsuri, final String protocol, final Map<String, String> headers) {
        Log("[BridgeController] Open Start");

        try {
            Headers headerBuild = Headers.of(headers);

            Builder clientBuilder = new Builder()
                    .readTimeout(0, TimeUnit.MILLISECONDS);

            Request request = new Request.Builder()
                    .url(wsuri)
                    .headers(headerBuild)
                    .build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
                enableTls12(clientBuilder);
            }

            OkHttpClient client = clientBuilder.build();

            WebSocket webSocket = client.newWebSocket(request, new okhttp3.WebSocketListener() {

                        /**
                         * Invoked when a web socket has been accepted by the remote peer and may begin transmitting
                         * messages.
                         */
                        @Override
                        public void onOpen(WebSocket webSocket, okhttp3.Response response) {
                            Log("[BridgeController OkHttp] onOpen: " + response.message());
                            // connection succeeded
                            mConnection = webSocket;
                            RaiseOpened();
                        }

                        /** Invoked when a text (type {@code 0x1}) message has been received. */
                        @Override
                        public void onMessage(WebSocket webSocket, String text) {
                            Log("[BridgeController OkHttp] onMessage (text): " + text);
                            try {
                                RaiseMessage(text);
                            } catch (Exception ex) {
                                RaiseError("Error onMessage - " + ex.getMessage());
                            }
                        }

                        /** Invoked when a binary (type {@code 0x2}) message has been received. */
                        @Override
                        public void onMessage(WebSocket webSocket, ByteString bytes) {
                            Log("[BridgeController OkHttp] onMessage (bytes): " + bytes.hex());
                            try {
                                /*RaiseData(bytes);*/
                            } catch (Exception ex) {
                                RaiseError("Error onMessage - " + ex.getMessage());
                            }
                        }

                        /** Invoked when the peer has indicated that no more incoming messages will be transmitted. */
                        @Override
                        public void onClosing(WebSocket webSocket, int code, String reason)
                        {
                            Log("[BridgeController OkHttp] onClosing");
                            //mConnection.close(NORMAL_CLOSURE_STATUS, null);
                        }

                        /**
                         * Invoked when both peers have indicated that no more messages will be transmitted and the
                         * connection has been successfully released. No further calls to this listener will be made.
                         */
                        @Override
                        public void onClosed(WebSocket webSocket, int code, String reason) {
                            Log("[BridgeController OkHttp] onClosed: " + reason);
                            // no more messages and the connection should be released
                            mConnection = null;
                            RaiseClosed();
                        }

                        /**
                         * Invoked when a web socket has been closed due to an error reading from or writing to the
                         * network. Both outgoing and incoming messages may have been lost. No further calls to this
                         * listener will be made.
                         */
                        @Override
                        public void onFailure(WebSocket webSocket, Throwable t, okhttp3.Response response) {
                            // unexpected error
                            Log("[BridgeController OkHttp] onFailure");
                            //ignore errors, fire closed, which will require a reconnect on the other side of things
                            //webSocket.close(NORMAL_CLOSURE_STATUS, null);
                            //RaiseError(response.message());
                            mConnection = null;
                            RaiseClosed();
                        }


                    }
                );

            // Trigger shutdown of the dispatcher's executor so this process can exit cleanly.
            client.dispatcher().executorService().shutdown();

        }catch (Exception ex){
            Error("Open "+ex.getMessage());
        }

        Log("[BridgeController] Open Finish");
    }

    public void Close() {
        Log("[BridgeController] Close Start");
        try
        {
            if(mConnection != null) {
                Log("[BridgeController] Close - mConnection != null");
                mConnection.close(NORMAL_CLOSURE_STATUS, null);
                mConnection = null;
            }
            else
            {
                Log("[BridgeController] Close - mConnection == null");
            }


        }catch (Exception ex){
            RaiseError("Error Close - "+ex.getMessage());
        }
        Log("[BridgeController] Close Finish");
    }

    // send a message
    public void Send(final String message) {
        Log("[BridgeController] Send Start");
        try
        {
            if(mConnection == null)
            {
                Log("[BridgeController] Send Start: mConnection == null");
                return;
            }
            else
            {
                Log("[BridgeController] Send Start: Before mConnection.send(message)");
                mConnection.send(message);
                Log("[BridgeController] Send Start: After mConnection.send(message)");
            }
        }catch (Exception ex){
            RaiseError("Error Send - " + ex.getMessage());
        }
        Log("[BridgeController] Send Finish");
    }

    private void Log(final String args) {
        Log.d(TAG, args);
        RaiseLog(args);
    }

    private void Error(final String args) {
        Log.d(TAG, args);
        RaiseError(String.format("Error: %s", args));
    }

    private void RaiseOpened() {
      try{
          if(proxy != null)
              proxy.RaiseOpened();
      }catch(Exception ex){
          RaiseClosed();
          Error("Failed to Open");
      }
    }

    private void RaiseClosed() {
        try{
            if(proxy != null)
                proxy.RaiseClosed();
        }catch(Exception ex){
            //Don't try to close as it just tried to close and failed and will keep hitting this over and over
            // RaiseClosed();
            Error("Failed to Close");
        }
    }

    private void RaiseMessage(String message) {
        try{
            if(proxy != null)
                proxy.RaiseMessage(message);
        }catch(Exception ex){
            RaiseClosed();
            Error("Failed to Raise Message");
        }
    }

    /*private void RaiseData(ByteString data) {
        try{
            if(proxy != null)
                proxy.RaiseData(data);
        }catch(Exception ex){
            RaiseClosed();
            Error("Failed to Raise Data");
        }
    }*/

    private void RaiseLog(String message) {
        try{
            if(proxy != null)
                proxy.RaiseLog(message);
        }catch(Exception ex){
            RaiseClosed();
            Error("Failed to Log");
        }
    }

    private void RaiseError(String message) {
        try{
            if(proxy != null)
                proxy.RaiseError(message);
        }catch(Exception ex){
            RaiseClosed();
            Error("Failed to Error");
        }
    }

    /**
     * Enable TLS on the OKHttp builder by setting a custom SocketFactory
     */
    private static OkHttpClient.Builder enableTls12(OkHttpClient.Builder client) {
        Log.i(TAG, "Enabling HTTPS compatibility mode");
        try {

            client.sslSocketFactory(new TLSSocketFactory(), new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[]{}; }
            });

            ConnectionSpec cs = new ConnectionSpec.Builder(okhttp3.ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_1)
                    .build();

            List<ConnectionSpec> specs = new ArrayList<>();
            specs.add(cs);
            specs.add(ConnectionSpec.COMPATIBLE_TLS);
            specs.add(ConnectionSpec.CLEARTEXT);

            client.connectionSpecs(specs);
        } catch (Exception exc) {
            Log.e(TAG, "Error while setting TLS", exc);
        }
        return client;
    }
}
