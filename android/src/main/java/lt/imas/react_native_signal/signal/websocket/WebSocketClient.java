package lt.imas.react_native_signal.signal.websocket;

import org.whispersystems.libsignal.InvalidMessageException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Random;

import lt.imas.react_native_signal.signal.ProtocolStorage;
import lt.imas.react_native_signal.signal.websocket.message.ProtobufWebSocketMessageFactory;
import lt.imas.react_native_signal.signal.websocket.message.WebSocketMessage;
import lt.imas.react_native_signal.signal.websocket.message.WebSocketMessageFactory;
import lt.imas.react_native_signal.signal.websocket.protobuf.TextSecure;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okio.ByteString;
import timber.log.Timber;

public class WebSocketClient extends okhttp3.WebSocketListener {

    private OkHttpClient client;
    private String url;
    private WebSocketMessageFactory factory;
    private final int NORMAL_CLOSURE_STATUS = 1000;
    private WebSocket webSocket;
    private boolean webSocketRunning = false;

    public WebSocketClient(String url) {
        this.url = url;
        this.factory = new ProtobufWebSocketMessageFactory();
        this.client = new OkHttpClient.Builder().build();
    }

    public void start() {
        Request request = new Request.Builder().url(url).build();
        webSocket = client.newWebSocket(request, this);
        client.dispatcher().executorService().shutdown();
    }

    public void stop() {
        webSocket.close(NORMAL_CLOSURE_STATUS, "stop");
    }

    public boolean isRunning() {
        return webSocketRunning;
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        Timber.d("WEBSOCKET onMessage: %s", text);
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        try {
            WebSocketMessage message = factory.parseMessage(com.google.protobuf.ByteString.copyFrom(bytes.toByteArray()));
            if (message.getType() == WebSocketMessage.Type.REQUEST_MESSAGE) {
                com.google.protobuf.ByteString body = message.getRequestMessage().getBody();
                if (message.getRequestMessage().getVerb().equals("PUT") && message.getRequestMessage().getPath().equals("/api/v1/message")){

                }
                sendResponse(message.getRequestMessage().getRequestId(), 200, "OK");
            } else if (message.getType() == WebSocketMessage.Type.RESPONSE_MESSAGE) {
                Timber.d("WEBSOCKET onMessage ByteString RESPONSE:\n%s\n%s\n%s\n%s\n%s",
                    message.getResponseMessage().getRequestId(),
                    message.getResponseMessage().getStatus(),
                    message.getResponseMessage().getHeaders(),
                    message.getResponseMessage().getMessage(),
                    message.getResponseMessage().getBody() != null ? new String(message.getResponseMessage().getBody(), "UTF-8") : ""
                );
            } else {
                Timber.w("Received websocket message of unknown type: %s", message.getType());
            }
        } catch (InvalidMessageException | UnsupportedEncodingException e) {
            Timber.e("onMessage FAILED");
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        webSocketRunning = true;
        try {
            Timber.d("WEBSOCKET onOpen: %s", response.body().string());
        } catch (IOException e) {
            e.printStackTrace();
        }
        sendRequest(new Random().nextLong(), "GET", "/v1/keepalive");
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        webSocketRunning = false;
        webSocket.close(NORMAL_CLOSURE_STATUS, null);
        Timber.d("WEBSOCKET onMessage: %s (%s)", reason, code);
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        Timber.e(t);
        try {
            Timber.d("WEBSOCKET onFailure: %s", response.body().string());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendRequest(long id, String verb, String path) {
        Timber.d("WEBSOCKET sendRequest:\n%s\n%s\n%s", id, verb, path);
        WebSocketMessage request = factory.createRequest(id, verb, path, null, null);
        webSocket.send(ByteString.of(ByteBuffer.wrap(request.toByteArray())));
    }

    public void sendResponse(long id, int code, String message) {
        sendResponse(id, code, message, null, null);
    }

    public void sendResponse(long id, int code, String message, List<String> headers, byte[] body) {
        try {
            Timber.d("WEBSOCKET sendResponse:\n%s\n%s\n%s\n%s\n%s", id, code, message, headers, body != null && body.length != 0 ? new String(body, "UTF-8") : null);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        WebSocketMessage response = factory.createResponse(id, code, message, headers, body);
        webSocket.send(ByteString.of(ByteBuffer.wrap(response.toByteArray())));
    }

}
