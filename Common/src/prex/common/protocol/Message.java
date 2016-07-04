package prex.common.protocol;

import prex.common.PreXTimestamp;

import java.io.Serializable;

/**
 * Created by jorl17 on 18/04/16.
 */
public abstract class Message implements Serializable {
    private String src;
    private PreXTimestamp timestamp;

    public Message(String src) {
        this.src = src;
        timestamp = new PreXTimestamp();
    }

    public String getSrc() {
        return src;
    }

    public PreXTimestamp getTimestamp() {
        return timestamp;
    }
}
