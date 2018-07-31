package lt.imas.react_native_signal;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;

import org.json.JSONArray;
import org.json.JSONObject;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.SessionRecord;

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

    private SignalServer signalServer;
    private SignalClient signalClient;

    private String username;
    private String password;
    private String host;

    public RNSignalClientModule(ReactApplicationContext reactContext) {
        super(reactContext);
        Timber.plant(new Timber.DebugTree());
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
            this.username = config.getString("username");
            this.password = config.getString("password");
            this.host = config.getString("host");
            signalServer = new SignalServer(this.host, this.username, this.password);
            signalClient = new SignalClient(getReactApplicationContext(), signalServer);
            ProtocolStorage protocolStorage = new ProtocolStorage(getReactApplicationContext());
            if (protocolStorage.getLocalUsername().equals(this.username) && protocolStorage.isLocalRegistered()){
                signalClient.checkRemotePreKeys(promise);
            } else {
                MessageStorage messageStorage = new MessageStorage(getReactApplicationContext());
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
        ProtocolStorage protocolStorage = new ProtocolStorage(getReactApplicationContext());
        MessageStorage messageStorage = new MessageStorage(getReactApplicationContext());
        protocolStorage.deleteAll();
        messageStorage.deleteAll();
        promise.resolve("ok");
    }

    @ReactMethod
    public void addContact(String username, Promise promise){
        ProtocolStorage protocolStorage = new ProtocolStorage(getReactApplicationContext());
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
        ProtocolStorage protocolStorage = new ProtocolStorage(getReactApplicationContext());
        MessageStorage messageStorage = new MessageStorage(getReactApplicationContext());
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
        MessageStorage messageStorage = new MessageStorage(getReactApplicationContext());
        JSONArray messagesJSONA = messageStorage.getContactMessages(username);
        ArrayList<JSONObject> messagesList = new ArrayList<JSONObject>();
        for (int i = 0; i < messagesJSONA.length(); i++) messagesList.add(messagesJSONA.optJSONObject(i));
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
        MessageStorage messageStorage = new MessageStorage(getReactApplicationContext());
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
