package prex.common;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

// An execution run, with monotonic IDs and a start and finish. Training will only happen with data pertaining
// execution runs.
public class ExecutionRun {
    private int id;
    private PreXTimestamp start, finish;

    public ExecutionRun(PreXTimestamp start, PreXTimestamp finish) {
        this.start = start;
        this.finish = finish;
        this.id = -1;
    }

    public ExecutionRun() {
    }

    public static void createTables(Connection c) throws SQLException {
        PreparedStatement s = c.prepareStatement("CREATE TABLE EXECUTION_RUN (ID INT AUTO_INCREMENT, start TIMESTAMP, finish TIMESTAMP, PRIMARY KEY (ID))");
        s.executeUpdate();
    }

    // Get the total number of execution runs. This assumes nobody tampered with the database, which is a really bad
    // assumption. It works...for now (FIXME)
    public static int numRuns(Connection c) {
        try {
            PreparedStatement s = c.prepareStatement("SELECT ID from EXECUTION_RUN ORDER BY ID DESC LIMIT 1");
            ResultSet resultSet = s.executeQuery();
            if ( resultSet.next() )
                return resultSet.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return -1;
    }

    // Get the ExecutionRun from its ID.
    public static ExecutionRun fromID(Connection c, int id) {
        ExecutionRun r = new ExecutionRun();
        try {
            r.id = id;
            PreparedStatement s = c.prepareStatement("SELECT start,finish FROM EXECUTION_RUN WHERE ID = ?");
            s.setInt(1, id);
            ResultSet resultSet = s.executeQuery();
            if ( resultSet.next() ) {
                r.start=new PreXTimestamp(resultSet.getTimestamp(1));
                r.finish =new PreXTimestamp(resultSet.getTimestamp(2));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return r;
    }

    public boolean insert(Connection c) {
        try {
            PreparedStatement s = c.prepareStatement("INSERT INTO EXECUTION_RUN (start,finish) VALUES (?,?)");
            s.setTimestamp(1, start.asTimestamp());
            s.setTimestamp(2, finish.asTimestamp());
            s.executeUpdate();
            ResultSet rs = s.getGeneratedKeys();
            if (rs.next()) {
                this.id = rs.getInt(1);
                System.err.println("Run got ID: " + this.id);
            }
            else
                System.err.println("NO GENERATED KEY!!!");
            c.commit();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public PreXTimestamp getStart() {
        return start;
    }

    public PreXTimestamp getFinish() {
        return finish;
    }

    @Override
    public String toString() {
        return "ExecutionRun{" +
                "id=" + id +
                ", start=" + start +
                ", finish=" + finish +
                '}';
    }
}
