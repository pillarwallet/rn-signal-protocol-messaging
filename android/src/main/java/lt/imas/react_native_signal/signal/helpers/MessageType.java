package lt.imas.react_native_signal.signal.helpers;

import android.support.annotation.NonNull;

public enum MessageType {
    MESSAGE,
    WARNING;

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
