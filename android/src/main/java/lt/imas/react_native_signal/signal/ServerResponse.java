package lt.imas.react_native_signal.signal;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Response;

public class ServerResponse {
    private JSONObject serverResponse;

    public ServerResponse(Response response){
        try {
            String stringResponse = response.body().string();
            Log.i("API", "ServerResponse: " + response);
            Log.i("API", "API RAW RESPONSE: " + stringResponse);

            serverResponse = (isValidResponseCode(response.code()))
                    ? new JSONObject(stringResponse)
                    : new JSONObject();
        } catch (IOException | JSONException e) {
            Log.e("API", "ServerResponse", e);
            serverResponse = new JSONObject();
        }
    }

    private boolean isValidResponseCode(int code) {
        return code >= 200 && code != 204 && code < 300;
    }

    public JSONObject getResponseJSONObject(){
        return serverResponse;
    }
}
