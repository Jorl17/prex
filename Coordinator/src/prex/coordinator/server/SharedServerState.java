package prex.coordinator.server;

import prex.common.PreXTimestamp;
import prex.coordinator.db.DB;
import prex.coordinator.train.PredictionThreadManager;
import prex.coordinator.train.TrainingManager;

import java.util.concurrent.atomic.AtomicBoolean;

// This is just a big goofy mess to share data among all of these highly coupled structures. Sorry not sorry.
public class SharedServerState {

    // Are we currently in the middle of a run?
    private AtomicBoolean running;

    // When did the run start?
    private PreXTimestamp start;

    // Our fellow managers.
    private PredictionThreadManager predictionThreadManager;
    private TrainingManager trainingManager;

    // Is the coordinator in "make predictions" mode?
    private boolean doPredictions;

    public SharedServerState(boolean running, boolean doPredictions) {
        this.running = new AtomicBoolean(running);
        this.start = null;
        this.predictionThreadManager = new PredictionThreadManager(this);
        this.trainingManager = new TrainingManager(predictionThreadManager);
        this.doPredictions = doPredictions;
        trainingManager.start(); // FIXME: It is a bit ugly that we manually start the manager. Perhaps change this?
    }

    public synchronized void setRunning(boolean running) {
        if (this.running.get() && !running) {
            // Started and Stopped
            PreXTimestamp end = new PreXTimestamp();
            System.out.println("Stopped run started at " + start + " at " + end);
            DB.getInstance().logRun(start, end);
        }else if (!this.running.get() && running) {
            // Started and Stopped
            start = new PreXTimestamp();
            System.out.println("Starting run at " + start);
        } else {
            System.err.println("WARN: Trying to set running to already existing state (" + running + ")");
        }
        this.running.set(running);
    }

    public boolean isRunning() {
        return running.get();
    }

    public PredictionThreadManager getPredictionThreadManager() {
        return predictionThreadManager;
    }

    public TrainingManager getTrainingManager() {
        return trainingManager;
    }

    public boolean doPredictions() {
        return doPredictions;
    }
}
