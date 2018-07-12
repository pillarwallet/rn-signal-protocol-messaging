package lt.imas.react_native_signal.signal;

import android.content.Context;

import com.facebook.react.bridge.Promise;
import com.google.protobuf.ByteString;

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
import org.whispersystems.libsignal.protocol.SignalProtos;
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.ByteUtil;
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

import static lt.imas.react_native_signal.RNSignalClientModule.ERR_NATIVE_FAILED;
import static lt.imas.react_native_signal.RNSignalClientModule.ERR_SERVER_FAILED;

public class SignalClient {
    private SignalServer signalServer;
    private Context context;

    public SignalClient(Context context, SignalServer signalServer) {
        this.context = context;
        this.signalServer = signalServer;
    }

    public void registerPreKeys(final Promise promise, int start, int count){
        ProtocolStorage signalProtocolStore = new ProtocolStorage(context);
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
        } catch (JSONException e) {
            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
            e.printStackTrace();
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
                SignalServer.call(SignalServer.URL_KEYS, "PUT", requestJSON, new Callback() {
                    @Override
                    public void onFailure(Call call, final IOException e) {
                        SignalServer.mainThreadCallback(new Runnable() {
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
                        SignalServer.mainThreadCallback(new Runnable() {
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
        } catch (JSONException e) {
            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
            e.printStackTrace();
        }
    }

    public void requestPreKeys(final String username, final Promise promise){
        SignalServer.call(SignalServer.URL_KEYS + "/" + username + "/1", "GET", new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                SignalServer.mainThreadCallback(new Runnable() {
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
                SignalServer.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject responseJSONO = serverResponse.getResponseJSONObject();
                            JSONArray devicesJSONA = responseJSONO.getJSONArray("devices");
                            JSONObject firstDevice = devicesJSONA.getJSONObject(0);

                            ProtocolStorage mySignalProtocolStore = new ProtocolStorage(context);
                            SessionBuilder sessionBuilder = new SessionBuilder(mySignalProtocolStore, new SignalProtocolAddress(username, 1));

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
                        } catch (JSONException e) {
                            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                            e.printStackTrace();
                        } catch (UntrustedIdentityException e) {
                            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                            e.printStackTrace();
                        } catch (InvalidKeyException e) {
                            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                            e.printStackTrace();
                        } catch (IOException e) {
                            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                            e.printStackTrace();
                        }
                    }
                });
            }
        });
    }

    public void checkRemotePreKeys(final Promise promise){
        SignalServer.call(SignalServer.URL_KEYS, "GET", new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                SignalServer.mainThreadCallback(new Runnable() {
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
                SignalServer.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        int preKeyCount = serverResponse.getResponseJSONObject().optInt("count", 0);
                        if (preKeyCount <= 10) {
                            int count = 100 - preKeyCount;
                            ProtocolStorage protocolStorage = new ProtocolStorage(context);
                            registerPreKeys(promise, protocolStorage.getLastPreKeyIndex(), count);
                        } else {
                            promise.resolve("ok");
                        }
                    }
                });
            }
        });
    }

    public void registerAccount(String username, final Promise promise){
        ProtocolStorage signalProtocolStore = new ProtocolStorage(context);
        if (signalProtocolStore.isLocalRegistered()){
            promise.resolve("ok");
            return;
        }
        JSONObject requestJSON = new JSONObject();
        int registrationId = KeyHelper.generateRegistrationId(true);
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
        SignalServer.call(SignalServer.URL_ACCOUNTS, "PUT", requestJSON, new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                SignalServer.mainThreadCallback(new Runnable() {
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
                SignalServer.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        registerPreKeys(promise, 0, 100);
                    }
                });
            }
        });
    }

    public void getContactMessages(final String username, final Promise promise, final boolean decodeAndSave) {
        SignalServer.call(SignalServer.URL_MESSAGES, "GET", new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                SignalServer.mainThreadCallback(new Runnable() {
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
                SignalServer.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONArray messagesJSONA = serverResponse.getResponseJSONObject().getJSONArray("messages");
                            JSONArray receivedMessagesJSONA = new JSONArray();
                            JSONObject unreadJSONO = new JSONObject();
                            ProtocolStorage signalProtocolStore = new ProtocolStorage(context);
                            MessageStorage messageStorage = new MessageStorage(context);
                            for (int i=0;i<messagesJSONA.length(); i++) {
                                try {
                                    JSONObject messageJSONO = messagesJSONA.getJSONObject(i);
                                    String messageString = messageJSONO.getString("message");
                                    SignalProtocolAddress address = new SignalProtocolAddress(messageJSONO.getString("source"), 1);
                                    if (username.equals(address.getName()) && signalProtocolStore.containsSession(address)) {
                                        int unreadCount = unreadJSONO.optInt(username, 0);
                                        unreadJSONO.put(username, unreadCount+1);
                                        JSONObject newMessageJSONO = new JSONObject();
                                        if (decodeAndSave) {
                                            long serverTimestamp = messageJSONO.optLong("timestamp", 0);
                                            if (messageString != null && !messageString.isEmpty()){
                                                byte[] messageBytes = null;
                                                SessionCipher sessionCipher = new SessionCipher(signalProtocolStore, address);
                                                try {
                                                    PreKeySignalMessage signalMessage = new PreKeySignalMessage(Base64.decode(messageString));
                                                    messageBytes = sessionCipher.decrypt(signalMessage);
                                                } catch (LegacyMessageException | InvalidKeyIdException | UntrustedIdentityException | DuplicateMessageException | InvalidVersionException | InvalidMessageException | InvalidKeyException e) {
                                                    SignalMessage signalMessage = new SignalMessage(Base64.decode(messageString));
                                                    messageBytes = sessionCipher.decrypt(signalMessage);
                                                }
                                                if (messageBytes != null){
                                                    String messageBodyString = new String(messageBytes, "UTF-8");
                                                    int currentUnixTime = Integer.parseInt(String.valueOf(System.currentTimeMillis()/1000L));
                                                    newMessageJSONO.put("content", messageBodyString);
                                                    newMessageJSONO.put("username", address.getName());
                                                    newMessageJSONO.put("device", address.getDeviceId());
                                                    newMessageJSONO.put("serverTimestamp", serverTimestamp);
                                                    newMessageJSONO.put("savedTimestamp", currentUnixTime);
                                                    messageStorage.storeMessage(address.getName(), newMessageJSONO);
                                                    receivedMessagesJSONA.put(newMessageJSONO);
                                                }
                                            }
                                            signalServer.call(SignalServer.URL_MESSAGES + "/" + address.getName() + "/" + serverTimestamp, "DELETE", null, null, true);
                                        }
                                    }
                                } catch (JSONException e) {
                                    promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                                    e.printStackTrace();
                                } catch (IOException e) {
                                    promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                                    e.printStackTrace();
                                } catch (DuplicateMessageException e) {
                                    promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                                    e.printStackTrace();
                                } catch (UntrustedIdentityException e) {
                                    promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                                    e.printStackTrace();
                                } catch (LegacyMessageException e) {
                                    promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                                    e.printStackTrace();
                                } catch (InvalidMessageException e) {
                                    promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                                    e.printStackTrace();
                                }
                            }
                            JSONObject promiseJSONO = new JSONObject();
                            promiseJSONO.put("unreadCount", unreadJSONO);
                            if (receivedMessagesJSONA.length() != 0) promiseJSONO.put("messages", receivedMessagesJSONA);
                            promise.resolve(promiseJSONO.toString());
                        } catch (NoSessionException e) {
                            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                            e.printStackTrace();
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
            public void onFailure(Call call, IOException e) {
                promise.reject(ERR_NATIVE_FAILED, e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final ServerResponse serverResponse = new ServerResponse(response);
                SignalServer.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        final int timestamp = serverResponse.getResponseJSONObject().optInt("timestamp", 0);
                        ProtocolStorage signalProtocolStore = new ProtocolStorage(context);
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
                            SignalServer.call(SignalServer.URL_MESSAGES + "/" + username, "PUT", requestJSONO, new Callback() {
                                @Override
                                public void onFailure(Call call, final IOException e) {
                                    SignalServer.mainThreadCallback(new Runnable() {
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
                                    SignalServer.mainThreadCallback(new Runnable() {
                                        @Override
                                        public void run() {
                                            MessageStorage messageStorage = new MessageStorage(context);
                                            JSONObject messageJSONO = new JSONObject();
                                            try {
                                                messageJSONO.put("content", messageString);
                                                messageJSONO.put("username", signalServer.username);
                                                messageJSONO.put("device", 1);
                                                messageJSONO.put("serverTimestamp", timestamp);
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
                        } catch (JSONException e) {
                            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                            e.printStackTrace();
                        } catch (UntrustedIdentityException e) {
                            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                            e.printStackTrace();
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                    }
                });
            };
        });
    }
}
