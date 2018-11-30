package lt.imas.react_native_signal.signal.websocket.message;

import java.util.Map;
import java.util.Optional;

import com.google.protobuf.ByteString;

public interface WebSocketRequestMessage {

    public String             getVerb();
    public String             getPath();
    public Map<String,String> getHeaders();
    public ByteString         getBody();
    public long               getRequestId();
    public boolean            hasRequestId();

}