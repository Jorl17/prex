package prex.coordinator.train;

import prex.common.PreXException;
import prex.common.PredictionContext;
import prex.coordinator.preprocess.SummarizedDataset;
import weka.classifiers.Classifier;
import weka.classifiers.functions.MultilayerPerceptron;
import weka.classifiers.functions.SMO;
import weka.classifiers.lazy.IBk;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.REPTree;
import weka.classifiers.trees.RandomForest;
import weka.classifiers.trees.RandomTree;
import weka.core.Instances;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

// The training manager loops waiting for training requests. Once they appear, they are dispatched, by training
// multiple models with several (T,k) parameter combinations (which are supplied) and comparing them to determine
// the winner. This is very CPU-intensive and so it should be done either for a small number of parameter combinations
// or when there is nothing much going on involving the coordinator.
public class TrainingManager extends Thread {
    private static final long MAX_TRAINING_WAIT_TIME = 15*60*1000; // ms
    private PredictionThreadManager predictionThreadManager;

    // The queue where training requests are queued
    private BlockingQueue<Object[]> requestQueue = new LinkedBlockingQueue<>();

    // Should we stop and die?
    private AtomicBoolean stop = new AtomicBoolean(false);

    public TrainingManager(PredictionThreadManager predictionThreadManager) {
        this.predictionThreadManager = predictionThreadManager;
    }

    public void train(PredictionContext ctx, PreXException exception, int[] Ts, int[] ks ) {
        if ( stop.get() )
            return;

        System.out.println("Starting training for " + ctx.getName() + " " + exception.getExceptionClass());

        // Create a threadpool with 6 different threads that will work concurrently
        ExecutorService executorService = Executors.newFixedThreadPool(6);

        // Will hold all the trained models
        ArrayList<Model> models = new ArrayList<>();

        // Load best model and submit its reevaluation on the current data
        Model currBestModel = Model.loadBest(".", ctx, exception);
        if (currBestModel != null) {
            Instances instances = getInstancesFor(ctx, exception, currBestModel.getT(), currBestModel.getK());
            executorService.submit(() -> currBestModel.reevaluate(instances));
            models.add(currBestModel);
        }


        //int[] Ts = {/*200,200,200,200,200,   500,500,/*};*/   /*1000, 2000, */2500,    5000,5000,10000,  }; //15000};//2000};
        //int[] ks = {/*1,  2,  3,  4,  5,     1,2,/*};*/       /*10,   5,    */4,       2,   1,   1,     };//  1};

        // Now train several (currently hard-coded) models. Note that we start with the larger values of T
        // first (we assume they are ordered in increasing size. This is because those train faster.
        for (int i = Ts.length-1; i >= 0; i--) {

            int T = Ts[i], k = ks[i];

            // Get the instances for this prediction (T,k) combination
            Instances instances = getInstancesFor(ctx, exception, T, k);

            // This is just a helpful function that calls addModel. This way we don't repeat so much of this code and
            // can just do trainModel.accept(c).
            Consumer<Classifier> trainModel = (c) -> addModel(models, ctx, exception, T, k, instances, executorService, c);

            // J48
            trainModel.accept(new J48());

            // RandomTree (depth 2)
            RandomTree randomTree = new RandomTree();
            randomTree.setMaxDepth(2);
            trainModel.accept(randomTree);

            // RandomTree (depth 3)
            randomTree = new RandomTree();
            randomTree.setMaxDepth(3);
            trainModel.accept(randomTree);

            // RandomTree (depth 5)
            randomTree = new RandomTree();
            randomTree.setMaxDepth(5);
            trainModel.accept(randomTree);

            // RandomTree (unlimited depth)
            trainModel.accept(new RandomTree());

            // REPTree (depth 2)
            REPTree repTree = new REPTree();
            repTree.setMaxDepth(2);
            trainModel.accept(repTree);

            // REPTree (depth 3)
            repTree = new REPTree();
            repTree.setMaxDepth(3);
            trainModel.accept(repTree);

            // REPTree (depth 5)
            repTree = new REPTree();
            repTree.setMaxDepth(5);
            trainModel.accept(repTree);

            // REPTree (unlimited depth)
            trainModel.accept(new REPTree());

            // MultiLayerPerceptron (as many neurons as features)
            MultilayerPerceptron perceptron = new MultilayerPerceptron();
            perceptron.setLearningRate(0.3);
            perceptron.setHiddenLayers("a");
            perceptron.setMomentum(0.2);
            perceptron.setTrainingTime(100);
            perceptron.setNormalizeAttributes(true);
            perceptron.setNominalToBinaryFilter(true);
            perceptron.setNormalizeNumericClass(true);
            trainModel.accept(perceptron);

            // MultiLayerPerceptron (as many neurons as features, two hidden layers)
            perceptron = new MultilayerPerceptron();
            perceptron.setLearningRate(0.3);
            perceptron.setHiddenLayers("a,a");
            perceptron.setMomentum(0.2);
            perceptron.setTrainingTime(100);
            perceptron.setNormalizeAttributes(true);
            perceptron.setNominalToBinaryFilter(true);
            perceptron.setNormalizeNumericClass(true);
            trainModel.accept(perceptron);

            // MultiLayerPerceptron (10 neurons)
            perceptron = new MultilayerPerceptron();
            perceptron.setLearningRate(0.3);
            perceptron.setHiddenLayers("10");
            perceptron.setMomentum(0.2);
            perceptron.setTrainingTime(100);
            perceptron.setNormalizeAttributes(true);
            perceptron.setNominalToBinaryFilter(true);
            perceptron.setNormalizeNumericClass(true);
            trainModel.accept(perceptron);

            // RandomForest
            trainModel.accept(new RandomForest());

            // IBK (5)
            trainModel.accept(new IBk(5));

            // IBK (30)
            trainModel.accept(new IBk(30));

            // IBK (100)
            trainModel.accept(new IBk(100));
        }

        // Request that all work is quickly terminated
        executorService.shutdown();

        // Wait at most MAX_TRAINING_WAIT_TIME milliseconds until we compare the models
        try {
            executorService.awaitTermination(MAX_TRAINING_WAIT_TIME, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {}

        // Kill all the remaining services (they may still go on but we're going to determine the best model now)
        executorService.shutdownNow();

        System.out.println("Comparing models for " + ctx.getName() + " " + exception.getExceptionClass());

        Model newBestModel = determineBestModel(models);
        if ( newBestModel == null )
            System.err.println("Error training models!");
        else if ( currBestModel == null || newBestModel != currBestModel ) {
            // If there was a new best, save it and notify the prediction thread
            newBestModel.saveAsBest(".");
            predictionThreadManager.notifyNewBest(ctx, exception);
        }
    }

    // Loads the appropriate summarized dataset for the given context, exception and (T,k) parameter combination
    private Instances getInstancesFor(PredictionContext ctx, PreXException exception, int T, int k) {
        SummarizedDataset summarizedDataset = new SummarizedDataset(ctx, exception, T, k);
        summarizedDataset.buildFromAllRuns("."); //Automatically saves the topruns file needed for getInstances
        return summarizedDataset.getInstances(".");
    }

    // Creates a new model, adds it to the list of models and submits its training for processing. This is a bit ugly,
    // but it works.
    private void addModel(ArrayList<Model> models, PredictionContext ctx, PreXException exception, int T, int k, Instances instances, ExecutorService executorService, Classifier c) {
        Model model = new Model(ctx, exception, T, k);
        models.add(model);
        executorService.submit(() -> model.train(instances, c));
    }

    // Determines the best model. We currently sort them all, which isn't the fastest way to do it, but this way
    // we got to see them ranked instead of just picking the best
    private Model determineBestModel(ArrayList<Model> models) {
        // These are sorted worse-to-best
        Collections.sort(models);

        for (int i = 0; i < models.size(); i++) {

            Model m = models.get(i);
            if (m.getClassifier() == null) {
                System.out.println("Invalid model (probably didn't finish training)!");
                continue;
            }
            System.out.println("Model " + i + ": " + m.getClassifier().getClass().getName());
            System.out.println("FPR: " + m.getFPR());
            System.out.println("FNR: " + m.getFNR());
            System.out.println("F-Measure: " + m.getfMeasure());
            System.out.println("T,k: " + m.getT() + "," + m.getK());
        }

        // Either there is no best model, in which case we should return null, or the best model is the last
        return models.isEmpty() ? null : models.get(models.size()-1);
    }

    @Override
    public void run() {

        // Wait for requests, which were put in the form of a context and an exeption (this is very ugly, FIXME)
        // and process them. Only one thing can be trained at a time!
        while ( !stop.get() ) {
            try {
                Object[] ctxAndException = requestQueue.take();

                PredictionContext ctx = (PredictionContext) ctxAndException[0];
                PreXException ex = (PreXException) ctxAndException[1];
                int[] T = (int[]) ctxAndException[2];
                int[] k = (int[]) ctxAndException[3];
                train(ctx, ex, T, k);
            } catch (InterruptedException e) {
            }
        }
    }

    public void stopAllTraining() {
        stop.set(true);
        interrupt();
    }
}
