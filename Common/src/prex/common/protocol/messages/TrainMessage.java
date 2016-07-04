package prex.common.protocol.messages;

import prex.common.PreXException;
import prex.common.PredictionContext;
import prex.common.protocol.Message;

/**
 * Created by jorl17 on 02/06/16.
 */
public class TrainMessage extends Message {
    private PredictionContext context;
    private PreXException exception;
    private int[] T;
    private int[] k;

    public TrainMessage(String src, int[] T, int[] k, PredictionContext context, PreXException exception) {
        super(src);
        this.context = context;
        this.exception = exception;
        this.T  = T;
        this.k = k;
    }

    public PredictionContext getContext() {
        return context;
    }

    public PreXException getException() {
        return exception;
    }

    public int[] getT() {
        return T;
    }

    public int[] getK() {
        return k;
    }
}
