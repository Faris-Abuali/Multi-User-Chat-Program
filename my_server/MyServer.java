package my_server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import my_client.ClientProcessor;

/**
 *
 * @author Fares Abu Ali
 */
public class MyServer extends Thread {

    private ServerSocket serverSocket;
    private int serverPort;

    private ArrayList<ClientProcessor> clientsList = new ArrayList<>();

    // Simple Table to store the username and password for all registered clients:
    public static Hashtable<String, String> usersTable = new Hashtable<>();

    MyServer(int serverPort) {
        this.serverPort = serverPort;
    }// end constructor

    public List<ClientProcessor> getClientsList() {
        return this.clientsList;
    }

    //=================================================
    public Hashtable<String, String> getUsersTable() {
        return this.usersTable;
    }

    public boolean registerNewUser(String username, String password) {

        //First, check if this username already exists or not:
        if (this.usersTable.get(username) == null) {
            //then this username doesn't exist, so add him:
            this.usersTable.put(username, password);
            return true; // registered user successfully
        }
        return false; // didn't register the user because the username is already taken.
    }// end method

    public boolean deregisterUser(String username) {
        return (this.usersTable.remove(username) != null);
    }
    //=================================================

    public void removeClientProcess(ClientProcessor clientProcess) {
        clientsList.remove(clientProcess);
    }// end method

    @Override
    public void run() {
        try {
            this.serverSocket = new ServerSocket(serverPort);
            System.out.println("Server is listening on port: " + this.serverSocket.getLocalPort());
            // The System will choose an available port and give it to the serverSocket to listen on it.
            // So I am here printing out the number of the chosen serverPort, so the ckients can know what serverPort to connect on.

            // Register Some Users in the users hastable For The Sake of Testing:
            usersTable.put("Fares", "Fares1234"); // username, password
            usersTable.put("Motaz", "motz_789"); // username, password
            usersTable.put("Mohammad", "moh123"); // username, password

            //System.out.println(usersTable);
            while (true) {
                System.out.println("Waiting for client connection..");
                Socket clientSocket = serverSocket.accept();
                System.out.println("Accepted connection from " + clientSocket);

                //Multi-Threading: Now the server can handle multiple clients concurrently
                ClientProcessor client = new ClientProcessor(this, clientSocket);
                //In order for those 'clients' to access the 'Server' instance, pass 'this' to the ClientProcessor constructor.

                clientsList.add(client); // add this client to the list
                client.start();

            }// end while
        } catch (Exception e) {
            e.printStackTrace();
        }
    }// end method

}// end class
