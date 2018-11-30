package lt.imas.react_native_signal.signal.websocket.message;

import com.google.protobuf.ByteString;

import org.whispersystems.libsignal.InvalidMessageException;

import java.util.List;

public interface WebSocketMessageFactory {

    public WebSocketMessage parseMessage(ByteString serialized)
            throws InvalidMessageException;

    public WebSocketMessage createRequest(Long requestId,
                                          String verb, String path,
                                          List<String> headers,
                                          byte[] body);

    public WebSocketMessage createRequest(Long requestId,
                                          String verb, String path);

    public WebSocketMessage createResponse(long requestId, int status, String message,
                                           List<String> headers,
                                           byte[] body);

}
