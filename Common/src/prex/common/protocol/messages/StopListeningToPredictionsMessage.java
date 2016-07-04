package prex.common.protocol.messages;

import prex.common.PreXException;
import prex.common.PredictionContext;
import prex.common.protocol.Message;

/**
 * Created by jorl17 on 02/06/16.
 */
public class StopListeningToPredictionsMessage extends Message {
    private PreXException exception; // The exception we want to predict
    private PredictionContext context; // The prediction context of the exception we want to predict

    public StopListeningToPredictionsMessage(String src, PreXException exception, PredictionContext context) {
        super(src);
        this.exception = exception;
        this.context = context;
    }

    public PreXException getException() {
        return exception;
    }

    public void setException(PreXException exception) {
        this.exception = exception;
    }

    public PredictionContext getContext() {
        return context;
    }

    public void setContext(PredictionContext context) {
        this.context = context;
    }
}
