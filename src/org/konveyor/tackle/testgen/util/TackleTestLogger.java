package org.konveyor.tackle.testgen.util;

import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TackleTestLogger {

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
            "[%1$tF %1$tT] [%4$-7s] [%2$s] %5$s %n");
    }

    private static Level DEFAULT_LOG_LEVEL = Level.OFF;

    public static Logger getLogger(Class<?> cls) {
        Logger logger = Logger.getLogger(cls.getSimpleName());
        Handler handler = new ConsoleHandler();
        logger.addHandler(handler);
        logger.setLevel(DEFAULT_LOG_LEVEL);
        logger.setUseParentHandlers(false);
        return logger;
    }

}
