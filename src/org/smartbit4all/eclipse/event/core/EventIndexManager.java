package org.smartbit4all.eclipse.event.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.smartbit4all.eclipse.event.ast.EventAnnotationScanner;
import org.smartbit4all.eclipse.event.ast.EventPublisherScanner;

/**
 * Central event index manager handling event relationships
 */
public class EventIndexManager {

    private static EventIndexManager instance;

    // Index data structures
    private Map<String, EventDefinition> eventsByKey;
    private Map<IMethod, EventPublisherInfo> publishers;
    private Map<IMethod, EventSubscriberInfo> subscribers;
    private List<IEventIndexChangeListener> listeners;

    private EventIndexManager() {
        // Initialize data structures
        this.eventsByKey = new HashMap<>();
        this.publishers = new HashMap<>();
        this.subscribers = new HashMap<>();
        this.listeners = new ArrayList<>();
    }

    public static synchronized EventIndexManager getInstance() {
        if (instance == null) {
            EventLogger.debug("EventIndexManager: Creating singleton instance");
            instance = new EventIndexManager();
            EventLogger.info("EventIndexManager singleton initialized");
        }
        return instance;
    }

    /**
     * Index the entire workspace
     */
    public void indexWorkspace() {
        EventLogger.info("indexWorkspace: Starting workspace-wide indexing");
        // TODO: Implement workspace indexing
        EventLogger.warn("indexWorkspace: Not yet implemented");
    }

    /**
     * Index a specific project
     */
    public void indexProject(Object project) {
        EventLogger.info("indexProject: Starting project indexing");
        // TODO: Implement project indexing
        EventLogger.warn("indexProject: Not yet implemented");
    }

    /**
     * Index a compilation unit
     */
    public void indexCompilationUnit(Object unit) {
        if (!(unit instanceof ICompilationUnit)) {
            EventLogger.debug("indexCompilationUnit: Invalid unit type, skipping");
            return;
        }
        
        ICompilationUnit compilationUnit = (ICompilationUnit) unit;
        EventLogger.debug("indexCompilationUnit: Starting indexing for " + compilationUnit.getElementName());
        
        // Step 1: Scan for @EventSubscription annotations
        EventAnnotationScanner annotationScanner = new EventAnnotationScanner();
        List<?> subscriberResults = annotationScanner.scanForSubscribers(compilationUnit);
        
        // Step 2: Scan for publisher patterns (invocationApi.publisher())
        EventPublisherScanner publisherScanner = new EventPublisherScanner();
        List<?> publisherResults = publisherScanner.scanForPublishers(compilationUnit);
        
        // Step 3: Store results in Maps
        // Store subscribers
        if (subscriberResults != null && !subscriberResults.isEmpty()) {
            EventLogger.debug("indexCompilationUnit: Found " + subscriberResults.size() + " subscribers");
            for (Object result : subscriberResults) {
                if (result instanceof EventSubscriberInfo) {
                    EventSubscriberInfo subscriberInfo = (EventSubscriberInfo) result;
                    Object methodObj = subscriberInfo.getMethod();
                    if (methodObj instanceof IMethod) {
                        IMethod method = (IMethod) methodObj;
                        this.subscribers.put(method, subscriberInfo);
                        EventLogger.debug("indexCompilationUnit: Indexed subscriber in " + method.getElementName());
                    }
                }
            }
        }
        
        // Store publishers
        if (publisherResults != null && !publisherResults.isEmpty()) {
            EventLogger.debug("indexCompilationUnit: Found " + publisherResults.size() + " publishers");
            for (Object result : publisherResults) {
                if (result instanceof EventPublisherInfo) {
                    EventPublisherInfo publisherInfo = (EventPublisherInfo) result;
                    Object methodObj = publisherInfo.getMethod();
                    if (methodObj instanceof IMethod) {
                        IMethod method = (IMethod) methodObj;
                        this.publishers.put(method, publisherInfo);
                        EventLogger.debug("indexCompilationUnit: Indexed publisher in " + method.getElementName());
                    }
                }
            }
        }
        
        // Step 4: Notify listeners about index change
        notifyListeners();
        EventLogger.info("indexCompilationUnit: Completed for " + compilationUnit.getElementName());
    }

    /**
     * Find subscribers for a given event
     */
    public List<EventSubscriberInfo> findSubscribers(EventDefinition event) {
        List<EventSubscriberInfo> result = new ArrayList<>();
        
        if (event == null) {
            EventLogger.debug("findSubscribers: Event is null");
            return result;
        }
        
        EventLogger.debug("findSubscribers: Searching for event " + event.getApi() + ":" + event.getEventName());
        
        // Linear search: iterate through subscribers map
        for (EventSubscriberInfo subscriberInfo : this.subscribers.values()) {
            // Check if subscriber's event definition matches the given event
            EventDefinition subscriberEvent = subscriberInfo.getEventDefinition();
            if (subscriberEvent != null && subscriberEvent.equals(event)) {
                result.add(subscriberInfo);
            }
        }
        
        EventLogger.debug("findSubscribers: Found " + result.size() + " subscribers");
        return result;
    }

    /**
     * Find publisher for a given event
     */
    public EventPublisherInfo findPublisher(EventDefinition event) {
        if (event == null) {
            EventLogger.debug("findPublisher: Event is null");
            return null;
        }
        
        EventLogger.debug("findPublisher: Searching for publisher of event " + event.getApi() + ":" + event.getEventName());
        
        // Linear search: iterate through publishers map
        for (EventPublisherInfo publisherInfo : this.publishers.values()) {
            // Check if publisher's event definition matches the given event
            EventDefinition publisherEvent = publisherInfo.getEventDefinition();
            if (publisherEvent != null && publisherEvent.equals(event)) {
                EventLogger.debug("findPublisher: Found publisher");
                return publisherInfo;
            }
        }
        
        EventLogger.debug("findPublisher: No publisher found");
        return null;
    }

    /**
     * Find event by API and event name
     */
    public EventDefinition findEvent(String api, String eventName) {
        if (api == null || eventName == null) {
            EventLogger.debug("findEvent: API or eventName is null");
            return null;
        }
        
        // Construct composite key: "api:eventName"
        String compositeKey = buildEventKey(api, eventName);
        EventLogger.debug("findEvent: Lookup for key " + compositeKey);
        
        // Lookup in eventsByKey map
        EventDefinition result = this.eventsByKey.get(compositeKey);
        if (result != null) {
            EventLogger.debug("findEvent: Found event");
        } else {
            EventLogger.debug("findEvent: Event not found");
        }
        return result;
    }
    
    /**
     * Build composite key for event lookup
     */
    private String buildEventKey(String api, String eventName) {
        return api + ":" + eventName;
    }
    
    /**
     * Clear all index data structures
     */
    public void clear() {
        EventLogger.info("clear: Clearing all index data structures");
        this.eventsByKey.clear();
        this.publishers.clear();
        this.subscribers.clear();
        // Note: listeners are NOT cleared - they should be persistent
        EventLogger.debug("clear: Index cleared");
    }

    /**
     * Add listener for index changes
     */
    public void addIndexChangeListener(IEventIndexChangeListener listener) {
        if (listener != null && !this.listeners.contains(listener)) {
            this.listeners.add(listener);
            EventLogger.debug("addIndexChangeListener: Listener registered, total listeners: " + this.listeners.size());
        }
    }

    /**
     * Remove listener for index changes
     */
    public void removeIndexChangeListener(IEventIndexChangeListener listener) {
        if (listener != null) {
            this.listeners.remove(listener);
            EventLogger.debug("removeIndexChangeListener: Listener removed, total listeners: " + this.listeners.size());
        }
    }
    
    /**
     * Notify all listeners about index changes
     */
    private void notifyListeners() {
        EventLogger.debug("notifyListeners: Notifying " + this.listeners.size() + " listeners");
        for (IEventIndexChangeListener listener : this.listeners) {
            if (listener != null) {
                try {
                    listener.indexChanged();
                } catch (Exception e) {
                    EventLogger.error("notifyListeners: Error calling listener.indexChanged()", e);
                }
            }
        }
    }
}
