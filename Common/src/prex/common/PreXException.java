package prex.common;

import java.io.Serializable;

// This is the base class that represents an exception within PreX. It might be accompanied by a timestamp, which
// might be the time at which it happened, or when it is predicted to happen. It might also be null if that's not relevant.
// An exception itself is characterized by a String, which is its class string. Note that it is up to client libraries
// to guarantee that class hierarchies are guaranteed. Insofar as the coordinator is concerned, an exception is just
// a string, and no hierarchy is checked: client libraries have to recognize that, e.g., SQLException inherits Exception
// and send a PreXException for Exception if that's how it's stored in the coordinator.
//
// Granted, I would like to change the way this works in the future. The coordinator should not be keeping track of
// "only" a string, but this decision made it easier to store the data in the database (string vs class)
public class PreXException implements Serializable {
    // Time of WHEN the exception is predicted to happen OR when it happened
    protected PreXTimestamp time;
    protected String exceptionClass; //Class<? extends Exception> ...

    public PreXException(PreXTimestamp time, String exceptionClass) {
        this.time = time;
        this.exceptionClass = exceptionClass;
    }

    public PreXException(String exceptionClass) {
        this.exceptionClass = exceptionClass;
    }

    public PreXTimestamp getTime() {
        return time;
    }

    public String getExceptionClass() {
        return exceptionClass;
    }

    // Exceptions are the same if they have the same class name. Once again, note how this does not check inheritance!
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PreXException)) return false;

        PreXException that = (PreXException) o;

        return exceptionClass.equals(that.exceptionClass);

    }

    @Override
    public int hashCode() {
        return exceptionClass.hashCode();
    }

    @Override
    public String toString() {
        return "PreXException{" +
                "time=" + time +
                ", exceptionClass='" + exceptionClass + '\'' +
                '}';
    }
}
