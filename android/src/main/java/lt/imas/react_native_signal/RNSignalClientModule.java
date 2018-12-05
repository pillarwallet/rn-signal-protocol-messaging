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

import lt.imas.react_native_signal.signal.LogSender;
import lt.imas.react_native_signal.signal.MessageStorage;
import lt.imas.react_native_signal.signal.SignalClient;
import lt.imas.react_native_signal.signal.SignalServer;
import lt.imas.react_native_signal.signal.ProtocolStorage;
import static lt.imas.react_native_signal.signal.PromiseRejectCode.ERR_WRONG_CONFIG;
import timber.log.Timber;

public class RNSignalClientModule extends ReactContextBaseJavaModule {
    private LogSender logSender = LogSender.getInstance();

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
        try {
            if (!config.hasKey("host")
                    || !config.hasKey("username")
                    || !config.hasKey("password")) {
                promise.reject(ERR_WRONG_CONFIG, "Wrong config provided.");
            } else {
                logSender.init(config);

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
        } catch (Throwable e) {
            logSender.reportError(e);
            promise.reject(e);
        }
    }

    @ReactMethod
    public void registerAccount(final Promise promise){
        try {
            signalClient.registerAccount(username, promise);
        } catch (Throwable e) {
            logSender.reportError(e);
            promise.reject(e);
        }
    }

    @ReactMethod
    public void resetAccount(Promise promise){
        try {
            protocolStorage.deleteAll();
            messageStorage.deleteAll();
            promise.resolve("ok");
        } catch (Throwable e) {
            logSender.reportError(e);
            promise.reject(e);
        }
    }

    @ReactMethod
    public void addContact(String username, String userId, String userConnectionAccessToken, Promise promise){
        try {
            SignalProtocolAddress address = new SignalProtocolAddress(username, 1);
            if (!protocolStorage.containsSession(address)){
                signalClient.requestPreKeys(username, userId, userConnectionAccessToken, promise);
            } else {
                promise.resolve("ok");
            }
        } catch (Throwable e) {
            logSender.reportError(e);
            promise.reject(e);
        }
    }

    @ReactMethod
    public void deleteContact(String username, Promise promise){
        try {
            SignalProtocolAddress address = new SignalProtocolAddress(username, 1);
            protocolStorage.removeIdentity(address);
            protocolStorage.deleteSession(address);
            messageStorage.deleteAllContactMessages(username);
            signalClient.deleteContactPendingMessages(username, "*", promise);
            promise.resolve("ok");
        } catch (Throwable e) {
            logSender.reportError(e);
            promise.reject(e);
        }
    }

    @ReactMethod
    public void deleteContactMessages(String username, String tag, Promise promise){
        try {
            messageStorage.deleteContactMessages(username, tag);
            signalClient.deleteContactPendingMessages(username, tag, promise);
        } catch (Throwable e) {
            logSender.reportError(e);
            promise.reject(e);
        }
    }

    @ReactMethod
    public void receiveNewMessagesByContact(String username, String tag, final Promise promise){
        try {
            signalClient.getContactMessages(username, tag, promise, true);
        } catch (Throwable e) {
            logSender.reportError(e);
            promise.reject(e);
        }
    }

    @ReactMethod
    public void getMessagesByContact(String username, String tag, final Promise promise){
        try {
            JSONArray messagesJSONA = messageStorage.getContactMessages(username, tag);
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
            promise.resolve(new JSONArray(messagesList).toString());
        } catch (Throwable e) {
            logSender.reportError(e);
            promise.reject(e);
        }
    }

    @ReactMethod
    public void getUnreadMessagesCount(String tag, final Promise promise){
        try {
            signalClient.getContactMessages("", tag, promise, false);
        } catch (Throwable e) {
            logSender.reportError(e);
            promise.reject(e);
        }
    }

    @ReactMethod
    public void getExistingMessages(String tag, final Promise promise){
        try {
            promise.resolve(messageStorage.getExistingMessages(tag).toString());
        } catch (Throwable e) {
            logSender.reportError(e);
            promise.reject(e);
        }
    }

    @ReactMethod
    public void sendSilentMessageByContact(String tag, ReadableMap params, final Promise promise) {
        try {
            signalClient.sendMessage(params.getString("username"), params.getString("message"), params.getString("userId"), params.getString("userConnectionAccessToken"), tag, true, promise);
        } catch (Throwable e) {
            logSender.reportError(e);
            promise.reject(e);
        }
    }

    @ReactMethod
    public void sendMessageByContact(String tag, ReadableMap params, final Promise promise) {
        try {
            signalClient.sendMessage(params.getString("username"), params.getString("message"), params.getString("userId"), params.getString("userConnectionAccessToken"), tag, false, promise);
        } catch (Throwable e) {
            logSender.reportError(e);
            promise.reject(e);
        }
    }

    @ReactMethod
    public void setFcmId(String fcmId, final Promise promise) {
        try {
            signalClient.setFcmId(fcmId, promise);
        } catch (Throwable e) {
            logSender.reportError(e);
            promise.reject(e);
        }
    }
}
