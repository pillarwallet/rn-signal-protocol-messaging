package lt.imas.react_native_signal.helpers;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import timber.log.Timber;

public class SampleResponse {

    public static JSONObject getByPath(String path) {
        JSONObject response = new JSONObject();
        try {
            String responseSamples = readFromFile("response-samples.json");
            JSONObject responseSamplesJSONO = new JSONObject(responseSamples);
            response = responseSamplesJSONO.optJSONObject(path);
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
        return response;
    }

    private static String readFromFile(String filename) throws IOException {
        InputStream is = SampleResponse.class.getClassLoader().getResourceAsStream(filename);
        StringBuilder stringBuilder = new StringBuilder();
        int i;
        byte[] b = new byte[4096];
        while ((i = is.read(b)) != -1) {
            stringBuilder.append(new String(b, 0, i));
        }
        return stringBuilder.toString();
    }

    private static String readFromFile1(String filename) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        InputStream is = SampleResponse.class.getClassLoader().getResourceAsStream(filename);
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            if (is != null) {
                String str = "";
                while ((str = reader.readLine()) != null) {
                    stringBuilder.append(str + "\n" );
                }
            }
        } finally {
            try { is.close(); } catch (Throwable ignore) {}
        }
        return stringBuilder.toString();
    }
}
