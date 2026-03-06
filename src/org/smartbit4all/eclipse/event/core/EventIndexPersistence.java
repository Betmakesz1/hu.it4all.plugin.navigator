package org.smartbit4all.eclipse.event.core;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaCore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Handles JSON-based persistence for event index data.
 * Saves and loads index data to/from workspace metadata directory.
 */
public class EventIndexPersistence {

    private static final String INDEX_FILE_NAME = "event-index.json";
    private static final String STATE_LOCATION_PATH = "hu.it4all.plugin.navigator";
    private static final String CACHE_SCHEMA_VERSION_PROPERTY = "event.index.cache.schema.version";
    private static final String DEFAULT_CACHE_SCHEMA_VERSION = "1.0";
    
    private final Gson gson;
    private final File indexFile;
    private final String expectedCacheSchemaVersion;
    
    /**
     * Creates persistence handler with default file location.
     */
    public EventIndexPersistence() {
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
        this.expectedCacheSchemaVersion = resolveExpectedCacheSchemaVersion();
        
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
                            Map<String, EventSubscriberInfo> subscribers,
                            Map<String, Long> fileFingerprints) {
        try {
            EventLogger.info("EventIndexPersistence: Saving index to " + indexFile.getAbsolutePath());
            EventLogger.info("EventIndexPersistence: Publishers: " + publishers.size() + ", Subscribers: " + subscribers.size());
            
            // Create container object for JSON structure
            IndexData data = new IndexData();
            data.publishers = publishers;
            data.subscribers = subscribers;
            data.fileFingerprints = fileFingerprints;
            data.version = expectedCacheSchemaVersion;
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
                            Map<String, EventSubscriberInfo> subscribers,
                            Map<String, Long> fileFingerprints) {
        if (!indexFile.exists()) {
            EventLogger.info("EventIndexPersistence: Index file does not exist: " + indexFile.getAbsolutePath());
            return false;
        }
        
        try {
            EventLogger.info("EventIndexPersistence: Loading index from " + indexFile.getAbsolutePath());
            
            // Read from file
            JsonElement jsonRoot;
            try (FileReader reader = new FileReader(indexFile)) {
                jsonRoot = JsonParser.parseReader(reader);
            }

            if (!isValidCacheStructure(jsonRoot)) {
                return invalidateCacheAndFail("Schema mismatch or invalid JSON structure");
            }

            IndexData data = gson.fromJson(jsonRoot, IndexData.class);
            
            if (data == null) {
                return invalidateCacheAndFail("Parsed cache data is null");
            }

            if (data.publishers == null || data.subscribers == null || data.fileFingerprints == null) {
                return invalidateCacheAndFail("Required cache sections are missing");
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

            if (data.fileFingerprints != null) {
                fileFingerprints.putAll(data.fileFingerprints);
            }
            
            EventLogger.info("EventIndexPersistence: Loaded " + publishers.size() 
                + " publishers and " + subscribers.size() + " subscribers");
            return true;
            
        } catch (Exception e) {
            EventLogger.error("EventIndexPersistence: Error loading index", e);
            clearCache();
            return false;
        }
    }

    private boolean isValidCacheStructure(JsonElement root) {
        if (root == null || !root.isJsonObject()) {
            EventLogger.warn("EventIndexPersistence: Cache root is not a JSON object");
            return false;
        }

        JsonObject object = root.getAsJsonObject();
        if (!object.has("version") || !object.get("version").isJsonPrimitive()) {
            EventLogger.warn("EventIndexPersistence: Missing or invalid 'version' field");
            return false;
        }

        String version = object.get("version").getAsString();
        if (!expectedCacheSchemaVersion.equals(version)) {
            EventLogger.warn("EventIndexPersistence: Cache version mismatch. Expected "
                + expectedCacheSchemaVersion + ", found " + version);
            return false;
        }

        if (!object.has("publishers") || !object.get("publishers").isJsonObject()) {
            EventLogger.warn("EventIndexPersistence: Missing or invalid 'publishers' section");
            return false;
        }

        if (!object.has("subscribers") || !object.get("subscribers").isJsonObject()) {
            EventLogger.warn("EventIndexPersistence: Missing or invalid 'subscribers' section");
            return false;
        }

        if (!object.has("fileFingerprints") || !object.get("fileFingerprints").isJsonObject()) {
            EventLogger.warn("EventIndexPersistence: Missing or invalid 'fileFingerprints' section");
            return false;
        }

        return true;
    }

    private boolean invalidateCacheAndFail(String reason) {
        EventLogger.warn("EventIndexPersistence: Invalid cache - " + reason + ". Clearing cache file.");
        clearCache();
        return false;
    }

    private String resolveExpectedCacheSchemaVersion() {
        String configuredVersion = EventPluginProperties.get(CACHE_SCHEMA_VERSION_PROPERTY);
        if (configuredVersion == null) {
            return DEFAULT_CACHE_SCHEMA_VERSION;
        }

        String trimmedVersion = configuredVersion.trim();
        if (trimmedVersion.isEmpty()) {
            return DEFAULT_CACHE_SCHEMA_VERSION;
        }

        return trimmedVersion;
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
        Map<String, Long> fileFingerprints;
    }
}
