package client;

import prex.client.PrexClient;
import prex.client.Try;
import prex.common.*;
import prex.common.protocol.messages.BufferedSamplesMessage;
import prex.common.protocol.messages.StartListeningToPredictionsMessage;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.BufferOverflowException;
import java.sql.SQLData;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by jorl17 on 02/06/16.
 */
public class Client {
    public static void main(String[] args) throws IOException, InterruptedException {
        PrexClient client = new PrexClient("client_1", "localhost", 1610);
        AtomicBoolean e = new AtomicBoolean(false);
        new Thread(() -> {
            while (true) {
                client.sample("feature_1", e.get() ? 1.0f : 0.0f);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }
        }).start();
        final boolean[] sending = {true};
        for (int i = 0; i < 1000; i++) {
            e.set(Math.random() <= 0.3f);
            final boolean[] avoid = {false};
            client
            .Try("test-exception", (t) -> {
                        if ( Math.random() <= 0.2 ) {
                            System.out.println("A desfazar");
                            Thread.sleep(200);
                        }
                        avoid[0] = false;
                        t.check(); //FIXME: Maybe automatically insert the check using a pre-processor later

                        /*t.sample("feature_1", e ? 1.0f : 0.0f);
                        t.sample("feature_3", (float) (Math.random()*5));*/

                        if ( Math.random() <= 0.3 ) {
                            System.out.println("Flipping!");
                            sending[0] = !sending[0];
                        }
                        if (sending[0])
                            t.sample("feature_2", (float) (Math.random()*5));
                        t.sample("feature_3", (float) (Math.random()*5));
                        Thread.sleep(1000);
                        if (e.get() && !avoid[0])
                            throw new SQLDataException();

                    }
            ).Prevent(Exception.class, (pio) -> {
                        System.out.println("Predicted SQLDataException! with e = " + e.get() + ", " + pio );
                        avoid[0] = true;
            }).Catch(Exception.class, (e2) -> {
                        System.out.println("Caught SQLDataException! " + e2);
            }).sync();
    }

    /*
        client.addPredictionContextIDs("test-context", new String[][]{
                new String[]{"feature1", "client1"},
                new String[]{"feature2", "client1"},
        });*/
    }

}
