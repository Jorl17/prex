package prex.coordinator.db;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.function.Function;

// A set of utilities for dealing with database operations. It offers behavior similar to automated destructors,
// automatically calling .close() on connections, or even auto-commiting them.
//
// This auxiliary class was grabbed (and adapted) from the Evaristo project, 2015, by the awesome SoftwareHut backend
// team (Maxi, Jota and Brás).
public class DBUtils {

    // Perform an activity with a connection. If c is null, it is automatically created (and closed). Autocommit
    // is set as requested. The return value of the wrapped method is propagated to the outside.
    // There are many convenience versions of this method below
    public static <R> R withConnection(boolean autocommit, Connection c, Function<Connection,R> method) {
        boolean closeConnection = false;
        if (c == null) {
            try {
                c = DB.getInstance().getConnection(autocommit);
                c.setAutoCommit(autocommit);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            closeConnection = true;
        }

        R result;
        try {
            result = method.apply(c);
        } catch (Exception e) {
            System.err.println("Caught a null pointer while using a method accessesing the DB. Closing and rethrowing");
            try {
                if ( c != null )
                    c.close();
            } catch (SQLException e2) {
                e2.printStackTrace();
            }
            throw e;
        }

        if (closeConnection) {
            try {
                if ( c != null )
                    c.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public static <R> R withConnection(Connection c, Function<Connection,R> method) {
        return withConnection(false, c, method);
    }

    public static <R> R withConnection(boolean autocommit, Function<Connection,R> method) {
        return withConnection(autocommit, null, method);
    }

    public static <R> R withConnection(Function<Connection,R> method) {
        return withConnection(false, null, method);
    }


    public static void rollback(Connection c) {
        try {
            c.rollback();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static int getGeneratedId(PreparedStatement s) throws SQLException {
        ResultSet rs = s.getGeneratedKeys();
        if (rs != null && rs.next()) return rs.getInt(1);
        throw new SQLException("Unexpected null ID");
    }

    public static Timestamp timestamp() {
        return new java.sql.Timestamp(new java.util.Date().getTime());
    }
}
