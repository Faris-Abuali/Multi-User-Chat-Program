package my_server;

/**
 *
 * @author Fares Abu Ali
 */
public class ServerStarter {
    //Starting The Server

    public static void main(String[] args) {

        /**
         * "Make sure you do not hard-code port, but let your server socket
         * choose an open port".
         *
         * If I specify a port of 0 to the ServerSocket constructor, then it
         * will listen on any free port:
         */
        int port = 0;
        MyServer server = new MyServer(port);
        server.start(); // will invoke the 'run()' method of the 'Server' class
    }// end main
}// end class


