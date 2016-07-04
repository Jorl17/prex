package prex.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Consumer;

// This is a buffer for samples that automatically invokes a callback when it is full. It then clears the data.
// The main idea is that the callback does something with the data just before it is cleared. Most likely the
// callback just flushes it off to someplace useful.
//
// The buffer can work synchronously and asynchronously. When used synchronously, the callback is invoked
// in the thread that called add(), when the buffer is full. This might hinder performance if the callback function
// delays execution. An alternative is asynchronous mode, where the callback is called in a new thread. Note that
// even in the asynchronous mode, the buffer is cleared, and that might force future calls to add to block until
// the callback has finished.
//
// A proposed solution to the aforementioned blocking is to just create a new buffer and use a dispatch queue for
// the callbacks. Maybe in a later version.
public class SampleBuffer implements Serializable {
    private Sample[] buffer;
    private int currPos;
    private boolean async;

    // Callback as soon as the buffer is full. It is cleared afterwards
    transient private Consumer<SampleBuffer> callback;

    // Default to synchronous mode
    public SampleBuffer(int size, Consumer<SampleBuffer> callback) {
        this(size, callback, false);
    }

    public SampleBuffer(int size, Consumer<SampleBuffer> callback, boolean async) {
        this.buffer = new Sample[size];
        this.currPos = 0;
        this.async = async;

        if ( this.async )
            this.callback = (s) -> new Thread(() -> {callback.accept(s); clear();} ).start();
        else
            this.callback = (s) -> { synchronized (this) { callback.accept(s); clear(); } };
    }



    public synchronized void add(Sample s) {
        this.buffer[this.currPos++] = s;
        if (this.buffer.length == this.currPos) {
            if ( this.callback != null ) {
                callback.accept(this);
            }
        }
    }

    public Sample[] getSamples() {
        return buffer;
    }

    public synchronized void clear() {
        this.currPos = 0;
    }

    @Override
    public String toString() {
        return "SampleBuffer{" +
                "buffer=" + Arrays.toString(buffer) +
                ", currPos=" + currPos +
                ", callback=" + callback +
                '}';
    }
}
