package Utilities;

import java.util.logging.*;

public class AppLogger extends Formatter {
    // ANSI escape codes for coloring output in the console
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_WHITE = "\u001B[37m";



    private static final Logger logger = Logger.getLogger("AppLogger");
    static {
        logger.setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        logger.setLevel(Level.ALL);
        handler.setFormatter(new AppLogger());
        logger.addHandler(handler);
    }

    public static Logger getLogger() {
        return logger;
    }

    @Override
    public String format(LogRecord record) {
        StringBuilder builder = new StringBuilder();

        // Determine the color based on the log level
        String levelColor = ANSI_WHITE; // Default color
        if (record.getLevel() == Level.SEVERE) {
            levelColor = ANSI_RED;
        } else if (record.getLevel() == Level.WARNING) {
            levelColor = ANSI_YELLOW;
        } else if (record.getLevel() == Level.INFO) {
            levelColor = ANSI_GREEN;
        }


        builder.append(levelColor);


        builder.append("[");
        builder.append(DateTimeUtils.calcDate(record.getMillis()));
        builder.append("] ");
        builder.append("[");
        builder.append(record.getSourceClassName());
        builder.append("] ");
        builder.append("[");
        builder.append(record.getLevel().getName());
        builder.append("] - ");

        // Append the log message
        builder.append(formatMessage(record));

        // Append parameters if available
        Object[] params = record.getParameters();
        if (params != null) {
            builder.append("\t");
            for (int i = 0; i < params.length; i++) {
                builder.append(params[i]);
                if (i < params.length - 1) {
                    builder.append(", ");
                }
            }
        }

        // Reset the color at the end of the log entry
        builder.append(ANSI_RESET);
        builder.append("\n");
        return builder.toString();
    }

}
