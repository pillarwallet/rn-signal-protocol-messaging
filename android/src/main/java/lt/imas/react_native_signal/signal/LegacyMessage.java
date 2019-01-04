package lt.imas.react_native_signal.signal;

import org.whispersystems.libsignal.util.Hex;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import lt.imas.react_native_signal.helpers.Base64;

public class LegacyMessage {
    private static final int SUPPORTED_VERSION =  1;
    private static final int CIPHER_KEY_SIZE   = 32;
    private static final int MAC_KEY_SIZE      = 20;
    private static final int MAC_SIZE          = 10;

    private static final int VERSION_OFFSET    =  0;
    private static final int VERSION_LENGTH    =  1;
    private static final int IV_OFFSET         = VERSION_OFFSET + VERSION_LENGTH;
    private static final int IV_LENGTH         = 16;
    private static final int CIPHERTEXT_OFFSET = IV_OFFSET + IV_LENGTH;

    private byte[] serialized;

    public LegacyMessage(byte[] ciphertext, String signalingKey) throws IOException {
        SecretKeySpec cipherKey  = getCipherKey(signalingKey);
        SecretKeySpec macKey     = getMacKey(signalingKey);
        verifyMac(ciphertext, macKey);
        serialized = getPlaintext(ciphertext, cipherKey);
    }

    public byte[] getBytes(){
        return serialized;
    }

    private byte[] getPlaintext(byte[] ciphertext, SecretKeySpec cipherKey) throws IOException {
        try {
            byte[] ivBytes = new byte[IV_LENGTH];
            System.arraycopy(ciphertext, IV_OFFSET, ivBytes, 0, ivBytes.length);
            IvParameterSpec iv = new IvParameterSpec(ivBytes);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, cipherKey, iv);

            return cipher.doFinal(ciphertext, CIPHERTEXT_OFFSET,
                    ciphertext.length - VERSION_LENGTH - IV_LENGTH - MAC_SIZE);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | java.security.InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException e) {
            throw new AssertionError(e);
        } catch (BadPaddingException e) {
            throw new IOException("Bad padding?");
        }
    }

    private void verifyMac(byte[] ciphertext, SecretKeySpec macKey) throws IOException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(macKey);

            if (ciphertext.length < MAC_SIZE + 1)
                throw new IOException("Invalid MAC!");

            mac.update(ciphertext, 0, ciphertext.length - MAC_SIZE);

            byte[] ourMacFull  = mac.doFinal();
            byte[] ourMacBytes = new byte[MAC_SIZE];
            System.arraycopy(ourMacFull, 0, ourMacBytes, 0, ourMacBytes.length);

            byte[] theirMacBytes = new byte[MAC_SIZE];
            System.arraycopy(ciphertext, ciphertext.length-MAC_SIZE, theirMacBytes, 0, theirMacBytes.length);

            if (!Arrays.equals(ourMacBytes, theirMacBytes)) {
                throw new IOException("Invalid MAC compare!");
            }
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            e.printStackTrace();
        }
    }


    private SecretKeySpec getCipherKey(String signalingKey) throws IOException {
        byte[] signalingKeyBytes = Base64.decode(signalingKey);
        byte[] cipherKey         = new byte[CIPHER_KEY_SIZE];
        System.arraycopy(signalingKeyBytes, 0, cipherKey, 0, cipherKey.length);

        return new SecretKeySpec(cipherKey, "AES");
    }


    private SecretKeySpec getMacKey(String signalingKey) throws IOException {
        byte[] signalingKeyBytes = Base64.decode(signalingKey);
        byte[] macKey            = new byte[MAC_KEY_SIZE];
        System.arraycopy(signalingKeyBytes, CIPHER_KEY_SIZE, macKey, 0, macKey.length);

        return new SecretKeySpec(macKey, "HmacSHA256");
    }
}