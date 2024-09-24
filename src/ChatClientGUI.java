import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.*;

public class ChatClientGUI extends JFrame {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int PORT = 12345;
    private Socket socket;
    private PrintWriter out;
    private JTextArea chatArea;
    private String currentRoom = "default"; // Default room

    public ChatClientGUI() {
        setTitle("Chat Client");
        chatArea = new JTextArea(15, 30);
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);

        JTextField inputField = new JTextField(25);
        JButton sendButton = new JButton("Send");
        JButton quitButton = new JButton("Quit");
        JButton fileButton = new JButton("Send File");
        JButton roomButton = new JButton("Join Room");

        // Layout setup
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(inputField, BorderLayout.CENTER);
        panel.add(sendButton, BorderLayout.EAST);
        panel.add(quitButton, BorderLayout.WEST);
        panel.add(fileButton, BorderLayout.SOUTH);
        panel.add(roomButton, BorderLayout.NORTH);

        // Main frame setup
        getContentPane().add(scrollPane, BorderLayout.CENTER);
        getContentPane().add(panel, BorderLayout.SOUTH);
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);

        fileButton.addActionListener(e -> {
            String filePath = selectFile();
            if (filePath != null) {
                sendFile(filePath);
            }
        });

        // Send Button Action
        sendButton.addActionListener(e -> {
            String message = inputField.getText();
            if (!message.isEmpty()) {
                sendMessage(message);
                inputField.setText(""); // Clear the text box after sending
            }
        });

        // Quit Button Action
        quitButton.addActionListener(e -> quitApplication());

        // File Button Action
        fileButton.addActionListener(e -> {
            String filePath = selectFile();
            if (filePath != null) {
                sendFile(filePath);
            }
        });

        // Room Button Action
        roomButton.addActionListener(e -> createChatRoomUI());

        new Thread(this::connectToServer).start();
    }

    private void createChatRoomUI() {
        JFrame roomFrame = new JFrame("Chat Rooms");
        JTextField roomInput = new JTextField(15);
        JButton joinButton = new JButton("Join Room");

        joinButton.addActionListener(e -> {
            String roomName = roomInput.getText();
            if (!roomName.isEmpty()) {
                out.println("/join " + roomName);
                currentRoom = roomName; // Update the current room
            }
        });

        JPanel panel = new JPanel();
        panel.add(roomInput);
        panel.add(joinButton);
        roomFrame.add(panel);
        roomFrame.pack();
        roomFrame.setVisible(true);
    }

    private void connectToServer() {
        try {
            socket = new Socket(SERVER_ADDRESS, PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String fromServer;
            while ((fromServer = in.readLine()) != null) {
                chatArea.append("Server: " + fromServer + "\n");
            }
        } catch (IOException e) {
            chatArea.append("Connection closed by server: " + e.getMessage() + "\n");
        }
    }

    private void sendMessage(String message) {
        if (out != null && !message.trim().isEmpty()) {
            out.println(message);
            chatArea.append("You: " + message + "\n");
        }
    }

    private void quitApplication() {
        if (out != null) {
            out.println("/quit");
        }
        System.exit(0);
    }

    private void sendFile(String filePath) {
        if (filePath == null || out == null || socket == null) {
            chatArea.append("Error: Invalid file path or socket.\n");
            return;
        }

        try {
            File file = new File(filePath);
            if (!file.exists()) {
                chatArea.append("Error: File not found.\n");
                return;
            }

            out.println("/file " + file.getName());  // Notify server of file transfer

            // Use DataOutputStream for file transfer
            DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
            FileInputStream fileInputStream = new FileInputStream(file);

            long fileSize = file.length();
            dataOutputStream.writeLong(fileSize);  // Send file size

            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalBytesSent = 0;

            // Send file data
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                dataOutputStream.write(buffer, 0, bytesRead);
                totalBytesSent += bytesRead;
            }

            dataOutputStream.flush();
            fileInputStream.close();

            chatArea.append("File sent: " + file.getName() + " (" + totalBytesSent + " bytes)\n");

        } catch (IOException e) {
            chatArea.append("Failed to send file: " + e.getMessage() + "\n");
        }
    }


    private void openFile(File file) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                desktop.open(file); // Open the file
            } else {
                chatArea.append("Opening files is not supported on this system.\n");
            }
        } catch (IOException e) {
            chatArea.append("Failed to open file: " + e.getMessage() + "\n");
        }
    }

    private String selectFile() {
        JFileChooser fileChooser = new JFileChooser();
        int returnValue = fileChooser.showOpenDialog(null);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            return selectedFile.getAbsolutePath();
        }
        return null;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatClientGUI::new);
    }
}
