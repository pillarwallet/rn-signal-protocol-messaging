package lt.imas.react_native_signal.signal;

import org.json.JSONException;
import org.json.JSONObject;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import lt.imas.react_native_signal.helpers.Base64;

public class ProtocolStorage implements SignalProtocolStore {
    private LogSender logSender = LogSender.getInstance();

    private String PRE_KEYS_JSON_FILENAME = "prekeys.json";
    private String SIGNED_PRE_KEYS_JSON_FILENAME = "signed_prekeys.json";
    private String SESSIONS_JSON_FILENAME = "sessions.json";
    private String IDENTITES_JSON_FILENAME = "identites.json";
    private String LOCAL_JSON_FILENAME = "user.json";

    private String absolutePath;

    public ProtocolStorage(String  absolutePath) {
        this.absolutePath = absolutePath;
    }

    private String readFromStorage(String fileName) {
        String dirPath = absolutePath + "/signal";
        File file = new File(dirPath, fileName);
        if (file.exists()){
            try {
                FileInputStream fis = new FileInputStream(file);
                InputStreamReader isr = new InputStreamReader(fis);
                BufferedReader bufferedReader = new BufferedReader(isr);
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            } catch (IOException e) {
                logSender.reportError(e);
                return null;
            }
        } else {
            return null;
        }
    }

    private void writeToStorageFile(String fileName, String data) {
        try {
            String dirPath = absolutePath + "/signal";
            File dir = new File(dirPath);
            dir.mkdirs();
            File file = new File(dirPath, fileName);
            if (!file.exists()) {
                file.createNewFile();
            }
            FileOutputStream fos = new FileOutputStream(file);
            if (data != null) fos.write(data.getBytes());
            fos.flush();
            fos.close();
        } catch (IOException e) {
            logSender.reportError(e);
        }
    }

    public void deleteAll(){
        File dir = new File(absolutePath + "/signal");
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
                && !dataJSONO.isNull("identityKeyPair")
                && dataJSONO.has("registrationId")
                && !dataJSONO.isNull("registrationId");
        } catch (JSONException e) {
            logSender.reportError(e);
        }
        return false;
    }

    public void storeLocalUsername(String username){
        String data = readFromStorage(LOCAL_JSON_FILENAME);
        if (data == null || data.isEmpty()) data = "{}";
        try {
            JSONObject dataJSONO = new JSONObject(data);
            if (!dataJSONO.has("username") || dataJSONO.isNull("username")) {
                dataJSONO.put("username", username);
                writeToStorageFile(LOCAL_JSON_FILENAME, dataJSONO.toString());
            }
        } catch (JSONException e) {
            logSender.reportError(e);
        }
    }

    public void storeIdentityKeyPair(IdentityKeyPair identityKeyPair){
        String data = readFromStorage(LOCAL_JSON_FILENAME);
        if (data == null || data.isEmpty()) data = "{}";
        try {
            JSONObject dataJSONO = new JSONObject(data);
            if (!dataJSONO.has("identityKeyPair") || dataJSONO.isNull("identityKeyPair")) {
                dataJSONO.put("identityKeyPair", Base64.encodeBytes(identityKeyPair.serialize()));
                writeToStorageFile(LOCAL_JSON_FILENAME, dataJSONO.toString());
            }
        } catch (JSONException e) {
            logSender.reportError(e);
        }
    }

    public void storeLocalRegistrationId(int registrationId){
        String data = readFromStorage(LOCAL_JSON_FILENAME);
        if (data == null || data.isEmpty()) data = "{}";
        try {
            JSONObject dataJSONO = new JSONObject(data);
            if (!dataJSONO.has("registrationId") || dataJSONO.isNull("registrationId")) {
                dataJSONO.put("registrationId", registrationId);
                writeToStorageFile(LOCAL_JSON_FILENAME, dataJSONO.toString());
            }
        } catch (JSONException e) {
            logSender.reportError(e);
        }
    }

    @Override
    public IdentityKeyPair getIdentityKeyPair() {
        IdentityKeyPair identityKeyPair = null;
        try {
            String data = readFromStorage(LOCAL_JSON_FILENAME);
            if (data == null || data.isEmpty()) return null;
            JSONObject dataJSONO = new JSONObject(data);
            if (!dataJSONO.has("identityKeyPair") || dataJSONO.isNull("identityKeyPair")) return null;
            byte[] keyPairBytes = Base64.decode(dataJSONO.getString("identityKeyPair"));
            identityKeyPair = new IdentityKeyPair(keyPairBytes);
        } catch (JSONException
                | InvalidKeyException
                | IOException e) {
            logSender.reportError(e);
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
            if (!dataJSONO.has("registrationId") || dataJSONO.isNull("registrationId")) return localRegistrationId;
            localRegistrationId = dataJSONO.getInt("registrationId");
        } catch (JSONException e) {
            logSender.reportError(e);
        }
        return localRegistrationId;
    }

    public String getLocalUsername() {
        String localUsername = "";
        try {
            String data = readFromStorage(LOCAL_JSON_FILENAME);
            if (data == null || data.isEmpty()) return localUsername;
            JSONObject dataJSONO = new JSONObject(data);
            if (!dataJSONO.has("username") || dataJSONO.isNull("username")) return localUsername;
            localUsername = dataJSONO.getString("username");
        } catch (JSONException e) {
            logSender.reportError(e);
        }
        return localUsername;
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
                addressJSONO.put("identityKey", Base64.encodeBytes(identityKey.serialize()));
                dataJSONO.put(address.toString(), addressJSONO);
                writeToStorageFile(IDENTITES_JSON_FILENAME, dataJSONO.toString());
                return true;
            } catch (JSONException e) {
                logSender.reportError(e);
            }
        }
        return false;
    }

    public void removeIdentity(SignalProtocolAddress address) {
        String data = readFromStorage(IDENTITES_JSON_FILENAME);
        if (data == null || data.isEmpty()) data = "{}";
        try {
            JSONObject dataJSONO = new JSONObject(data);
            if (dataJSONO.has(address.toString())){
                dataJSONO.remove(address.toString());
                writeToStorageFile(IDENTITES_JSON_FILENAME, dataJSONO.toString());
            }
        } catch (JSONException e) {
            logSender.reportError(e);
        }
    }

    @Override
    public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
        // direction used for additional checks if needed
        String data = readFromStorage(IDENTITES_JSON_FILENAME);
        if (data == null || data.isEmpty()) return true;  // trust on first use
        try {
            JSONObject dataJSONO = new JSONObject(data);
            if (dataJSONO.has(address.toString())){
                JSONObject addressJSONO = dataJSONO.getJSONObject(address.toString());
                byte[] identityKeyBytes = Base64.decode(addressJSONO.getString("identityKey"));
                return identityKey.serialize() == identityKeyBytes;
            } else {
                return true; // trust on first use
            }
        } catch (JSONException | IOException e) {
            logSender.reportError(e);
        }
        return false;
    }

    @Override
    public PreKeyRecord loadPreKey(int preKeyId) {
        String data = readFromStorage(PRE_KEYS_JSON_FILENAME);
        if (data == null || data.isEmpty()) return null;
        try {
            JSONObject dataJSONO = new JSONObject(data);
            if (dataJSONO.has(String.valueOf(preKeyId))){
                byte[] preKeyBytes = Base64.decode(dataJSONO.getString(String.valueOf(preKeyId)));
                return new PreKeyRecord(preKeyBytes);
            }
        } catch (JSONException | IOException e) {
            logSender.reportError(e);
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
//                logSender.reportError(e);
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
            dataJSONO.put(String.valueOf(preKeyId), Base64.encodeBytes(record.serialize()));
            writeToStorageFile(PRE_KEYS_JSON_FILENAME, dataJSONO.toString());
        } catch (JSONException e) {
            logSender.reportError(e);
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
            logSender.reportError(e);
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
                byte[] sessionBytes = Base64.decode(dataJSONO.getString(address.toString()));
                return new SessionRecord(sessionBytes);
            }
        } catch (JSONException | IOException e) {
            logSender.reportError(e);
        }
        return record;
    }


    public ArrayList<SessionRecord> loadAllSessions() {
        String data = readFromStorage(SESSIONS_JSON_FILENAME);
        ArrayList<SessionRecord> records = new ArrayList<>();
        if (data == null || data.isEmpty()) return records;
        try {
            JSONObject dataJSONO = new JSONObject(data);
            Iterator<?> keys = dataJSONO.keys();
            while (keys.hasNext()){
                String key = (String)keys.next();
                if (dataJSONO.get(key) instanceof String){
                    String encodedBytesString = dataJSONO.getString(key);
                    byte[] sessionBytes = Base64.decode(encodedBytesString);
                    SessionRecord sessionRecord = new SessionRecord(sessionBytes);
                    records.add(sessionRecord);
                }
            }
        } catch (JSONException | IOException e) {
            logSender.reportError(e);
        }
        return records;
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
//            logSender.reportError(e);
//        } catch (IOException e) {
//            logSender.reportError(e);
//        }
        return results;
    }

    @Override
    public void storeSession(SignalProtocolAddress address, SessionRecord record) {
        String data = readFromStorage(SESSIONS_JSON_FILENAME);
        if (data == null || data.isEmpty()) data = "{}";
        try {
            if (containsSession(address)) deleteSession(address);
            JSONObject dataJSONO = new JSONObject(data);
            dataJSONO.put(address.toString(), Base64.encodeBytes(record.serialize()));
            writeToStorageFile(SESSIONS_JSON_FILENAME, dataJSONO.toString());
        } catch (JSONException e) {
            logSender.reportError(e);
        }
    }

    @Override
    public boolean containsSession(SignalProtocolAddress address) {
        SessionRecord record = loadSession(address);
        return record != null && !record.isFresh();
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
            logSender.reportError(e);
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
//            logSender.reportError(e);
//        }
    }

    @Override
    public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) {
        String data = readFromStorage(SIGNED_PRE_KEYS_JSON_FILENAME);
        if (data == null || data.isEmpty()) return null;
        try {
            JSONObject dataJSONO = new JSONObject(data);
            if (dataJSONO.has(String.valueOf(signedPreKeyId))){
                byte[] preKeyBytes = Base64.decode(dataJSONO.getString(String.valueOf(signedPreKeyId)));
                return new SignedPreKeyRecord(preKeyBytes);
            }
        } catch (JSONException | IOException e) {
            logSender.reportError(e);
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
                    byte[] preKeyBytes = Base64.decode(String.valueOf(dataJSONO.get(key)));
                    results.add(new SignedPreKeyRecord(preKeyBytes));
                }
            }
        } catch (JSONException | IOException e) {
            logSender.reportError(e);
        }
        return results;
    }

    @Override
    public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
        String data = readFromStorage(SIGNED_PRE_KEYS_JSON_FILENAME);
        if (data == null || data.isEmpty()) data = "{}";
        try {
            JSONObject dataJSONO = new JSONObject(data);
            dataJSONO.put(String.valueOf(signedPreKeyId), Base64.encodeBytes(record.serialize()));
            writeToStorageFile(SIGNED_PRE_KEYS_JSON_FILENAME, dataJSONO.toString());
        } catch (JSONException e) {
            logSender.reportError(e);
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
            logSender.reportError(e);
        }
    }

    public int getLastPreKeyIndex() {
        String data = readFromStorage(PRE_KEYS_JSON_FILENAME);
        if (data == null || data.isEmpty()) return 0;
        try {
            JSONObject dataJSONO = new JSONObject(data);
            return dataJSONO.length();
        } catch (JSONException e) {
            logSender.reportError(e);
        }
        return 0;
    }
}
