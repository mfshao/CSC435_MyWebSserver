/*--------------------------------------------------------

1. Mingfei Shao / 10/05/2016:

2. Java version used: build 1.8.0_102-b14

3. Precise command-line compilation examples / instructions:
> javac MyListener.java

4. Precise examples / instructions to run this program:
In separate shell windows:
> java MyListener

----------------------------------------------------------*/

import java.io.*; // Get the Input Output libraries
import java.net.*; // Get the Java networking libraries

// Worker class to handle client requests, each worker class will run on a new thread
class ListenerWorker extends Thread {
    Socket sock;

    ListenerWorker(Socket s) {// Constructor to initialize socket
        sock = s;
    }

    // Define the behavior of a running thread
    public void run() {
        BufferedReader in;
        try {
            // Initialize the input stream of the socket as BufferedReader
            in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            String request;

            try {
                while (!(request = in.readLine()).isEmpty())
                    System.out.println(request);
            } catch (IOException x) { // In case read from input stream fails
                System.out.println("Server read error");
                x.printStackTrace();
            }
            System.out.println();
            sock.close(); // Close the socket
        } catch (IOException ioe) {
            System.out.println(ioe); // In case anything wrong with the socket
        }
    }
}

public class MyListener {
    private static final int DEFAULT_PORT = 2540; // Define default port number

    @SuppressWarnings("resource")
    public static void main(String[] args) throws IOException {
        int q_len = 6; // Not interesting. Number of requests for OpSys to queue
        int port = DEFAULT_PORT; // Initialize port number to default

        Socket sock;
        ServerSocket servSock = new ServerSocket(port, q_len); // Initialize a new server type socket using port number and queue length
        System.out.println("Mingfei Shao's MyListener starting up, listening at port " + port + ".\n");
        while (true) { // Stick here to serve any incoming clients
            sock = servSock.accept(); // Wait for client to connect
            new ListenerWorker(sock).start(); // After connected, start a new worker thread to handle client's request, and main thread stays in the loop, waiting for next client
        }
    }
}
