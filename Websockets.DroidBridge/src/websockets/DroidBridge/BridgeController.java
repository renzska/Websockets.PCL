
/**
 * Created by Nicholas Ventimiglia on 11/27/2015.
 * nick@avariceonline.com
 * <p/>
 * Android Websocket bridge application. Beacause Mono Networking sucks.
 * Unity talks with BridgeClient (java) and uses a C Bridge to raise events.
 * .NET Methods <-->  BridgeClient (Java / NDK) <--->  Websocket (Java)
 */
package websockets.DroidBridge;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okio.ByteString;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class BridgeController {

    private WebSocket mConnection;
    private static String TAG = "websockets";

    //MUST BE SET
    public BridgeProxy proxy;
    Handler mainHandler;


    public BridgeController() {
        Log.d(TAG, "ctor");
        mainHandler   = new Handler(Looper.getMainLooper());
    }

    // connect websocket
    public void Open(final String wsuri, final String protocol) {
        Log("BridgeController:Open");

        try {
            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();

             Request request = new Request.Builder()
                    .url(wsuri)
                    .build();

            OkHttpClient client = enableTls12OnPreLollipop(clientBuilder).build();

            WebSocket webSocket = client.newWebSocket(request, new okhttp3.WebSocketListener() {

                        @Override
                        public void onOpen(WebSocket webSocket, okhttp3.Response response) {
                            // connection succeeded
                            RaiseOpened();
                            mConnection = webSocket;
                        }

                        @Override
                        public void onMessage(WebSocket webSocket, String text) {
                            // text message received
                            try {
                                RaiseMessage(text);
                            } catch (Exception ex) {
                                RaiseError("Error onMessage - " + ex.getMessage());
                            }
                        }

                        @Override
                        public void onMessage(WebSocket webSocket, ByteString bytes) {
                            // binary message received
                            try {
                                RaiseMessage(bytes.hex());

                            } catch (Exception ex) {
                                RaiseError("Error onMessage - " + ex.getMessage());
                            }
                        }

                        @Override
                        public void onClosed(WebSocket webSocket, int code, String reason) {
                            // no more messages and the connection should be released
                            RaiseClosed();
                            mConnection = null;
                        }

                        @Override
                        public void onFailure(WebSocket webSocket, Throwable t, okhttp3.Response response) {
                            // unexpected error
                            RaiseError(response.message());
                            RaiseClosed();
                            mConnection = null;
                        }
                    }
                );

            // Trigger shutdown of the dispatcher's executor so this process can exit cleanly.
            client.dispatcher().executorService().shutdown();

        }catch (Exception ex){
            Error("Open "+ex.getMessage());
        }

    }

    public void Close() {

        try
        {
            if(mConnection == null)
                return;
            mConnection.close(1000,"CLOSE_NORMAL");

        }catch (Exception ex){
            RaiseError("Error Close - "+ex.getMessage());
        }
    }

    // send a message
    public void Send(final String message) {
        try
        {
            if(mConnection == null)
                return;
                 Log.d(TAG, message);

            mConnection.send(message);
        }catch (Exception ex){
            RaiseError("Error Send - "+ex.getMessage());
        }
    }

    private void Log(final String args) {
        Log.d(TAG, args);
        RaiseLog(args);
    }

    private void Error(final String args) {
        Log.e(TAG, args);
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
            Error("Failed to Raise");
        }
    }

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

    public static OkHttpClient.Builder enableTls12OnPreLollipop(OkHttpClient.Builder client) {
        if (android.os.Build.VERSION.SDK_INT >= 16 && android.os.Build.VERSION.SDK_INT < 22) {
            try {
                SSLContext sc = SSLContext.getInstance("TLSv1.2");
                sc.init(null, null, null);
                client.sslSocketFactory(new Tls12SocketFactory(sc.getSocketFactory()),
                                new X509TrustManager() {
                                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[]{}; }
                                }
                );

                okhttp3.ConnectionSpec cs = new okhttp3.ConnectionSpec.Builder(okhttp3.ConnectionSpec.MODERN_TLS)
                        .tlsVersions(okhttp3.TlsVersion.TLS_1_2)
                        .build();

                List<okhttp3.ConnectionSpec> specs = new ArrayList<>();
                specs.add(cs);
                specs.add(okhttp3.ConnectionSpec.COMPATIBLE_TLS);
                specs.add(okhttp3.ConnectionSpec.CLEARTEXT);

                client.connectionSpecs(specs);
            } catch (Exception exc) {
                Log.e("OkHttpTLSCompat", "Error while setting TLS 1.2", exc);
            }
        }

        return client;
    }
}
