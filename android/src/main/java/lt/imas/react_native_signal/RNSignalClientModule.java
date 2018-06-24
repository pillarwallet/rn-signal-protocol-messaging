package lt.imas.react_native_signal;

import android.content.Context;
import android.content.SharedPreferences;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.whispersystems.libsignal.DecryptionCallback;
import org.whispersystems.libsignal.DuplicateMessageException;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.libsignal.LegacyMessageException;
import org.whispersystems.libsignal.SessionBuilder;
import org.whispersystems.libsignal.SessionCipher;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.UntrustedIdentityException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.protocol.PreKeySignalMessage;
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.KeyHelper;
import org.whispersystems.libsignal.util.Medium;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import lt.imas.react_native_signal.helpers.Base64;
import lt.imas.react_native_signal.signal.ServerApiClient;
import lt.imas.react_native_signal.signal.ServerResponse;
import lt.imas.react_native_signal.signal.ProtocolStorage;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import timber.log.Timber;

public class RNSignalClientModule extends ReactContextBaseJavaModule {
    private ServerApiClient signalServer;
    private final String ERR_WRONG_CONFIG = "ERR_WRONG_CONFIG";
    private final String ERR_SERVER_FAILED = "ERR_SERVER_FAILED";
    private final String ERR_NATIVE_FAILED = "ERR_NATIVE_FAILED";
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
            signalServer = new ServerApiClient(this.username, this.password);
            promise.resolve("ok");
        }
    }

    @ReactMethod
    public void reset(){

    }

    @ReactMethod
    public void getKeys(final Promise promise){
        signalServer.call(ServerApiClient.URL_KEYS, "GET", new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                ServerApiClient.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        promise.reject(ERR_SERVER_FAILED, e.getMessage());
                        Timber.d("Signal server failed: %s", e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final ServerResponse serverResponse = new ServerResponse(response);
                ServerApiClient.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        Timber.d(serverResponse.getResponseJSONObject().toString());
                        promise.resolve("ok");
                    }
                });
            }
        });
    }

    @ReactMethod
    public void getMessages(final Promise promise){
        signalServer.call(ServerApiClient.URL_MESSAGES, "GET", new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                ServerApiClient.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        promise.reject(ERR_SERVER_FAILED, e.getMessage());
                        Timber.d("Signal server failed: %s", e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final ServerResponse serverResponse = new ServerResponse(response);
                ServerApiClient.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        Timber.d(serverResponse.getResponseJSONObject().toString());
                        promise.resolve("ok");
                    }
                });
            }
        });
    }

    @ReactMethod
    public void sendMessage(String username, String messageString, final Promise promise) {
        long currentUnixTime = System.currentTimeMillis() / 1000L;
        ProtocolStorage signalProtocolStore = new ProtocolStorage(getReactApplicationContext());
        SignalProtocolAddress address = new SignalProtocolAddress(username, 1);
        SessionCipher sessionCipher = new SessionCipher(signalProtocolStore, address);
        JSONObject requestJSONO = new JSONObject();
        JSONObject messageBodyJSONO = new JSONObject();
        try {
            messageBodyJSONO.put("type", "message");
            messageBodyJSONO.put("body", new JSONObject().put("message", messageString));
            CiphertextMessage message = sessionCipher.encrypt(messageBodyJSONO.toString().getBytes("UTF-8"));
            JSONArray messagesJSONA = new JSONArray();
            JSONObject messageJSONO = new JSONObject();
            messageJSONO.put("type", 1);
            messageJSONO.put("destination", username);
            messageJSONO.put("content", Base64.encodeBytesWithoutPadding(String.valueOf(signalProtocolStore.getLocalRegistrationId()).getBytes()));
            messageJSONO.put("timestamp", currentUnixTime);
            messageJSONO.put("destinationDeviceId", 1);
            messageJSONO.put("destinationRegistrationId", sessionCipher.getRemoteRegistrationId());
            messageJSONO.put("body", Base64.encodeBytesWithoutPadding(message.serialize()));
            messagesJSONA.put(messageJSONO);
            requestJSONO.put("messages", messagesJSONA);
            signalServer.call(ServerApiClient.URL_MESSAGES + "/" + username, "PUT", requestJSONO, new Callback() {
                @Override
                public void onFailure(Call call, final IOException e) {
                    ServerApiClient.mainThreadCallback(new Runnable() {
                        @Override
                        public void run() {
                            promise.reject(ERR_SERVER_FAILED, e.getMessage());
                            Timber.d("Signal server failed: %s", e.getMessage());
                        }
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    final ServerResponse serverResponse = new ServerResponse(response);
                    ServerApiClient.mainThreadCallback(new Runnable() {
                        @Override
                        public void run() {
                            Timber.d(serverResponse.getResponseJSONObject().toString());
                            promise.resolve("ok");
                        }
                    });
                }
            });
        } catch (JSONException e) {
            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
            e.printStackTrace();
        } catch (UntrustedIdentityException e) {
            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
            e.printStackTrace();
        }
    }

    @ReactMethod
    public void requestPreKeys(final String username, final Promise promise){
        signalServer.call(ServerApiClient.URL_KEYS + "/" + username + "/1", "GET", new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                ServerApiClient.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        promise.reject(ERR_SERVER_FAILED, e.getMessage());
                        Timber.d("Signal server failed: %s", e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final ServerResponse serverResponse = new ServerResponse(response);
                ServerApiClient.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject responseJSONO = serverResponse.getResponseJSONObject();
                            JSONArray devicesJSONA = responseJSONO.getJSONArray("devices");
                            JSONObject firstDevice = devicesJSONA.getJSONObject(0);

                            ProtocolStorage mySignalProtocolStore = new ProtocolStorage(getReactApplicationContext());
                            SessionBuilder sessionBuilder = new SessionBuilder(mySignalProtocolStore, new SignalProtocolAddress(username, 1));

                            JSONObject preKeyJSONO = firstDevice.getJSONObject("preKey");
                            String preKeyPublicString = preKeyJSONO.getString("publicKey");
                            ECPublicKey preKeyPublic = Curve.decodePoint(Base64.decodeWithoutPadding(preKeyPublicString), 0);

                            JSONObject signedPreKeyJSONO = firstDevice.getJSONObject("signedPreKey");
                            String signedPreKeySignature = signedPreKeyJSONO.getString("signature");
                            String signedPreKeyPublicString = signedPreKeyJSONO.getString("publicKey");
                            ECPublicKey signedPreKeyPublic = Curve.decodePoint(Base64.decodeWithoutPadding(signedPreKeyPublicString), 0);

                            String identityKeyString = responseJSONO.getString("identityKey");
                            IdentityKey identityKey = new IdentityKey(Base64.decodeWithoutPadding(identityKeyString), 0);

                            PreKeyBundle preKeyBundle = new PreKeyBundle(
                                firstDevice.getInt("registrationId"),
                                firstDevice.getInt("deviceId"),
                                preKeyJSONO.getInt("keyId"),
                                preKeyPublic,
                                signedPreKeyJSONO.getInt("keyId"),
                                signedPreKeyPublic,
                                Base64.decodeWithoutPadding(signedPreKeySignature),
                                identityKey
                            );
                            promise.resolve("ok");
                            sessionBuilder.process(preKeyBundle);
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

    @ReactMethod
    public void updatePreKeys(final Promise promise){
        // TODO: check prekeys count and update if needed
    }

    @ReactMethod
    public void registerPreKeys(final Promise promise){
        ProtocolStorage signalProtocolStore = new ProtocolStorage(getReactApplicationContext());
        JSONObject requestJSON = new JSONObject();
        IdentityKeyPair identityKeyPair = KeyHelper.generateIdentityKeyPair();
        signalProtocolStore.storeIdentityKeyPair(identityKeyPair);
        List<PreKeyRecord> preKeys = KeyHelper.generatePreKeys(0, 100);
        PreKeyRecord lastResortKey = null;
        if (signalProtocolStore.containsPreKey(Medium.MAX_VALUE)) {
            lastResortKey = signalProtocolStore.loadPreKey(Medium.MAX_VALUE);
        } else {
            ECKeyPair keyPair = Curve.generateKeyPair();
            lastResortKey = new PreKeyRecord(Medium.MAX_VALUE, keyPair);
        }
        SignedPreKeyRecord signedPreKey;
        byte[] bytes = new byte[52];
        new SecureRandom().nextBytes(bytes);
        JSONArray preKeysJSONA = new JSONArray();
        try {
            for (PreKeyRecord preKeyRecord : preKeys){
                preKeysJSONA.put(new JSONObject()
                    .put("keyId", preKeyRecord.getId())
                    .put("publicKey", Base64.encodeBytesWithoutPadding(preKeyRecord.getKeyPair().getPublicKey().serialize()))
                );
                signalProtocolStore.storePreKey(preKeyRecord.getId(), preKeyRecord);
            }
        } catch (JSONException e) {
            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
            e.printStackTrace();
        }
        try {
            signedPreKey = KeyHelper.generateSignedPreKey(identityKeyPair, 0);
            signalProtocolStore.storeSignedPreKey(signedPreKey.getId(), signedPreKey);
            assert lastResortKey != null;
            requestJSON.put("lastResortKey", new JSONObject()
                .put("keyId", lastResortKey.getId())
                .put("publicKey", Base64.encodeBytesWithoutPadding(lastResortKey.getKeyPair().getPublicKey().serialize())
            ));
            requestJSON.put("preKeys", preKeysJSONA);
            requestJSON.put("identityKey", Base64.encodeBytesWithoutPadding(identityKeyPair.getPublicKey().serialize()));
            requestJSON.put("signedPreKey", new JSONObject()
                    .put("keyId", signedPreKey.getId())
                    .put("publicKey",  Base64.encodeBytesWithoutPadding(signedPreKey.getKeyPair().getPublicKey().serialize()))
                    .put("signature", Base64.encodeBytesWithoutPadding(signedPreKey.getSignature()))
            );
            signalServer.call(ServerApiClient.URL_KEYS, "PUT", requestJSON, new Callback() {
                @Override
                public void onFailure(Call call, final IOException e) {
                    ServerApiClient.mainThreadCallback(new Runnable() {
                        @Override
                        public void run() {
                            promise.reject(ERR_SERVER_FAILED, e.getMessage());
                            Timber.d("Signal server failed: %s", e.getMessage());
                        }
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    final ServerResponse serverResponse = new ServerResponse(response);
                    ServerApiClient.mainThreadCallback(new Runnable() {
                        @Override
                        public void run() {
                            Timber.d(serverResponse.getResponseJSONObject().toString());
                            promise.resolve("ok");
                        }
                    });
                }
            });
        } catch (JSONException e) {
            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
            e.printStackTrace();
        }
    }

    @ReactMethod
    public void registerAccount(final Promise promise){
        ProtocolStorage signalProtocolStore = new ProtocolStorage(getReactApplicationContext());
        signalProtocolStore.resetLocal();
        if (signalProtocolStore.isLocalRegistered()) return;
        JSONObject requestJSON = new JSONObject();
        int registrationId = KeyHelper.generateRegistrationId(true);
        signalProtocolStore.storeLocalRegistrationId(registrationId);
        byte[] bytes = new byte[52];
        new SecureRandom().nextBytes(bytes);
        String signalingKey = Base64.encodeBytesWithoutPadding(bytes);
        try {
            requestJSON.put("signalingKey", signalingKey);
            requestJSON.put("fetchesMessages", true);
            requestJSON.put("registrationId", registrationId);
            requestJSON.put("name", this.username);
            requestJSON.put("voice", false);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        signalServer.call(ServerApiClient.URL_ACCOUNTS, "PUT", requestJSON, new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                ServerApiClient.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        promise.reject(ERR_SERVER_FAILED, e.getMessage());
                        Timber.d("Signal server failed: %s", e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final ServerResponse serverResponse = new ServerResponse(response);
                ServerApiClient.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        Timber.d(serverResponse.getResponseJSONObject().toString());
                        registerPreKeys(promise);
                    }
                });
            }
        });
    }

    public void readMessages(final Promise promise) {
        signalServer.call(ServerApiClient.URL_MESSAGES, "GET", new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                ServerApiClient.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        promise.reject(ERR_SERVER_FAILED, e.getMessage());
                        Timber.d("Signal server failed: %s", e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final ServerResponse serverResponse = new ServerResponse(response);
                ServerApiClient.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONArray messagesJSONA = serverResponse.getResponseJSONObject().getJSONArray("messages");
                            JSONObject messageJSONO = messagesJSONA.getJSONObject(messagesJSONA.length()-1);
                            // TODO: go through all message array
                            String messageString = messageJSONO.getString("message");
                            ProtocolStorage signalProtocolStore = new ProtocolStorage(getReactApplicationContext());
                            SignalProtocolAddress address = new SignalProtocolAddress(messageJSONO.getString("source"), 1);
                            SessionCipher sessionCipher = new SessionCipher(signalProtocolStore, address);
                            PreKeySignalMessage signalMessage = new PreKeySignalMessage(Base64.decodeWithoutPadding(messageString));
                            byte[] messageBytes = sessionCipher.decrypt(signalMessage);
                            String messageBodyString = new String(messageBytes, "UTF-8");
                            promise.resolve("ok");
                            // TODO: Store messages and send API delivery report (DELETE remote pending)
                        } catch (JSONException e) {
                            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                            e.printStackTrace();
                        } catch (IOException e) {
                            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                            e.printStackTrace();
                        } catch (InvalidVersionException e) {
                            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                            e.printStackTrace();
                        } catch (InvalidMessageException e) {
                            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                            e.printStackTrace();
                        } catch (DuplicateMessageException e) {
                            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                            e.printStackTrace();
                        } catch (InvalidKeyException e) {
                            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                            e.printStackTrace();
                        } catch (UntrustedIdentityException e) {
                            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                            e.printStackTrace();
                        } catch (InvalidKeyIdException e) {
                            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                            e.printStackTrace();
                        } catch (LegacyMessageException e) {
                            promise.reject(ERR_NATIVE_FAILED, e.getMessage());
                            e.printStackTrace();
                        }
                    }
                });
            }
        });
    }
}
