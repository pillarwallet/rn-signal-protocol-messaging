package lt.imas.react_native_signal.signal;

import com.facebook.react.bridge.ReadableMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
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

    public void init(ReadableMap config) {
        try {
            String dsn = config.hasKey("errorTrackingDSN") ? config.getString("errorTrackingDSN") : null;
            isSendingLogs = config.hasKey("isSendingLogs") ? config.getBoolean("isSendingLogs") : false;

            if (dsn == null || !isSendingLogs) return;

            Sentry
                .init(dsn)
                .addTag("artifact", "android-rn-signal-protocol-messaging");

            initUserContext(config);
        } catch (Throwable e) {
            Timber.e(e);
            isSendingLogs = false;
        }
    }

    private void initUserContext(ReadableMap config) {
        if (config == null) return;

        try {
            Map<String, Object> extra = new HashMap<>();
            List<String> extraKeys = Arrays.asList("walletId", "ethAddress");

            for (String key : extraKeys) {
                if (config.hasKey(key))
                    extra.put(key, config.getString(key));
            }

            UserBuilder userBuilder = new UserBuilder();

            if (config.hasKey("userId"))
                userBuilder.setId(config.getString("userId"));
            if (config.hasKey("username"))
                userBuilder.setUsername(config.getString("username"));
            if (extra.size() > 0)
                userBuilder.setData(extra);

            Sentry.getContext().setUser(userBuilder.build());
        } catch (Throwable e) {
            Timber.e(e);
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
