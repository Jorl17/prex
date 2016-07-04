package prex.client;

import prex.common.SampleBuffer;
import prex.common.protocol.messages.BufferedSamplesMessage;

import java.io.IOException;

/**
 * Created by jorl17 on 02/06/16.
 */
public class Probe extends PrexClient {
    public Probe(String src, String host, int port, int bufferSize) throws IOException {
        super(src, host, port, bufferSize, false);
    }

}
