import java.io.*;
import java.net.Socket;
import java.util.concurrent.*;

public class Client {
    // server host name and port
    private static final String SERVER_HOST = "net_java_server";
    private static final int SERVER_PORT = 8080;

    // the current chat room
    private static String currentRoom = null;
    private static ScheduledExecutorService messagePoller; // scheduled task for polling messages

    public static void main(String[] args) {
        try {
            // create a socket connection to the server
            Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
            // server outptut stream
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            // server input stream
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedReader consoleInput = new BufferedReader(new InputStreamReader(System.in)); // input from the console

            System.out.println("Connected to the Chat Server!");

            // get the user's username and crete room with name
            String username = getUsername(consoleInput); 
            out.println("create " + username);

            // start a thread for listening to the server
            Thread serverListener = new Thread(new ServerListener(socket, in));
            serverListener.start();

            // create a scheduled task for message polling
            messagePoller = Executors.newScheduledThreadPool(1); 
            messagePoller.scheduleAtFixedRate(new MessagePoller(socket, out), 0, 1500, TimeUnit.MILLISECONDS);

            while (true) {
                System.out.println("Commands: list | create <room_name> | join <room_name> | send <message> | leave");
                String input = consoleInput.readLine();

                if (input.startsWith("list")) {
                    out.println("list"); // get a list of chat rooms
                } 
                else if (input.startsWith("create")) {
                    out.println(input); // create a new chat room
                } 
                else if (input.startsWith("join")) {
                    out.println(input); // join a chat room
                } 
                else if (input.startsWith("send")) {
                    // send a message to the current room
                    out.println(input);
                } 
                else if (input.startsWith("leave")) {
                    out.println("leave"); // leave the current chat room
                } 
                else {
                    System.out.println("Commands: list | create <room_name> | join <room_name> | send <message> | leave");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String getUsername(BufferedReader consoleInput) throws IOException {
        System.out.print("Enter your username: ");
        return consoleInput.readLine(); // get the user's username
    }

    static class ServerListener implements Runnable {
        private Socket socket;
        private BufferedReader in;

        public ServerListener(Socket socket, BufferedReader in) {
            this.socket = socket;
            this.in = in;
        }

        public void run() {
            try {
                String serverResponse;
                while ((serverResponse = in.readLine()) != null) {
                    if (serverResponse.startsWith("User: ")) {
                        System.out.println(serverResponse); // print user-related messages
                    } else if (serverResponse.startsWith("Room: ")) {
                        String roomName = serverResponse.substring(6);
                        String message;
                        while (!(message = in.readLine()).isEmpty()) {
                            System.out.println("[" + roomName + "] " + message); // print messages in a chat room
                        }
                    } else {
                        System.out.println(serverResponse); // print other server responses
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static class MessagePoller implements Runnable {
        private Socket socket;
        private PrintWriter out;

        public MessagePoller(Socket socket, PrintWriter out) {
            this.socket = socket;
            this.out = out;
        }

        public void run() {
            if (currentRoom != null) {
                // poll for new messages in the current room
                out.println("fetch " + currentRoom);
            }
        }
    }
}
