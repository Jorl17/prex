package prex.common;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

// A sample is characterized by its ID, timestamp and value. The ID is the <src,name> pair. The src denotes the
// originating entity (e.g. "Machine 1"), whereas the name denotes the name of the sample within that entity (e.g.
// "Free Ram"). In the future, I'd like to join this pair into a SampleID class, since it became a hassle to have to
// carry both around.
//
// Note also that currently all samples are floating point, but this isn't really hardcoded. A change in the database
// for the generality, coupled with clever generics is enough to move beyond that. However, floats fit our purpose
// perfectly.
//
// Note that although all samples are stored in a SAMPLE table, they are also stored in a a SAMPLE_SNAPSHOT table.
// Due to the amount of data, the SAMPLE table quickly becomes hard to process, and so a SAMPLE_SNAPSHOT containing
// the most recent data can be used instead (you should flush/clear this table by calling clearSnapshotTable).
//
// PredictionContext methods that look for data "Since" some time usually access the SAMPLE_SNAPSHOT table for these
// performance reasons.
public class Sample implements Serializable {
    private PreXTimestamp time;

    // <src,name> from the ID of the sample
    private String name;
    private String src;

    // FIXME: Maybe later make this generic (see above)
    private float value;

    public Sample(PreXTimestamp time, String name, String src, float value) {
        this.time = time;
        this.name = name;
        this.src = src;
        this.value = value;
    }

    public String getId() {
        return src + "-" + name;
    }



    @Override
    public String toString() {
        return "Sample{" +
                "time=" + time +
                ", name='" + name + '\'' +
                ", src='" + src + '\'' +
                ", value=" + value +
                '}';
    }

    public static void createTables(Connection c) throws SQLException {
        PreparedStatement s = c.prepareStatement("CREATE TABLE SAMPLE (time TIMESTAMP, name VARCHAR, src VARCHAR, value FLOAT, PRIMARY KEY (name,src,time))");
        s.executeUpdate();
        s = c.prepareStatement("CREATE TABLE SAMPLE_SNAPSHOT (time TIMESTAMP, name VARCHAR, src VARCHAR, value FLOAT, PRIMARY KEY (name,src,time))");
        s.executeUpdate();
    }

    public static Void clearSnapshotTable(Connection c) {
        PreparedStatement s;
        try {
            s = c.prepareStatement("TRUNCATE TABLE SAMPLE_SNAPSHOT");
            s.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    public boolean insert(Connection c) {
        try {
            PreparedStatement s = c.prepareStatement("INSERT INTO SAMPLE (time,name,src,value) VALUES (?,?,?,?)");
            s.setTimestamp(1, time.asTimestamp());
            s.setString(2, name);
            s.setString(3, src);
            s.setFloat(4, value);
            s.executeUpdate();

            s = c.prepareStatement("INSERT INTO SAMPLE_SNAPSHOT (time,name,src,value) VALUES (?,?,?,?)");
            s.setTimestamp(1, time.asTimestamp());
            s.setString(2, name);
            s.setString(3, src);
            s.setFloat(4, value);
            s.executeUpdate();

            c.commit();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static Sample fromResultSet(ResultSet resultSet) throws SQLException {
        return new Sample( new PreXTimestamp(resultSet.getTimestamp("time")),
                resultSet.getString("name"),
                resultSet.getString("src"),
                resultSet.getFloat("value"));
    }

    // Grab all samples from the same source
    public static List<Sample> allFromSrc(Connection c, String src) {
        ArrayList<Sample> samples = new ArrayList<>();
        try {
            PreparedStatement s = c.prepareStatement("SELECT s.* from SAMPLE s WHERE src = ?");
            s.setString(1, src);
            ResultSet resultSet = s.executeQuery();
            while ( resultSet.next() ) {
                samples.add(fromResultSet(resultSet));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return samples;
    }

    // Return array of all <name,src> pairs. This is VERY EXPENSIVE if there is no index (and why would you add an
    // index to this gigantic table anyway? A major FIXME is to remove all usage of this function.
    public static ArrayList<String[]> allSampleIds(Connection c) {
        ArrayList<String[]> samples = new ArrayList<>();
        try {
            PreparedStatement s = c.prepareStatement("SELECT DISTINCT NAME,SRC FROM SAMPLE ");
            ResultSet resultSet = s.executeQuery();
            while ( resultSet.next() )
                samples.add(new String[] { resultSet.getString("NAME"), resultSet.getString("SRC") });
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return samples;
    }
    public PreXTimestamp getTime() {
        return time;
    }

    public float getValue() {
        return value;
    }
}
