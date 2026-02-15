package org.smartbit4all.eclipse.event.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
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
        clear();

        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        for (IProject project : projects) {
            if (!project.isOpen()) {
                continue;
            }
            try {
                if (!project.hasNature(JavaCore.NATURE_ID)) {
                    continue;
                }
                indexProject(JavaCore.create(project));
            } catch (Exception e) {
                EventLogger.error("indexWorkspace: Error indexing project " + project.getName(), e);
            }
        }
    }

    /**
     * Index a specific project
     */
    public void indexProject(Object project) {
        EventLogger.info("indexProject: Starting project indexing");
        if (!(project instanceof IJavaProject)) {
            EventLogger.warn("indexProject: Unsupported project type");
            return;
        }

        IJavaProject javaProject = (IJavaProject) project;
        try {
            IPackageFragment[] packages = javaProject.getPackageFragments();
            for (IPackageFragment pkg : packages) {
                if (pkg.getKind() == IPackageFragmentRoot.K_SOURCE) {
                    ICompilationUnit[] units = pkg.getCompilationUnits();
                    for (ICompilationUnit unit : units) {
                        if (unit.exists()) {
                            indexCompilationUnit(unit);
                        }
                    }
                }
            }
        } catch (Exception e) {
            EventLogger.error("indexProject: Error indexing project " + javaProject.getElementName(), e);
        }
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

        // Validate that the project has a JRE system library configured
        IJavaProject javaProject = compilationUnit.getJavaProject();
        if (javaProject != null && !hasSystemLibrary(javaProject)) {
            EventLogger.warn("indexCompilationUnit: Skipping file " + compilationUnit.getElementName() 
                + " - Project '" + javaProject.getElementName() 
                + "' is missing JRE System Library. Please add JRE to project build path.");
            return;
        }

        // Remove stale entries for this compilation unit before re-indexing
        removeEntriesForCompilationUnit(compilationUnit);
        
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
     * Remove all indexed entries that belong to the given compilation unit.
     */
    public void removeCompilationUnit(ICompilationUnit compilationUnit) {
        if (compilationUnit == null) {
            return;
        }
        removeEntriesForCompilationUnit(compilationUnit);
        notifyListeners();
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
        EventLogger.debug("findSubscribers: Total indexed subscribers: " + this.subscribers.size());
        
        // Linear search: iterate through subscribers map
        for (EventSubscriberInfo subscriberInfo : this.subscribers.values()) {
            // Check if subscriber's event definition matches the given event
            EventDefinition subscriberEvent = subscriberInfo.getEventDefinition();
            if (subscriberEvent != null && subscriberEvent.equals(event)) {
                EventLogger.debug("findSubscribers: Match found - " + subscriberInfo.getClassName() 
                    + "." + subscriberInfo.getMethodName());
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
        EventLogger.debug("findPublisher: Total indexed publishers: " + this.publishers.size());
        
        // Debug: log all indexed publishers
        int publisherCount = 0;
        for (EventPublisherInfo pubInfo : this.publishers.values()) {
            EventDefinition pubEvent = pubInfo.getEventDefinition();
            if (pubEvent != null) {
                EventLogger.debug("findPublisher: Publisher #" + (++publisherCount) + " API=" + pubEvent.getApi() 
                    + " EventName=" + pubEvent.getEventName() + " Method=" + pubInfo.getClassName() + "." + pubInfo.getMethodName());
            }
        }
        
        // Linear search: iterate through publishers map
        for (EventPublisherInfo publisherInfo : this.publishers.values()) {
            // Check if publisher's event definition matches the given event
            EventDefinition publisherEvent = publisherInfo.getEventDefinition();
            if (publisherEvent != null && publisherEvent.equals(event)) {
                EventLogger.debug("findPublisher: Match found - " + publisherInfo.getClassName() 
                    + "." + publisherInfo.getMethodName());
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

    private void removeEntriesForCompilationUnit(ICompilationUnit compilationUnit) {
        removeEntriesByUnit(this.subscribers, compilationUnit);
        removeEntriesByUnit(this.publishers, compilationUnit);
    }

    private <T> void removeEntriesByUnit(Map<IMethod, T> map, ICompilationUnit compilationUnit) {
        Iterator<Map.Entry<IMethod, T>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<IMethod, T> entry = iterator.next();
            IMethod method = entry.getKey();
            if (method != null && compilationUnit.equals(method.getCompilationUnit())) {
                iterator.remove();
            }
        }
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
    
    /**
     * Check if a Java project has a JRE System Library configured
     */
    private boolean hasSystemLibrary(IJavaProject javaProject) {
        try {
            IClasspathEntry[] entries = javaProject.getRawClasspath();
            for (IClasspathEntry entry : entries) {
                if (entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
                    String path = entry.getPath().toString();
                    // Check for JRE System Library container
                    if (path.startsWith("org.eclipse.jdt.launching.JRE_CONTAINER")) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            EventLogger.error("hasSystemLibrary: Error checking system library for project " 
                + javaProject.getElementName(), e);
            return false;
        }
    }
}
