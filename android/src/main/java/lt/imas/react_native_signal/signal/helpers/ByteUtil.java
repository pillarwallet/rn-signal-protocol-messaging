package lt.imas.react_native_signal.signal.helpers;

import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;

public class ByteUtil {
    public static String toHexString(byte[] data) {
        return data == null ? "" : Hex.toHexString(data);
    }

    public static String toZeroPaddedHexString(final byte[] data, final int size) {
        final String hex = toHexString(data);
        final StringBuffer sb = new StringBuffer("");
        final int requiredPadding = size - hex.length();
        while (sb.length() < requiredPadding) {
            sb.append("0");
        }
        sb.append(hex);
        return sb.toString();
    }

    public static byte[] bigIntegerToBytes(BigInteger b, int numBytes) {
        if (b == null)
            return null;
        byte[] bytes = new byte[numBytes];
        byte[] biBytes = b.toByteArray();
        int start = (biBytes.length == numBytes + 1) ? 1 : 0;
        int length = Math.min(biBytes.length, numBytes);
        System.arraycopy(biBytes, start, bytes, numBytes - length, length);
        return bytes;
    }

    public static byte[] bigIntegerToBytes(BigInteger value) {
        if (value == null)
            return null;

        byte[] data = value.toByteArray();

        if (data.length != 1 && data[0] == 0) {
            byte[] tmp = new byte[data.length - 1];
            System.arraycopy(data, 1, tmp, 0, tmp.length);
            data = tmp;
        }
        return data;
    }

    public static BigInteger bytesToBigInteger(byte[] bb) {
        return new BigInteger(1, bb);
    }
}
