package lt.imas.react_native_signal.signal;

import android.os.Handler;
import android.os.Looper;

import com.facebook.react.bridge.Promise;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.whispersystems.libsignal.DuplicateMessageException;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.libsignal.LegacyMessageException;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.SessionBuilder;
import org.whispersystems.libsignal.SessionCipher;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.UntrustedIdentityException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.protocol.PreKeySignalMessage;
import org.whispersystems.libsignal.protocol.SignalMessage;
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.KeyHelper;
import org.whispersystems.libsignal.util.Medium;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.util.List;

import lt.imas.react_native_signal.helpers.Base64;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import timber.log.Timber;

import static lt.imas.react_native_signal.signal.PromiseRejectCode.ERR_NATIVE_FAILED;
import static lt.imas.react_native_signal.signal.PromiseRejectCode.ERR_SERVER_FAILED;
import static lt.imas.react_native_signal.signal.PromiseRejectCode.ERR_ADD_CONTACT_FAILED;

public class SignalClient {
    private LogSender logSender = LogSender.getInstance();

    public final String MESSAGE_TYPE_CIPHERTEXT = "1";

    public final String URL_ACCOUNTS = "/v1/accounts";
    public final String URL_KEYS = "/v2/keys";
    public final String URL_MESSAGES = "/v1/messages";
    public final String URL_RECEIPT = "/v1/receipt";
    public final String URL_GCM = "/v1/accounts/gcm";

    private SignalServer signalServer;
    private ProtocolStorage signalProtocolStore;
    private MessageStorage messageStorage;

    public SignalClient(SignalServer signalServer, ProtocolStorage protocolStorage, MessageStorage messageStorage) {
        this.signalServer = signalServer;
        this.signalProtocolStore = protocolStorage;
        this.messageStorage = messageStorage;
    }

    public void registerPreKeys(final Promise promise, int start, int count){
        IdentityKeyPair identityKeyPair = signalProtocolStore.getIdentityKeyPair();
        if (identityKeyPair == null) {
            identityKeyPair = KeyHelper.generateIdentityKeyPair();
            signalProtocolStore.storeIdentityKeyPair(identityKeyPair);
        }
        PreKeyRecord lastResortKey = null;
        if (signalProtocolStore.containsPreKey(Medium.MAX_VALUE)) {
            lastResortKey = signalProtocolStore.loadPreKey(Medium.MAX_VALUE);
        } else {
            ECKeyPair keyPair = Curve.generateKeyPair();
            lastResortKey = new PreKeyRecord(Medium.MAX_VALUE, keyPair);
        }

        JSONArray preKeysJSONA = new JSONArray();
        try {
            List<PreKeyRecord> preKeys = KeyHelper.generatePreKeys(start, count);
            for (PreKeyRecord preKeyRecord : preKeys){
                preKeysJSONA.put(new JSONObject()
                        .put("keyId", preKeyRecord.getId())
                        .put("publicKey", Base64.encodeBytes(preKeyRecord.getKeyPair().getPublicKey().serialize()))
                );
                signalProtocolStore.storePreKey(preKeyRecord.getId(), preKeyRecord);
            }
        } catch (Throwable e) {
            logSender.reportError(e);
            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
            return;
        }
        try {
            SignedPreKeyRecord signedPreKey = signalProtocolStore.loadSignedPreKey(0);
            if (signedPreKey == null){
                signedPreKey = KeyHelper.generateSignedPreKey(identityKeyPair, 0);
                signalProtocolStore.storeSignedPreKey(signedPreKey.getId(), signedPreKey);
            }
            if (lastResortKey != null) {
                JSONObject requestJSON = new JSONObject();
                requestJSON.put("lastResortKey", new JSONObject()
                    .put("keyId", lastResortKey.getId())
                    .put("publicKey", Base64.encodeBytes(lastResortKey.getKeyPair().getPublicKey().serialize()))
                );
                requestJSON.put("preKeys", preKeysJSONA);
                requestJSON.put("identityKey", Base64.encodeBytes(identityKeyPair.getPublicKey().serialize()));
                requestJSON.put("signedPreKey", new JSONObject()
                    .put("keyId", signedPreKey.getId())
                    .put("publicKey", Base64.encodeBytes(signedPreKey.getKeyPair().getPublicKey().serialize()))
                    .put("signature", Base64.encodeBytes(signedPreKey.getSignature()))
                );
                signalServer.call(URL_KEYS, "PUT", requestJSON, new Callback() {
                    @Override
                    public void onFailure(Call call, final IOException e) {
                        signalServer.mainThreadCallback(new Runnable() {
                            @Override
                            public void run() {
                                promise.reject(ERR_SERVER_FAILED, e.getMessage());
                                logSender.reportError(e);
                            }
                        });
                    }

                    @Override
                    public void onResponse(Call call, Response response) {
                        signalServer.mainThreadCallback(new Runnable() {
                            @Override
                            public void run() {
                                promise.resolve("ok");
                            }
                        });
                    }
                });
            } else {
                promise.reject(ERR_NATIVE_FAILED, "lastResortKey failed");
            }
        } catch (Throwable e) {
            logSender.reportError(e);
            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
        }
    }

    public void requestPreKeys(final String username, final String userId, final String userConnectionAccessToken, final Promise promise, final Runnable callback){
        String url = String.format("%s/%s/1?userId=%s&userConnectionAccessToken=%s", URL_KEYS, username, userId, userConnectionAccessToken);
        signalServer.call(url, "GET", new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                signalServer.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        promise.reject(ERR_SERVER_FAILED, e.getMessage());
                        logSender.reportError(e);
                    }
                });
            }

            @Override
            public void onResponse(Call call, final Response response) {
                final ServerResponse serverResponse = new ServerResponse(response);
                signalServer.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        if (response.code() == 404){
                            promise.reject(ERR_ADD_CONTACT_FAILED, String.format("User %s doesn't exist.", username));
                            return;
                        }
                        try {
                            JSONObject responseJSONO = serverResponse.getResponseJSONObject();
                            JSONArray devicesJSONA = responseJSONO.optJSONArray("devices");
                            if (devicesJSONA == null || devicesJSONA.length() == 0){
                                // remote user haven't provided any keys to Signal back-end or didn't complete Signal server registration
                                promise.reject(ERR_ADD_CONTACT_FAILED, String.format("User %s doesn't exist.", username));
                                return;
                            }
                            JSONObject firstDevice = devicesJSONA.getJSONObject(0);

                            SignalProtocolAddress address = new SignalProtocolAddress(username, 1);

                            // force delete: anytime when method is requested it will add new Identity Key and new Pre Key
                            signalProtocolStore.removeIdentity(address);
                            signalProtocolStore.deleteSession(address);

                            SessionBuilder sessionBuilder = new SessionBuilder(signalProtocolStore, address);

                            JSONObject preKeyJSONO = firstDevice.getJSONObject("preKey");
                            String preKeyPublicString = preKeyJSONO.getString("publicKey");
                            ECPublicKey preKeyPublic = Curve.decodePoint(Base64.decode(preKeyPublicString), 0);

                            JSONObject signedPreKeyJSONO = firstDevice.getJSONObject("signedPreKey");
                            String signedPreKeySignature = signedPreKeyJSONO.getString("signature");
                            String signedPreKeyPublicString = signedPreKeyJSONO.getString("publicKey");
                            ECPublicKey signedPreKeyPublic = Curve.decodePoint(Base64.decode(signedPreKeyPublicString), 0);

                            String identityKeyString = responseJSONO.getString("identityKey");
                            IdentityKey identityKey = new IdentityKey(Base64.decode(identityKeyString), 0);

                            PreKeyBundle preKeyBundle = new PreKeyBundle(
                                firstDevice.getInt("registrationId"),
                                firstDevice.getInt("deviceId"),
                                preKeyJSONO.getInt("keyId"),
                                preKeyPublic,
                                signedPreKeyJSONO.getInt("keyId"),
                                signedPreKeyPublic,
                                Base64.decode(signedPreKeySignature),
                                identityKey
                            );
                            sessionBuilder.process(preKeyBundle);
                            if (promise != null) promise.resolve("ok");
                            if (callback != null) new Handler(Looper.getMainLooper()).post(callback);
                        } catch (JSONException
                                | UntrustedIdentityException
                                | InvalidKeyException
                                | IOException e) {
                            if (promise != null) promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                            logSender.reportError(e);
                        }
                    }
                });
            }
        });
    }

    public void requestPreKeys(final String username, final String userId, final String userConnectionAccessToken, final Promise promise){
        requestPreKeys(username, userId, userConnectionAccessToken, promise, null);
    }

    public void checkRemotePreKeys(final Promise promise){
        signalServer.call(URL_KEYS, "GET", new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                signalServer.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        promise.reject(ERR_SERVER_FAILED, e.getMessage());
                        logSender.reportError(e);
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) {
                final ServerResponse serverResponse = new ServerResponse(response);
                final int responseCode = response.code();
                signalServer.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        int preKeyCount = serverResponse.getResponseJSONObject().optInt("count", 0);
                        if (preKeyCount <= 10 && responseCode == 200) {
                            int count = 100 - preKeyCount;
                            registerPreKeys(promise, signalProtocolStore.getLastPreKeyIndex(), count);
                        } else {
                            promise.resolve("ok");
                        }
                    }
                });
            }
        });
    }

    public void registerAccount(String username, final Promise promise){
        if (signalProtocolStore.isLocalRegistered()){
            if (signalProtocolStore.getSignalingKey() == null || signalProtocolStore.getSignalingKey().isEmpty()){
                // updating signalingKey on migration to websocket messaging
                String signalingKey = generateSignalingKey();
                signalProtocolStore.storeSignalingKey(signalingKey);
                JSONObject requestJSON = new JSONObject();
                try {
                    requestJSON.put("signalingKey", signalingKey);
                    requestJSON.put("fetchesMessages", true);
                    requestJSON.put("registrationId", signalProtocolStore.getLocalRegistrationId());
                    requestJSON.put("name", signalProtocolStore.getLocalUsername());
                    requestJSON.put("voice", false);
                } catch (JSONException e) {
                    logSender.reportError(e);
                    promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                    return;
                }
                signalServer.call(URL_ACCOUNTS + "/attributes", "PUT", requestJSON, new Callback() {
                    @Override
                    public void onFailure(Call call, final IOException e) {
                        signalServer.mainThreadCallback(new Runnable() {
                            @Override
                            public void run() {
                                promise.reject(ERR_SERVER_FAILED, e.getMessage());
                                logSender.reportError(e);
                            }
                        });
                    }

                    @Override
                    public void onResponse(Call call, Response response) {
                        signalServer.mainThreadCallback(new Runnable() {
                            @Override
                            public void run() {
                                promise.resolve("ok");
                            }
                        });
                    }
                });
                return;
            }
            promise.resolve("ok");
            return;
        }
        JSONObject requestJSON = new JSONObject();
        int registrationId = KeyHelper.generateRegistrationId(true);
        String signalingKey = generateSignalingKey();
        signalProtocolStore.storeLocalUsername(username);
        signalProtocolStore.storeLocalRegistrationId(registrationId);
        signalProtocolStore.storeSignalingKey(signalingKey);
        try {
            requestJSON.put("signalingKey", signalingKey);
            requestJSON.put("fetchesMessages", true);
            requestJSON.put("registrationId", registrationId);
            requestJSON.put("name", username);
            requestJSON.put("voice", false);
        } catch (JSONException e) {
            logSender.reportError(e);
            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
            return;
        }
        signalServer.call(URL_ACCOUNTS, "PUT", requestJSON, new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                signalServer.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        promise.reject(ERR_SERVER_FAILED, e.getMessage());
                        logSender.reportError(e);
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) {
                final ServerResponse serverResponse = new ServerResponse(response);
                signalServer.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        registerPreKeys(promise, 0, 100);
                    }
                });
            }
        });
    }

    public void getContactMessages(final String username, final String messageTag, final Promise promise, final boolean decodeAndSave) {
        signalServer.call(URL_MESSAGES, "GET", new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                signalServer.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        promise.reject(ERR_SERVER_FAILED, e.getMessage());
                        logSender.reportError(e);
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) {
                final ServerResponse serverResponse = new ServerResponse(response);
                signalServer.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONArray messagesJSONA = serverResponse.getResponseJSONObject().optJSONArray("messages");
                            JSONArray receivedMessagesJSONA = new JSONArray();
                            JSONObject unreadJSONO = new JSONObject();
                            if (messagesJSONA != null) {
                                for (int i = 0; i < messagesJSONA.length(); i++) {
                                    try {
                                        JSONObject messageJSONO = messagesJSONA.getJSONObject(i);
                                        String messageString = messageJSONO.getString("message");
                                        String source = messageJSONO.getString("source");
                                        long serverTimestamp = messageJSONO.optLong("timestamp", 0);
                                        String tag = messageJSONO.optString("tag");
                                        if (messageTag.equals(tag)){
                                            if (MESSAGE_TYPE_CIPHERTEXT.equals(messageJSONO.getString("type"))) {
                                                JSONObject unreadSourceJSONO = unreadJSONO.optJSONObject(source);
                                                if (unreadSourceJSONO == null)
                                                    unreadSourceJSONO = new JSONObject();
                                                int unreadCount = unreadSourceJSONO.optInt("count", 0);
                                                unreadCount++;
                                                long latestTimestamp = unreadSourceJSONO.optLong("latest", 0);
                                                latestTimestamp = serverTimestamp > latestTimestamp ? serverTimestamp : latestTimestamp;
                                                unreadSourceJSONO.put("count", unreadCount);
                                                unreadSourceJSONO.put("latest", latestTimestamp);
                                                unreadJSONO.put(source, unreadSourceJSONO);
                                            }

                                            SignalProtocolAddress address = new SignalProtocolAddress(source, 1);

                                            if (username != null
                                                    && username.equals(address.getName())
                                                    && signalProtocolStore.containsSession(address)
                                                    && decodeAndSave) {

                                                boolean duplicate = false;

                                                if (messageString != null && !messageString.isEmpty()) {
                                                    byte[] decodeMessageString = Base64.decode(messageString);
                                                    byte[] messageBytes = null;
                                                    SessionCipher sessionCipher = new SessionCipher(signalProtocolStore, address);

                                                    try {
                                                        messageBytes = sessionCipher.decrypt(new SignalMessage(decodeMessageString));
                                                    } catch (InvalidMessageException | LegacyMessageException | DuplicateMessageException | UntrustedIdentityException e) {
                                                        Timber.e(e);
                                                        duplicate = e.getClass() == DuplicateMessageException.class;
                                                        try {
                                                            messageBytes = sessionCipher.decrypt(new PreKeySignalMessage(decodeMessageString));
                                                        } catch (UntrustedIdentityException e2){
                                                            logSender.send("Captured UntrustedIdentityException, session resets");
                                                            signalProtocolStore.removeIdentity(address);
                                                            Timber.e(e2);
                                                            try {
                                                                messageBytes = sessionCipher.decrypt(new PreKeySignalMessage(decodeMessageString));
                                                            } catch (DuplicateMessageException | LegacyMessageException
                                                                    | InvalidKeyIdException | InvalidMessageException
                                                                    | InvalidVersionException | InvalidKeyException
                                                                    | UntrustedIdentityException e1) {
                                                                Timber.e(e1);
                                                                if (!duplicate) duplicate = e1.getClass() == DuplicateMessageException.class;
                                                            }
                                                        } catch (LegacyMessageException | InvalidMessageException
                                                                | InvalidKeyIdException | InvalidKeyException
                                                                | InvalidVersionException | DuplicateMessageException e3) {
                                                            Timber.e(e3);
                                                            if (!duplicate) duplicate = e3.getClass() == DuplicateMessageException.class;
                                                        }
                                                    }

                                                    if (!duplicate) {
                                                        JSONObject newMessageJSONO = toMessageJSONO(
                                                                messageBytes,
                                                                address.getName(),
                                                                address.getDeviceId(),
                                                                serverTimestamp);

                                                        messageStorage.storeMessage(address.getName(), newMessageJSONO, tag);
                                                        receivedMessagesJSONA.put(newMessageJSONO);
                                                    }

                                                    signalServer.call(
                                                            URL_MESSAGES + "/" + address.getName() + "/" + serverTimestamp,
                                                            "DELETE",
                                                            null,
                                                            new Callback() {
                                                                @Override
                                                                public void onFailure(Call call, IOException e) {
                                                                    logSender.reportError(e);
                                                                }

                                                                @Override
                                                                public void onResponse(Call call, Response res) {
                                                                    Timber.d(String.format("DELETE messages: %s, %s", res.code(), res.message()));
                                                                }
                                                            },
                                                            true
                                                    );
                                                }
                                            }
                                        }
                                    } catch (JSONException | IOException | NoSessionException e) {
                                        promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                                        logSender.reportError(e);
                                        return;
                                    }
                                }
                            }

                            JSONObject promiseJSONO = new JSONObject();
                            promiseJSONO.put("unread", unreadJSONO);
                            if (receivedMessagesJSONA.length() != 0) promiseJSONO.put("messages", receivedMessagesJSONA);
                            promise.resolve(promiseJSONO.toString());
                        } catch (JSONException e) {
                            logSender.reportError(e);
                            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                        }
                    }
                });
            }
        });
    }

    public void deleteContactPendingMessages(final String username, final String messageTag, final Promise promise) {
        signalServer.call(URL_MESSAGES, "GET", new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                signalServer.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        promise.reject(ERR_SERVER_FAILED, e.getMessage());
                        logSender.reportError(e);
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) {
                final ServerResponse serverResponse = new ServerResponse(response);
                signalServer.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONArray messagesJSONA = serverResponse.getResponseJSONObject().optJSONArray("messages");
                            if (messagesJSONA != null) {
                                for (int i = 0; i < messagesJSONA.length(); i++) {
                                    JSONObject messageJSONO = messagesJSONA.getJSONObject(i);
                                    String source = messageJSONO.getString("source");
                                    String tag = messageJSONO.getString("tag");
                                    if (messageTag.equals(tag) || messageTag.equals("*")){
                                        long serverTimestamp = messageJSONO.optLong("timestamp", 0);
                                        if (source != null && source.equals(username)) {
                                            signalServer.call(
                                                    URL_MESSAGES + "/" + username + "/" + serverTimestamp,
                                                    "DELETE",
                                                    null,
                                                    new Callback() {
                                                        @Override
                                                        public void onFailure(Call call, IOException e) {
                                                            logSender.reportError(e);
                                                        }

                                                        @Override
                                                        public void onResponse(Call call, Response res) {
                                                            //
                                                        }
                                                    },
                                                    true
                                            );
                                        }
                                    }
                                }
                            }
                            promise.resolve("ok");
                        } catch (JSONException e) {
                            logSender.reportError(e);
                            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                        }
                    }
                });
            }
        });
    }

    public void sendMessage(final String username, final String messageString, final String userId, final String userConnectionAccessToken, final String messageTag, final boolean silent, final Promise promise) {
        signalServer.requestServerTimestamp(new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                signalServer.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        logSender.reportError(e);
                        promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final ServerResponse serverResponse = new ServerResponse(response);
                signalServer.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        final int timestamp = serverResponse.getResponseJSONObject().optInt("timestamp", 0);
                        try {
                            JSONObject requestJSONO = prepareApiBody(username, messageString, userId, userConnectionAccessToken, messageTag, silent);
                            signalServer.call(URL_MESSAGES + "/" + username, "PUT", requestJSONO, new Callback() {
                                @Override
                                public void onFailure(Call call, final IOException e) {
                                    signalServer.mainThreadCallback(new Runnable() {
                                        @Override
                                        public void run() {
                                            promise.reject(ERR_SERVER_FAILED, e.getMessage());
                                            Timber.d("Signal server failed: %s", e.getMessage());
                                        }
                                    });
                                }

                                @Override
                                public void onResponse(Call call, Response response) {
                                    final ServerResponse serverResponse = new ServerResponse(response);
                                    signalServer.mainThreadCallback(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (serverResponse.getResponseJSONObject() != null
                                                    && serverResponse.getResponseJSONObject().optJSONArray("staleDevices") != null){
                                                // staleDevices found, request new user PreKey and retry message send
                                                Runnable retrySendMessage = new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        sendMessage(username, messageString, userId, userConnectionAccessToken, messageTag, silent, promise);
                                                    }
                                                };
                                                requestPreKeys(username, userId, userConnectionAccessToken, null, retrySendMessage);
                                            } else {
                                                saveSentMessage(messageTag, username, messageString, timestamp, promise);
                                            }
                                        }
                                    });
                                }
                            }, true, timestamp);
                        } catch (JSONException
                                | IllegalArgumentException
                                | UntrustedIdentityException
                                | UnsupportedEncodingException e) {
                            logSender.reportError(e);
                            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                        }
                    }
                });
            };
        });
    }

    public void setFcmId(String fcmId, final Promise promise) {
        if (fcmId != null && !fcmId.isEmpty()) {
            try {
                JSONObject dataJSONO = new JSONObject()
                        .put("gcmRegistrationId", fcmId);
                signalServer.call(URL_GCM, "PUT", dataJSONO, new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                        logSender.reportError(e);
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        signalServer.mainThreadCallback(new Runnable() {
                            @Override
                            public void run() {
                                promise.resolve("ok");
                            }
                        });
                    }
                });
            } catch (JSONException e) {
                logSender.reportError(e);
                promise.reject(ERR_NATIVE_FAILED, e.getMessage());
            }
        } else {
            promise.resolve("ok");
        }
    }

    public JSONObject prepareApiBody(String username, String message, String userId, String userConnectionAccessToken, String tag, boolean silent)
            throws JSONException, UnsupportedEncodingException, UntrustedIdentityException {
        SignalProtocolAddress address = new SignalProtocolAddress(username, 1);
        SessionCipher sessionCipher = new SessionCipher(signalProtocolStore, address);
        JSONObject requestJSONO = new JSONObject();
        CiphertextMessage ciphertextMessage = sessionCipher.encrypt(message.getBytes("UTF-8"));
        JSONArray messagesJSONA = new JSONArray();
        JSONObject messageJSONO = new JSONObject();
        messageJSONO.put("type", 1);
        messageJSONO.put("tag", tag);
        if (userId != null) messageJSONO.put("userId", userId);
        if (userConnectionAccessToken != null) messageJSONO.put("userConnectionAccessToken", userConnectionAccessToken);
        messageJSONO.put("destination", username);
        messageJSONO.put("silent", silent);
        messageJSONO.put("content", ""); //Base64.encodeBytes(String.valueOf(signalProtocolStore.getLocalRegistrationId()).getBytes()));
        messageJSONO.put("destinationDeviceId", 1);
        messageJSONO.put("destinationRegistrationId", sessionCipher.getRemoteRegistrationId());
        messageJSONO.put("body", Base64.encodeBytes(ciphertextMessage.serialize()));
        messagesJSONA.put(messageJSONO);
        requestJSONO.put("messages", messagesJSONA);
        return requestJSONO;
    }

    public void saveSentMessage(String tag, String username, String message, int timestamp, Promise promise){
        JSONObject messageJSONO = new JSONObject();
        try {
            messageJSONO.put("content", message);
            messageJSONO.put("username", signalProtocolStore.getLocalUsername());
            messageJSONO.put("device", 1);
            messageJSONO.put("serverTimestamp", (long) (timestamp) * 1000);
            messageJSONO.put("savedTimestamp", timestamp);
            messageStorage.storeMessage(username, messageJSONO, tag);
            promise.resolve("ok");
        } catch (JSONException e) {
            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
            logSender.reportError(e);
        }
    }

    private String generateSignalingKey() {
        byte[] bytes = new byte[52];
        new SecureRandom().nextBytes(bytes);
        return Base64.encodeBytes(bytes);
    }

    private JSONObject toMessageJSONO(byte[] messageBytes,
                                      String username,
                                      int device,
                                      long serverTimestamp)
            throws JSONException, UnsupportedEncodingException {
        int currentUnixTime = Integer.parseInt(String.valueOf(System.currentTimeMillis()/1000L));
        JSONObject messageJSONO = new JSONObject()
                .put("username", username)
                .put("device", device)
                .put("serverTimestamp", serverTimestamp)
                .put("savedTimestamp", currentUnixTime);

        if (messageBytes == null) {
            messageJSONO
                    .put("content", "\uD83D\uDD12 You cannot read this message.")
                    .put("type", MessageType.WARNING)
                    .put("status", MessageStatus.UNDECRYPTABLE_MESSAGE);
        } else {
            messageJSONO
                    .put("content", new String(messageBytes, "UTF-8"))
                    .put("type", MessageType.MESSAGE);
        }

        return messageJSONO;
    }

    public void decryptSignalMessage(String messageTag, String receivedMessage, Promise promise) {
        try {
            JSONObject messageJSONO = new JSONObject(receivedMessage);
            String messageString = messageJSONO.getString("legacyMessage");
            String source = messageJSONO.getString("source");
            long serverTimestamp = messageJSONO.optLong("timestamp", 0);
            SignalProtocolAddress address = new SignalProtocolAddress(source, 1);
            if (signalProtocolStore.containsSession(address)) {
                boolean duplicate = false;
                if (messageString != null && !messageString.isEmpty()) {
                    byte[] decodeMessageString = Base64.decode(messageString);
                    byte[] messageBytes = null;
                    SessionCipher sessionCipher = new SessionCipher(signalProtocolStore, address);

                    try {
                        messageBytes = sessionCipher.decrypt(new SignalMessage(decodeMessageString));
                    } catch (InvalidMessageException | LegacyMessageException | DuplicateMessageException | UntrustedIdentityException e) {
                        Timber.e(e);
                        duplicate = e.getClass() == DuplicateMessageException.class;
                        try {
                            messageBytes = sessionCipher.decrypt(new PreKeySignalMessage(decodeMessageString));
                        } catch (UntrustedIdentityException e2){
                            logSender.send("Captured UntrustedIdentityException, session resets");
                            signalProtocolStore.removeIdentity(address);
                            Timber.e(e2);
                            try {
                                messageBytes = sessionCipher.decrypt(new PreKeySignalMessage(decodeMessageString));
                            } catch (DuplicateMessageException | LegacyMessageException
                                    | InvalidKeyIdException | InvalidMessageException
                                    | InvalidVersionException | InvalidKeyException
                                    | UntrustedIdentityException e1) {
                                Timber.e(e1);
                                if (!duplicate) duplicate = e1.getClass() == DuplicateMessageException.class;
                            }
                        } catch (LegacyMessageException | InvalidMessageException
                                | InvalidKeyIdException | InvalidKeyException
                                | InvalidVersionException | DuplicateMessageException e3) {
                            Timber.e(e3);
                            if (!duplicate) duplicate = e3.getClass() == DuplicateMessageException.class;
                        }
                    }

                    if (!duplicate) {
                        JSONObject newMessageJSONO = toMessageJSONO(
                                messageBytes,
                                address.getName(),
                                address.getDeviceId(),
                                serverTimestamp);

                        messageStorage.storeMessage(address.getName(), newMessageJSONO, messageTag);
                    }
                }
            }
        } catch (JSONException | IOException | NoSessionException e) {
            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
            logSender.reportError(e);
            return;
        }
        promise.resolve("ok");
    }

    public void deleteSignalMessage(String username, long timestamp){
        signalServer.call(
            URL_MESSAGES + "/" + username + "/" + timestamp,
            "DELETE",
            null,
            new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    logSender.reportError(e);
                }

                @Override
                public void onResponse(Call call, Response res) {
                    //
                }
            },
            true
        );
    }
}
