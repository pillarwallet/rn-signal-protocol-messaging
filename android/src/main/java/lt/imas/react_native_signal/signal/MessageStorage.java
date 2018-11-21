package lt.imas.react_native_signal.signal;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class MessageStorage {
    private LogSender logSender = LogSender.getInstance();
    private String absolutePath;

    public MessageStorage(String  absolutePath) {
        this.absolutePath = absolutePath;
    }

    private String getMessageStoreFilename(String tag){
        switch (tag){
            case "chat": return "messages.json"; // keep regular "messages.json" to avoid deprecating previous structure
            case "tx-note": return "messages_txnote.json";
            default: return "messages_other.json";
        }
    }

    private String readFromStorage(String fileName) {
        String dirPath = absolutePath + "/messages";
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
            String dirPath = absolutePath + "/messages";
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

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);

        fileOrDirectory.delete();
    }

    public void deleteAll(){
        deleteRecursive(new File(absolutePath + "/messages"));
    }

    public void deleteAllContactMessages(String username){
        deleteRecursive(new File(absolutePath + "/messages/" + username));
    }

    public void deleteContactMessages(String username, String tag){
        deleteRecursive(new File(absolutePath + "/messages/" + username + "/" + getMessageStoreFilename(tag)));
    }

    public void storeMessage(String username, JSONObject newMessagesJSONO, String tag){
        String dirPath = absolutePath + "/messages/" + username;
        File dir = new File(dirPath);
        dir.mkdirs();
        String userPath = username + "/" + getMessageStoreFilename(tag);
        String data = readFromStorage(userPath);
        if (data == null || data.isEmpty()) data = "[]";
        try {
            JSONArray existingMessagesJSONA = new JSONArray(data);
            existingMessagesJSONA.put(newMessagesJSONO);
            writeToStorageFile(userPath, existingMessagesJSONA.toString());
        } catch (JSONException e) {
            logSender.reportError(e);
        }
    }

    public JSONArray getContactMessages(String username, String tag){
        JSONArray messagesJSONA = new JSONArray();
        String userPath = username + "/" + getMessageStoreFilename(tag);
        String data = readFromStorage(userPath);
        if (data == null || data.isEmpty()) data = "[]";
        try {
            messagesJSONA = new JSONArray(data);
        } catch (JSONException e) {
            logSender.reportError(e);
        }
        return messagesJSONA;
    }

    public JSONArray getExistingMessages(String tag) {
        JSONArray chatsJSONA = new JSONArray();
        String dirPath = absolutePath + "/messages";
        File directory = new File(dirPath);
        File[] files = directory.listFiles();
        if (files != null && files.length > 0){
            for (File file : files) {
                String username = file.getName();
                try {
                    JSONObject chatJSONO = new JSONObject();
                    JSONArray messagesJSONA = getContactMessages(username, tag);
                    chatJSONO.put("username", username);
                    if (messagesJSONA != null && messagesJSONA.length() != 0) {
                        if (tag.equals("chat")) {
                            chatJSONO.put("lastMessage", messagesJSONA.get(messagesJSONA.length() - 1));
                        } else {
                            chatJSONO.put("messages", messagesJSONA);
                        }
                    }
                    chatJSONO.put("unread", 0); // keep regular "messages.json" to avoid deprecating previous structure
                    chatsJSONA.put(chatJSONO);
                } catch (JSONException e) {
                    logSender.reportError(e);
                }
            }
        }
        return chatsJSONA;
    }
}
