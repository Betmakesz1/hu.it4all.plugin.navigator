package org.smartbit4all.eclipse.event.ui.gutter;

import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.resource.ImageDescriptor;
import org.smartbit4all.eclipse.event.core.EventPublisherInfo;
import org.smartbit4all.eclipse.event.core.EventSubscriberInfo;

/**
 * Event-related Annotation types for gutter icon display in Eclipse editors.
 * 
 * These annotations are added to the IAnnotationModel of a compilation unit
 * to display visual indicators in the editor gutter.
 */
public final class EventGutterAnnotations {

    // Annotation type constants
    public static final String EVENT_PUBLISHER_ANNOTATION_TYPE = 
        "org.smartbit4all.eclipse.event.publisherAnnotation";
    
    public static final String EVENT_SUBSCRIBER_ANNOTATION_TYPE = 
        "org.smartbit4all.eclipse.event.subscriberAnnotation";
    
    public static final String EVENT_WARNING_ANNOTATION_TYPE = 
        "org.smartbit4all.eclipse.event.warningAnnotation";

    /**
     * Abstract base class for event-related annotations.
     * Provides common functionality for publisher and subscriber annotations.
     */
    public abstract static class EventAnnotation extends Annotation {
        
        /**
         * Get descriptive text for the annotation (shown in editor hover).
         * @return Descriptive text
         */
        @Override
        public abstract String getText();
        
        /**
         * Get the path to the icon resource for this annotation.
         * @return Icon path (e.g., "icons/event_publisher.png")
         */
        public abstract String getIconPath();
        
        /**
         * Get the ImageDescriptor for the annotation icon.
         * @return ImageDescriptor for rendering
         */
        public abstract ImageDescriptor getImage();
    }

    /**
     * Annotation for Event Publisher markers.
     * Displayed as a blue icon in the gutter indicating that this method
     * publishes an event.
     */
    public static class EventPublisherAnnotation extends EventAnnotation {
        
        private final EventPublisherInfo publisherInfo;
        private int subscriberCount;
        
        /**
         * Create a new publisher annotation.
         * @param publisherInfo Information about the event publisher
         * @param subscriberCount Number of active subscribers for this event
         */
        public EventPublisherAnnotation(EventPublisherInfo publisherInfo, int subscriberCount) {
            super();
            this.publisherInfo = publisherInfo;
            this.subscriberCount = subscriberCount;
            setType(EVENT_PUBLISHER_ANNOTATION_TYPE);
        }
        
        @Override
        public String getText() {
            return String.format(
                "Publishes event: %s.%s (%d subscriber%s)",
                publisherInfo.getApi(),
                publisherInfo.getEvent(),
                subscriberCount,
                subscriberCount == 1 ? "" : "s"
            );
        }
        
        @Override
        public String getIconPath() {
            return "icons/event_publisher.png";
        }
        
        @Override
        public ImageDescriptor getImage() {
            // TODO: Load from plugin image registry
            return null;
        }
        
        public EventPublisherInfo getPublisherInfo() {
            return publisherInfo;
        }
        
        public int getSubscriberCount() {
            return subscriberCount;
        }
    }

    /**
     * Annotation for Event Subscriber markers.
     * Displayed as a green icon in the gutter indicating that this method
     * subscribes to (handles) an event.
     */
    public static class EventSubscriberAnnotation extends EventAnnotation {
        
        private final EventSubscriberInfo subscriberInfo;
        private boolean publisherFound;
        
        /**
         * Create a new subscriber annotation.
         * @param subscriberInfo Information about the event subscriber
         * @param publisherFound Whether the publisher for this event was found
         */
        public EventSubscriberAnnotation(EventSubscriberInfo subscriberInfo, boolean publisherFound) {
            super();
            this.subscriberInfo = subscriberInfo;
            this.publisherFound = publisherFound;
            setType(EVENT_SUBSCRIBER_ANNOTATION_TYPE);
        }
        
        @Override
        public String getText() {
            String text = String.format(
                "Subscribes to: %s.%s",
                subscriberInfo.getApi(),
                subscriberInfo.getEvent()
            );
            
            if (subscriberInfo.getChannel() != null && !subscriberInfo.getChannel().isEmpty()) {
                text += String.format(" [Channel: %s]", subscriberInfo.getChannel());
            }
            
            if (!publisherFound) {
                text += " Publisher not found";
            }
            
            return text;
        }
        
        @Override
        public String getIconPath() {
            return publisherFound 
                ? "icons/event_subscriber.png"
                : "icons/event_warning.png";
        }
        
        @Override
        public ImageDescriptor getImage() {
            // TODO: Load from plugin image registry
            return null;
        }
        
        public EventSubscriberInfo getSubscriberInfo() {
            return subscriberInfo;
        }
        
        public boolean isPublisherFound() {
            return publisherFound;
        }
    }

    /**
     * Warning annotation for event-related issues.
     * Displayed as a yellow/orange icon in the gutter indicating
     * a potential problem with event definitions or relationships.
     */
    public static class EventWarningAnnotation extends EventAnnotation {
        
        /**
         * Warning type enumeration.
         */
        public enum WarningType {
            NO_PUBLISHER("No publisher found for this subscriber event"),
            NO_SUBSCRIBERS("Event publisher has no subscribers"),
            INVALID_SIGNATURE("Subscriber method signature does not match publisher"),
            CHANNEL_NOT_CONFIGURED("Event channel is not configured");
            
            private final String defaultMessage;
            
            WarningType(String defaultMessage) {
                this.defaultMessage = defaultMessage;
            }
            
            public String getDefaultMessage() {
                return defaultMessage;
            }
        }
        
        private final WarningType warningType;
        private final String customMessage;
        
        /**
         * Create a new warning annotation with default message.
         * @param warningType Type of warning
         */
        public EventWarningAnnotation(WarningType warningType) {
            this(warningType, null);
        }
        
        /**
         * Create a new warning annotation with custom message.
         * @param warningType Type of warning
         * @param customMessage Custom message (if null, uses default from WarningType)
         */
        public EventWarningAnnotation(WarningType warningType, String customMessage) {
            super();
            this.warningType = warningType;
            this.customMessage = customMessage;
            setType(EVENT_WARNING_ANNOTATION_TYPE);
        }
        
        @Override
        public String getText() {
            return customMessage != null 
                ? customMessage 
                : warningType.getDefaultMessage();
        }
        
        @Override
        public String getIconPath() {
            return "icons/event_warning.png";
        }
        
        @Override
        public ImageDescriptor getImage() {
            // TODO: Load from plugin image registry
            return null;
        }
        
        public WarningType getWarningType() {
            return warningType;
        }
        
        public String getCustomMessage() {
            return customMessage;
        }
    }

    // Prevent instantiation
    private EventGutterAnnotations() {
        throw new UnsupportedOperationException("EventGutterAnnotations is a utility class");
    }
}
