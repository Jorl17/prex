package prex.coordinator.train;

import prex.common.PreXException;
import prex.common.PredictionContext;
import prex.coordinator.server.ClientThread;
import prex.coordinator.server.SharedServerState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

// The PredictionThreadManager manages all threads doing predictions. Clients can ask it to start predicting
// exceptions, and it will have to make sure that threads do the right predictions and that, when needed, they notify
// these clients of changes in those same predictions.
//
// This is achieved by mapping a PredictionContext/Exception pair to a prediction thread, so that only one
// prediction thread can exist for the same exception at a time. In practice, this would be better with a Set, but
// for now this works.
//
// In addition, every client wanting to predict some exception within a prediction context is also mapped with a Map
// to the PredictionContext/Exception pair. This is so that when a thread tells us that it "changed its prediction
// status", we know whom to notify. Perhaps it would be better if this logic was moved onto the threads themselves
// in a later version.
public class PredictionThreadManager {

    // The time to wait before we kill a thread if it has no clients. Set this to 0 and watch the world burn before your
    // eyes
    private static final long WAIT_DIE_TIME = 5000; // ms

    // < <PredictionContext name-exceptionClass name>, PredictionThread>
    private Map<String,PredictionThread> allThreads = new HashMap<>();

    // < <PredictionContext name-exceptionClass name>, multiple ClientThread listeners >
    private Map<String,ArrayList<ClientThread>> listeners = new HashMap<>();

    private SharedServerState state;

    public PredictionThreadManager(SharedServerState state) {
        this.state = state;
    }

    // This is an auxiliary method. Since we didn't use a Pair<T,K> class (which we really should have!!) this gives
    // us a key to use in the Map.
    private String mapKeyFromContextAndException(PredictionContext ctx, PreXException exception) {
        return ctx.getName() + "-" + exception.getExceptionClass();
    }

    // A client thread should use this to tell the manager that it no longer cares about some prediction.
    public synchronized void stopPredicting(PredictionContext ctx, PreXException exception, ClientThread thread) {
        String key = mapKeyFromContextAndException(ctx, exception);

        synchronized (listeners) {

            // Remove the thread from the list of threads interested in this prediction
            if (listeners.containsKey(key))
                listeners.get(key).remove(thread);


            // If no more listeners, ask the thread to kill itself _IN A WHILE_
            if (listeners.containsKey(key) && listeners.get(key).isEmpty()) {
                listeners.remove(key);
                if (allThreads.containsKey(key)) // Sanity check!
                    allThreads.get(key).pleaseDieAt(System.currentTimeMillis() + WAIT_DIE_TIME);
            }
        }
    }

    /*
    public synchronized boolean getCurrentPrediction(PredictionContext ctx, PreXException exception) {
        String key = mapKeyFromContextAndException(ctx, exception);
        if (allThreads.containsKey(key)) {
            //   System.err.println("WARN: Tried to start predicting while already predicting! " + ctx);
            allThreads.get(key).dontDie();
            if ( !allThreads.get(key).isAlive() ) {
                PredictionThread predictionThread = new PredictionThread(ctx, exception, this);
                allThreads.put(key, predictionThread);
                predictionThread.start();
            }

            //FIXME: Once again fugly code repetitin
            return allThreads.get(key).getCurrentPrediction();
        } else {
            PredictionThread predictionThread = new PredictionThread(ctx, exception, this);
            allThreads.put(key, predictionThread);
            predictionThread.start();
        }

        return false;
    }*/

    // A client thread should invoke this method when it wants to predict some exception in some prediction context.
    // The method returns the current prediction state (it could have already started predicting!)
    public synchronized boolean startPredicting(PredictionContext ctx, PreXException exception, ClientThread thread) {
        boolean ret = false;
        if ( !state.doPredictions() ) return false;

        String key = mapKeyFromContextAndException(ctx, exception);

        if (allThreads.containsKey(key)) {
            // If there's already a thread, ask it to stay alive!
            allThreads.get(key).dontDie();
            if ( !allThreads.get(key).isAlive() ) {
                // In spite of our efforts, it might have already died...
                // (means it also has been removed from allThreads already)
                startThread(ctx,exception);
                ret = false;
            } else {
                // It is still alive! Use its current prediction
                ret = allThreads.get(key).getCurrentPrediction();
            }
        } else {
            // There is no thread doing these predictions yet. Start it!
            startThread(ctx,exception);
        }

        // Add this client to the listeners interested in this prediction.
        synchronized (listeners) {
            if (listeners.containsKey(key)) {
                listeners.get(key).add(thread);
            } else {
                ArrayList<ClientThread> l = new ArrayList<>();
                l.add(thread);
                listeners.put(key, l);
            }
        }

        return ret;

    }

    private void startThread(PredictionContext ctx, PreXException exception) {
        String key = mapKeyFromContextAndException(ctx, exception);
        PredictionThread predictionThread = new PredictionThread(ctx, exception, this);
        allThreads.put(key, predictionThread);
        predictionThread.start();
    }

    // Use to notify the manager that there is a new best model. The manager propagates the notification to its
    // relevant threads.
    public void notifyNewBest(PredictionContext ctx, PreXException exception) {
        String key = mapKeyFromContextAndException(ctx,exception);
        System.out.println("New best model for " + key);
        if ( allThreads.containsKey(key) )
            allThreads.get(key).notifyNewBest();
    }

    // Prediction threads are supposed to invoke this method when their prediction changes. The manager then propagates
    // the notification to the clients.
    public void notifyOfPredictionState(PredictionContext ctx, PreXException exception, boolean state) {
        String key = mapKeyFromContextAndException(ctx,exception);
        synchronized (listeners) {
            if (listeners.containsKey(key)) { // Sanity check!
                for (ClientThread c : listeners.get(key))
                    try {
                        c.notifyOfPredictionState(ctx, exception, state);
                    } catch (IOException e) {
                        System.err.println("Seems like we have a dead client/thread! FIXME need to remove!"); //FIXME
                    }
            } else {
                System.err.println("WARN: We are apparently predicting for no reason!");
            }
        }
    }

    // A prediction thread uses this to notify the manager that it has died.
    public synchronized void notifyStoppedPredicting(PredictionThread predictionThread) {
        String key = mapKeyFromContextAndException(predictionThread.getContext(), predictionThread.getException());
        if (allThreads.containsKey(key)) { // Sanity check!
            allThreads.remove(key);
            System.out.println("Predictions have stopped for " + key);
        } else
            System.out.println("Predictions have stopped for " + key + " but we weren't doing it!");
    }

}
