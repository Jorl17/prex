package prex.client;

import prex.common.*;
import prex.common.protocol.Message;
import prex.common.protocol.messages.*;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

// This is the base class which every client should use to connect to a coordinator. This can be a probe, for which
// there is an auxiliary class named Probe (which ultimately extends this one), or a client looking to use the
// try-prevent-catch construct.
//
// If the intention is to act as a probe, it is merely sufficient to pass samples to the client's sample function.
// They will be automatically timestamped and their ID (made of their name and the src of this client) will be generated.
//
// If the intention is to use the try-prevent-catch construct, then you can start the construct using the Try method.
// The way this is done is as follows:
//
// client.Try( (t) -> {
//      ...code within the try block... may throw exceptions...
//         call t.sample() to use the sample construct
//         you MUST call t.check() whenever you want to "check for alarms"
//         if you don't call t.check(), alarms will NEVER be triggered.
//}).Prevent( <exception type>.class, (predictionInformationObject o) ->
//      ...code when an alarm is raised, within the prevent block, with the
//         prediction informtion object o
//}).Catch( <exception type>.class, (e) -> {
//      ...code when an exception of the given type is raised. The exception is e
//}).sync()
//
// The last call to sync implies the synchronous version of the try-prevent-catch construct. If you want to use the
// asynchronous version (also known as try-prevent_async-catch), call async at the end.
//
// This model has some limitations, namely with regards to compile-time type checking within the try block. However,
// it fully supports exception inheritance, so you're okay to use it that way. Also note that you can emulate
// the functionality of the "throws" keyword by catching an exception and then rethrowing it.
// You can also chain as many prevent and catch calls as you'd like. Also note that for each prevent you should
// have a corresponding catch call. Though we could enforce this at run-time (in fact I think we could cook up
// some generic magic to get type-checking at compile-time), time-constraints have prevented us from doing that.
//
// The PrexClient class IS NOT THREAD SAFE. Although support for this can be implemented in the future, if you wish
// to have multiple threads at the same time using the try-prevent-catch construct or the sample construct,
// then you should create one separate client instance for each one of them. You may reuse the source name, since it is
// the same source after all.
//
// Note: Although this class and the Try class work, a lot of code was hammered in here when I realized that exception
// hierarchies weren't fully supported. As such, a bit of this code is a monstrous spaghetti monster, with the incorrect
// data structures used and a lot of code to shoehorn extra functionality into them. These are the first classes that I
// will refactor when I can. Sorry to all who dear understand this madness.
//
public class PrexClient {
    // Sockets and streams to communicate with the coordinator
    protected Socket s;
    protected ObjectInputStream inStream;
    protected ObjectOutputStream outStream;

    // The "unique id" of this client (e.g. probe1, prex1, etc)
    protected String src;

    // This is the current context (set whenever we enter a try block)
    private PredictionContext currentContext;

    // The list of exceptions that we "care about", either because we want to predict or catch them
    private ArrayList<PreXException> exceptionsToMonitor;

    // The state of the prediction for each of the exceptions we are predicting.
    // In practice, using a map has come back to byte us, because it makes hierarchy much harder to work with. That's
    // a FIXME.
    private HashMap<PreXException,Boolean> exceptionStates;

    // The sample buffer. One per client. Defaults to synchronous mode
    private SampleBuffer buffer;

    public PrexClient(String src, String host, int port) throws IOException {
        this(src,host,port,1, false);
    }

    public PrexClient(String src, String host, int port, int bufferSize, boolean async) throws IOException {
        this.src = src;
        exceptionsToMonitor = new ArrayList<>();
        exceptionStates = new HashMap<>();
        s = new Socket(host, port);
        outStream = new ObjectOutputStream(s.getOutputStream());
        inStream = new ObjectInputStream(s.getInputStream());

        // Whenever the buffer is full, just flush it out to the coordinator. For some reason, we need to reset
        // the stream, otherwise it wouldn't flush data on successive messages.
        this.buffer = new SampleBuffer(bufferSize, (buf) -> {
            try {
                outStream.reset(); // Just in case...
                outStream.writeObject(new BufferedSamplesMessage(src, buf));
            } catch (IOException e) {
                e.printStackTrace();
                close();
            }
        }, async);
    }

    // Notify the client that it has entered a prediction context. Used by the Try class
    protected synchronized void startPredictionContext(String context) {
        this.currentContext = new PredictionContext(context);
    }

    // Notify the client that it should start predicting a type of exception. Used by the Try class
    protected synchronized void startPredicting(Class<? extends Exception> c) {
        try {
            PreXException e = new PreXException(c.getName());
            exceptionsToMonitor.add(e);

            // Ask the coordinator to start listening to predictions
            outStream.reset(); // Just in case...
            outStream.writeObject(new StartListeningToPredictionsMessage(src, e, currentContext));

            // The coordinator replies with the current prediction state
            YesNoMessage m = (YesNoMessage)inStream.readObject();
            exceptionStates.put(e, m.yes());
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    // sample(name, value) construct
    public synchronized void sample(String name, float value) {
        buffer.add(new Sample(new PreXTimestamp(), name, src, value));
    }

    // Notify the client that it should stop predicting a type of exception. Used by the Try class
    public synchronized void stopPredicting(Class<? extends Exception> c) {
        assert currentContext != null;
        try {
            PreXException e = new PreXException(c.getName());
            exceptionsToMonitor.remove(e);
            exceptionStates.remove(e);

            // Notify the coordinator
            outStream.reset(); // Just in case...
            outStream.writeObject(new StopListeningToPredictionsMessage(src, e, currentContext));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Notify the client that a given exception (class) has happened
    protected synchronized void exceptionClass(Class<? extends Exception> eClass) {
        assert currentContext != null;
        RecordedException ex = new RecordedException(new PreXTimestamp(), eClass.getName(), currentContext);

        // Check to see if we really care about this exception
        if ( exceptionsToMonitor.contains(ex) )

            // Notify the coordinator
            try {
                outStream.reset(); // Just in case
                outStream.writeObject(new RecordedExceptionMessage(src, ex));
            } catch (IOException e1) {
                e1.printStackTrace();
            }
    }


    // Ask the client to check if any of the current exceptions have been predicted to happen. The client first
    // processes any pending messages (which may bring new data) and, then, it checks the states of current exceptions.
    //
    // Note that the exception that is predicted first is also returned first. A FIXME is to make this work iteratively
    // for multiple exception types.
    public synchronized PredictionInformationObject checkPredictions() {
        try {
            // Check for new updates
            while (s.getInputStream().available() > 0) {
                Message m = (Message) inStream.readObject();
                if (m instanceof ExceptionPredictionStateMessage) {

                    PreXException e = ((ExceptionPredictionStateMessage) m).getException();
                    if ( exceptionStates.containsKey(e) )
                        exceptionStates.put(e, ((ExceptionPredictionStateMessage) m).getState());

                } else {
                    System.err.println("Unexpected message!!!");
                }
            }

            // Check the states of current predictions
            for (PreXException e : this.exceptionsToMonitor)
                if (exceptionStates.get(e))
                    return new PredictionInformationObject(e);
        } catch (IOException | ClassNotFoundException e1) {
            e1.printStackTrace();
        }


        return null;
    }

    // Notify the client that it has exited a prediction context. Used by the Try class
    public synchronized void endPredictionContext(String context) {
        this.currentContext = null;
        try {
            if ( !this.exceptionsToMonitor.isEmpty() ) {
                for (PreXException e : this.exceptionsToMonitor) {
                    outStream.writeObject(new StopListeningToPredictionsMessage(src, e, currentContext));
                }
                exceptionsToMonitor.clear();
            }
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }

    public synchronized void close() {
        try {
            outStream.writeObject(new GoodbyeMessage(src));
            s.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getSrc() {
        return src;
    }

    public Try Try(String predictionContext, ConsumerWithExceptions<Try> r) {
        return Try.Try(predictionContext, r, this);
    }

    // Down below are simple wrappers for administrative actions

    public void startRun() {
        try {
            outStream.writeObject(new SetRunningMessage(src, true));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stopRun() {
        try {
            outStream.writeObject(new SetRunningMessage(src, false));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addPredictionContextIDs(String context, String[][] ids) {
        try {
            outStream.writeObject(new AddRemovePredictionContextSampleIDsMessage(src, new PredictionContext(context), ids));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void removePredictionContextIDs(String context, String[][] ids) {
        try {
            outStream.writeObject(new AddRemovePredictionContextSampleIDsMessage(src, new PredictionContext(context), ids, false));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void startTraining(String context, String exceptionClass, int[]T, int[] k) {
        try {
            outStream.writeObject(new TrainMessage(src, T, k, new PredictionContext(context), new PreXException(exceptionClass)));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
