package lt.imas.react_native_signal.signal;

import io.sentry.Sentry;
import timber.log.Timber;

public class LogSender {

    private static LogSender instance;

    public static LogSender getInstance() {
        if (instance == null) {
            instance = new LogSender();
        }
        return instance;
    }

    private LogSender() {
    }

    private boolean isSendingLogs = false;

    public void init(String dsn, boolean isSendingLogs) {
        if (dsn == null) return;
        if (!isSendingLogs) return;

        this.isSendingLogs = isSendingLogs;

        try {
            Sentry
                .init(dsn)
                .addTag("artifact", "android-rn-signal-protocol-messaging");
        } catch (Throwable e) {
            Timber.e(e);
            this.isSendingLogs = false;
        }
    }

    public void send(String message) {
        if (isSendingLogs)
            try {
                Sentry.capture(message);
            } catch (Throwable e) {
                Timber.e(e);
            }
    }

    public void send(Throwable throwable) {
        if (isSendingLogs)
            try {
                Sentry.capture(throwable);
            } catch (Throwable e) {
                Timber.e(e);
            }
    }

    public void reportError(Throwable throwable) {
        Timber.e(throwable);
        send(throwable);
    }

}
