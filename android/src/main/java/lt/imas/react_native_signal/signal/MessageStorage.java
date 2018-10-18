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
    
    private final String MESSAGES_JSON_FILENAME = "messages.json";
    private String absolutePath;

    public MessageStorage(String  absolutePath) {
        this.absolutePath = absolutePath;
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

    public void deleteAll(){
        File dir = new File(absolutePath + "/messages");
        if (dir.isDirectory()){
            String[] children = dir.list();
            for (String aChildren : children) {
                new File(dir, aChildren).delete();
            }
        }
    }

    public void deleteContactMessages(String username){
        File dir = new File(absolutePath + "/messages/" + username);
        if (dir.isDirectory()){
            String[] children = dir.list();
            for (String aChildren : children) {
                new File(dir, aChildren).delete();
            }
        }
    }

    public void storeMessage(String username, JSONObject newMessagesJSONO){
        String dirPath = absolutePath + "/messages/" + username;
        File dir = new File(dirPath);
        dir.mkdirs();
        String userPath = username + "/" + MESSAGES_JSON_FILENAME;
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

    public JSONArray getContactMessages(String username){
        JSONArray messagesJSONA = new JSONArray();
        String userPath = username + "/" + MESSAGES_JSON_FILENAME;
        String data = readFromStorage(userPath);
        if (data == null || data.isEmpty()) data = "[]";
        try {
            messagesJSONA = new JSONArray(data);
        } catch (JSONException e) {
            logSender.reportError(e);
        }
        return messagesJSONA;
    }

    public JSONArray getExistingChats() {
        JSONArray chatsJSONA = new JSONArray();
        String dirPath = absolutePath + "/messages";
        File directory = new File(dirPath);
        File[] files = directory.listFiles();
        if (files != null && files.length > 0){
            for (File file : files) {
                String username = file.getName();
                try {
                    JSONObject chatJSONO = new JSONObject();
                    JSONArray messagesJSONA = getContactMessages(username);
                    chatJSONO.put("username", username);
                    if (messagesJSONA != null && messagesJSONA.length() != 0)
                        chatJSONO.put("lastMessage", messagesJSONA.get(messagesJSONA.length() - 1));
                    chatJSONO.put("unread", 0);
                    chatsJSONA.put(chatJSONO);
                } catch (JSONException e) {
                    logSender.reportError(e);
                }
            }
        }
        return chatsJSONA;
    }
}
