package prex.common;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

// Represents an exception that happened in the past during some prediction context. This is used for training.
public class RecordedException extends PreXException {

    private PredictionContext context;

    public RecordedException(PreXTimestamp time, String exceptionClass, PredictionContext context) {
        super(time,exceptionClass);
        this.context = context;
    }


    public static void createTables(Connection c) throws SQLException {
        PreparedStatement s = c.prepareStatement("CREATE TABLE RECORDED_EXCEPTION (time TIMESTAMP, EXCEPTION_CLASS VARCHAR, PREDICTION_CONTEXT VARCHAR, PRIMARY KEY (TIME, EXCEPTION_CLASS, PREDICTION_CONTEXT))");
        s.executeUpdate();
    }

    public boolean insert(Connection c) {
        try {

            // First ensure the context exists
            context.ensureExistsAndFetchIDs(c);

            PreparedStatement s = c.prepareStatement("INSERT INTO RECORDED_EXCEPTION (TIME, EXCEPTION_CLASS,PREDICTION_CONTEXT) VALUES (?,?,?)");
            s.setTimestamp(1, time.asTimestamp());
            s.setString(2, exceptionClass);
            s.setString(3, context.getName());
            s.executeUpdate();

            c.commit();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static RecordedException fromResultSet(ResultSet resultSet) throws SQLException {
        return new RecordedException( new PreXTimestamp(resultSet.getTimestamp("TIME")),
                resultSet.getString("EXCEPTION_CLASS"),
                new PredictionContext(resultSet.getString("PREDICTION_CONTEXT")));
    }

    @Override
    public String toString() {
        return "RecordedException{" +
                "context=" + context +
                "}, " + super.toString();
    }
}
