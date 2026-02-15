package org.smartbit4all.eclipse.event.core;

/**
 * Publisher metadata
 */
public class EventPublisherInfo {

    private String api;
    private String event;
    private String className;
    private String methodName;
    private String methodHandle; // IMethod.getHandleIdentifier() for persistence
    private transient Object method;
    private transient Object invocationNode;

    public EventPublisherInfo(String api, String event, String className, 
                             String methodName, Object method, Object invocationNode) {
        this.api = api;
        this.event = event;
        this.className = className;
        this.methodName = methodName;
        this.method = method;
        this.invocationNode = invocationNode;
        
        // Store method handle for persistence
        if (method instanceof org.eclipse.jdt.core.IMethod) {
            this.methodHandle = ((org.eclipse.jdt.core.IMethod) method).getHandleIdentifier();
        }
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

    public String getMethodHandle() {
        return methodHandle;
    }

    public void setMethodHandle(String methodHandle) {
        this.methodHandle = methodHandle;
    }

    public EventDefinition getEventDefinition() {
        return new EventDefinition(api, event);
    }

    @Override
    public String toString() {
        return className + "." + methodName + " publishes " + api + "." + event;
    }
}
