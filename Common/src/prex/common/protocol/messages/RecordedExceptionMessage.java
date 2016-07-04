package prex.common.protocol.messages;

import prex.common.RecordedException;
import prex.common.protocol.Message;

/**
 * Created by jorl17 on 02/06/16.
 */
public class RecordedExceptionMessage extends Message {
    private RecordedException exception;

    public RecordedExceptionMessage(String src, RecordedException exception) {
        super(src);
        this.exception = exception;
        //System.out.println(this);
    }

    public RecordedException getException() {
        return exception;
    }

    @Override
    public String toString() {
        return "RecordedExceptionMessage{" +
                "exception=" + exception +
                '}';
    }
}
