package lt.imas.react_native_signal.signal;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class MessageStorage {
    private Context context;
    private String MESSAGES_JSON_FILENAME = "messages.json";

    public MessageStorage(Context context) {
        this.context = context;
    }

    private String readFromStorage(String fileName) {
        String dirPath = context.getFilesDir().getAbsolutePath() + "/messages";
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
            String dirPath = context.getFilesDir().getAbsolutePath() + "/messages";
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
            e.printStackTrace();
        }
    }

    public void deleteAll(){
        File dir = new File(context.getFilesDir().getAbsolutePath() + "/messages");
        if (dir.isDirectory()){
            String[] children = dir.list();
            for (String aChildren : children) {
                new File(dir, aChildren).delete();
            }
        }
    }

    public void deleteContactMessages(String username){
        File dir = new File(context.getFilesDir().getAbsolutePath() + "/messages/" + username);
        if (dir.isDirectory()){
            String[] children = dir.list();
            for (String aChildren : children) {
                new File(dir, aChildren).delete();
            }
        }
    }

    public void storeMessage(String username, JSONObject newMessagesJSONO){
        String dirPath = context.getFilesDir().getAbsolutePath() + "/messages/" + username;
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
            e.printStackTrace();
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
            e.printStackTrace();
        }
        return messagesJSONA;
    }

    public JSONArray getExistingChats() {
        JSONArray chatsJSONA = new JSONArray();
        String dirPath = context.getFilesDir().getAbsolutePath() + "/messages";
        File directory = new File(dirPath);
        File[] files = directory.listFiles();
        if (files != null && files.length > 0){
            for (int i = 0; i < files.length; i++){
                String username = files[i].getName();
                try {
                    JSONObject chatJSONO = new JSONObject();
                    JSONArray messagesJSONA = getContactMessages(username);
                    chatJSONO.put("username", username);
                    if (messagesJSONA != null && messagesJSONA.length() != 0) chatJSONO.put("lastMessage", messagesJSONA.get(messagesJSONA.length()-1));
                    chatJSONO.put("unread", 0);
                    chatsJSONA.put(chatJSONO);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        return chatsJSONA;
    }
}
