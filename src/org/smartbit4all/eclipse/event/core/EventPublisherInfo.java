package org.smartbit4all.eclipse.event.core;

/**
 * Publisher metadata
 */
public class EventPublisherInfo {

    private String api;
    private String event;
    private String className;
    private String methodName;
    private Object method;
    private Object invocationNode;

    public EventPublisherInfo(String api, String event, String className, 
                             String methodName, Object method, Object invocationNode) {
        this.api = api;
        this.event = event;
        this.className = className;
        this.methodName = methodName;
        this.method = method;
        this.invocationNode = invocationNode;
    }

    public String getApi() {
        return api;
    }

    public String getEvent() {
        return event;
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

    public Object getInvocationNode() {
        return invocationNode;
    }

    public EventDefinition getEventDefinition() {
        return new EventDefinition(api, event);
    }

    @Override
    public String toString() {
        return className + "." + methodName + " publishes " + api + "." + event;
    }
}
