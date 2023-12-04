import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int PORT = 8080;

    // concurrentHashMap to store chatrooms
    private static Map<String, List<Socket>> chatRooms = new ConcurrentHashMap<>();

    // concurrentHashMap to store messages in a chat room
    private static Map<String, BlockingQueue<String>> chatRoomMessages = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try {
            // socket to listen for the server
            ServerSocket serverSocket = new ServerSocket(PORT);

            System.out.println("Chat Server is running on port " + PORT);

            // accept requests from clients
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // this inner class handles the requests from clients and 
    // interprets them by calling the right methods.
    static class ClientHandler extends Thread {
        private Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;
        private String currentRoom;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        public void run() {
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                out.println("Welcome to the Chat Server!");

                // read input from the user
                String input;
                while ((input = in.readLine()) != null) {
                    // get command for the server as well as any other arguments
                    String[] tokens = input.split(" ");
                    if (tokens.length == 0) {
                        continue;
                    }
                    String command = tokens[0];
                    switch (command) {
                        case "create":
                            createChatRoom(tokens);
                            break;
                        case "list":
                            listChatRooms();
                            break;
                        case "join":
                            joinChatRoom(tokens);
                            break;
                        case "leave":
                            leaveChatRoom();
                            break;
                        case "send":
                            sendMessage(tokens);
                            break;
                        default:
                            out.println("Unknown command: " + command);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                // if client was part of a chat room, 
                // remove the client's socket from the room and close the socket.
                try {
                    if (currentRoom != null) {
                        chatRooms.get(currentRoom).remove(clientSocket);
                    }
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // create a chatroom
        private void createChatRoom(String[] tokens) {
            // user error handling
            if (tokens.length < 2) {
                out.println("Usage: create <room_name>");
                return;
            }

            // get the name of the chat room and add it to the ConcurrentHashMap
            String roomName = tokens[1];
            chatRooms.putIfAbsent(roomName, new CopyOnWriteArrayList<>());

            // create a BlockingQueue for messages in this chat room
            chatRoomMessages.putIfAbsent(roomName, new LinkedBlockingQueue<>());

            // announce to the user that the room is created
            out.println("Chat room '" + roomName + "' created.");
        }

        // list all the available chat rooms
        private void listChatRooms() {
            out.println("List of existing chat rooms:");
            for (String room : chatRooms.keySet()) {
                out.println(room);
            }
        }

        // join an existing chat room
        private void joinChatRoom(String[] tokens) {
            // user error handling
            if (tokens.length < 2) {
                out.println("Usage: join <room_name>");
                return;
            }
            // get name
            String roomName = tokens[1];

            // check if the room exists in ConcurrentHashMap
            if (!chatRooms.containsKey(roomName)) {
                out.println("Chat room '" + roomName + "' does not exist.");
                return;
            }

            if (currentRoom != null) {
                chatRooms.get(currentRoom).remove(clientSocket);
            }
            // add the client to the chat room
            chatRooms.get(roomName).add(clientSocket);
            currentRoom = roomName;

            // announce to the client
            out.println("You have joined the chat room '" + roomName + "'.");

            //System.out.println("cur is "+currentRoom);

            // get all the messages in chat room
            fetchMessages(roomName);
        }

        // send each previous message to the client
        // this method is used when the client joins a new room
        private void fetchMessages(String roomName) {
            BlockingQueue<String> messages = chatRoomMessages.get(roomName);
            if (messages != null) {
                for (String message : messages) {
                    out.println(message);
                }
            }
        }

        // leave the current chat room and go back to the lobby
        private void leaveChatRoom() {
            // check whether the currentRoom attribute is not null,
            // which means that the client is currently in a chat room
            if (currentRoom != null) {
                // if in a chat room, then leave
                chatRooms.get(currentRoom).remove(clientSocket);
                currentRoom = null;
                out.println("You have left the chat room.");
            } else {
                // alert the client that they are in the menu
                out.println("You are not in a chat room.");
            }
        }

        // method used to send messages in chat rooms
        private void sendMessage(String[] tokens) {
            //System.out.println("message to cur is "+currentRoom);
            // check if the user is currently in a chat room 
            // and if there's a message to send
            if (currentRoom != null && tokens.length > 1) {
                // get the message content from the input
                String message = String.join(" ", Arrays.copyOfRange(tokens, 1, tokens.length));

                // add message to the chat room's message history
                BlockingQueue<String> messages = chatRoomMessages.get(currentRoom);
                if (messages != null) {
                    messages.add("User: " + message);
                }

                // create a message to be sent to the chat room
                String formattedMessage = "User: " + message;

                // send the message to all clients in the current chat room
                sendToRoom(currentRoom, formattedMessage);
            } else {
                // if conditions aren't met, inform the user of the correct usage
                out.println("Usage: send <message>");
            }
        }

        // method sends a message to all clients in a specific chat room
        private void sendToRoom(String roomName, String message) {
            // get list of client sockets for the specified chat room
            List<Socket> clients = chatRooms.get(roomName);

            // Loop through each client socket in the chat room
            for (Socket socket : clients) {
                try {
                    // make a PrintWriter to send messages to the client
                    PrintWriter roomOut = new PrintWriter(socket.getOutputStream(), true);

                    // send the specified message to the current client's socket
                    roomOut.println(message);
                } catch (IOException e) {
                    // handle potential errors
                    e.printStackTrace();
                }
            }
        }
    }
}
