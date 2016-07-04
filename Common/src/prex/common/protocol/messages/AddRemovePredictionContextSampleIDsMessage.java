package prex.common.protocol.messages;

import prex.common.PredictionContext;
import prex.common.protocol.Message;

/**
 * Created by jorl17 on 02/06/16.
 */
public class AddRemovePredictionContextSampleIDsMessage extends Message {
    private PredictionContext context;
    /* Array of <name,src> */
    private String[][] ids;

    private boolean add;

    public AddRemovePredictionContextSampleIDsMessage(String src, PredictionContext context, String[][] ids) {
        this(src,context,ids,true);
    }

    public AddRemovePredictionContextSampleIDsMessage(String src, PredictionContext context, String[][] ids, boolean add) {
        super(src);
        this.context = context;
        this.ids = ids;
        this.add = add;
    }

    public String[][] getIds() {
        return ids;
    }

    public boolean isAdd() {
        return add;
    }

    public PredictionContext getContext() {
        return context;
    }
}
