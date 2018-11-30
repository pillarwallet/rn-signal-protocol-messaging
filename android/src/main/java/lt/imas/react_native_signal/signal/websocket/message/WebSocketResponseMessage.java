package lt.imas.react_native_signal.signal.websocket.message;

import java.util.Map;
import java.util.Optional;

public interface WebSocketResponseMessage {

    public long               getRequestId();
    public int                getStatus();
    public String             getMessage();
    public Map<String,String> getHeaders();
    public byte[]             getBody();

}
