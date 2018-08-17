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
            Log.i("API", "API RAW RESPONSE: " + stringResponse);

            serverResponse = new JSONObject(stringResponse);
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            serverResponse = new JSONObject();
        }
    }

    public JSONObject getResponseJSONObject(){
        return serverResponse;
    }
}
