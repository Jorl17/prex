package prex.admin;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import prex.admin.gui.AdminGui;
import prex.client.PrexClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jorl17 on 02/06/16.
 */
public class Admin {
    private static class Parameters {
        @Parameter(names = {"-s", "--start-run"}, description = "Start run")
        private boolean startRun = false;

        @Parameter(names = {"-e", "--end-run"}, description = "End Run")
        private boolean stopRun = false;

        @Parameter(names = {"-p", "--prediction-context"}, description = "Prediction Context")
        private String context = null;

        @Parameter(names = {"-t", "--train"}, description = "Train the given prediction context for the given exception")
        private String exceptionClass = null;

        @Parameter(names = {"-T", "--train-T"}, description = "T values used for prediction")
        private int[] T = {2500,    5000,5000,10000};

        @Parameter(names = {"-k", "--train-k"}, description = "K values used for prediction")
        private int[] k = {4,       2,   1,   1};

        @Parameter(names = {"-ch", "--coordinator-host"})
        private String host = "localhost";

        @Parameter(names = {"-cp", "--coordinator-port"})
        private int port = 1610;

        @Parameter(names = {"-ssrc", "--sample-src"})
        private String sampleSrc  = null;

        @Parameter(names = {"-sname", "--sample-name"})
        private String sampleName  = null;

        @Parameter(names = {"-h", "--help"}, help = true)
        private boolean help;

    }
    public static void main(String[] args) throws IOException {
        Parameters parameters = new Parameters();
        JCommander jcmd = new JCommander(parameters, args);
        if (parameters.help) {
            jcmd.usage();
            return;
        }
        PrexClient admin = new PrexClient("ADMIN", parameters.host, parameters.port);

        if (parameters.startRun) {
            System.out.println("Marking run as started");
            admin.startRun();
        }
        else if (parameters.stopRun) {
            System.out.println("Marking run as stopped");
            admin.stopRun();
        }
        else if (parameters.exceptionClass != null) {
            if ( parameters.T.length != parameters.k.length) {
                System.err.println("T and k array must be the same size");
                return;
            }
            System.out.println("Starting train of exception" + parameters.exceptionClass + " for context " + parameters.context);
            admin.startTraining(parameters.context, parameters.exceptionClass, parameters.T, parameters.k);
        } else if (parameters.sampleSrc != null && parameters.sampleName != null) {
                admin.addPredictionContextIDs(parameters.context, new String[][] { new String[] {parameters.sampleName, parameters.sampleSrc}});
        } else {
            // Default to GUI mode
            AdminGui.main(args);
        }
    }
}
