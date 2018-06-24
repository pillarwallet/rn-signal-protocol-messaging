package lt.imas.react_native_signal.signal;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Response;

public class ServerResponse {
//    private String errorMessage = "";
//    private boolean serverError = false;
    private JSONObject serverResponse;

    public ServerResponse(Response response){
        String stringResponse = null;
        try {
            stringResponse = response.body().string();
            Log.i("API", "API RAW RESPONSE: " + stringResponse);
        } catch (IOException e) {
            e.printStackTrace();
//            serverError = true;
        }
        if (stringResponse != null) {
            try {
                serverResponse = !stringResponse.isEmpty() ? new JSONObject(stringResponse) : new JSONObject();
//                if (!serverResponse.optBoolean("status", false)){
//                    errorMessage = getResponseJSONObject().getString("message");
//                }
            } catch (JSONException e) {
                e.printStackTrace();
//                serverError = true;
            }
        } else {
//            serverError = true;
        }
    }

//    public boolean hasError(){
//        return serverError || !this.errorMessage.isEmpty();
//    }

//    public String getErrorMessage(Context context){
//        if (serverError) errorMessage = "Cannot reach server";
//        return this.errorMessage ;
//    }

    public JSONObject getResponseJSONObject(){
        return serverResponse != null && serverResponse.length() != 0 ? serverResponse : new JSONObject();
    }

//    public JSONArray getResponseJSONArray(){
//        JSONArray response = new JSONArray();
//        if (serverResponse != null && serverResponse.length() != 0){
//            try {
//                response = serverResponse; //.getJSONArray("response");
//            } catch (JSONException e) {
//                e.printStackTrace();
//            }
//        }
//        return response;
//    }
}
