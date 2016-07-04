package prex.common;

// In its current version, the PredictionInformationObject is terribly simple, indicating the start of the prediction
// window. The predicted exception itself is also available, but this is of little use.
public class PredictionInformationObject {
    private PreXException exception;

    public PredictionInformationObject(PreXException exception) {
        this.exception = exception;
    }

    public PreXException getException() {
        return exception;
    }

    public PreXTimestamp getStartOfPredictedWindow() {
        if ( exception != null )
            return exception.getTime();
        return null;
    }

    @Override
    public String toString() {
        return "PredictionInformationObject{" +
                "exception=" + exception +
                '}';
    }
}
