package prex.coordinator.server;

import prex.common.PreXException;
import prex.common.PredictionContext;
import prex.common.protocol.Message;
import prex.common.protocol.messages.*;
import prex.coordinator.db.DB;
import prex.coordinator.db.DBUtils;
import prex.coordinator.train.TrainingManager;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;

// Each client gets its own thread, regardless of it being a probe, an administration application or something entirely
// different.
//
// Note also that each client is assumed to run within ONE SINGLE THREAD and so, it can only have ONE prediction context
// at a time (inside which it may have different exceptions to predict).
public class ClientThread extends Thread {
    private SharedServerState state;
    private TrainingManager train;
    private DB db;
    private Socket s;
    private ObjectInputStream inStream;
    private ObjectOutputStream outStream;


    public ClientThread(SharedServerState state, DB db, Socket s) throws IOException {
        this.state = state;
        this.db = db;
        this.s = s;
        this.inStream = new ObjectInputStream(s.getInputStream());
        this.outStream = new ObjectOutputStream(s.getOutputStream());
    }

    @Override
    public void run() {
        //
        PredictionContext currentContext = null;
        ArrayList<PreXException> exceptionsToMonitor = new ArrayList<>();

        // Loop just receiving messages
        while (true) {
            try {
                Message m = (Message)inStream.readObject();


                if ( m instanceof BufferedSamplesMessage )
                    db.writeSamples(((BufferedSamplesMessage) m).getBuffer().getSamples()); // New data!
                else if ( m instanceof RecordedExceptionMessage ) {
                    System.err.println(m); // An exception just happened!
                    db.writeRecordedException(((RecordedExceptionMessage) m).getException());
                }
                else if ( m instanceof StartListeningToPredictionsMessage) {
                    // Get a hold of the PredictionManager and get it to start predicting threads. Also ask it what
                    // the current prediction state is.

                    // The current context is now set!
                    currentContext = ((StartListeningToPredictionsMessage) m).getContext();
                    DBUtils.withConnection(currentContext::ensureExistsAndFetchIDs);

                    exceptionsToMonitor.add(((StartListeningToPredictionsMessage) m).getException());

                    synchronized (outStream) {
                        // Send the current prediction (since the thread might have been running already, it may
                        // be true)
                        boolean currPrediction = state.getPredictionThreadManager().startPredicting(currentContext, ((StartListeningToPredictionsMessage) m).getException(), this);
                        outStream.writeObject(new YesNoMessage("COORDINATOR",currPrediction));
                    }
                }
                else if ( m instanceof StopListeningToPredictionsMessage) {
                    exceptionsToMonitor.remove(((StopListeningToPredictionsMessage) m).getException());

                    DBUtils.withConnection(currentContext::ensureExistsAndFetchIDs);
                    state.getPredictionThreadManager().stopPredicting(currentContext, ((StopListeningToPredictionsMessage) m).getException(), this);

                    // FIXME: I don't like this. Maybe we need an explicit EnterContext end ExitContext message. eugh
                    if ( exceptionsToMonitor.isEmpty() )
                        currentContext = null;
                } else if ( m instanceof SetRunningMessage ) { // Ah! Here is the administration client doing its thing!
                    state.setRunning(((SetRunningMessage) m).getRunning());
                } else if ( m instanceof AddRemovePredictionContextSampleIDsMessage) {
                    PredictionContext ctx = ((AddRemovePredictionContextSampleIDsMessage) m).getContext();
                    DBUtils.withConnection(ctx::ensureExistsAndFetchIDs);

                    for (String[] id : ((AddRemovePredictionContextSampleIDsMessage) m).getIds())
                        if ( ((AddRemovePredictionContextSampleIDsMessage) m).isAdd() )
                            ctx.addId(id[0] /* name */, id[1]/* src*/);
                        else
                            ctx.removeId(id[0] /* name */, id[1]/* src*/);

                    DBUtils.withConnection(ctx::update);
                } else if ( m instanceof TrainMessage) {
                    state.getTrainingManager().train(((TrainMessage) m).getContext(), ((TrainMessage) m).getException(), ((TrainMessage) m).getT(), ((TrainMessage) m).getK());
                }

            } catch (IOException | ClassNotFoundException e) {
                //e.printStackTrace();
                break;
            }
        }


        if ( currentContext != null)
            for (PreXException e : exceptionsToMonitor)
                state.getPredictionThreadManager().stopPredicting(currentContext, e, this);
        //System.out.println("Bye bye client!");
    }

    public void notifyOfPredictionState(PredictionContext ctx, PreXException exception, boolean state) throws IOException {
        synchronized (outStream) {
            outStream.writeObject(new ExceptionPredictionStateMessage("COORDINATOR", exception,state));
        }

    }
}
