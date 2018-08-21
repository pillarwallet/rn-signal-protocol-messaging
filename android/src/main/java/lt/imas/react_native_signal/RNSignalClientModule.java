package lt.imas.react_native_signal;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;

import org.json.JSONArray;
import org.json.JSONObject;
import org.whispersystems.libsignal.SignalProtocolAddress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import lt.imas.react_native_signal.signal.MessageStorage;
import lt.imas.react_native_signal.signal.SignalClient;
import lt.imas.react_native_signal.signal.SignalServer;
import lt.imas.react_native_signal.signal.ProtocolStorage;
import timber.log.Timber;

public class RNSignalClientModule extends ReactContextBaseJavaModule {
    public static final String ERR_WRONG_CONFIG = "ERR_WRONG_CONFIG";
    public static final String ERR_SERVER_FAILED = "ERR_SERVER_FAILED";
    public static final String ERR_NATIVE_FAILED = "ERR_NATIVE_FAILED";

    private SignalClient signalClient;
    private ProtocolStorage protocolStorage;
    private MessageStorage messageStorage;

    private String username;

    public RNSignalClientModule(ReactApplicationContext reactContext) {
        super(reactContext);
        Timber.plant(new Timber.DebugTree());

        String absolutePath = getReactApplicationContext().getFilesDir().getAbsolutePath();
        protocolStorage = new ProtocolStorage(absolutePath);
        messageStorage = new MessageStorage(absolutePath);
    }

    @Override
    public String getName() {
        return "SignalClient";
    }

    @ReactMethod
    public void init(ReadableMap config, Promise promise){
        if (!config.hasKey("host")
                || !config.hasKey("username")
                || !config.hasKey("password")) {
            promise.reject(ERR_WRONG_CONFIG, "Wrong config provided.");
        } else {
            String password = config.getString("password");
            String host = config.getString("host");

            username = config.getString("username");

            SignalServer signalServer = new SignalServer(host, username, password);
            signalClient = new SignalClient(signalServer, protocolStorage, messageStorage);

            if (protocolStorage.getLocalUsername().equals(username) && protocolStorage.isLocalRegistered()){
                signalClient.checkRemotePreKeys(promise);
            } else {
                protocolStorage.deleteAll();
                messageStorage.deleteAll();
                registerAccount(promise);
            }
        }
    }

    @ReactMethod
    public void registerAccount(final Promise promise){
        signalClient.registerAccount(username, promise);
    }

    @ReactMethod
    public void resetAccount(Promise promise){
        protocolStorage.deleteAll();
        messageStorage.deleteAll();
        promise.resolve("ok");
    }

    @ReactMethod
    public void addContact(String username, Promise promise){
        SignalProtocolAddress address = new SignalProtocolAddress(username, 1);
        if (!protocolStorage.containsSession(address)){
            signalClient.requestPreKeys(username, promise);
//            protocolStorage.deleteSession(address);
        } else {
            promise.resolve("ok");
        }
    }

    @ReactMethod
    public void deleteContact(String username, Promise promise){
        SignalProtocolAddress address = new SignalProtocolAddress(username, 1);
        protocolStorage.deleteSession(address);
        messageStorage.deleteContactMessages(username);
        promise.resolve("ok");
    }

    @ReactMethod
    public void receiveNewMessagesByContact(String username, final Promise promise){
        signalClient.getContactMessages(username, promise, true);
    }

    @ReactMethod
    public void getChatByContact(String username, final Promise promise){
        JSONArray messagesJSONA = messageStorage.getContactMessages(username);
        ArrayList<JSONObject> messagesList = new ArrayList<>();
        for (int i = 0; i < messagesJSONA.length(); i++)
            messagesList.add(messagesJSONA.optJSONObject(i));
        Collections.sort(messagesList, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject o1, JSONObject o2) {
                long ts1 = o1.optLong("savedTimestamp");
                long ts2 = o2.optLong("savedTimestamp");
                return Long.compare(ts2, ts1);
            }
        });
        messagesJSONA = new JSONArray(messagesList);
        promise.resolve(messagesJSONA.toString());
    }

    @ReactMethod
    public void getUnreadMessagesCount(final Promise promise){
        signalClient.getContactMessages("", promise, false);
    }

    @ReactMethod
    public void getExistingChats(final Promise promise){
        JSONArray chatsJSONA = messageStorage.getExistingChats();
        promise.resolve(chatsJSONA.toString());
    }

    @ReactMethod
    public void sendMessageByContact(String username, String message, final Promise promise) {
        signalClient.sendMessage(username, message, promise);
    }

    @ReactMethod
    public void setFcmId(String fcmId, final Promise promise) {
        signalClient.setFcmId(fcmId, promise);
    }
}
