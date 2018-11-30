package lt.imas.react_native_signal.signal.websocket.message;

public interface WebSocketMessage {

    public enum Type {
        UNKNOWN_MESSAGE,
        REQUEST_MESSAGE,
        RESPONSE_MESSAGE
    }

    public Type                     getType();
    public WebSocketRequestMessage getRequestMessage();
    public WebSocketResponseMessage getResponseMessage();
    public byte[]                   toByteArray();

}
