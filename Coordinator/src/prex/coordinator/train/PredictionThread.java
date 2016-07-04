package prex.coordinator.train;

import prex.common.PreXException;
import prex.common.PredictionContext;
import prex.common.PreXTimestamp;
import prex.coordinator.preprocess.Dataset;
import prex.coordinator.preprocess.SummarizedDataset;
import weka.core.Instances;

import java.io.ObjectOutputStream;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

// This is a thread dedicated to making predictions for a given exception within a given prediction context.
// It automatically loads the best model (and it reloads models if a new one is out).
//
// The thread can be notified of the new best model, and it can be asked to stop. Furthermore, it can be asked
// to stop "at some time in the future". The main idea of this is that the PredictionThreadManager might change its
// mind and then tell it to keep going instead of dying. This makes efficient use of threads, allowing threads for the
// same prediction context and exception to live a bit longer even if they are predicting "for no-one". See the thesis
// for more details.
//
// Note also that it is the sole responsibility of the PredictionThreadManager to guarantee that only one
// PredictionThread exists for each exception and prediction context. This makes programming the thread itself much
// easier
public class PredictionThread extends Thread {
    // Context and exception for which to predict
    private PredictionContext context;
    private PreXException exception;

    // Should we stop and kill ourselves?
    private AtomicBoolean stop = new AtomicBoolean(false);

    // Is there a new best that we should load?
    private AtomicBoolean newBest = new AtomicBoolean(false);

    // Who's our daddy?
    private PredictionThreadManager manager;

    // When should we die? -1 for never.
    private final AtomicLong dieTime = new AtomicLong();

    // What is the current prediction? True (exception) or false (all is well)?
    private AtomicBoolean currentPrediction = new AtomicBoolean(false);

    public PredictionThread(PredictionContext context, PreXException exception, PredictionThreadManager manager) {
        this.context = context;
        this.exception = exception;
        this.manager = manager;
        dieTime.set(-1);
    }

    @Override
    public void run() {
        System.out.println("Starting predictions for " + context + " " + exception);
        Model currentBestModel = Model.loadBest(".", context, exception);
        if ( currentBestModel == null ) {
            //System.err.println("Loaded no best model!!! for " + context +", " + exception);
            manager.notifyStoppedPredicting(this);
            return;
        }

        while ( !stop.get() ) {
            synchronized (dieTime) {
                long l = dieTime.get();
                if ( System.currentTimeMillis() >= l && l != -1 )
                    break; // Bye-bye, cruel life!
            }

            // If there's a new best, load it!
            if ( newBest.get() ) {
                newBest.set(false);
                currentBestModel = Model.loadBest(".", context, exception);
                if (currentBestModel != null)
                    System.err.println("Loaded newbest for " + context + ", " + exception);
                else
                    break;

            }

            // Here be dragons.
            try {
                PreXTimestamp startTime = new PreXTimestamp();

                // Sleep for T*k ms, enough to get the necessary data.
                Thread.sleep(currentBestModel.getT()* currentBestModel.getK());

                // Get data since before we started sleeping. FIXME: Might want to get the current time and
                // subtract T*k ms to get more accurate data
                Dataset d = new Dataset(context, exception, -1 /* -1 ends up meaning "run-time data" */);
                d.gatherSamplesSince(startTime);

                // Apply the algorithm to build the windows and instances.
                // Set unique parameters
                SummarizedDataset summarizedDataset = new SummarizedDataset(context, exception, currentBestModel.getT(), currentBestModel.getK());

                // Add the data. Since it has no run associated, we need to pass the start and end time (now). Note also
                // that we pass "true" to tell it that this is data for prediction
                summarizedDataset.addRun(d, startTime.asTimestamp(), new PreXTimestamp().asTimestamp(), true);

                // FIXME: It's a bit nasty but we have to save the dataset before getting the instances.
                summarizedDataset.save(".");

                // Get the instances. There should really only be one instance here.
                Instances instances = summarizedDataset.getInstances(".");
                boolean classify = currentBestModel.classify(instances);



                // Check if the prediction changed. If it has, notify the prediction manager so it tells all interested
                // parties.
                boolean oldState = currentPrediction.get();
                currentPrediction.set(classify);
                if (oldState != classify) {
                    // Thi is also quite ugly. Set the time of this prediction
                    Calendar cal = Calendar.getInstance(); cal.setTime(startTime.asTimestamp());
                    cal.add(Calendar.MILLISECOND, currentBestModel.getT()*(currentBestModel.getK()));
                    Timestamp predictionT = new Timestamp(cal.getTime().getTime());
                    manager.notifyOfPredictionState(context, new PreXException(new PreXTimestamp(predictionT), exception.getExceptionClass()), classify);
                }
            } catch (InterruptedException e) {

            }
        }

        // We're all done and we must tell this to the manager before it tries to tell us to do more work!
        manager.notifyStoppedPredicting(this);
    }

    public void stopPredicting() {
        stop.set(true);
        interrupt();
    }

    public void notifyNewBest() {
        newBest.set(true);
        interrupt();
    }

    // Ask the thread to kill itself at the given time (in ms from currentTimeMillis)
    public void pleaseDieAt(long ms) {
        dieTime.set(ms);
    }

    // Tell the thread that we don't really mean it any harm and it should stay alive....for now
    public void dontDie() {
        synchronized ( dieTime ) {
            dieTime.set(-1);
        }
    }


    public PredictionContext getContext() {
        return context;
    }

    public PreXException getException() {
        return exception;
    }

    public boolean getCurrentPrediction() {
        return currentPrediction.get();
    }
}
