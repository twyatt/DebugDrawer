package io.palaima.debugdrawer.timber.data;

import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.palaima.debugdrawer.timber.model.LogEntry;
import timber.log.Tree;

public class LumberYard {

    private static final boolean HAS_TIMBER;

    static {
        boolean hasDependency;

        try {
            Class.forName("timber.log.Timber");
            hasDependency = true;
        } catch (ClassNotFoundException e) {
            hasDependency = false;
        }

        HAS_TIMBER = hasDependency;
    }

    private static final int BUFFER_SIZE = 200;

    private static final DateFormat FILENAME_DATE = new SimpleDateFormat("yyyy-MM-dd HHmm a", Locale.US);
    private static final DateFormat LOG_DATE_PATTERN = new SimpleDateFormat("MM-dd HH:mm:ss.S", Locale.US);

    private static final String LOG_FILE_END = ".log";

    private static LumberYard sInstance;

    private final Context context;

    private final Deque<LogEntry> entries = new ArrayDeque<>(BUFFER_SIZE + 1);

    private OnLogListener onLogListener;

    public LumberYard(@NonNull Context context) {
        if (!HAS_TIMBER) {
            throw new RuntimeException("Timber dependency is not found");
        }
        this.context = context.getApplicationContext();
    }

    public static LumberYard getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new LumberYard(context);
        }

        return sInstance;
    }

    private static final int MAX_TAG_LENGTH = 23;
    private static final int CALL_STACK_INDEX = 5;
    private static final Pattern ANONYMOUS_CLASS = Pattern.compile("(\\$\\d+)+$");

    public Tree tree() {

        return new Tree() {
            @Override
            protected void performLog(int priority, String tag, Throwable throwable, String message) {
                if (tag == null) {
                    tag = getTag();
                }
                addEntry(new LogEntry(priority, tag, message, LOG_DATE_PATTERN.format(Calendar.getInstance().getTime())));
            }

            /**
             * Extract the tag which should be used for the message from the {@code element}. By default
             * this will use the class name without any anonymous class suffixes (e.g., {@code Foo$1}
             * becomes {@code Foo}).
             */
            private String createStackElementTag(@NotNull StackTraceElement element) {
                String tag = element.getClassName();
                Matcher m = ANONYMOUS_CLASS.matcher(tag);
                if (m.find()) {
                    tag = m.replaceAll("");
                }
                tag = tag.substring(tag.lastIndexOf('.') + 1);
                // Tag length limit was removed in API 24.
                if (tag.length() <= MAX_TAG_LENGTH || Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    return tag;
                }
                return tag.substring(0, MAX_TAG_LENGTH);
            }

            private String getTag() {
                // DO NOT switch this to Thread.getCurrentThread().getStackTrace(). The test will pass
                // because Robolectric runs them on the JVM but on Android the elements are different.
                StackTraceElement[] stackTrace = new Throwable().getStackTrace();
                if (stackTrace.length <= CALL_STACK_INDEX) {
                    throw new IllegalStateException(
                            "Synthetic stacktrace didn't have enough elements: are you using proguard?");
                }
                return createStackElementTag(stackTrace[CALL_STACK_INDEX]);
            }
        };
    }

    public void setOnLogListener(OnLogListener onLogListener) {
        this.onLogListener = onLogListener;
    }

    private synchronized void addEntry(LogEntry entry) {
        entries.addLast(entry);

        if (entries.size() > BUFFER_SIZE) {
            entries.removeFirst();
        }

        onLog(entry);
    }

    public List<LogEntry> bufferedLogs() {
        return new ArrayList<>(entries);
    }

    /**
     * Save the current logs to disk.
     */
    public void save(OnSaveLogListener listener) {
        File dir = getLogDir();

        if (dir == null) {
            listener.onError("Can't save logs. External storage is not mounted. " +
                "Check android.permission.WRITE_EXTERNAL_STORAGE permission");
            return;
        }

        FileWriter fileWriter = null;

        try {
            File output = new File(dir, getLogFileName());
            fileWriter = new FileWriter(output, true);

            List<LogEntry> entries = bufferedLogs();
            for (LogEntry entry : entries) {
                fileWriter.write(entry.prettyPrint() + "\n");
            }

            listener.onSave(output);

        } catch (IOException e) {
            listener.onError(e.getMessage());
            e.printStackTrace();

        } finally {
            if (fileWriter != null) {
                try {
                    fileWriter.close();
                } catch (IOException e) {
                    listener.onError(e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    public void cleanUp() {
        File dir = getLogDir();
        if (dir != null) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(LOG_FILE_END)) {
                        file.delete();
                    }
                }
            }
        }
    }

    private File getLogDir() {
        return context.getExternalFilesDir(null);
    }

    private void onLog(LogEntry entry) {
        if (onLogListener != null) {
            onLogListener.onLog(entry);
        }
    }

    private String getLogFileName() {
        String pattern = "%s%s";
        String currentDate = FILENAME_DATE.format(Calendar.getInstance().getTime());

        return String.format(pattern, currentDate, LOG_FILE_END);
    }

    public interface OnSaveLogListener {
        void onSave(File file);

        void onError(String message);
    }

    public interface OnLogListener {
        void onLog(LogEntry logEntry);
    }
}
