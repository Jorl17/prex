package prex.common;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Date;

// This class represents an event in time. It can be presented as a UNIX timestamp or in human readable fashion.
// Mostly, it acts as a wrapper for Date.
public class PreXTimestamp implements Serializable {
    private final Date date;
    private final boolean unixPresentationModeByDefault;

    public PreXTimestamp(Date date) {
        this.date = date;
        this.unixPresentationModeByDefault = false;
    }

    public PreXTimestamp(boolean unixPresentationModeByDefault) {
        this.date= new Date();
        this.unixPresentationModeByDefault = unixPresentationModeByDefault;
    }
    public PreXTimestamp() {
        this(false);
    }

    public String asUnixTime() {
        return "" + this.date.getTime();
    }

    public Timestamp asTimestamp() {
        return new Timestamp(this.date.getTime());
    }

    public String asReadableTimestamp() {
        return "" +  asTimestamp();
    }

    public String toString()  {
        if (unixPresentationModeByDefault)
            return asUnixTime();
        else
            return asReadableTimestamp();
    }
}
