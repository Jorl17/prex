package prex.client;

import prex.common.PredictionInformationObject;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

// See the documentation of PrexClient to understand how this class works. Pretty much like mentioned there, these
// are the first two classes I'll refactor when I can, because they have become spaghetti monsters with inappropriate
// structures. They work, but this could all be shorter and better
public class Try {

    // The current prediction context (although it was already stored in the client)
    private String predictionContext;

    // The code within the try block
    private ConsumerWithExceptions<Try> r;

    // The PrexClient that originated this Try block, with whom we have to communicate
    private PrexClient prexClient;

    // All <Exception Class, handler> maps (contains the "code" within the prevent block)
    private Map<Class<? extends Exception>, Consumer<PredictionInformationObject>> preventHandlers;

    // All <Exception Class, catch handler> maps (contains the "code" within the catch block)
    private Map<Class<? extends Exception>, Consumer<Exception>> catchHandlers;

    protected Try(String predictionContext, ConsumerWithExceptions<Try> r, PrexClient prexClient) {
        this.predictionContext = predictionContext;
        this.r = r;
        this.prexClient = prexClient;
        this.preventHandlers = new HashMap<>();
        this.catchHandlers = new HashMap<>();
    }

    public static Try Try(String predictionContext, ConsumerWithExceptions<Try> r, PrexClient prexClient) {
        return new Try(predictionContext, r, prexClient);
    }

    // Prevent just stores the higher-order function
    public Try Prevent(Class<? extends Exception> e, Consumer<PredictionInformationObject> handler) {
        this.preventHandlers.put(e, handler);
        return this;
    }

    // Catch just stores the higher-order function
    public Try Catch(Class<? extends Exception> e, Consumer<Exception> handler) {
        this.catchHandlers.put(e, handler);
        return this;
    }

    // This function is one of those "shoe-horned" functions. It accepts a map of <exception class, T>, where T
    // can be anything (a catch function or a prevent function). It then iterates the map to find any key (exception
    // class) within that map that the provided "key" inherits from and returns its value. In other words, if the map
    // contains java.lang.Exception, then this will always the return java.lang.Exception value, because all exceptions
    // inherit from it. If the map contains SQLException, then passing key=SQLDataException will return the value
    // associated with SQLException.
    //
    // This is really ugly, and clearly a map wasn't the best structure for this (or this wasn't the way to implement
    // the search), but it works for now.
    private <T> T getKeyOrParentClass(Map<Class<? extends Exception>, T> m, Class<? extends Exception> key) {
        if (m.containsKey(key)) return m.get(key);

        for ( Class<? extends Exception> c : m.keySet() )
            if ( c.isAssignableFrom(key) )
                return m.get(c);
        return null;
    }

    // See the documentation for getKeyOrParentClass. This class does the same but returns the actual key. So if you
    // have a SQLException key and pass key=SQLDataException, the SQLException key will be returned.
    private <T> Class<? extends Exception> realMapClass(Map<Class<? extends Exception>, T> m, Class<? extends Exception> key) {
        if (m.containsKey(key)) return key;

        for ( Class<? extends Exception> c : m.keySet() )
            if ( c.isAssignableFrom(key) )
                return c;
        return null;
    }

    // See the documentation for getKeyOrParentClass. This just returns true if any key is found.
    private <T> boolean mapContainsKeyOrParentClass(Map<Class<? extends Exception>, T> m, Class<? extends Exception> key) {
        return getKeyOrParentClass(m,key) != null;
    }

    // The synchronous version of the try-prevent-catch
    public void sync() {
        // Notify the coordinator that we have entered a prediction context
        prexClient.startPredictionContext(predictionContext);

        // Notify the coordinator that we want to get predictions for all these exceptions
        for ( Class<? extends Exception> e : preventHandlers.keySet() )
            prexClient.startPredicting(e);

        // Execute the code within the try block. Note that the developer should have included a call to
        // check() within that block.
        try {
            r.consume(this);
        } catch (Exception e) {
            // If an exception happens, we check if we want to predict it (using hierarchy). If we do, then this
            // exception must be logged to the server, for future training. Note also that we want to log
            // the BASE class and not the class that actually triggered the exception
            if ( mapContainsKeyOrParentClass(preventHandlers,e.getClass()) ) {
                // Log the base class to the coordinator
                prexClient.exceptionClass(realMapClass(preventHandlers, e.getClass()));
            }

            // If we also want to catch this exception, then we have a handler for it and should invoke it right now.
            // Note that, again, we use hierarchy for this
            if ( mapContainsKeyOrParentClass(catchHandlers,e.getClass()) ) {
                getKeyOrParentClass(catchHandlers,e.getClass()).accept(e);
            } else {
                System.err.println("Caught exception not in handlers!" + e);
            }
        }

        // We are all done with predicting exceptions
        prexClient.endPredictionContext(predictionContext);
    }

    // The asynchronous version of the try-prevent-catch. This is currently a very simple implementation which merely
    // uses the synchronous version and loops checking for exceptions (remember that check might jump to the prevent
    // handler). We would be MUCH better off with just idlying while waiting for data, but that's life.
    public void async() {
        Thread preventThread = new Thread(() -> {
            try {
                Thread.sleep(1000); // Wait a second before we start checking for predictions
                while (!Thread.interrupted()) {
                    check();
                    Thread.sleep(100);

                }
            } catch (InterruptedException e) {
                // Exit because we were interrupted!
            }
        });
        preventThread.start();
        sync();
        preventThread.interrupt();
    }

    // Check for any predictions.
    public synchronized void check() {
        PredictionInformationObject predictionInformationObject = prexClient.checkPredictions();

        // One exception is predicted! Which one? Do we care about it?
        if ( predictionInformationObject != null ) {
            String exceptionClass = predictionInformationObject.getException().getExceptionClass();

            // Do we care about it?
            for (Class<? extends Exception> c : preventHandlers.keySet())
                if ( exceptionClass.equals(c.getName()) ) {
                    //FIXME: Do we lose some generality here? Maybe not
                    // Invoke the handler!
                    preventHandlers.get(c).accept(predictionInformationObject);
                    return;
                }
        }
    }

    public void sample(String name, float value) {
        prexClient.sample(name, value);
    }
}
