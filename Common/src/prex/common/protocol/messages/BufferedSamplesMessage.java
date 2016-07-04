package prex.common.protocol.messages;

import prex.common.Sample;
import prex.common.SampleBuffer;
import prex.common.protocol.Message;

/**
 * Created by jorl17 on 18/04/16.
 */
public class BufferedSamplesMessage extends Message {

    private SampleBuffer buffer;

    public BufferedSamplesMessage(String src, SampleBuffer buffer) {
        super(src);
        this.buffer = buffer;
    }

    public SampleBuffer getBuffer() {
        return buffer;
    }
}
