import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private static final int PORT = 12345;
    private static Map<String, Set<ClientHandler>> chatRooms = new HashMap<>(); // Chat rooms

    public static void main(String[] args) {
        System.out.println("Server is running on port " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress().getHostAddress());

                // Handle the client connection in a new thread
                ClientHandler handler = new ClientHandler(clientSocket);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Broadcast message to clients in the same chat room
    public static void broadcastMessage(String message, ClientHandler sender, boolean isFileMessage) {
        String currentRoom = sender.getChatRoom(); // Get the sender's chat room

        synchronized (chatRooms) {
            Set<ClientHandler> clientsInRoom = chatRooms.get(currentRoom);
            if (clientsInRoom != null) {
                for (ClientHandler client : clientsInRoom) {
                    if (client != sender) { // Only send to other clients in the same room
                        if (isFileMessage) {
                            client.sendMessage("File received: " + message);
                        } else {
                            client.sendMessage(message);
                        }
                    }
                }
            }
        }
    }

    // Remove client from chat room when they disconnect
    public static void removeClient(ClientHandler clientHandler) {
        synchronized (chatRooms) {
            String room = clientHandler.getChatRoom();
            Set<ClientHandler> clientsInRoom = chatRooms.get(room);
            if (clientsInRoom != null) {
                clientsInRoom.remove(clientHandler);
                if (clientsInRoom.isEmpty()) {
                    chatRooms.remove(room); // Remove empty room
                }
            }
        }
    }

    // ClientHandler class to manage individual client connections
    private static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String chatRoom = "default"; // Default room on join

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Add the client to the default chat room
                joinRoom(chatRoom);

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("/join")) {
                        String newRoom = message.split(" ")[1];
                        joinRoom(newRoom);
                    } else if (message.startsWith("/file")) {
                        String fileName = message.split(" ")[1];
                        receiveFile(socket, fileName);
                    } else {
                        System.out.println("Received in room [" + chatRoom + "]: " + message);
                        broadcastMessage(message, this, false); // Broadcast to room
                    }
                }
            } catch (IOException e) {
                System.out.println("Connection error: " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                removeClient(this); // Remove from chat room
                System.out.println("Client disconnected.");
            }
        }

        // Join a specific chat room
        private void joinRoom(String newRoom) {
            synchronized (chatRooms) {
                // Remove from current room
                Set<ClientHandler> currentRoomClients = chatRooms.get(chatRoom);
                if (currentRoomClients != null) {
                    currentRoomClients.remove(this);
                    if (currentRoomClients.isEmpty()) {
                        chatRooms.remove(chatRoom);
                    }
                }

                // Join the new room
                chatRoom = newRoom;
                chatRooms.computeIfAbsent(chatRoom, k -> new HashSet<>()).add(this);

                sendMessage("You joined room: " + chatRoom);
                System.out.println("Client joined room: " + chatRoom);
            }
        }

        public void sendMessage(String message) {
            if (out != null) {
                out.println(message);
            }
        }

        public String getChatRoom() {
            return chatRoom;
        }
    }

    // Method to receive file
    private static void receiveFile(Socket clientSocket, String fileName) {
        try {
            DataInputStream dataInputStream = new DataInputStream(clientSocket.getInputStream());
            long fileSize = dataInputStream.readLong();

            FileOutputStream fileOutputStream = new FileOutputStream("received_" + fileName);

            byte[] buffer = new byte[4096];
            long totalBytesRead = 0;
            int bytesRead;

            while (totalBytesRead < fileSize && (bytesRead = dataInputStream.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }

            fileOutputStream.close();
            broadcastMessage(fileName, null, true); // Broadcast file to room
        } catch (IOException e) {
            System.out.println("Failed to receive file: " + e.getMessage());
        }
    }
}
