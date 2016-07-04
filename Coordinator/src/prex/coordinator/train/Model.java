package prex.coordinator.train;

import prex.common.PreXException;
import prex.common.PredictionContext;
import prex.coordinator.preprocess.SummarizedDataset;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Instances;
import weka.core.Utils;

import java.io.*;
import java.util.Random;

/**
 * A model encapsulates a trained Weka model for some given exception, within some given prediction context, for some
 * value of the (T,k) parameters. The most recent validation metrics are stored for comparison.
 *
 * Models can be compared to ascertain which is the best model. See the compareTo() implementation for more information.
 */
public class Model implements Serializable, Comparable<Model> {

    // Context, exception, T and k. All the training parameters
    private PredictionContext context;
    private PreXException exception;
    private int T, k;

    // Weka model and training instances. The instances are stored as somewhat of a hack. Without them, the class
    // somehow labels got screwed up. The trainInstances variable contains no real instances, only the class information
    private Classifier model;
    private Instances trainInstances;

    // Performance metrics computed with the most recent data
    private double FPR, FNR, fMeasure, TPR, TNR;

    // Load the best available model for the given prediction context and exception within the "base" directory.
    public static Model loadBest(String base, PredictionContext context, PreXException exception) {
        try {
            FileInputStream fin = new FileInputStream(base + "/" + context.getName() + "_" + exception.getExceptionClass() +"_" + "best" + ".prexmodel");
            ObjectInputStream o = new ObjectInputStream(fin);
            Model d = (Model)o.readObject();
            o.close();
            System.out.println("Classifier: " + d.model.getClass().getName() + " " + d.model);
            System.out.println("Classifier: " + d.model.getClass().getName() + " " + d.model);
            System.out.println("FPR: " + d.FPR + ", FNR: " + d.FNR + ", f-measure: " + d.fMeasure + " TNR " + d.TNR + "FPR: " + d.FPR);
            System.out.println("T: " + d.T + " k: " + d.k);
            return d;
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("No best model for " + context + " " + exception);
        }
        return null;
    }

    public Model(PredictionContext context, PreXException exception, int t, int k) {
        this.context = context;
        this.exception = exception;
        T = t;
        this.k = k;
    }

    // Save this as the best model in the "base" directory
    public void saveAsBest(String base) {
        try {
            FileOutputStream fout = new FileOutputStream(base + "/" + context.getName() + "_" + exception.getExceptionClass()+"_" + "best.prexmodel");
            ObjectOutputStream o = new ObjectOutputStream(fout);
            o.writeObject(this);
            o.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Train the model for the given instances, with a given classifier. Note that the current classifier is set
    // to the one passed as a parameter. It isn't the prettiest way of doing things, but it works.


    public void train(Instances trainData, Classifier c) {
        try {
            this.model = c;

            // Randomize and copy instances
            Random rand = new Random();
            Instances randData = new Instances(trainData);
            randData.randomize(rand);


            c.buildClassifier(randData);

            // Set trainInstances to contain the corrent class values. Note how we don't really store the instances
            // themselves!
            this.trainInstances = new Instances(trainData);
            trainInstances.clear();

            // Evaluate the model for the first time, setting the performance metrics and outputting some info.
            reevaluate(randData);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // This is used when making predictions. At least one instance should be provided, and if more than one instance
    // is provided, then only the first is used (you would only have one window to classify)
    public synchronized boolean classify(Instances classificationData) {
        if ( classificationData.size() == 0) {
            System.err.println("WARN: Not enough data for classification!");
            return false;
        }
        try {

            // Print out the classifier name and the prediction while we're at it.
            System.out.println("Classifier: " + this.model.getClass().getName() + " " + this.model);

            // We add the data to the training instances to presere the original labels. It's messu and hackish, but
            // it works!
            trainInstances.add(classificationData.get(0));
            double pred = this.model.classifyInstance(trainInstances.get(0));
            trainInstances.clear(); // Classification done, go away!

            System.out.println("PREDICTION: " + pred);

            return trainInstances.classAttribute().value((int) pred).equalsIgnoreCase("true");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public int getT() {
        return T;
    }

    public int getK() {
        return k;
    }

    public double getFPR() {
        return FPR;
    }

    public double getFNR() {
        return FNR;
    }

    public double getfMeasure() {
        return fMeasure;
    }

    public double getTPR() {
        return TPR;
    }

    public double getTNR() {
        return TNR;
    }

    public Classifier getClassifier() {
        return model;
    }

    // Auxiliary method to compare two models based on their fMeasure (-1,0,1)
    private int fMeasureComparison(Model o) {
        return fMeasure < o.fMeasure ? -1 : (fMeasure == o.fMeasure ? 0 : 1) ;
    }

    // Auxiliary method to compare two models based on their FPR (-1,0,1)
    private int FPRComparison(Model o) {
        return FPR > o.FPR ? -1 : (FPR == o.FPR ? fMeasureComparison(o) : 1) ;
    }

    // First compare two models using the F-Measure. If the are both "sufficiently accurate" (75%), pick the one with
    // the lowest FNR (so we don't miss exceptions)
    @Override
    public int compareTo(Model o) {
        //What's below solves comparisons when models aren't fully trained. That's not a problem in the current version.
        //if ( FNR == 0 || FPR == 0 || o.FNR == 0 || o.FPR == 0 )
            //return fMeasureComparison(o);

        if ( fMeasure >= 0.75 && o.fMeasure >= 0.75 ) {
            // Want to minimize FNR
            return FNR > o.FNR ? -1 : (FNR == o.FNR ? FPRComparison(o) : 1) ;
        } else
            return fMeasureComparison(o);
    }

    // (Re)Evaluate the model on the instances. We do this with 10-fold cross-validation
    public void reevaluate(Instances data) {
        try {
            int NUM_FOLDS = 10;
            Evaluation eval = new Evaluation(data);
            eval.crossValidateModel(model, data, NUM_FOLDS, new Random(1));
            int trueClass = data.classAttribute().indexOfValue("true");

            FPR = eval.falsePositiveRate(trueClass);
            FNR = eval.falseNegativeRate(trueClass);
            TPR = eval.truePositiveRate(trueClass);
            TNR = eval.trueNegativeRate(trueClass);
            fMeasure = eval.fMeasure(trueClass);

            // Print out useful metrics. Of course if they're all being trained in parallel this is going to be a
            // mess of interleaved output!
            System.out.println(eval.toSummaryString("=== " + NUM_FOLDS + "-fold Cross-validation ===", false));
            System.out.println("FPR: " + FPR + ", FNR: " + FNR + ", f-measure: " + fMeasure + " TNR " + TNR + "FPR: " + FPR);
            System.out.println("True Positives: " + eval.numTruePositives(trueClass));
            System.out.println("False Positives: " + eval.numFalsePositives(trueClass));
            System.out.println(eval.toSummaryString());
            System.out.println(eval.toMatrixString());
            /*double[][] confusionMatrix = eval.confusionMatrix();
            for (double[] r : confusionMatrix) {
                for (double d : r)
                    System.out.print(d + " ");
                System.out.println();
            }*/

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
