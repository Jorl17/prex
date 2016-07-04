package prex.common.protocol.messages;

import prex.common.PreXException;
import prex.common.protocol.Message;

public class ExceptionPredictionStateMessage extends Message {
    private PreXException exception;
    private boolean state;

    public ExceptionPredictionStateMessage(String src, PreXException exception, boolean state) {
        super(src);
        this.exception = exception;
        this.state = state;
    }

    public boolean getState() {
        return state;
    }

    public PreXException getException() {
        return exception;
    }

    @Override
    public String toString() {
        return "ExceptionPredictionStateMessage{" +
                "exception=" + exception +
                ", state=" + state +
                '}';
    }
}
