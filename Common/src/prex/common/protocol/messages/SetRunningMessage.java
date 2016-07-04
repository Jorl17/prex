package prex.common.protocol.messages;

import prex.common.protocol.Message;

/**
 * Created by jorl17 on 02/06/16.
 */
public class SetRunningMessage extends Message {
    private boolean running;

    public SetRunningMessage(String src, boolean running) {
        super(src);
        this.running = running;
    }

    public boolean getRunning() {
        return running;
    }
}
