package lt.imas.react_native_signal.signal;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.json.JSONObject;
import org.web3j.crypto.Hash;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import timber.log.Timber;

public class SignalServer {
    public static final String URL_ACCOUNTS = "/v1/accounts";
    public static final String URL_KEYS = "/v2/keys";
    public static final String URL_MESSAGES = "/v1/messages";
    public static final String URL_ACCOUNTS_BOOTSTRAP = "/v1/accounts/bootstrap";
    public static final String URL_RECEIPT = "/v1/receipt";
    public static String username;
    public static String password;
    public static String host;

    public SignalServer(String host, String username, String password){
        this.host = host;
        this.username = username;
        this.password = password;
    }

    public static OkHttpClient call(String url, String method, JSONObject requestJSONO, Callback responseHandler) {
        return call(url, method, requestJSONO, responseHandler, true);
    }

    public static OkHttpClient call(String url, String method, Callback responseHandler) {
        return call(url, method, new JSONObject(), responseHandler, true);
    }

    public static OkHttpClient requestServerTimestamp(Callback callback) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(getFullApiUrl(URL_ACCOUNTS_BOOTSTRAP)).build();
        client.newCall(request).enqueue(callback);
        return client;
    }

    public static OkHttpClient call(String url, String method, JSONObject requestJSONO, Callback responseHandler, boolean async, int timestamp) {
        OkHttpClient.Builder clientBuilder = new OkHttpClient().newBuilder();

        method = method != null ? method.toLowerCase().trim() : "";

        Request.Builder requestBuilder = new Request.Builder()
                .url(getFullApiUrl(url));

//        apiClient.setSSLSocketFactory(MySSLSocketFactory.getFixedSocketFactory());
//        apiClient.setConnectTimeout(5000);
//        apiClient.setMaxRetriesAndTimeout(5, 5000);
//        apiClient.setEnableRedirects(true);

        if (requestJSONO == null) requestJSONO = new JSONObject();

        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), requestJSONO.toString());

        String credential = Credentials.basic(username, password);

        requestBuilder.addHeader("Token-Timestamp", String.valueOf(timestamp));
        requestBuilder.addHeader("Authorization", credential).build();
        requestBuilder.addHeader("Token-ID-Address", "address");
        requestBuilder.addHeader("Token-Signature", "signature");
        requestBuilder.addHeader("Content-Type", "application/json");
        requestBuilder.addHeader("Accept", "application/json");

        switch (method) {
            case "delete":
                requestBuilder.delete(requestBody);
                break;
            case "put":
                requestBuilder.put(requestBody);
                break;
            default:
                break;
        }

        OkHttpClient client = clientBuilder.build();
        Request request = requestBuilder.build();

        Timber.e("API REQUEST METHOD: " + method);
        Timber.d("API REQUEST URL: " + url);
        Timber.d("API REQUEST BODY: " + bodyToString(request));

        if (responseHandler == null){
            responseHandler = new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Timber.d("API RESPONSE DEFAULT CALLBACK: %s", e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    final ServerResponse serverResponse = new ServerResponse(response);
                    SignalServer.mainThreadCallback(new Runnable() {
                        @Override
                        public void run() {
                            Timber.d("API RESPONSE DEFAULT CALLBACK: %s", serverResponse.getResponseJSONObject().toString());
                        }
                    });
                }
            };
        }

        if (async) {
            client.newCall(request).enqueue(responseHandler);
        } else {
            Call call = client.newCall(request);
            try {
                Response response = call.execute();
                responseHandler.onResponse(call, response);
            } catch (IOException e) {
                e.printStackTrace();
                responseHandler.onFailure(call, e);
            }
        }

        return client;
    }

    public static OkHttpClient call(final String url, final String method, final JSONObject requestJSONO, final Callback responseHandler, final boolean async) {
        return requestServerTimestamp(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Timber.d("API RESPONSE DEFAULT CALLBACK: %s", e.getMessage());
            }

            @Override
            public void onResponse(final Call call, Response response) throws IOException {
                final ServerResponse serverResponse = new ServerResponse(response);
                SignalServer.mainThreadCallback(new Runnable() {
                    @Override
                    public void run() {
                        int timestamp = serverResponse.getResponseJSONObject().optInt("timestamp", 0);
                        call(url, method, requestJSONO, responseHandler, async, timestamp);
                    }
                });
            }
        });
    }

    private static String getFullApiUrl(String target_url) {
        return host + target_url;
    }

    public static void mainThreadCallback(Runnable task) {
        new Handler(Looper.getMainLooper()).post(task);
    }

    private static String bodyToString(final Request request){
        try {
            final Request copy = request.newBuilder().build();
            final okio.Buffer buffer = new okio.Buffer();
            if (copy.body() == null) return "";
            copy.body().writeTo(buffer);
            return buffer.readUtf8();
        } catch (final IOException e) {
            return "";
        }
    }

    public static boolean isActiveInternetConnection(Context context){
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo() != null;
    }

    private static String buildPayloadHeader(ECKey identityKey, String method, String url, RequestBody requestBody, String timestamp){
        final Buffer buffer = new Buffer();
        String encodedBody = "";
        if (requestBody != null) {
            try {
                requestBody.writeTo(buffer);
                final byte[] body = buffer.readByteArray();
                final byte[] hashedBody = Hash.sha3(body);
                encodedBody = Base64.encodeToString(hashedBody, Base64.NO_WRAP);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        final String forSigning = method + "\n" + url + "\n" + timestamp + "\n" + encodedBody;
        final byte[] msgHash = Hash.sha256(forSigning.getBytes());
        final ECKey.ECDSASignature signature = identityKey.sign(Sha256Hash.wrap(msgHash));

//        int recId = -1;
//        byte[] thisKey = identityKey.getPubKey();
//        for (int i = 0; i < 4; i++) {
//            byte[] k = ECKey.fromPublicOnly(i, signature, Sha256Hash.wrap(msgHash), false).getPubKey();
//            if (k != null && Arrays.equals(k, thisKey)) {
//                recId = i;
//                break;
//            }
//        }
//        if (recId == -1)
//            throw new RuntimeException("Could not construct a recoverable key. This should never happen.");
//        byte v = (byte) (recId + 27);

        final String hexR = lt.imas.react_native_signal.helpers.ByteUtil.toZeroPaddedHexString(lt.imas.react_native_signal.helpers.ByteUtil.bigIntegerToBytes(signature.r), 64);
        final String hexS = lt.imas.react_native_signal.helpers.ByteUtil.toZeroPaddedHexString(lt.imas.react_native_signal.helpers.ByteUtil.bigIntegerToBytes(signature.s), 64);
        final String hexV = lt.imas.react_native_signal.helpers.ByteUtil.toHexString(new byte[]{27});


        return "0x" + hexR + hexS + hexV;
    }
}
