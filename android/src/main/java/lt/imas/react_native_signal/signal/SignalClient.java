package lt.imas.react_native_signal.signal;

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

public class SignalClient {
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
        JSONObject requestJSON = new JSONObject();
        IdentityKeyPair identityKeyPair = signalProtocolStore.getIdentityKeyPair();
        if (signalProtocolStore.getIdentityKeyPair() == null) {
            identityKeyPair = KeyHelper.generateIdentityKeyPair();
            signalProtocolStore.storeIdentityKeyPair(identityKeyPair);
        }
        List<PreKeyRecord> preKeys = KeyHelper.generatePreKeys(start, count);
        PreKeyRecord lastResortKey = null;
        if (signalProtocolStore.containsPreKey(Medium.MAX_VALUE)) {
            lastResortKey = signalProtocolStore.loadPreKey(Medium.MAX_VALUE);
        } else {
            ECKeyPair keyPair = Curve.generateKeyPair();
            lastResortKey = new PreKeyRecord(Medium.MAX_VALUE, keyPair);
        }
        byte[] bytes = new byte[52];
        new SecureRandom().nextBytes(bytes);
        JSONArray preKeysJSONA = new JSONArray();
        try {
            for (PreKeyRecord preKeyRecord : preKeys){
                preKeysJSONA.put(new JSONObject()
                        .put("keyId", preKeyRecord.getId())
                        .put("publicKey", Base64.encodeBytes(preKeyRecord.getKeyPair().getPublicKey().serialize()))
                );
                signalProtocolStore.storePreKey(preKeyRecord.getId(), preKeyRecord);
            }
        } catch (Throwable e) {
            Timber.e(e);
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
                requestJSON.put("lastResortKey", new JSONObject()
                        .put("keyId", lastResortKey.getId())
                        .put("publicKey", Base64.encodeBytes(lastResortKey.getKeyPair().getPublicKey().serialize())
                        ));
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
                                promise.resolve("ok");
                            }
                        });
                    }
                });
            } else {
                promise.reject(ERR_NATIVE_FAILED, "lastResortKey failed");
            }
        } catch (Throwable e) {
            Timber.e(e);
            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
        }
    }

    public void requestPreKeys(final String username, final Promise promise){
        signalServer.call(URL_KEYS + "/" + username + "/1", "GET", new Callback() {
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
                        try {
                            JSONObject responseJSONO = serverResponse.getResponseJSONObject();
                            JSONArray devicesJSONA = responseJSONO.getJSONArray("devices");
                            JSONObject firstDevice = devicesJSONA.getJSONObject(0);

                            SessionBuilder sessionBuilder = new SessionBuilder(signalProtocolStore, new SignalProtocolAddress(username, 1));

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
                            promise.resolve("ok");
                        } catch (JSONException
                                | UntrustedIdentityException
                                | InvalidKeyException
                                | IOException e) {
                            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                            e.printStackTrace();
                        }
                    }
                });
            }
        });
    }

    public void checkRemotePreKeys(final Promise promise){
        signalServer.call(URL_KEYS, "GET", new Callback() {
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
                        int preKeyCount = serverResponse.getResponseJSONObject().optInt("count", 0);
                        if (preKeyCount <= 10) {
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
            promise.resolve("ok");
            return;
        }
        JSONObject requestJSON = new JSONObject();
        int registrationId = KeyHelper.generateRegistrationId(true);
        signalProtocolStore.storeLocalUsername(username);
        signalProtocolStore.storeLocalRegistrationId(registrationId);
        byte[] bytes = new byte[52];
        new SecureRandom().nextBytes(bytes);
        String signalingKey = Base64.encodeBytes(bytes);
        try {
            requestJSON.put("signalingKey", signalingKey);
            requestJSON.put("fetchesMessages", true);
            requestJSON.put("registrationId", registrationId);
            requestJSON.put("name", username);
            requestJSON.put("voice", false);
        } catch (JSONException e) {
            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
            e.printStackTrace();
        }
        signalServer.call(URL_ACCOUNTS, "PUT", requestJSON, new Callback() {
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
                        registerPreKeys(promise, 0, 100);
                    }
                });
            }
        });
    }

    public void getContactMessages(final String username, final Promise promise, final boolean decodeAndSave) {
        signalServer.call(URL_MESSAGES, "GET", new Callback() {
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
                        try {
                            JSONArray messagesJSONA = serverResponse.getResponseJSONObject().getJSONArray("messages");
                            JSONArray receivedMessagesJSONA = new JSONArray();
                            JSONObject unreadJSONO = new JSONObject();
                            for (int i=0;i<messagesJSONA.length(); i++) {
                                try {
                                    JSONObject messageJSONO = messagesJSONA.getJSONObject(i);
                                    String messageString = messageJSONO.getString("message");
                                    String source = messageJSONO.getString("source");
                                    SignalProtocolAddress address = new SignalProtocolAddress(source, 1);
                                    int unreadCount = unreadJSONO.optInt(source, 0);
                                    unreadJSONO.put(source, unreadCount+1);
                                    if (username.equals(address.getName())
                                            && signalProtocolStore.containsSession(address)
                                            && decodeAndSave) {
                                        long serverTimestamp = messageJSONO.optLong("timestamp", 0);
                                        if (messageString != null && !messageString.isEmpty()){
                                            byte[] decodeMessageString = Base64.decode(messageString);
                                            byte[] messageBytes = null;
                                            SessionCipher sessionCipher = new SessionCipher(signalProtocolStore, address);

                                            try {
                                                messageBytes = sessionCipher.decrypt(new SignalMessage(decodeMessageString));
                                            } catch (InvalidMessageException | DuplicateMessageException | LegacyMessageException
                                                    | NoSessionException | UntrustedIdentityException e) {
                                                Timber.e(e);
                                            }

                                            if (messageBytes == null){
                                                try {
                                                    messageBytes = sessionCipher.decrypt(new PreKeySignalMessage(decodeMessageString));
                                                } catch (DuplicateMessageException | LegacyMessageException | InvalidMessageException
                                                        | InvalidKeyIdException | InvalidKeyException | UntrustedIdentityException
                                                        | InvalidVersionException e) {
                                                    Timber.e(e);
                                                }
                                            }

                                            if (messageBytes != null){
                                                String messageBodyString = new String(messageBytes, "UTF-8");
                                                int currentUnixTime = Integer.parseInt(String.valueOf(System.currentTimeMillis()/1000L));
                                                JSONObject newMessageJSONO = new JSONObject()
                                                        .put("content", messageBodyString)
                                                        .put("username", address.getName())
                                                        .put("device", address.getDeviceId())
                                                        .put("serverTimestamp", serverTimestamp)
                                                        .put("savedTimestamp", currentUnixTime);
                                                messageStorage.storeMessage(address.getName(), newMessageJSONO);
                                                receivedMessagesJSONA.put(newMessageJSONO);
                                            }
                                        }

                                        signalServer.call(
                                                URL_MESSAGES + "/" + address.getName() + "/" + serverTimestamp,
                                                "DELETE",
                                                null,
                                                new Callback() {
                                                    @Override
                                                    public void onFailure(Call call, IOException e) {
                                                        Timber.e(e);
                                                    }

                                                    @Override
                                                    public void onResponse(Call call, Response res) {
                                                        Timber.d(String.format("DELETE messages: %s, %s", res.code(), res.message()));
                                                    }
                                                },
                                                true
                                        );
                                    }
                                } catch (JSONException | IOException e) {
                                    promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                                    e.printStackTrace();
                                    return;
                                }
                            }
                            JSONObject promiseJSONO = new JSONObject();
                            promiseJSONO.put("unreadCount", unreadJSONO);
                            if (receivedMessagesJSONA.length() != 0) promiseJSONO.put("messages", receivedMessagesJSONA);
                            promise.resolve(promiseJSONO.toString());
                        } catch (JSONException e) {
                            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                            e.printStackTrace();
                        }
                    }
                });
            }
        });
    }

    public void sendMessage(final String username, final String messageString, final Promise promise) {
        signalServer.requestServerTimestamp(new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                signalServer.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        Timber.e("Send Message failed: %s", e);
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
                        SignalProtocolAddress address = new SignalProtocolAddress(username, 1);
                        SessionCipher sessionCipher = new SessionCipher(signalProtocolStore, address);
                        JSONObject requestJSONO = new JSONObject();
                        try {
                            CiphertextMessage message = sessionCipher.encrypt(messageString.getBytes("UTF-8"));
                            JSONArray messagesJSONA = new JSONArray();
                            JSONObject messageJSONO = new JSONObject();
                            messageJSONO.put("type", 1);
                            messageJSONO.put("destination", username);
                            messageJSONO.put("content", ""); //Base64.encodeBytes(String.valueOf(signalProtocolStore.getLocalRegistrationId()).getBytes()));
                            messageJSONO.put("timestamp", timestamp);
                            messageJSONO.put("destinationDeviceId", 1);
                            messageJSONO.put("destinationRegistrationId", ""); //sessionCipher.getRemoteRegistrationId());
                            messageJSONO.put("body", Base64.encodeBytes(message.serialize()));
                            messagesJSONA.put(messageJSONO);
                            requestJSONO.put("messages", messagesJSONA);
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
                                            JSONObject messageJSONO = new JSONObject();
                                            try {
                                                messageJSONO.put("content", messageString);
                                                messageJSONO.put("username", signalServer.username);
                                                messageJSONO.put("device", 1);
                                                messageJSONO.put("serverTimestamp", (long)(timestamp) * 1000);
                                                messageJSONO.put("savedTimestamp", timestamp);
                                                messageStorage.storeMessage(username, messageJSONO);
                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                            }
                                            promise.resolve("ok");
                                        }
                                    });
                                }
                            }, true, timestamp);
                        } catch (JSONException
                                | IllegalArgumentException
                                | UntrustedIdentityException
                                | UnsupportedEncodingException e) {
                            e.printStackTrace();
                            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                        }
                    }
                });
            };
        });
    }

    public void setFcmId(String fcmId, final Promise promise) {
        if (fcmId != null && !fcmId.isEmpty()) {
            JSONObject dataJSONO = new JSONObject();
            try {
                dataJSONO.put("gcmRegistrationId", fcmId);
                signalServer.call(URL_GCM, "PUT", dataJSONO, new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        promise.reject(ERR_NATIVE_FAILED, e.getMessage());
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
                e.printStackTrace();
                promise.reject(ERR_NATIVE_FAILED, e.getMessage());
            }
        } else {
            promise.resolve("ok");
        }
    }
}
