package lt.imas.react_native_signal;

import android.content.Context;

import com.facebook.react.bridge.Promise;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.SessionState;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.KeyHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import lt.imas.react_native_signal.helpers.SampleResponse;
import lt.imas.react_native_signal.signal.MessageStorage;
import lt.imas.react_native_signal.signal.ProtocolStorage;
import lt.imas.react_native_signal.signal.SignalClient;
import lt.imas.react_native_signal.signal.SignalServer;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AddContactTests {

    private static class MemoryProtocolStorage extends ProtocolStorage {
        HashMap<String, String> memoryStorage = new HashMap<>();

        public MemoryProtocolStorage() {
            super("/");
        }

        @Override
        public void writeToStorageFile(String fileName, String data) {
            memoryStorage.put(fileName, data);
        }

        @Override
        public String readFromStorage(String fileName) {
            return memoryStorage.get(fileName);
        }
    }

    private class SignalServerExtended extends SignalServer {
        public SignalServerExtended(String host, String accessToken, Context context) {
            super(host, accessToken, context);
        }

        @Override
        public String getFullApiUrl(String targetUrl) {
            return signalWebServerMock.url(targetUrl).toString();
        }

        @Override
        public OkHttpClient.Builder initHttpClientBuilder() {
            return new OkHttpClient().newBuilder();
        }

        @Override
        public boolean isNetworkAvailable() {
            return true;
        }

        @Override
        public void mainThreadCallback(Runnable task) {
            task.run();
        }
    }

    static private MemoryProtocolStorage protocolStorage;
    static private MockWebServer signalWebServerMock;

    private SignalServerExtended signalServerMock;
    private Promise promiseMock;
    private SignalClient signalClient;
    private SignalProtocolAddress receiverAddress = new SignalProtocolAddress("receiver-username-1", 1);

    @BeforeClass
    public static void Setup_Before_Class() throws IOException, InvalidKeyException{
        signalWebServerMock = new MockWebServer();
        signalWebServerMock.start();
        try {
            IdentityKeyPair identityKeyPair = KeyHelper.generateIdentityKeyPair();
            SignedPreKeyRecord signedPreKey = KeyHelper.generateSignedPreKey(identityKeyPair, 0);
            String senderUsername = "sender-username";
            protocolStorage = new MemoryProtocolStorage();
            protocolStorage.storeLocalUsername(senderUsername);
            protocolStorage.storeLocalRegistrationId(1234);
            protocolStorage.storeSignalingKey("signaling-key");
            protocolStorage.storeSignalResetVersion(1);
            protocolStorage.storeSignedPreKey(signedPreKey.getId(), signedPreKey);
            protocolStorage.storeIdentityKeyPair(identityKeyPair);
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
    }

    @Before
    public void Setup_Before() {
        SignalServerExtended signalServer = new SignalServerExtended(signalWebServerMock.getHostName(), "access-token", mock(Context.class));
        signalServerMock = spy(signalServer);
        promiseMock = mock(Promise.class);
        signalClient = new SignalClient(signalServerMock, protocolStorage, mock(MessageStorage.class), 0);
    }

    @Test
    public void Should_Save_Contact_Pre_Key() throws InterruptedException {
        String callUrl = String.format("%s/%s/%s", signalClient.URL_KEYS, receiverAddress.getName(), receiverAddress.getDeviceId());
        JSONObject preKeyResponseBodyJSONO = SampleResponse.getByPath(callUrl).optJSONObject("body");
        signalWebServerMock.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("{\"timestamp\": 1551278268}"));
        signalWebServerMock.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(preKeyResponseBodyJSONO.toString()));
        signalClient.requestPreKeys(receiverAddress.getName(), null, null, promiseMock);
        signalWebServerMock.takeRequest(); // remove enqueued timestamp response mock
        Thread.sleep(100); // session bundle processing takes some time in background while on test
        verify(signalServerMock).call(eq(callUrl), eq("GET"), any(Callback.class));
        verify(promiseMock).resolve(eq("ok"));
        assertTrue(protocolStorage.containsSession(receiverAddress));
    }

    @AfterClass
    static public void Setup_After_Class() throws IOException {
        signalWebServerMock.shutdown();
    }
}
