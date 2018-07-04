package lt.imas.react_native_signal.signal;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import lt.imas.react_native_signal.helpers.Base64;

public class ProtocolStorage implements SignalProtocolStore {
    private Context context;
    private String PRE_KEYS_JSON_FILENAME = "prekeys.json";
    private String SIGNED_PRE_KEYS_JSON_FILENAME = "signed_prekeys.json";
    private String SESSIONS_JSON_FILENAME = "sessions.json";
    private String IDENTITES_JSON_FILENAME = "identites.json";
    private String LOCAL_JSON_FILENAME = "user.json";

    public ProtocolStorage(Context context) {
        this.context = context;
    }

    private String readFromStorage(String fileName) {
        String path = context.getFilesDir().getAbsolutePath() + "/signal/" + fileName;
        File file = new File(path);
        if (file.exists()){
            try {
                FileInputStream fis = context.openFileInput(fileName);
                InputStreamReader isr = new InputStreamReader(fis);
                BufferedReader bufferedReader = new BufferedReader(isr);
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    sb.append(line);
                }
                String data = sb.toString();
                return data;
            } catch (FileNotFoundException fileNotFound) {
                return null;
            } catch (IOException ioException) {
                return null;
            }
        } else {
            return null;
        }
    }

    private void writeToStorageFile(String fileName, String data) {
        try {
            FileOutputStream fos = context.openFileOutput("signal/" + fileName, Context.MODE_PRIVATE);
            if (data != null) fos.write(data.getBytes());
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void deleteAll(){
        File dir = new File(context.getFilesDir().getAbsolutePath() + "/signal");
        if (dir.isDirectory()){
            String[] children = dir.list();
            for (String aChildren : children) {
                new File(dir, aChildren).delete();
            }
        }
    }

    public boolean isLocalRegistered(){
        String data = readFromStorage(LOCAL_JSON_FILENAME);
        if (data == null || data.isEmpty()) data = "{}";
        try {
            JSONObject dataJSONO = new JSONObject(data);
            return dataJSONO.has("identityKeyPair")
                || dataJSONO.has("registrationId");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void storeIdentityKeyPair(IdentityKeyPair identityKeyPair){
        String data = readFromStorage(LOCAL_JSON_FILENAME);
        if (data == null || data.isEmpty()) data = "{}";
        try {
            JSONObject dataJSONO = new JSONObject(data);
            if (!dataJSONO.has("identityKeyPair")) {
                dataJSONO.put("identityKeyPair", Base64.encodeBytesWithoutPadding(identityKeyPair.serialize()));
                writeToStorageFile(LOCAL_JSON_FILENAME, dataJSONO.toString());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void storeLocalRegistrationId(int registrationId){
        String data = readFromStorage(LOCAL_JSON_FILENAME);
        if (data == null || data.isEmpty()) data = "{}";
        try {
            JSONObject dataJSONO = new JSONObject(data);
            if (!dataJSONO.has("registrationId")) {
                dataJSONO.put("registrationId", registrationId);
                writeToStorageFile(LOCAL_JSON_FILENAME, dataJSONO.toString());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public IdentityKeyPair getIdentityKeyPair() {
        IdentityKeyPair identityKeyPair = null;
        try {
            String data = readFromStorage(LOCAL_JSON_FILENAME);
            if (data == null || data.isEmpty()) return null;
            JSONObject dataJSONO = new JSONObject(data);
            byte[] keyPairBytes = Base64.decodeWithoutPadding(dataJSONO.getString("identityKeyPair"));
            identityKeyPair = new IdentityKeyPair(keyPairBytes);
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return identityKeyPair;
    }

    @Override
    public int getLocalRegistrationId() {
        int localRegistrationId = 0;
        try {
            String data = readFromStorage(LOCAL_JSON_FILENAME);
            if (data == null || data.isEmpty()) return localRegistrationId;
            JSONObject dataJSONO = new JSONObject(data);
            localRegistrationId = dataJSONO.getInt("registrationId");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return localRegistrationId;
    }

    @Override
    public boolean saveIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
        boolean alreadyExists = isTrustedIdentity(address, identityKey, Direction.SENDING);
        if (!alreadyExists){
            try {
                String data = readFromStorage(IDENTITES_JSON_FILENAME);
                if (data == null || data.isEmpty()) data = "{}";
                JSONObject dataJSONO = new JSONObject(data);
                JSONObject addressJSONO = new JSONObject();
                addressJSONO.put("address", address.toString());
                addressJSONO.put("identityKey", Base64.encodeBytesWithoutPadding(identityKey.serialize()));
                dataJSONO.put(address.toString(), addressJSONO);
                writeToStorageFile(IDENTITES_JSON_FILENAME, dataJSONO.toString());
                return true;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Override
    public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
        String data = readFromStorage(IDENTITES_JSON_FILENAME);
        return true;
//        TODO: remove force true
//        if (data == null || data.isEmpty()) return false;
//        try {
//            JSONObject dataJSONO = new JSONObject(data);
//            if (dataJSONO.has(address.toString())){
//                JSONObject addressJSONO = dataJSONO.getJSONObject(address.toString());
//                byte[] identityKeyBytes = Base64.decodeWithoutPadding(addressJSONO.getString("identityKey"));
//                return identityKey.serialize() == identityKeyBytes;
//            }
//        } catch (JSONException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return false;
    }

    @Override
    public PreKeyRecord loadPreKey(int preKeyId) {
        String data = readFromStorage(PRE_KEYS_JSON_FILENAME);
        if (data == null || data.isEmpty()) return null;
        try {
            JSONObject dataJSONO = new JSONObject(data);
            if (dataJSONO.has(String.valueOf(preKeyId))){
                byte[] preKeyBytes = Base64.decodeWithoutPadding(dataJSONO.getString(String.valueOf(preKeyId)));
                return new PreKeyRecord(preKeyBytes);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

//    TODO: keep for later improvements
//    public PreKeyPublic loadRandomPreKey() {
//        Connection conn = GetSQLConnection.getConn();
//        PreKeyRecord record = null;
//        PreKeyPublic pubKey = null;
//
//        if (conn != null) {
//            try {
//                String sql = "SELECT preKeyRecord FROM preKeyStorage ORDER BY RAND() LIMIT 1";
//                PreparedStatement ps = conn.prepareStatement(sql);
//                ResultSet rs = ps.executeQuery();
//                rs.next();
//                record = new PreKeyRecord(rs.getBytes("preKeyRecord"));
//                conn.close();
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//
//            if (record != null) {
//                pubKey = new PreKeyPublic(record.getKeyPair().getPublicKey(), record.getId());
//            }
//        }
//
//        return pubKey;
//    }

    @Override
    public void storePreKey(int preKeyId, PreKeyRecord record) {
        String data = readFromStorage(PRE_KEYS_JSON_FILENAME);
        if (data == null || data.isEmpty()) data = "{}";
        try {
            JSONObject dataJSONO = new JSONObject(data);
            dataJSONO.put(String.valueOf(preKeyId), Base64.encodeBytesWithoutPadding(record.serialize()));
            writeToStorageFile(PRE_KEYS_JSON_FILENAME, dataJSONO.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean containsPreKey(int preKeyId) {
        return loadPreKey(preKeyId) != null;
    }

    @Override
    public void removePreKey(int preKeyId) {
        String data = readFromStorage(PRE_KEYS_JSON_FILENAME);
        if (data == null || data.isEmpty()) data = "{}";
        try {
            JSONObject dataJSONO = new JSONObject(data);
            if (dataJSONO.has(String.valueOf(preKeyId))){
                dataJSONO.remove(String.valueOf(preKeyId));
                writeToStorageFile(PRE_KEYS_JSON_FILENAME, dataJSONO.toString());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public SessionRecord loadSession(SignalProtocolAddress address) {
        String data = readFromStorage(SESSIONS_JSON_FILENAME);
        SessionRecord record = new SessionRecord();
        if (data == null || data.isEmpty()) return record;
        try {
            JSONObject dataJSONO = new JSONObject(data);
            if (dataJSONO.has(address.toString())){
                byte[] sessionBytes = Base64.decodeWithoutPadding(dataJSONO.getString(address.toString()));
                return new SessionRecord(sessionBytes);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return record;
    }

    @Override
    public List<Integer> getSubDeviceSessions(String s) {
        List<Integer> results = new LinkedList<>();
//        TODO: keep for later improvements
//        String data = readFromStorage(SESSIONS_JSON_FILENAME);
//        if (data == null || data.isEmpty()) return results;
//        try {
//            JSONObject dataJSONO = new JSONObject(data);
//            Iterator<?> keys = dataJSONO.keys();
//            while (keys.hasNext()){
//                String key = (String)keys.next();
//                if (dataJSONO.get(key) instanceof JSONObject) {
//                    JSONObject sessionJSONO = (JSONObject) dataJSONO.get(key);
//                    int addressId = sessionJSONO.getInt("addressId");
//                    String name = sessionJSONO.getString("name");
//                    if (name.equals(s) && addressId != 1){
//                        addressId
//                    }
//                }
//            }
//        } catch (JSONException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        return results;
    }

    @Override
    public void storeSession(SignalProtocolAddress address, SessionRecord record) {
        String data = readFromStorage(SESSIONS_JSON_FILENAME);
        if (data == null || data.isEmpty()) data = "{}";
        try {
            JSONObject dataJSONO = new JSONObject(data);
            dataJSONO.put(address.toString(), Base64.encodeBytesWithoutPadding(record.serialize()));
            writeToStorageFile(SESSIONS_JSON_FILENAME, dataJSONO.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean containsSession(SignalProtocolAddress address) {
        return loadSession(address) != null;
    }

    @Override
    public void deleteSession(SignalProtocolAddress address) {
        String data = readFromStorage(SESSIONS_JSON_FILENAME);
        if (data == null || data.isEmpty()) data = "{}";
        try {
            JSONObject dataJSONO = new JSONObject(data);
            if (dataJSONO.has(address.toString())){
                dataJSONO.remove(address.toString());
                writeToStorageFile(SESSIONS_JSON_FILENAME, dataJSONO.toString());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deleteAllSessions(String name) {
//        TODO: keep for later improvements: search sessions through array by name
//        String data = readFromStorage(SESSIONS_JSON_FILENAME);
//        if (data == null || data.isEmpty()) data = "{}";
//        try {
//            JSONObject dataJSONO = new JSONObject(data);
//            for (int i=0; i<dataJSONO.length(); i++){
//                String recordString = dataJSONO.keys().;
//            }
//            writeToStorageFile(SESSIONS_JSON_FILENAME, dataJSONO.toString());
//        } catch (JSONException e) {
//            e.printStackTrace();
//        }
    }

    @Override
    public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) {
        String data = readFromStorage(SIGNED_PRE_KEYS_JSON_FILENAME);
        if (data == null || data.isEmpty()) return null;
            try {
            JSONObject dataJSONO = new JSONObject(data);
            if (dataJSONO.has(String.valueOf(signedPreKeyId))){
                byte[] preKeyBytes = Base64.decodeWithoutPadding(dataJSONO.getString(String.valueOf(signedPreKeyId)));
                return new SignedPreKeyRecord(preKeyBytes);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<SignedPreKeyRecord> loadSignedPreKeys() {
        List<SignedPreKeyRecord> results = new LinkedList<>();
        String data = readFromStorage(SIGNED_PRE_KEYS_JSON_FILENAME);
        if (data == null || data.isEmpty()) return results;
        try {
            JSONObject dataJSONO = new JSONObject(data);
            Iterator<?> keys = dataJSONO.keys();
            while (keys.hasNext()){
                String key = (String)keys.next();
                if (dataJSONO.get(key) instanceof String){
                    byte[] preKeyBytes = Base64.decodeWithoutPadding(String.valueOf(dataJSONO.get(key)));
                    results.add(new SignedPreKeyRecord(preKeyBytes));
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return results;
    }

    @Override
    public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
        String data = readFromStorage(SIGNED_PRE_KEYS_JSON_FILENAME);
        if (data == null || data.isEmpty()) data = "{}";
        try {
            JSONObject dataJSONO = new JSONObject(data);
            dataJSONO.put(String.valueOf(signedPreKeyId), Base64.encodeBytesWithoutPadding(record.serialize()));
            writeToStorageFile(SIGNED_PRE_KEYS_JSON_FILENAME, dataJSONO.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean containsSignedPreKey(int signedPreKeyId) {
        return loadSignedPreKey(signedPreKeyId) != null;
    }

    @Override
    public void removeSignedPreKey(int signedPreKeyId) {
        String data = readFromStorage(SESSIONS_JSON_FILENAME);
        if (data == null || data.isEmpty()) data = "{}";
        try {
            JSONObject dataJSONO = new JSONObject(data);
            if (dataJSONO.has(String.valueOf(signedPreKeyId))){
                dataJSONO.remove(String.valueOf(signedPreKeyId));
                writeToStorageFile(SESSIONS_JSON_FILENAME, dataJSONO.toString());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
