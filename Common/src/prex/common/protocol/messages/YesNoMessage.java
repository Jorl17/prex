package prex.common.protocol.messages;

import prex.common.protocol.Message;

/**
 * Created by jorl17 on 15/06/16.
 */
public class YesNoMessage extends Message {
    private boolean yes;

    public YesNoMessage(String src, boolean yes) {
        super(src);
        this.yes = yes;
    }

    public boolean yes() {
        return yes;
    }

    public boolean no() {
        return !yes();
    }
}
