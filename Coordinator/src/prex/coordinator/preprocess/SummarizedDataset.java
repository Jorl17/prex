package prex.coordinator.preprocess;

import prex.common.*;
import prex.coordinator.db.DBUtils;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ConverterUtils;
import weka.filters.supervised.instance.Resample;

import java.io.*;
import java.sql.Timestamp;
import java.util.*;

// This class grabs a Dataset and processes it according to the PreX pre-processing algorithm. This involves:
//    1. Time-Window construction: for each individual run, build windows of size T and summarize the features within
//       them with a set of summarized features (mean, max, min, etc). A window is labeled as "containing" exceptions
//       or not.
//    2. Window-Merging: Merge k windows with a sliding window algorithm. The features are named pre-fixed with
//       W<n> label where n is the number of the window. For k=2 there can be W1 and W2. The classification label
//       of the window is the one of the first window right after the merged-windows. This is the same as using l=1
//       in PreX's algorithm. Future versions can easily support different values.
//   3. The dataset is then balanced using Weka's resampling
//
// Not that this SummarizedDataset can also be written to and loaded from disk, forming another way of caching otherwise
// computationally expensive operations on the database and on its returned data.
//
// Lastly, note that the class automatically creates and manages its own instances of the Dataset class. You don't need
// to create it and feed it manually, except in rare cases (e.g. prediction at run-time).
public class SummarizedDataset implements Serializable {

    // The following uniquely identify a SummarizedDataset
    private PredictionContext context;
    private PreXException exception;
    private int T, k;

    // All of the features. This includes the W<n> prefixes.
    private ArrayList<String> features;

    // Map containing the data for each run number. In practice, we could flatten it down to only a an ArrayList
    // of instances, but it might be useful to know which run they belong to.
    // <run number, data> where data is [instance1,instance2,instance3...] and instance = [feature1,feature2,feature3...]
    private Map<Integer,ArrayList<ArrayList<Float>>> data;

    // These instances are passed to a Model for training. They are "Weka-ready". We don't save them to disk because
    // it would essentially duplicat the data already in the previous variable (data). If we need the instances, we
    // just re-build them on-demand.
    private transient Instances instances = null;

    public SummarizedDataset(PredictionContext context, PreXException exception, int T, int k) {
        this.context = context;
        this.exception = exception;
        this.T = T;
        this.k = k;
        data = new HashMap<>();
    }

    // Auxiliary method to determine the top run contained in this dataset.
    private int topRun() {
        ArrayList<Integer> runs = new ArrayList<>(data.keySet());
        return Collections.max(runs);
    }

    // Auxiliary method to determine the top run currently in the database
    private int getCurrentDBTopRun() {
        return DBUtils.withConnection(ExecutionRun::numRuns);
    }

    // Load a SummarizedDataset from disk. It modifies the current instance.
    private void buildFromFile(String filename) throws IOException, ClassNotFoundException {
        FileInputStream fin = new FileInputStream(filename);
        ObjectInputStream o = new ObjectInputStream(fin);
        SummarizedDataset d = (SummarizedDataset)o.readObject();
        this.data = d.data;
        this.features = d.features;
        o.close();
    }

    // Modifies the current instance to contain the summarized dataset for ALL the runs available. It automatically
    // queries the DB to know how many runs there have been. Note that it saves the newly-built summarized dataset to
    // disk, and that it can re-use previous datasets (e.g. if there is only a new run, it re-uses the previous dataset
    // and appends to it the new run).
    public void buildFromAllRuns(String base) {
        int currentDBTopRun = getCurrentDBTopRun();
        System.out.println("Trying to read topRuns file...");
        try {
            buildFromFile(base + "/" + context.getName() + "_" + exception.getExceptionClass() +"_" + T + "_" + k + "_" + currentDBTopRun + ".summary");
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("No current topRuns file! Building it and saving it");
            buildTopRunsFile(base);
            save(".");
        }
    }

    // See buildFromAllRuns.
    private void buildTopRunsFile(String base) {
        int lastRunRecorded = reuseAllPossibleRuns(base, getCurrentDBTopRun());
        for (int runNo = lastRunRecorded; runNo <= getCurrentDBTopRun(); runNo++) {
            //FIXME: I have a strong suspicion that this may go haywire with different prediction contexts at the same time.
            System.out.println("Gathering data for run " + runNo);
            Dataset dataset = new Dataset(context, exception, runNo);
            dataset.gatherSamplesAndExceptions();
            System.out.println("Processing run " + runNo);
            addRun(dataset);
            System.out.println("Done processing " + runNo);


        }
    }

    // Re-use previous datasets instead of fetching the data from the database. See buildFromAllRuns.
    private int reuseAllPossibleRuns(String base, int currentDBTopRun) {
        for (int i = currentDBTopRun-1; i >= 1; i--) {
            try {
                buildFromFile(base + "/" + context.getName() + "_" + exception.getExceptionClass() +"_" + T + "_" + k + "_" + i + ".summary");
                return i;
            } catch (IOException | ClassNotFoundException e) {
            }
        }

        return 1;
    }

    public void save(String base, String name) {
        System.out.println("Saving " + name);
        try {
            FileOutputStream fout = new FileOutputStream(base + "/" + name + ".summary");
            ObjectOutputStream o = new ObjectOutputStream(fout);
            o.writeObject(this);
            System.out.println("Saved summary. Saving CSV next");
            toCSV(base + "/" + name + ".csv");
            o.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Done saving " + name);
    }

    public void save(String base) {
        save(base, context.getName() + "_" + exception.getExceptionClass()+"_" + T + "_" + k + "_" + topRun());
    }

    // FIXME: Must have savedCSV before!
    // This gets a Weka-ready Instances instance, with the data for this SummarizedRun. Note that saveCSV() must have
    // been called before, as we use an extremely hackish way of building the dataset. Since buildFromAllRuns() already
    // save()s the data, which in turn saves the CSV, you should be able to call getInstances() without any hassle
    // after calling buildFromAllRuns()
    public Instances getInstances(String base) {
        try {
            if (instances == null) {
                instances = new ConverterUtils.DataSource(base + "/" + context.getName() + "_" + exception.getExceptionClass() + "_" + T + "_" + k + "_" + topRun() + ".csv").getDataSet();
                //if ( topRun() != -1 ) //FIXME?
                instances.setClassIndex(instances.numAttributes()-1);

                // Resample the data so that it is balanced. Don't do this if it has only one class (e.g. no data
                // at run-time)
                if ( instances.numClasses() > 1 ) {
                    Resample resample = new Resample();
                    resample.setBiasToUniformClass(1.0f);
                    resample.setInputFormat(instances);
                    resample.setNoReplacement(false);
                    resample.setSampleSizePercent(100);

                    instances = Resample.useFilter(instances, resample);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return instances;
    }

    // This is a bit embarassing, and I really hope I improve it in the future. The way we convert the data into
    // weka-format is to just save a CSV and then read it. This allowed for quick-n-dirty development, but it disgusts
    // me a bit. Make a FIXME out of this to improve it in the future.
    //
    // Note that we carefully escape all strings and convert the last number to true or false, so that Weka
    // catches it as a nominal class and not just a binary class made out of floats.
    private void toCSV(String path) {
        String SEP = ";";
        String LINE_SEP = "\n";
        try {
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(path));

            for ( int i = 0; i < features.size()-1;i++ )
                bufferedWriter.write("\"" + features.get(i) + "\"" + SEP);
            bufferedWriter.write("\"" + features.get(features.size()-1)  + "\"" + LINE_SEP);

            for ( int run : data.keySet() )
                for (ArrayList<Float> line : data.get(run) ) {
                    for ( int i = 0; i < line.size()-1;i++ )
                        bufferedWriter.write("\"" + line.get(i) + "\"" + SEP);
                    bufferedWriter.write("\"" + (line.get(line.size()-1)>=0.5f) + "\"" + LINE_SEP); //FIXME: Conversion to boolean (prediction) here
                }

            bufferedWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addRun(Dataset dataset) {
        addRun(dataset, null, null);
    }
    public void addRun(Dataset dataset, Timestamp startT, Timestamp endT) {
        addRun(dataset,startT,endT,false);
    }

    // Add the data from the given dataset. It triggers the algorithm that processes the whole dataset as a run.
    // You can override the start and end timestamps associated with the dataset (this is useful if the dataset has no
    // run, presumably because it was gathered at run-time). You can also inform the algorithm that the data is being
    // passed for prediction. This changes the way it builds the windows so that no data is wasted.
    //
    // The bulkd of the algorithm is in this function. It isn't really pretty, but it works.
    //
    public void addRun(Dataset dataset, Timestamp startT, Timestamp endT, boolean predicting) {
        try {
            //context = dataset.getContext(); //FIXME: I hate that we are setting it here at all times but meh

            // Need the feature names to know what features to build and how to name them
            ArrayList<String> originalFeatureNames = dataset.getFeatureNames();

            // Generate the feature names with the W<N> prefixes.
            generateFeatureNames(originalFeatureNames);

            //System.out.println(features);

            // Each window of features (after time-window construction)
            ArrayList<ArrayList<Float>> runData = timeWindowConstruction(startT,endT,dataset);

            // Grouped windows after window-merge step!
            ArrayList<ArrayList<Float>> groupedRunData = windowMerge(runData, predicting);

            data.put(dataset.getRunNo(), groupedRunData);
            instances = null;
        } catch(Exception e) {
            // Some problem with the runs. Last time I checked, there was a faulty drive in the PreX DEI machines.
        }
    }

    private ArrayList<ArrayList<Float>> windowMerge(ArrayList<ArrayList<Float>> runData, boolean predicting) {
        int L = 1; //the t parameter in (T,k,t), internally referred to as "L"

        ArrayList<ArrayList<Float>> groupedRunData = new ArrayList<>();
        for (int i = 0; i < runData.size() - k - (predicting ? 0 : L); i++) {

            // Will hold all of the merged features for this window (the i-th)
            ArrayList<Float> thisWindow = new ArrayList<>();
            for (int j = 0; j < k; j++) {
                ArrayList<Float> features = runData.get(i + j);
                thisWindow.addAll(features);
                thisWindow.remove(thisWindow.size() - 1); //Remove extra "exceptions" field in each of the windows
            }

            if ( !predicting )
                thisWindow.add(runData.get(i + k + L - 1).get(runData.get(i + k + L - 1).size()-1)); //FIXME
            else
                thisWindow.add(0.0f);
            groupedRunData.add(thisWindow);
        }

        return groupedRunData;
    }

    private ArrayList<ArrayList<Float>> timeWindowConstruction(Timestamp startT, Timestamp endT, Dataset dataset) {
        ArrayList<ArrayList<Float>> runData = new ArrayList<>();

        // Grab copies because we'll be removing samples and exceptions as we go. Remember that the data
        // in each of these is ALWAYS ordered from first to last sample/exception, making the overall algorithm
        // easier to implement.
        Map<String, ArrayList<Sample>> samplesMap = dataset.getSamplesMapCopy();
        ArrayList<RecordedException> exceptions = dataset.getExceptionsCopy();

        // Need the feature names to know what features to build
        ArrayList<String> originalFeatureNames = dataset.getFeatureNames();

        // Grab the execution run (if it exists!)
        ExecutionRun r = DBUtils.withConnection((c) -> ExecutionRun.fromID(c, dataset.getRunNo()));


        // Start at the beginning of the run (or startT if provided). End at end of the run (or endT if provided)
        // Move in steps of T windows.
        Timestamp nextT;
        for (Timestamp t = startT != null ? startT : r.getStart().asTimestamp();
             t.before(endT != null ? endT : r.getFinish().asTimestamp()); t = nextT) {

            // All features and exceptions for this window.
            ArrayList<Float> windowData = new ArrayList<>();
            ArrayList<RecordedException> windowExceptions;

            // Set the start of the nextWindow to t + T (also the end of this window)
            Calendar cal = Calendar.getInstance(); cal.setTime(t);
            cal.add(Calendar.MILLISECOND, T);
            nextT = new Timestamp(cal.getTime().getTime());


            // Find all the exceptions in this window
            windowExceptions = popExceptionsInWindow(exceptions, t, nextT);

            // Iterate ALL features and look for samples. If any samples are found within this window, pop them
            // out of the original data and summarize them. If no samples are found, fill it up with 0s and NaNs.
            // Note how this generates 6 * originalFeatureNames.size() features
            for (String featureName : originalFeatureNames) {
                if (!samplesMap.containsKey(featureName)) {
                    //FIXME: No data at all for this feature! What to do? Right now set it to Float.NaN (or -1?)
                    windowData.add(0.0f); //N
                    windowData.add(Float.NaN); //MEAN
                    windowData.add(Float.NaN); //STDDEV
                    windowData.add(Float.NaN); //MIN
                    windowData.add(Float.NaN); //MAX
                    windowData.add(Float.NaN); //DERIV
                } else {
                    // Build the summarized features!
                    ArrayList<Sample> samples = popSamplesInWindow(samplesMap.get(featureName), t, nextT);
                    windowData.add((float) samples.size()); //N
                    windowData.add(mean(samples));
                    windowData.add(stddev(samples));
                    windowData.add(min(samples));
                    windowData.add(max(samples));
                    windowData.add(deriv(samples));
                }
            }

            // Append the label after ALL 6 * originalFeatureNames.size() of the features.
            windowData.add(windowExceptions.isEmpty() ? 0.0f : 1.0f);

            // This window is done
            runData.add(windowData);
        }

        return runData;
    }

    private float deriv(ArrayList<Sample> samples) {
        if (samples.size() < 2) return -1; //FIXME: Maybe NaN??
        else return (samples.get(samples.size()-1).getValue() - samples.get(0).getValue()) /
                (samples.get(samples.size()-1).getTime().asTimestamp().getTime() - samples.get(0).getTime().asTimestamp().getTime());
    }

    /**
     * Returns the minimum value in the specified array.
     *
     * @param  a the array
     * @return the minimum value in the array <tt>a[]</tt>;
     *         <tt>Double.POSITIVE_INFINITY</tt> if no such value
     */
    public static float min(ArrayList<Sample> a) {
        float min = Float.POSITIVE_INFINITY;
        for (Sample anA : a) {
            if (Float.isNaN(anA.getValue())) continue;//return Float.NaN;
            if (anA.getValue() < min) min = anA.getValue();
        }
        return min;
    }

    public static float max(ArrayList<Sample> a) {
        float max = Float.NEGATIVE_INFINITY;
        for (Sample anA : a) {
            if (Float.isNaN(anA.getValue())) continue;//return Float.NaN;
            if (anA.getValue() > max) max = anA.getValue();
        }
        return max;
    }

    private float mean(ArrayList<Sample> a) {
        if (a.size() == 0) return Float.NaN;
        float sum = sum(a);
        return sum / a.size();
    }

    private float sum(ArrayList<Sample> a) {
        float sum = 0.0f;
        for (Sample value : a)
            sum += value.getValue();

        return sum;
    }

    public float var(ArrayList<Sample> a) {
        if (a.size() == 0) return Float.NaN;
        float avg = mean(a);
        float sum = 0.0f;
        for (Sample anA : a)
            sum += (anA.getValue() - avg) * (anA.getValue() - avg);

        return sum / (a.size() - 1);
    }

    public float stddev(ArrayList<Sample> a) {
        return (float)Math.sqrt(var(a));
    }

    private ArrayList<RecordedException> popExceptionsInWindow(ArrayList<RecordedException> exceptions, Timestamp t, Timestamp nextT) {
        ArrayList<RecordedException> windowExceptions = new ArrayList<>();

        RecordedException e;
        while ( !exceptions.isEmpty() && (e = exceptions.get(0)).getTime().asTimestamp().before(t)) exceptions.remove(0);

        while ( !exceptions.isEmpty() &&  (e = exceptions.get(0)).getTime().asTimestamp().before(nextT)) {
            exceptions.remove(0);
            windowExceptions.add(e);
        }

        return windowExceptions;
    }

    private ArrayList<Sample> popSamplesInWindow(ArrayList<Sample> samples, Timestamp t, Timestamp nextT) {
        ArrayList<Sample> windowSamples = new ArrayList<>();

        Sample s;
        while ( !samples.isEmpty() && (s = samples.get(0)).getTime().asTimestamp().before(t)) samples.remove(0);

        while ( !samples.isEmpty() && (s = samples.get(0)).getTime().asTimestamp().before(nextT)) {
            samples.remove(0);
            windowSamples.add(s);
        }

        return windowSamples;
    }

    private void generateFeatureNames(ArrayList<String> originalFeatureNames) {
        features = new ArrayList<>();
        for (int i =1; i <= k; i++)
            for (String s : originalFeatureNames) {
                features.add("W" + i + "_" + "[N]_" + s);
                features.add("W" + i + "_" + "[MEAN]_" + s);
                features.add("W" + i + "_" + "[STDDEV]_" + s);
                features.add("W" + i + "_" + "[MIN]_" + s);
                features.add("W" + i + "_" + "[MAX]_" + s);
                features.add("W" + i + "_" + "[DERIV]_" + s);
            }
        features.add("Exception");

    }

    @Override
    public String toString() {
        return "SummarizedDataset{" +
                "T=" + T +
                "\n k=" + k +
                "\n features=" + features +
                "\n data=" + data +
                '}';
    }
}
