package prex.coordinator.db;

import org.h2.jdbcx.JdbcConnectionPool;
import prex.common.*;
import prex.coordinator.preprocess.Dataset;
import prex.coordinator.server.SharedServerState;

import java.sql.*;

// This is a database using the singleton pattern. It provides connections and some auxiliary wrapper methods.
// To be honest I don't really like its overall design but it works.
public class DB {

    private JdbcConnectionPool pool;

    private static DB db;
    public static DB getInstance() {
        if (db == null) db = new DB();
        return db;
    }

    public  Connection getConnection(boolean autoCommit) throws SQLException {
        Connection c = pool.getConnection();
        c.setAutoCommit(autoCommit);
        return c;
    }

    public  Connection getConnection() {
        try {
            return getConnection(true);
        } catch (SQLException e) {
            return null;
        }
    }

    public DB() {
        open();
        createDB();

        // This resets the SAMPLE_SNAPSHOT table every 60 seconds.
        if ( pool != null && db != null )
            new Thread( () -> {
                while(true) {
                    try {
                        DBUtils.withConnection(Sample::clearSnapshotTable);
                        Thread.sleep(1 * 60 * 1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        return;
                    }
                }
            }).start();
    }

    // Try to create the tables. Detects if they already exist.
    private boolean createDB() {
        return DBUtils.withConnection(getConnection(), (c) -> {
            try {
                Sample.createTables(c);
                PredictionContext.createTables(c);
                RecordedException.createTables(c);
                ExecutionRun.createTables(c);

                return true;
            } catch (SQLException e) {
                if (e.getMessage().contains("exists")) {
                    System.out.println("Tables already created...");
                    return true;
                }
                else {
                    e.printStackTrace();
                    return false;
                }
            }
        });

    }

    // Logs an execution run
    public synchronized boolean logRun(PreXTimestamp start, PreXTimestamp end) {
        return DBUtils.withConnection((c) -> new ExecutionRun(start,end).insert(c));
    }

    // Stores a sample
    public boolean writeSample(Sample s) {
        return DBUtils.withConnection(s::insert);
    }

    // Stores several samples. This could be FAR more efficient.
    public boolean writeSamples(Sample[] samples) {
        for (Sample s : samples)
            if (!writeSample(s))
                return false;
        return true;
    }

    // Writes a recorded exception
    public boolean writeRecordedException(RecordedException e) {
        return DBUtils.withConnection(e::insert);
    }

    // Further below are just useless wrapper methods...
    /*public List<Sample> getSamplesFromSrc(String src) {
        return DBUtils.withConnection((c) -> Sample.allFromSrc(c,src) );
    }

    public List<Sample> getSamplesFromId(String name, String src) {
        return DBUtils.withConnection((c) -> Sample.allFromId(c, name, src) );
    }

    private List<Sample> getSamplesFromPredictionContext(PredictionContext p) {
        return DBUtils.withConnection(p::getSamples);
    }*/

    /*private List<RecordedException> getExceptionsFromPredictionContext(PredictionContext p) {
        return DBUtils.withConnection(p::getExceptions);
    }*/



    // This opens the H2 database and makes it ready for all the world to cheer on!
    private void open() {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        pool = JdbcConnectionPool.create("jdbc:h2:./prex-db", "", "");
        //pool = JdbcConnectionPool.create("jdbc:h2:./prex-db;AUTO_SERVER=true;CACHE_SIZE=1048576;EARLY_FILTER=true", "", "");
        pool.setMaxConnections(2000);

    }
}
