/*--------------------------------------------------------

1. Mingfei Shao / 10/05/2016:

2. Java version used: build 1.8.0_102-b14

3. Precise command-line compilation examples / instructions:
> javac MyTelnet.java

4. Precise examples / instructions to run this program:
In separate shell windows:
> java MyTelnet condor.depaul.edu 80

----------------------------------------------------------*/

import java.io.*; // Get the Input Output libraries
import java.net.*; // Get the Java networking libraries
import java.util.concurrent.TimeUnit;

public class MyTelnet {
    private static final String CRLF = "\r\n";

    public static void main(String args[]) {
        String serverName;
        int serverPort;

        if (args.length == 2) {
            try {
                serverName = args[0];
                serverPort = Integer.parseInt(args[1]);
            } catch (NumberFormatException nfe) {
                System.out.println("Cannot convert custom port to a valid number."); // User input is not a integer
                return;
            }
        } else {
            System.out.println("Bad");
            return;
        }
        System.out.println("Mingfei Shao's Telnet Client.\n");
        System.out.println("Using server: " + serverName + ", Port: " + serverPort);
        System.out.println("Double press enter to send out request.");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in)); // Initialize input stream as a BufferedReader
        try {
            String input;
            String result = "";
            StringBuilder sb = new StringBuilder();
            int consecutiveReturns = 0;
            do {
                input = in.readLine(); // Read first line of input from input stream as String
                if (input.equalsIgnoreCase("quit")) {
                    break;
                }
                if (consecutiveReturns < 2) {
                    if (input.isEmpty()) {
                        consecutiveReturns++;
                    } else {
                        consecutiveReturns = 0;
                    }
                    result = processInput(sb, input);
                }
                if (consecutiveReturns == 2) {
                    sendInput(result, serverName, serverPort); // call sendInput() to get the remote address of entered string
                    consecutiveReturns = 0;
                }
            }
            while (!input.equalsIgnoreCase("quit")); // do-while to make sure the above logic will be executed at least once
            System.out.println("Cancelled by user request.");
        } catch (IOException x) {
            x.printStackTrace(); // In case read from input stream fails
        }
    }

    static String processInput(StringBuilder sb, String input) {
        sb.append(input + CRLF);
        return sb.toString();
    }

    static void sendInput(String result, String serverName, int serverPort) {
        Socket sock;
        BufferedReader fromServer;
        PrintStream toServer;
        String textFromServer;
        try {
            // Open socket using given server address and port number
            sock = new Socket(serverName, serverPort);
            // Initialize the input stream of the socket as BufferedReader
            InputStream in = sock.getInputStream();
            fromServer = new BufferedReader(new InputStreamReader(in));
            // Initialize the output stream of the socket as PrintStream
            toServer = new PrintStream(sock.getOutputStream());
            // Send user input server name to server for query.
            toServer.print(result);
            toServer.flush();
            Thread.sleep(500);
            // Read up to 3 lines from the reply of server and output them
            if (fromServer.ready()) {
                while ((textFromServer = fromServer.readLine()) != null) {
                    System.out.println(textFromServer);
                }
            }
            toServer.close();
            fromServer.close();
            // Close the socket
            System.out.println("Connection closed by foreign host.");
            System.out.println();
            sock.close();
        } catch (IOException x) { // In case the socket cannot be created for some reason
            System.out.println("Socket error.");
            x.printStackTrace();
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }
    }
}
