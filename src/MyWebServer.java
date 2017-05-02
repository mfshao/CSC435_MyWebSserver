/*--------------------------------------------------------

1. Mingfei Shao / 10/06/2016:

2. Java version used: build 1.8.0_102-b14

3. Precise command-line compilation examples / instructions:
> javac MyWebServer.java

4. Precise examples / instructions to run this program:
In separate shell windows:
> java MyWebServer
The program will use the default port number (2540).

5. List of files:
a. MyWebServer.java
b. checklist-mywebserver.html
c. http-streams.txt
d. serverlog.txt


5. Notes:
I implemented a small helper class to construct html pages so the code will looks nicer.
I also implemented some method to send back standard HTTP error message rather than "200 OK".
Currently only supporting 400, 403 and 404 errors, which are the most common ones.
But it is possible and easy to add support to more error messages if needed.
I dealt with some security issues: if someone sends some HTTP GET request with path "../..",
the request will be denied for and the client will get a 403 error message.
Last point, this server is able to send favicon.ico to browser if there is one in the directory.
If not, it will send 404 error back, which is also acceptable to browsers.

----------------------------------------------------------*/

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

// Worker class to handle HTTP requests from browser, each worker class will run on a new thread
class ServerWorker extends Thread {
    private Socket sock;
    // Get the carriage return / line feed combination
    private String crlf = HtmlUtil.getCRLF();

    ServerWorker(Socket s) {// Constructor to initialize socket
        sock = s;
    }

    // Compose error message into html format
    private String composeHttpError(String title, String h1, String text) {
        // Construct message head by append title tag to message title
        String head = HtmlUtil.appendTitle(title);
        // Append head tag to message title
        head = HtmlUtil.appendHead(head);
        // Construct message body by append p and h1 tags to message text
        String body = HtmlUtil.appendH1(h1) + HtmlUtil.appendP(text);
        // Append body tag to message body
        body = HtmlUtil.appendBody(body);
        // Concatenate message head and message body, append html tag and return
        return HtmlUtil.appendHtml(head + body);
    }

    // Send HTTP error message out
    private void sendHttpError(int code, String filePath, PrintStream out) {
        // Message header
        String header;
        // Message body
        String message;

        // Compose different header and body according to error code
        switch (code) {
            // Bad Request
            case 400:
                header = "HTTP/1.1 400 Bad Request";
                message = composeHttpError("400 Bad Request", "Bad Request", "Your browser sent a request that this server could not understand.");
                break;
            // Forbidden
            case 403:
                header = "HTTP/1.1 403 Forbidden";
                message = composeHttpError("403 Forbidden", "Forbidden", "You don't have permission to access " + filePath + " on this server.");
                break;
            // Not Found
            case 404:
                header = "HTTP/1.1 404 Not Found";
                message = composeHttpError("404 Not Found", "Not Found", "The requested URL " + filePath + " was not found on this server.");
                break;
            // Just in case, can be ignored
            default:
                header = "";
                message = "";
        }

        // Send composed HTTP error message out
        sendHttpMessage(header, Integer.toString(message.length()), "text/html", message, out);
    }

    // Method to send HTTP message
    private void sendHttpMessage(String header, String contentLen, String contentType, String content, PrintStream out) {
        // Print header + crlf + content length + crlf + content type, followed by two crlfs and then the message content as convention
        out.print(header);
        out.print(crlf);
        out.print("Content-Length: " + contentLen);
        out.print(crlf);
        out.print("Content-Type: " + contentType);
        out.print(crlf);
        out.print(crlf);
        out.print(content);
        // Flush the output stream for safe
        out.flush();

        printServerConsoleMessage(header, contentLen, contentType, content);
    }

    private void sendHttpBinaryMessage(String header, String contentLen, String contentType, byte[] content, PrintStream out) {
        // Print header + crlf + content length + crlf + content type, followed by two crlfs and then the binary content as convention
        out.print(header);
        out.print(crlf);
        out.print("Content-Length: " + contentLen);
        out.print(crlf);
        out.print("Content-Type: " + contentType);
        out.print(crlf);
        out.print(crlf);
        // Use PrintStream.write() method to write binary data
        try {
            out.write(content);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Flush the output stream for safe
        out.flush();

        printServerConsoleMessage(header, contentLen, contentType, "[Binary data]"+crlf);
    }

    // Method used to print out debug message on server console, can be ignored or commented out
    private void printServerConsoleMessage(String header, String contentLen, String contentType, String content){
        System.out.println();
        System.out.println("======== Begin server reply ========");
        System.out.print(header);
        System.out.print(crlf);
        System.out.print("Content-Length: " + contentLen);
        System.out.print(crlf);
        System.out.print("Content-Type: " + contentType);
        System.out.print(crlf);
        System.out.print(crlf);
        System.out.print(content);
        System.out.println("======== End server reply ========");
        System.out.println();
    }

    private void sendFavIco(File file, String contentType, PrintStream out){
        try {
            DataInputStream  dis = new DataInputStream (new FileInputStream(file));
            byte[] buffer = new byte[dis.available()];
            dis.readFully(buffer);
            dis.close();
            sendHttpBinaryMessage("HTTP/1.1 200 OK", Long.toString(file.length()), contentType, buffer, out);
        } catch (FileNotFoundException fnfe) {
            fnfe.printStackTrace();
        }catch (IOException ioe){
            ioe.printStackTrace();
        }
    }

    // If the file requested by GET can be found on server side
    private void sendFileOK(File file, PrintStream out) {
        // Temp string to hold content type information
        String contentType = "";

        // Assign content type information by file extension
        if (file.getName().toLowerCase().endsWith(".txt")) {
            // Plain text file
            contentType = "text/plain";
        } else if (file.getName().toLowerCase().endsWith(".html") || file.getName().toLowerCase().endsWith(".htm")) {
            // Html file
            contentType = "text/html";
        }else if (file.getName().equalsIgnoreCase("favicon.ico")){
            // Favicon.ico file, has special MIME type
            contentType = "image/x-icon";
            sendFavIco(file, contentType, out);
            return;
        }


        // Read out file content
        StringBuilder sb = new StringBuilder();
        String textFromFile = "";
        try {
            BufferedReader in = new BufferedReader(new FileReader(file));
            // If not done reading
            while ((textFromFile = in.readLine()) != null) {
                // Read a line out from file, append crlf to it
                sb.append(textFromFile);
                sb.append(crlf);
            }
            // Full file content with crlfs at the end of each line
            textFromFile = sb.toString();
        } catch (FileNotFoundException fnfe) {
            fnfe.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        // Send successful message and file content back
        sendHttpMessage("HTTP/1.1 200 OK", Long.toString(file.length()), contentType, textFromFile, out);
    }

    // If the directory requested by GET can be found on server side
    private void sendDirOK(ArrayList<String> resultList, String path, PrintStream out) {
        // Construct directory and file list in html format
        StringBuilder sb = new StringBuilder();

        // Heading line
        sb.append(HtmlUtil.appendH1("Index of " + path));
        // The contents
        for (int i = 0; i < resultList.size(); i++) {
            if (i == 0) {
                // First path in resultList is the Parent Directory
                sb.append(HtmlUtil.appendHref("Parent Directory", resultList.get(i)));
            } else {
                // For everything else, their display name and path are the same as saved in resultList
                sb.append(HtmlUtil.appendHref(resultList.get(i), resultList.get(i)));
            }
        }
        String content = sb.toString();
        // Append pre tag to message
        content = HtmlUtil.appendPre(content);
        // Append html tag to message
        content = HtmlUtil.appendHtml(content);

        // Send successful message and constructed html information back
        sendHttpMessage("HTTP/1.1 200 OK", Integer.toString(content.length()), "text/html", content, out);
    }

    // Method to iterate through current folder and to gather information for files and directories, results are stored in resultList
    private void listDir(File file, ArrayList<String> resultList) {
        File[] strFilesDirs = file.listFiles();

        // If the directory is not empty
        if (strFilesDirs != null) {
            // Iterate through files and directories
            for (File f : strFilesDirs) {
                // Current entry is a directory
                if (f.isDirectory()) {
                    // Add "/" at the end of its name
                    resultList.add(f.getName() + "/");
                } else {
                    // This is a file, just get its name out
                    resultList.add(f.getName());
                }
            }
        }
    }

    // Method to handle the (fake) CGI request
    private void addnum(String paraAll, PrintStream out) {
        // Split the three arguments out
        String[] paraStr = paraAll.split("&");
        // Get the values of three arguments
        String personStr = paraStr[0].substring(paraStr[0].indexOf("=") + 1);
        String num1Str = paraStr[1].substring(paraStr[1].indexOf("=") + 1);
        String num2Str = paraStr[2].substring(paraStr[2].indexOf("=") + 1);
        int num1 = 0;
        int num2 = 0;

        // If any of the arguments is invalid, we have error message
        String errorMessage = "";
        if (personStr.isEmpty()) {
            // No person name entered
            errorMessage += "Please enter a valid person name. ";
        }
        try {
            num1 = Integer.parseInt(num1Str);
        } catch (NumberFormatException nfe) {
            // Parse num1 fails
            errorMessage += "Please enter a valid integer for num1. ";
        }
        try {
            num2 = Integer.parseInt(num2Str);
        } catch (NumberFormatException nfe) {
            // Parse num2 fails
            errorMessage += "Please enter a valid integer for num2. ";
        }

        // If found error message, then something is wrong
        if (!errorMessage.isEmpty()) {
            // Construct error message into html format, append pre and html tags
            errorMessage = HtmlUtil.appendPre(errorMessage);
            errorMessage = HtmlUtil.appendHtml(errorMessage);
            // Send error message out
            sendHttpMessage("HTTP/1.1 200 OK", Integer.toString(errorMessage.length()), "text/html", errorMessage, out);
        } else {
            // If no error message, we are good to go, add num1 and num2 together
            int addResult = num1 + num2;
            // Construct success message with person name, num1, num2 and the result after addition
            String successMessage = "Dear " + personStr.replace("+", " ") + ", the sum of " + num1Str + " and " + num2Str + " is " + Integer.toString(addResult) + ".";
            // Construct success message into html format, append pre and html tags
            successMessage = HtmlUtil.appendPre(successMessage);
            successMessage = HtmlUtil.appendHtml(successMessage);
            // Send success message out
            sendHttpMessage("HTTP/1.1 200 OK", Integer.toString(successMessage.length()), "text/html", successMessage, out);
        }
    }

    // Method to handle the HTTP GET request
    private void processGetRequest(String filePath, PrintStream out) {
        // Add "." in front of the path in GET request to start from current directory.
        filePath = "." + filePath;
        File file = new File(filePath);

        // A little bit security measure (latest version of Firefox and Chrome will actually take care of ../.. at browser side)
        if (filePath.contains("../..")) {
            // Someone is trying too peek around, send 403 error and not serving this request
            sendHttpError(403, filePath, out);
            return;
        }

        // If the GET request comes from the CGI form
        if (filePath.contains("cgi/addnums.fake-cgi")) {
            // Split the string by question mark
            String[] subStr = filePath.split("\\?");
            // Parameters are in the substring immediately following the question mark
            addnum(subStr[1], out);
        } else {
            // If the GET request asks for a folder
            if (filePath.endsWith("/")) {
                // If the folder exists
                if (file.exists()) {
                    // Construct an ArrayList to hold item information in the folder
                    ArrayList<String> resultList = new ArrayList<>();
                    // First element in the ArrayList is the path of parent directory
                    if (file.getParent() != null) {
                        // If the folder is not the root of the server, use "../" to go back a level
                        resultList.add("../");
                    } else {
                        // If the folder is the root of the server, restrict the parent directory to be itself so it will not go over the limited area
                        resultList.add("./");
                    }
                    // Call method to list over items in this directory
                    listDir(file, resultList);
                    // Send the directory information for processing (generating the html page)
                    sendDirOK(resultList, filePath, out);
                } else {
                    // If the requested folder does not exists, send 404 error out
                    sendHttpError(404, filePath, out);
                }
            } else {
                // In this case, looking for a file
                if (file.exists()) {
                    if (file.isFile()) {
                        // If file exists and is a file, then call method to process the content of the file and send out
                        sendFileOK(file, out);
                    } else {
                        // If it is not a file, something is wrong, send 403 error just for cautious
                        sendHttpError(403, filePath, out);
                    }
                } else {
                    // File not found, send 404 error out
                    sendHttpError(404, filePath, out);
                }
            }
        }
    }

    // Define the behavior of a running thread
    public void run() {
        PrintStream out;
        BufferedReader in;
        try {
            // Initialize the input stream of the socket as BufferedReader
            in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            // Initialize the output stream of the socket as PrintStream
            out = new PrintStream(sock.getOutputStream());
            // Hold the request string
            String request;
            String[] subStr = {};

            try {
                // Read request from client (browser)
                while (!(request = in.readLine()).isEmpty()) {
                    System.out.println(request);
                    // If it is a GET request (not taking consideration into HTTP protocol version or Host info at this time to simplify the problem)
                    if (request.contains("GET")) {
                        // Split the request string by white spaces
                        subStr = request.split("\\s+");
                    }
                }
                // If the HTTP GET request has arguments
                if (subStr.length > 1) {
                    // The string after the first white space is the path the server is trying to get
                    String filePath = subStr[1];
                    // Call method to handle GET request
                    processGetRequest(filePath, out);
                } else {
                    // The HTTP GET request is invalid, send 400 error out
                    sendHttpError(400, null, out);
                }
            } catch (IOException ioe) {
                // In case of read from input stream fails
                System.out.println("Server read error");
                ioe.printStackTrace();
            } catch (NullPointerException npe) {
            }
            System.out.println();
            // Close everything
            out.close();
            in.close();
            sock.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}

// Helper class for constructing html pages
class HtmlUtil {
    // Define crlf combination here
    private static final String CRLF = "\r\n";

    // Getter for crlf
    static String getCRLF() {
        return CRLF;
    }

    // Append html tag <title></title>
    static String appendTitle(String str) {
        return "<title>" + CRLF + str + "</title>" + CRLF;
    }

    // Append html tag <head></head>
    static String appendHead(String str) {
        return "<head>" + CRLF + str + "</head>" + CRLF;
    }

    // Append html tag <body></body>
    static String appendBody(String str) {
        return "<body>" + CRLF + str + "</body>" + CRLF;
    }

    // Append html tag <p></p>
    static String appendP(String str) {
        return "<p>" + CRLF + str + "</p>" + CRLF;
    }

    // Append html tag <html></html>
    static String appendHtml(String str) {
        return "<html>" + CRLF + str + "</html>" + CRLF;
    }

    // Append html tag <pre></pre>
    static String appendPre(String str) {
        return "<pre>" + CRLF + str + "</pre>" + CRLF;
    }

    // Append html tag <h1></h1>
    static String appendH1(String str) {
        return "<h1>" + str + "</h1>" + CRLF;
    }

    // Append html tag <a href></a>
    static String appendHref(String fileName, String filePath) {
        return "<a href=\"" + filePath + "\">" + fileName + "</a><br>" + CRLF;
    }
}

public class MyWebServer {
    // Define default port number
    private static final int DEFAULT_PORT = 2540;

    @SuppressWarnings("resource")
    public static void main(String[] args) throws IOException {
        // Not interesting. Number of requests for OpSys to queue
        int q_len = 6;
        // Initialize port number to default
        int port = DEFAULT_PORT;

        Socket sock;
        // Initialize a new server type socket using port number and queue length
        ServerSocket servSock = new ServerSocket(port, q_len);
        System.out.println("Mingfei Shao's MyWebServer starting up, listening at port " + port + ".\n");
        // Stick here to serve any incoming clients
        while (true) {
            // Wait for client to connect
            sock = servSock.accept();
            // After connected, start a new worker thread to handle client's request, and main thread stays in the loop, waiting for next client
            new ServerWorker(sock).start();
        }

    }
}
