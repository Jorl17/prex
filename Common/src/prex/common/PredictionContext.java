package prex.common;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

// A prediction context is characterized exclusively by a name. Perhaps in a future version we can extend the concept
// to allow for different prediction contexts in different systems, all using the same coordinator.
//
// A prediction context must also keep track of all the samples that it wants to use for prediction. This is done
// by mapping the array of relevant sample IDs in here. Remember that a sample "ID" is just its name and src.
public class PredictionContext implements Serializable {
    private String name;

    // ArrayList of <name,src> ids relevant to this prediction context
    private ArrayList<String[]> ids;

    public PredictionContext(String name, ArrayList<String[]> ids) {
        this.name = name;
        this.ids = ids;
    }

    public PredictionContext(String name) {
        this(name, new ArrayList<>());
    }

    public void addId(String name, String src) {
        this.ids.add(new String[]{name, src});
    }
    public void removeId(String s, String s1) {
        // FIXME
    }

    public ArrayList<String[]> getIDs() {
        return ids;
    }

    public void fetchIDs(Connection c) {
        ids = new ArrayList<>();
        try {
            PreparedStatement s;
            s = c.prepareStatement("SELECT SAMPLE_NAME,SAMPLE_SRC FROM PREDICTION_CONTEXT_IDs p_s WHERE  p_s.CONTEXT_NAME = ?");

            s.setString(1, name);
            ResultSet resultSet = s.executeQuery();
            while ( resultSet.next() )
                ids.add(new String[] { resultSet.getString("SAMPLE_NAME"), resultSet.getString("SAMPLE_SRC")});
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    protected boolean insertIDs(Connection c) {

            for ( String[] nameAndSrc : ids ) {
                try {
                    PreparedStatement s = c.prepareStatement("INSERT INTO PREDICTION_CONTEXT_IDS (CONTEXT_NAME,SAMPLE_NAME,SAMPLE_SRC) VALUES (?,?,?)");

                    s.setString(1, name);
                    s.setString(2, nameAndSrc[0]);
                    s.setString(3, nameAndSrc[1]);
                    s.executeUpdate();
                } catch (SQLException e) {
                    //e.printStackTrace();

                }
            }
            return true;
    }

    public boolean insert(Connection c) {
        try {
            PreparedStatement s = c.prepareStatement("INSERT INTO PREDICTION_CONTEXT (name) VALUES (?)");
            s.setString(1, name);
            s.executeUpdate();

            //FIXME: This is where we add all the features. Might want to remove in a future version. In practice
            // this means that the first time a prediction context is seen by the system, then it is automatically
            // mapped to all features/samples that already exist in the system. Also note that allSampleIds is probably
            // computationally expensive!!
            if ( ids == null || ids.isEmpty())
                ids = Sample.allSampleIds(c);


            if(!insertIDs(c)) return false;

            c.commit();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean update(Connection c) {
        if (!insertIDs(c)) return false;
        try {
            c.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void createTables(Connection c) throws SQLException {
        PreparedStatement s = c.prepareStatement("CREATE TABLE PREDICTION_CONTEXT (name VARCHAR UNIQUE, PRIMARY KEY (name))");
        s.executeUpdate();
        s = c.prepareStatement("CREATE TABLE PREDICTION_CONTEXT_IDS (context_name VARCHAR, sample_name varchar, sample_src varchar, PRIMARY KEY (CONTEXT_NAME,SAMPLE_NAME,SAMPLE_SRC), FOREIGN KEY (CONTEXT_NAME) REFERENCES PREDICTION_CONTEXT(NAME))");
        s.executeUpdate();
    }

    // Get ALL samples regarding this prediction context
    public List<Sample> getSamples(Connection c) {
        ArrayList<Sample> samples = new ArrayList<>();
        try {
            PreparedStatement s;
            s = c.prepareStatement("SELECT s.* FROM SAMPLE s, PREDICTION_CONTEXT p, PREDICTION_CONTEXT_IDs p_s WHERE p.name = p_s.context_name AND s.src = p_s.sample_src AND s.name = p_s.sample_name AND p.name = ? ORDER BY s.time ASC");

            s.setString(1, name);
            ResultSet resultSet = s.executeQuery();
            while ( resultSet.next() ) {
                samples.add(Sample.fromResultSet(resultSet));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return samples;
    }

    // Get samples for this prediction context for the run with the given ID
    public List<Sample> getSamplesFromRun(Connection c, int run) {
        ArrayList<Sample> samples = new ArrayList<>();
        try {
            PreparedStatement s;
            s = c.prepareStatement("SELECT s.* FROM SAMPLE s, PREDICTION_CONTEXT p, PREDICTION_CONTEXT_IDs p_s, EXECUTION_RUN r WHERE p.name = p_s.context_name AND s.src = p_s.sample_src AND s.name = p_s.sample_name AND p.name = ? AND s.time >= r.start AND s.time <= r.finish AND r.id = ? ORDER BY s.time ASC");

            s.setString(1, name);
            s.setInt(2, run);
            ResultSet resultSet = s.executeQuery();
            while ( resultSet.next() ) {
                samples.add(Sample.fromResultSet(resultSet));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return samples;
    }


    // This gets all samples for the current prediction context since a given data. Since it's meant to be used while
    // predicting, it bypasses the SAMPLE table and uses the SAMPLE_SNAPSHOT table.
    public List<Sample> getSamplesSince(Connection c, PreXTimestamp t) {
        ArrayList<Sample> samples = new ArrayList<>();
        try {
            PreparedStatement s;

            // FIXME: Make sure that the snapshot is far enough in the past?
            s = c.prepareStatement("SELECT s.* FROM SAMPLE_SNAPSHOT s, PREDICTION_CONTEXT p, PREDICTION_CONTEXT_IDs p_s, EXECUTION_RUN r WHERE p.name = p_s.context_name AND s.src = p_s.sample_src AND s.name = p_s.sample_name AND p.name = ? AND s.time >= ? ORDER BY s.time ASC");

            s.setString(1, name);
            s.setTimestamp(2, t.asTimestamp());
            ResultSet resultSet = s.executeQuery();
            while ( resultSet.next() ) {
                samples.add(Sample.fromResultSet(resultSet));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return samples;
    }

    public String getName() {
        return name;
    }

    // Get ALL exceptions regarding this prediction context
    public List<RecordedException> getExceptions(Connection c) {
        ArrayList<RecordedException> exceptions = new ArrayList<>();
        try {
            PreparedStatement s;
            s = c.prepareStatement("SELECT r.* FROM RECORDED_EXCEPTION r WHERE r.PREDICTION_CONTEXT= ?  ORDER BY r.time ASC");

            s.setString(1, name);
            ResultSet resultSet = s.executeQuery();
            while ( resultSet.next() ) {
                exceptions.add(RecordedException.fromResultSet(resultSet));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return exceptions;
    }

    // Get ALL the exceptions of the given type for this prediction context
    public List<RecordedException> getExceptionsOfType(Connection c, PreXException e) {
        ArrayList<RecordedException> exceptions = new ArrayList<>();
        try {
            PreparedStatement s;
            s = c.prepareStatement("SELECT r.* FROM RECORDED_EXCEPTION r WHERE r.PREDICTION_CONTEXT= ? AND r.EXCEPTION_CLASS = ?  ORDER BY r.time ASC");

            s.setString(1, name);
            s.setString(2, e.getExceptionClass());
            ResultSet resultSet = s.executeQuery();
            while ( resultSet.next() ) {
                exceptions.add(RecordedException.fromResultSet(resultSet));
            }
        } catch (SQLException e2) {
            e2.printStackTrace();
        }
        return exceptions;
    }

    // Get the exceptions og the given type for this prediction context for the run with the given ID
    public List<RecordedException> getExceptionsOfTypeFromRun(Connection c, int run, PreXException e) {
        ArrayList<RecordedException> exceptions = new ArrayList<>();
        try {
            PreparedStatement s;
            s = c.prepareStatement("SELECT r.* FROM RECORDED_EXCEPTION r, EXECUTION_RUN r2 WHERE r.PREDICTION_CONTEXT= ?  AND r.EXCEPTION_CLASS = ?  AND r.time >= r2.start AND r.time <= r2.finish AND r2.id = ?  ORDER BY r.time ASC");

            s.setString(1, name);
            s.setString(2, e.getExceptionClass());
            s.setInt(3, run);
            ResultSet resultSet = s.executeQuery();
            while ( resultSet.next() ) {
                exceptions.add(RecordedException.fromResultSet(resultSet));
            }
        } catch (SQLException e2) {
            e2.printStackTrace();
        }
        return exceptions;
    }

    @Override
    public String toString() {
        return "PredictionContext{" +
                "name='" + name + '\'' +
                //", ids=" + ids +
                '}';
    }

    // Whenever you get a PredictionContext, call this method to make sure that it exists in the database and that the
    // sample IDs are current. Note that if it doesn't exist yet, it will be created, and that currently also maps it
    // to all known sample IDs.
    public boolean ensureExistsAndFetchIDs(Connection c) {
        try {
            PreparedStatement s;
            s = c.prepareStatement("SELECT p.* FROM PREDICTION_CONTEXT p WHERE p.name = ?");
            s.setString(1, name);

            ResultSet resultSet = s.executeQuery();
            if ( resultSet.next() )
                fetchIDs(c);
            else {
                insert(c);
            }

            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }

    // Get the names of all the features used by this prediction context. These come as src-name pairs.
    // They are GUARANTEED to always come in the same order, so you can build a list of features under that assumption.
    public ArrayList<String> getFeatureNames(Connection c) {
        ArrayList<String> featureNames = new ArrayList<>();
        try {
            PreparedStatement s;
            s = c.prepareStatement("SELECT p_s.sample_src,p_s.sample_name FROM PREDICTION_CONTEXT_IDs p_s WHERE p_s.context_name = ? ORDER BY p_s.sample_src,p_s.sample_name");

            s.setString(1, name);
            ResultSet resultSet = s.executeQuery();
            while ( resultSet.next() ) {
                featureNames.add(resultSet.getString(1) + "-" + resultSet.getString(2));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return featureNames;
    }
}
