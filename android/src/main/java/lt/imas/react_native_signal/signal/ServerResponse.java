package lt.imas.react_native_signal.signal;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Response;

public class ServerResponse {
    private LogSender logSender = LogSender.getInstance();

    private JSONObject serverResponse;

    public ServerResponse(Response response){
        try {
            String stringResponse = response.body().string();

            serverResponse = (isValidResponseCode(response.code()))
                    ? new JSONObject(stringResponse)
                    : new JSONObject();
        } catch (IOException | JSONException e) {
            logSender.reportError(e);
            serverResponse = new JSONObject();
        }
    }

    private boolean isValidResponseCode(int code) {
        /*
          More: https://github.com/signalapp/Signal-Server/wiki/API-Protocol
          410 – means gone, but actually returns response which is used when sendMessage returns staleDevices array
         */
        return (code >= 200 && code != 204 && code < 300) || code == 410;
    }

    public JSONObject getResponseJSONObject(){
        return serverResponse;
    }
}
