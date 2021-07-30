package my_client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashSet;
import java.util.List;
import my_server.MyServer;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author Fares Abu Ali
 */

/*
    We want the server to be able to handle multiple clients, so we don't want a single client to block the
    server until it finishes. So we create individual thread for each client, and handle it separately.

    This allows the server to handle multiple clients concurrently.
 */
public class ClientProcessor extends Thread {

    private final Socket clientSocket;
    private final MyServer server;

    private String login = null; // to store the username for this client

    private OutputStream outputStream;

    /*
       - Each 'ClientProcessor' instance will has its own topicSet that stores which topics this current client (ClientProcessor) is            joined to.
       - So this set can be used to check wether this client is joined to a specific topic or not.
     */
    private HashSet<String> topicSet = new HashSet<>();

    public ClientProcessor(MyServer server, Socket clientSocket) {
        this.server = server;
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {

        try {
            processClientSocket();
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }// end run

    private void processClientSocket() throws IOException, InterruptedException {

        InputStream inputStream = clientSocket.getInputStream();
        this.outputStream = clientSocket.getOutputStream();

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));//so we can read line by line
        String line;

        while ((line = reader.readLine()) != null) {

            String[] tokens = StringUtils.split(line); // split the line into tokens based on white spaces

            if (tokens != null && tokens.length > 0) {

                String cmd = tokens[0].toLowerCase();
                boolean breakLoop = false;

                switch (cmd) {
                    case "quit":
                    case "logoff":
                        processLogoff();
                        breakLoop = true; // because the server no longer needs to read from this client.
                        break;

                    case "login":
                        processLogin(outputStream, tokens);
                        break;

                    case "msg":
                        String[] tokenMsg = StringUtils.split(line, " ", 3);//split the line to only 3 tokens: msg, receipient, body
                        processMessage(tokenMsg);
                        break;

                    case "msg-broadcast":
                        String[] tokenMsgBr = StringUtils.split(line, " ", 2); // split the line to only 2 tokens: msg, body
                        processMessageBroadcast(tokenMsgBr);
                        break;

                    case "join":
                        processJoin(tokens);
                        break;

                    case "leave":
                        processLeave(tokens);
                        break;

                    case "who-is-online":
                    case "who-is-connected":
                        //client will be able to query the server and see who is connected from the clients right now:
                        processQueryWhoIsConnected();
                        break;

                    case "whoami":
                        //Sometimes while testing, I forget the terminal I am working on belongs to which username :)
                        processWhoAmI();
                        break;

                    case "register":
                        processRegister(tokens);
                        break;

                    case "deregister":
                        boolean flag = processDeregister();
                        if (flag) {
                            //then client deregistered successfully, and clientSocket has been closed, so break the loop: 
                            breakLoop = true; // because the server no longer needs to read from this client.
                        }
                        break;

                    default:
                        String msg = "unknown " + cmd + "\n\r";
                        outputStream.write(msg.getBytes());

                }// end switch

                //break while loop only in case the clients wants to 'logoff'or 'deregister'
                if (breakLoop) {
                    break;
                }

            }// end if

        }// end while

        clientSocket.close();
    }// end method

    public String getLogin() {
        return this.login; // returns the username of this client who is logged in
    }

    private void processLogin(OutputStream outputstream, String[] tokens) throws IOException {

        // We expect the line to be: login <username> <password>  (3 tokens)
        if (tokens.length == 3) {
            String login = tokens[1];
            String password = tokens[2];

            // Get the password 'which is the value' of the username:
            String query = MyServer.usersTable.get(login);

            if (query != null && query.equals(password)) {

                String msg = "ok login\n\r";
                outputstream.write(msg.getBytes());
                this.login = login.trim(); // store the user's username
                System.out.println("user logged in successfully: " + login);

                List<ClientProcessor> clientsList = server.getClientsList();

                //Send (notify) current user all other online loggins (all other logged in users)
                for (ClientProcessor client : clientsList) {

                    // We don't care about clients who open the terminal but not logged in yet:
                    if (client.getLogin() != null) {

                        // Also nobody needs to be notified about himself being online :)
                        if (!login.equals(client.getLogin())) {
                            String msg2 = "online " + client.getLogin() + "\n\r";
                            send(msg2); // this.send(msg2), this means that every online client will have his 
                            //name written on this current user's stream (on the terminal of the current client)
                        }
                    }
                }

                //We want to broadcast all currently logged in clients, a message tells that this current client has logged in.
                String onlineMsg = "online " + login + "\n\r";
                for (ClientProcessor client : clientsList) {

                    // Also nobody needs to be notified about himself being online :)
                    if (!login.equals(client.getLogin())) {
                        client.send(onlineMsg);
                    }
                }
            } else {
                String msg = "error login" + "\n\r";
                outputstream.write(msg.getBytes());

                System.err.println("Login failed for " + login);
            }

        }
    }// end method

    private void processRegister(String[] tokens) throws IOException {

        // We expect the line to be: register <username> <password>  (3 tokens)
        if (tokens.length == 3) {
            String newUsername = tokens[1];
            String password = tokens[2];

            // Add the new user to the users hashtable
            boolean flag = server.registerNewUser(newUsername, password);

            if (flag) {
                String msg = "ok register\n\r";
                outputStream.write(msg.getBytes());

                System.out.println("user registered in successfully: " + newUsername);
                System.out.println(MyServer.usersTable);
            } else {
                String msg = "error register. Username is already taken\n\r";
                outputStream.write(msg.getBytes());

                System.out.println("Register failed for: " + newUsername + ". Username is taken");
                System.out.println(MyServer.usersTable);
            }
        } else {
            String msg = "error register" + "\n\r";
            this.outputStream.write(msg.getBytes());

            System.err.println("Register failed");
        }
    }// end method

    private void send(String msg) throws IOException {

        //Every instance of 'ClientProcessor', in other words, every client, will have a msg written on his outputStream.
        //This message informs him that a new client has logged in and is now online.
        if (login != null) {
            /* 
                if (login != null) this is because we want to check that the client is connected (logged in) before writing on his                   outputStream
             */
            outputStream.write(msg.getBytes());
            //please Fares remember that this (outputstream) is attribute of the object who has invkoked the send() method
        }

    }// end method

    private void processLeave(String[] tokens) throws IOException {
        //format: leave #topic
        if (tokens.length > 1) {
            String topic = tokens[1]; // the second token
            topicSet.remove(topic); // remove the topic from this client's topicSet.

            System.out.println(login + " has left " + topic);
            System.out.print("topicSet of " + login + ": ");
            System.out.println(topicSet);
        }

    }// end method

    public boolean isMemberOfTopic(String topic) {
        //This topicSet stores the topics that this current client is enrolled in.
        //Check if this client is enrolled in the passed parameter topic:
        return topicSet.contains(topic);
    }// end method

    private void processJoin(String[] tokens) throws IOException {

        //format: join #topic
        if (tokens.length > 1) {
            String topic = tokens[1]; // the second token
            topicSet.add(topic); // add the topic to this client's topicSet (So we can return to it and see wether this client is                   joined to a specific topic or not)

            System.out.println(login + " has left " + topic);
            System.out.print("topicSet of " + login + ": ");
            System.out.println(topicSet);
        }
    }// end method

    // format: "msg" "login" body..
    // format: "msg" "#topic" body..
    private void processMessage(String[] tokens) throws IOException {

        if (login != null && tokens.length == 3) {
            //The client must be logged in to be allowed to send messages to others:

            //tokens[] now is array of length 3: [msg, receipient, body...]
            /*
        Example:
            jim: "msg fares Hello, Fares. How are you today?" <-- sent
	    fares: "msg jim Hello, Fares. How are you today?" <-- received
             */
            String sendTo = tokens[1];
            String body = tokens[2];

            //Determine if the receiver is a single client, or it is a topic (chatroom):
            boolean isTopic = (sendTo.charAt(0) == '#');

            List<ClientProcessor> clientsList = server.getClientsList();
            for (ClientProcessor client : clientsList) {

                if (client.getLogin() != null) {

                    if (isTopic) {
                        // Check if this 'client' is joined to the topic name which is stored in the variable 'sendTo'.
                        if (client.isMemberOfTopic(sendTo)) {
                            //the 'client' will search in his topicSet, to see if he is joined to this topic.
                            //If so, then he will be sent a message.
                            String outMsg = "msg " + sendTo + ":" + login + " " + body + "\n\r";
                            client.send(outMsg);
                            //'sendTo' stores the name of the topic, and 'login' stores the username of the sender.
                        }
                    } else {
                        // Then the recipient is a single client
                        if (client.getLogin().equalsIgnoreCase(sendTo)) {
                            //String outMsg = "msg " + login + " " + body + "\n\r";
                            String outMsg = login + ": " + body + "\n\r";

                            // 'login' stores the name of the sender
                            client.send(outMsg); // write into the outputStream of the recipient only.
                        }
                    }
                }
            }
        } else {
            String msg = "You have to login in order to be allowed to send messages\n\r";
            this.outputStream.write(msg.getBytes());
        }

    }// end method

    private void processMessageBroadcast(String[] tokens) throws IOException {
        //tokens[] now is array of length 2: [msg-broadcast, body...]
        /*
        Example:
            "msg-broadcast Hello everyone, How are you all?" <-- (sent from 'Fares')
	    "msg fares Hello everyone, How are you all?" <-- (received by all other clients) 
         */

        if (login != null) {
            //The client must be logged in to be allowed to send messages to others:

            String msgBody = tokens[1]; // the second token
            String msg = "msg " + login + " " + msgBody + "\n\r";

            //Since this is a broadcast message, then send this message to ALL other clients:
            List<ClientProcessor> clientsList = server.getClientsList();

            for (ClientProcessor client : clientsList) {
                if (client.getLogin() != null) {
                    //We don't want the client to receive the message that he sent to others :)
                    if (!login.equals(client.getLogin())) {
                        client.send(msg);
                    }
                }
            }
        } else {
            String msg = "You have to login in order to be allowed to send messages\n\r";
            this.outputStream.write(msg.getBytes());
        }
    }// end method

    private void processLogoff() throws IOException {

        //remove this ClientProcessor instance from the list of ClientProcessors
        server.removeClientProcess(this);

        List<ClientProcessor> clientsList = server.getClientsList();

        //We want to broadcast all currently logged in clients, a message tells that this current client has logged off.
        String offlineMsg = "offline " + login + "\n\r";
        for (ClientProcessor client : clientsList) {

            // Also nobody needs to be notified about himself being logged off :)
            if (!login.equals(client.getLogin())) {
                client.send(offlineMsg);
            }
        }

        System.out.println("user logged off: " + login);

        clientSocket.close();
    }// end method

    private boolean processDeregister() throws IOException {

        // Logically speaking, The client must be first logged in in order to be able to derigister himself.
        // So if login == null, this means that the client isn't connected (logged in) yet.
        if (login != null) {
            // the the client is logged in, so I can now derigister him (delete him from the users hastable):

            //remove this ClientProcessor instance from the list of ClientProcessors
            server.removeClientProcess(this);

            List<ClientProcessor> clientsList = server.getClientsList();

            //remove this ClientProcessor instance from the list of ClientProcessors
            if (server.deregisterUser(login)) {
                String msg = "ok deregister: " + login + "\n\r";
                outputStream.write(msg.getBytes());

                System.out.println("Deregistered: " + login);
                System.out.println(server.usersTable);

                //We want to broadcast all currently logged in clients, a message tells that this current client has derigistered.
                String deregMsg = "deregistered " + login + "\n\r";
                for (ClientProcessor client : clientsList) {
                    client.send(deregMsg);
                }

                clientSocket.close();
            } else {
                String msg = "error deregister: " + login + "\n\r";
                outputStream.write(msg.getBytes());
            }

            return true; // deregistered successfully
        } else {
            String msg = "error derigister. You must be logged in to be able to derigister\n\r";
            this.outputStream.write(msg.getBytes());

            System.err.println("error derigister. You must be logged in to be able to derigister");

            return false;
        }
    }// end method

    private void processQueryWhoIsConnected() throws IOException {
        //"client will be able to query the server and see who is connected from the clients right now"
        send("List of Online Users:\n\r-------------------------\n\r");

        List<ClientProcessor> clientsList = server.getClientsList();

        //Send (notify) current user all other online loggins (all other logged in users)
        for (ClientProcessor client : clientsList) {

            // We don't care about clients who open the terminal but not logged in yet:
            if (client.getLogin() != null) {

                // Also nobody needs to be notified about himself being online :)
                if (!login.equals(client.getLogin())) {
                    String msg2 = "    - " + client.getLogin() + "\n\r";
                    send(msg2); // this.send(msg2), this means that every online client will have his 
                    //name written on this current user's stream (on the terminal of the current client)
                }
            } else {
                String msg = "You have to login in order to see the list of online usersn\r";
                this.outputStream.write(msg.getBytes());
            }
        }
    }// end method

    private void processWhoAmI() {
        String msg;

        try {
            if (login != null) {
                msg = login + "\n\r";
                outputStream.write(msg.getBytes());
            } else {
                msg = "You are a Guest. Login to have access on services suchs as sending and receiving messages\n\r";
                outputStream.write(msg.getBytes());
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }// end method

}// end class
