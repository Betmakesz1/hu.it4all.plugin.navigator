package org.smartbit4all.eclipse.event.core;

/**
 * Subscriber metadata
 */
public class EventSubscriberInfo {

    private String api;
    private String event;
    private String channel;
    private String className;
    private String methodName;
    private Object method;

    public EventSubscriberInfo(String api, String event, String channel, 
                              String className, String methodName, Object method) {
        this.api = api;
        this.event = event;
        this.channel = channel;
        this.className = className;
        this.methodName = methodName;
        this.method = method;
    }

    public String getApi() {
        return api;
    }

    public String getEvent() {
        return event;
    }

    public String getChannel() {
        return channel;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public Object getMethod() {
        return method;
    }

    public EventDefinition getEventDefinition() {
        return new EventDefinition(api, event);
    }

    @Override
    public String toString() {
        return className + "." + methodName + " subscribes to " + api + "." + event;
    }
}
