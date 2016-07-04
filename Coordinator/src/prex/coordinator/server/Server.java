package prex.coordinator.server;

import prex.coordinator.db.DB;

import java.io.IOException;
import java.net.ServerSocket;

// Nothing to see here, move along.
//
// You must instruct the server if it is running with predictions enabled or disabled. The first argument can be
// "predict" to signify that predictions should happen. Pass anything else and they won't be made.
public class Server {
    public static void main(String[] args) throws IOException {
        System.out.println("Hello World, the server is running!");
        DB db = DB.getInstance();
        SharedServerState state = new SharedServerState(false, args[0].equals("predict"));

        ServerSocket serverSocket = new ServerSocket(1610, 0, null);

        while (true) {
            new ClientThread(state,db,serverSocket.accept()).start();
        }
    }
}
