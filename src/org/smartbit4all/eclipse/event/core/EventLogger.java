package org.smartbit4all.eclipse.event.core;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Status;
import org.smartbit4all.eclipse.event.EventActivator;

/**
 * Centralized logger for Event Navigator Plugin
 * Outputs to: <workspace>/.metadata/event-navigator.log
 * 
 * Usage:
 *   EventLogger.info("Message");
 *   EventLogger.error("Error message", exception);
 *   EventLogger.debug("Debug message");
 */
public class EventLogger {

    private static final String PLUGIN_ID = EventActivator.PLUGIN_ID;
    private static final String LOG_FILE_NAME = "event-navigator.log";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    
    private static File logFile;
    private static FileWriter fileWriter;
    private static final Object LOCK = new Object();
    
    // Log levels
    private static final int DEBUG = 1;
    private static final int INFO = 2;
    private static final int WARNING = 3;
    private static final int ERROR = 4;
    
    static {
        initializeLogger();
    }
    
    /**
     * Initialize the logger and log file
     */
    private static void initializeLogger() {
        try {
            // Get workspace metadata directory
            File workspaceDir = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile();
            File metadataDir = new File(workspaceDir, ".metadata");
            
            // Create .metadata directory if it doesn't exist
            if (!metadataDir.exists()) {
                metadataDir.mkdirs();
            }
            
            // Create log file reference
            logFile = new File(metadataDir, LOG_FILE_NAME);
            
            // Create FileWriter in append mode
            fileWriter = new FileWriter(logFile, true);
            
            // Log initialization message
            String initMsg = "\n========================================\n" +
                           "Event Navigator Plugin Logger Initialized\n" +
                           "Timestamp: " + DATE_FORMAT.format(new Date()) + "\n" +
                           "========================================\n";
            fileWriter.write(initMsg);
            fileWriter.flush();
            
        } catch (IOException e) {
            // Fallback to stderr if initialization fails
            System.err.println("[" + PLUGIN_ID + "] Failed to initialize EventLogger");
            e.printStackTrace();
        }
    }
    
    /**
     * Log a debug message
     */
    public static void debug(String message) {
        log(DEBUG, message, null);
    }
    
    /**
     * Log an info message
     */
    public static void info(String message) {
        log(INFO, message, null);
    }
    
    /**
     * Log a warning message
     */
    public static void warn(String message) {
        log(WARNING, message, null);
    }
    
    /**
     * Log an error message
     */
    public static void error(String message) {
        log(ERROR, message, null);
    }
    
    /**
     * Log an error message with exception
     */
    public static void error(String message, Throwable exception) {
        log(ERROR, message, exception);
    }
    
    /**
     * Core logging method
     */
    private static void log(int level, String message, Throwable exception) {
        synchronized (LOCK) {
            try {
                if (fileWriter == null) {
                    System.err.println("[" + PLUGIN_ID + "] Logger not initialized");
                    return;
                }
                
                // Build log line
                String levelStr = getLevelString(level);
                String timestamp = DATE_FORMAT.format(new Date());
                String logLine = String.format("[%s] %s - %s\n", timestamp, levelStr, message);
                
                // Write to file
                fileWriter.write(logLine);
                
                // Write exception if provided
                if (exception != null) {
                    fileWriter.write("Exception: " + exception.getClass().getName() + "\n");
                    fileWriter.write("Message: " + exception.getMessage() + "\n");
                    fileWriter.write("Stack trace:\n");
                    
                    // Write stack trace
                    StackTraceElement[] stackTrace = exception.getStackTrace();
                    for (StackTraceElement element : stackTrace) {
                        fileWriter.write("  at " + element.toString() + "\n");
                    }
                    fileWriter.write("\n");
                }
                
                fileWriter.flush();
                
                // Also log to Eclipse console for important messages
                if (level >= WARNING) {
                    logToEclipse(level, message, exception);
                }
                
            } catch (IOException e) {
                System.err.println("[" + PLUGIN_ID + "] Failed to write log message");
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Get level string representation
     */
    private static String getLevelString(int level) {
        switch (level) {
            case DEBUG:
                return "DEBUG";
            case INFO:
                return "INFO ";
            case WARNING:
                return "WARN ";
            case ERROR:
                return "ERROR";
            default:
                return "UNKNOWN";
        }
    }
    
    /**
     * Log to Eclipse console (for warnings and errors)
     */
    private static void logToEclipse(int level, String message, Throwable exception) {
        try {
            int severity;
            switch (level) {
                case WARNING:
                    severity = Status.WARNING;
                    break;
                case ERROR:
                    severity = Status.ERROR;
                    break;
                default:
                    return;
            }
            
            Status status = new Status(severity, PLUGIN_ID, message, exception);
            EventActivator.getDefault().getLog().log(status);
        } catch (Exception e) {
            // Silently ignore
        }
    }
    
    /**
     * Get the log file location
     */
    public static File getLogFile() {
        return logFile;
    }
    
    /**
     * Shutdown the logger (cleanup)
     */
    public static void shutdown() {
        synchronized (LOCK) {
            try {
                if (fileWriter != null) {
                    fileWriter.write("\n========================================\n");
                    fileWriter.write("Event Navigator Plugin Logger Shutdown\n");
                    fileWriter.write("Timestamp: " + DATE_FORMAT.format(new Date()) + "\n");
                    fileWriter.write("========================================\n\n");
                    fileWriter.close();
                    fileWriter = null;
                }
            } catch (IOException e) {
                System.err.println("[" + PLUGIN_ID + "] Failed to shutdown EventLogger");
                e.printStackTrace();
            }
        }
    }
}
