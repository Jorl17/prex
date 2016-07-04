package prex.coordinator.preprocess;

import prex.common.*;
import prex.coordinator.db.DB;
import prex.coordinator.db.DBUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

// A Dataset encapsulates samples and exceptions for a given execution run. It is often passed to a SummarizedDataset.
// Note that datasets are always relative to some exception within some prediction context. They then possess
// appropriate estructures for quickly building the summarized dataset (e.g., all samples are acessible through
// their sample ids (name-src pairs).
// This should get a major overhaul if we implement the SampleID class. Right now it'd be too much of a hassle.
//
// Note that a dataset can be saved to and read from disk. Dataset files are serialized versions of this class with
// the .dataset suffix. This is extremely useful when the SAMPLE table grows very large and it compensates to just
// cache the dataset on-disk.
public class Dataset implements Serializable {
    private PredictionContext context;
    private PreXException exception;
    private ArrayList<String> featureNames;

    // All samples, ordered by first to last
    private ArrayList<Sample> samples;

    // <sample-id, [sample1, sample2, sample3 ... (ordered by first to last) ]>
    // Use to quickly get all samples with the same ID in ascending order of timestamp
    private Map<String,ArrayList<Sample>> samplesMap;

    // All recorded exception, ordered by first to last
    private ArrayList<RecordedException> exceptions;

    // Might be -1 if, e.g., this is being used at run-time to make predictions
    private int runNo;

    private transient DB db;

    public Dataset(PredictionContext context, PreXException exception, int runNo) {
        this.db = DB.getInstance();
        this.context = context;
        this.exception = exception;
        this.runNo = runNo;
        this.samples = new ArrayList<>();
        this.exceptions= new ArrayList<>();
        this.samplesMap = new HashMap<>();

        // Ensure the context exists! Might create it
        DBUtils.withConnection(context::ensureExistsAndFetchIDs);
        this.featureNames = DBUtils.withConnection(context::getFeatureNames);
    }

    // Each dataset can be uniquely identified by a triplet: <context, exception, runNo>. This is used to load
    // and save datasets based on these three identifiers. See load() and save()
    private String getDatasetName() {
        return context.getName() + "_" + exception.getExceptionClass() + "_" + runNo;
    }

    public void saveToFile() {
        save(getDatasetName());
    }

    private void save(String file) {
        FileOutputStream fout;
        try {
            fout = new FileOutputStream(file + ".dataset");
            ObjectOutputStream o = new ObjectOutputStream(fout);
            o.writeObject(this);
            System.out.println("Saved dataset");
            o.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public boolean loadFromExistingFile() {
        Dataset d = load(getDatasetName());
        if ( d == null )
            return false;
        //this.context = d.context;  //Should be the same
        //this.exception = d.exception;  //Should be the same
        this.exceptions = d.exceptions;
        this.featureNames = d.featureNames; //probably the same
        this.samples = d.samples;
        //this.runNo = d.runNo; //Should be the same
        this.samplesMap = d.samplesMap;
        return true;

    }

    public Dataset load(String file) {
        try {
            FileInputStream fin = new FileInputStream(file + ".dataset");
            ObjectInputStream o = new ObjectInputStream(fin);
            Dataset d = (Dataset)o.readObject();
            o.close();
            return d;
        } catch (IOException | ClassNotFoundException e) {
        }

        return null;
    }


    // This fetches all samples since te given timestamp and adds it to this dataset. This is presumably done at
    // run-time for predicting.
    public void gatherSamplesSince(PreXTimestamp t) {
        samples = new ArrayList<>();
        samples.addAll(DBUtils.withConnection((c) -> context.getSamplesSince(c, t)));

        buildSamplesMap();
        System.err.println("SamplesMap size:" + samplesMap.size());
    }

    // Builds the map of <sample-id, [sample1, sample2, sample3 ... (ordered by first to last) ]>
    // from the samples arraylist
    private void buildSamplesMap() {
        System.out.println("Building sample maps");
        for ( Sample s : samples ) {
            if ( samplesMap.containsKey(s.getId()) )
                samplesMap.get(s.getId()).add(s);
            else
                samplesMap.put(s.getId(), new ArrayList<>());
        }
    }

    // Grabs the current context, exception name and run number and builds the corresponding dataset.
    // It first checks to see if we have a cached version of the dataset. If we don't, then it queries the database
    // for the right data, which might be very slow for very large datasets.
    public void gatherSamplesAndExceptions() {
        if ( loadFromExistingFile() ) {
            System.out.println("Loaded dataset from existing dataset!");
            return;
        } else {
            System.out.println("No current dataset for " + getDatasetName() + ", building it!");


            samples = new ArrayList<>();
            samples.addAll(DBUtils.withConnection((c) -> context.getSamplesFromRun(c, runNo)));
            System.out.println("Samples gathered");
            exceptions.addAll(DBUtils.withConnection((c) -> context.getExceptionsOfTypeFromRun(c, runNo, exception)));
            System.out.println("Exceptions gathered");

            buildSamplesMap();
            saveToFile();
        }

    }

    public int getRunNo() {
        return runNo;
    }

    public ArrayList<Sample> getSamples() {
        return samples;
    }

    // Get a copy of the samples map. The SummarizedDataset uses this to apply the training algorithm
    public Map<String, ArrayList<Sample>> getSamplesMapCopy() {
        Map<String,ArrayList<Sample>> samplesMapCopy = new HashMap<>();
        for (String key : samplesMap.keySet()) {
            ArrayList<Sample> newList = new ArrayList<>();
            newList.addAll(samplesMap.get(key));
            samplesMapCopy.put(key, newList);
        }
        return samplesMapCopy;
    }

    // Get a copy of the samples map. The SummarizedDataset uses this to apply the training algorithm
    public ArrayList<RecordedException> getExceptionsCopy() {
        ArrayList<RecordedException> array = new ArrayList<>();
        array.addAll(exceptions);
        return array;
    }

    public ArrayList<String> getFeatureNames() {
        return featureNames;
    }

    public PredictionContext getContext() {
        return context;
    }

    @Override
    public String toString() {
        return "Dataset{" +
                "context=" + context +
                "\n featureNames=" + featureNames +
                "\n samples=" + samples +
                "\n samplesMap=" + samplesMap +
                "\n exceptions=" + exceptions +
                "\n runNo=" + runNo +
                "\n db=" + db +
                '}';
    }
}
