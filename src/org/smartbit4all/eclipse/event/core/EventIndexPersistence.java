package org.smartbit4all.eclipse.event.core;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaCore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * Handles JSON-based persistence for event index data.
 * Saves and loads index data to/from workspace metadata directory.
 */
public class EventIndexPersistence {

    private static final String INDEX_FILE_NAME = "event-index.json";
    private static final String STATE_LOCATION_PATH = "hu.it4all.plugin.navigator";
    
    private final Gson gson;
    private final File indexFile;
    
    /**
     * Creates persistence handler with default file location.
     */
    public EventIndexPersistence() {
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
        
        // Determine index file location in workspace metadata
        IPath stateLocation = Platform.getStateLocation(
            Platform.getBundle("org.smartbit4all.eclipse.event"));
        File stateDir = stateLocation.toFile();
        if (!stateDir.exists()) {
            stateDir.mkdirs();
        }
        
        this.indexFile = new File(stateDir, INDEX_FILE_NAME);
        EventLogger.debug("EventIndexPersistence: Index file location: " + indexFile.getAbsolutePath());
    }
    
    /**
     * Saves index data to JSON file.
     * 
     * @param publishers map of publishers (handle -> info)
     * @param subscribers map of subscribers (handle -> info)
     * @return true if save was successful
     */
    public boolean saveIndex(Map<String, EventPublisherInfo> publishers, 
                            Map<String, EventSubscriberInfo> subscribers) {
        try {
            EventLogger.info("EventIndexPersistence: Saving index to " + indexFile.getAbsolutePath());
            EventLogger.info("EventIndexPersistence: Publishers: " + publishers.size() + ", Subscribers: " + subscribers.size());
            
            // Create container object for JSON structure
            IndexData data = new IndexData();
            data.publishers = publishers;
            data.subscribers = subscribers;
            data.version = "1.0";
            data.timestamp = System.currentTimeMillis();
            
            // Write to file
            try (FileWriter writer = new FileWriter(indexFile)) {
                gson.toJson(data, writer);
            }
            
            EventLogger.info("EventIndexPersistence: Index saved successfully");
            return true;
            
        } catch (IOException e) {
            EventLogger.error("EventIndexPersistence: Error saving index", e);
            return false;
        }
    }
    
    /**
     * Loads index data from JSON file.
     * 
     * @param publishers map to populate with publishers
     * @param subscribers map to populate with subscribers
     * @return true if load was successful
     */
    public boolean loadIndex(Map<String, EventPublisherInfo> publishers, 
                            Map<String, EventSubscriberInfo> subscribers) {
        if (!indexFile.exists()) {
            EventLogger.info("EventIndexPersistence: Index file does not exist: " + indexFile.getAbsolutePath());
            return false;
        }
        
        try {
            EventLogger.info("EventIndexPersistence: Loading index from " + indexFile.getAbsolutePath());
            
            // Read from file
            IndexData data;
            try (FileReader reader = new FileReader(indexFile)) {
                data = gson.fromJson(reader, IndexData.class);
            }
            
            if (data == null) {
                EventLogger.warn("EventIndexPersistence: Index file is empty or invalid");
                return false;
            }
            
            EventLogger.info("EventIndexPersistence: Loaded version " + data.version 
                + ", timestamp: " + data.timestamp);
            
            // Restore IMethod references from handles
            if (data.publishers != null) {
                for (Map.Entry<String, EventPublisherInfo> entry : data.publishers.entrySet()) {
                    String handle = entry.getKey();
                    EventPublisherInfo info = entry.getValue();
                    
                    // Restore IMethod from handle
                    try {
                        IMethod method = (IMethod) JavaCore.create(handle);
                        if (method != null && method.exists()) {
                            // Use reflection to set the method field (it's private)
                            java.lang.reflect.Field methodField = EventPublisherInfo.class.getDeclaredField("method");
                            methodField.setAccessible(true);
                            methodField.set(info, method);
                            
                            publishers.put(handle, info);
                        } else {
                            EventLogger.debug("EventIndexPersistence: Skipping invalid publisher handle: " + handle);
                        }
                    } catch (Exception e) {
                        EventLogger.debug("EventIndexPersistence: Could not restore publisher from handle: " + handle);
                    }
                }
            }
            
            if (data.subscribers != null) {
                for (Map.Entry<String, EventSubscriberInfo> entry : data.subscribers.entrySet()) {
                    String handle = entry.getKey();
                    EventSubscriberInfo info = entry.getValue();
                    
                    // Restore IMethod from handle
                    try {
                        IMethod method = (IMethod) JavaCore.create(handle);
                        if (method != null && method.exists()) {
                            // Use reflection to set the method field (it's private)
                            java.lang.reflect.Field methodField = EventSubscriberInfo.class.getDeclaredField("method");
                            methodField.setAccessible(true);
                            methodField.set(info, method);
                            
                            subscribers.put(handle, info);
                        } else {
                            EventLogger.debug("EventIndexPersistence: Skipping invalid subscriber handle: " + handle);
                        }
                    } catch (Exception e) {
                        EventLogger.debug("EventIndexPersistence: Could not restore subscriber from handle: " + handle);
                    }
                }
            }
            
            EventLogger.info("EventIndexPersistence: Loaded " + publishers.size() 
                + " publishers and " + subscribers.size() + " subscribers");
            return true;
            
        } catch (IOException e) {
            EventLogger.error("EventIndexPersistence: Error loading index", e);
            return false;
        }
    }
    
    /**
     * Deletes the index file (clears cache).
     * 
     * @return true if deletion was successful or file didn't exist
     */
    public boolean clearCache() {
        if (!indexFile.exists()) {
            return true;
        }
        
        try {
            boolean deleted = indexFile.delete();
            if (deleted) {
                EventLogger.info("EventIndexPersistence: Cache cleared successfully");
            } else {
                EventLogger.warn("EventIndexPersistence: Could not delete cache file");
            }
            return deleted;
        } catch (Exception e) {
            EventLogger.error("EventIndexPersistence: Error clearing cache", e);
            return false;
        }
    }
    
    /**
     * Checks if cache file exists.
     */
    public boolean cacheExists() {
        return indexFile.exists();
    }
    
    /**
     * Gets cache file location.
     */
    public File getCacheFile() {
        return indexFile;
    }
    
    /**
     * Container class for JSON structure.
     */
    private static class IndexData {
        String version;
        long timestamp;
        Map<String, EventPublisherInfo> publishers;
        Map<String, EventSubscriberInfo> subscribers;
    }
}
