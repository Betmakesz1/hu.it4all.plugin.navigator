package org.smartbit4all.eclipse.event.core;

/**
 * Event definition model
 */
public class EventDefinition {

    private String api;
    private String eventName;

    public EventDefinition(String api, String eventName) {
        this.api = api;
        this.eventName = eventName;
    }

    public String getApi() {
        return api;
    }

    public String getEventName() {
        return eventName;
    }

    public String getKey() {
        return api + "." + eventName;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((api == null) ? 0 : api.hashCode());
        result = prime * result + ((eventName == null) ? 0 : eventName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        EventDefinition other = (EventDefinition) obj;
        if (api == null) {
            if (other.api != null)
                return false;
        } else if (!api.equals(other.api))
            return false;
        if (eventName == null) {
            if (other.eventName != null)
                return false;
        } else if (!eventName.equals(other.eventName))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return getKey();
    }
}
