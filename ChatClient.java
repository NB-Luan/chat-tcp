import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;


public class ChatClient extends JFrame {

    private final String serverAddress;
    private final int serverPort;
    private final String username;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    private JTextArea chatArea;
    private JTextField inputField;
    private DefaultListModel<String> fileListModel;
    private JList<String> fileList;

    private final File receiveFolder = new File("received_files");

    public ChatClient(String serverAddress, int serverPort, String username) {
        super("Chat Client - " + username);
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.username = username;

        if (!receiveFolder.exists()) {
            receiveFolder.mkdirs();
        }

        buildUI();
        connectToServer();
    }

    private void buildUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(750, 500);
        setLocationRelativeTo(null);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        JScrollPane chatScroll = new JScrollPane(chatArea);

        fileListModel = new DefaultListModel<>();
        fileList = new JList<>(fileListModel);
        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int idx = fileList.getSelectedIndex();
                    if (idx >= 0) {
                        openReceivedFile(fileListModel.getElementAt(idx));
                    }
                }
            }
        });
        JScrollPane fileScroll = new JScrollPane(fileList);
        fileScroll.setPreferredSize(new Dimension(200, 0));
        fileScroll.setBorder(BorderFactory.createTitledBorder("File da nhan (double-click de mo)"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chatScroll, fileScroll);
        splitPane.setResizeWeight(0.78);

        inputField = new JTextField();
        inputField.addActionListener(e -> sendMessage());

        JButton sendButton = new JButton("Gui");
        sendButton.addActionListener(e -> sendMessage());

        JButton sendFileButton = new JButton("Gui File");
        sendFileButton.addActionListener(e -> chooseAndSendFile());

        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        bottomPanel.add(inputField, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(sendButton);
        buttonPanel.add(sendFileButton);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(splitPane, BorderLayout.CENTER);
        getContentPane().add(bottomPanel, BorderLayout.SOUTH);

        setVisible(true);
    }

    private void connectToServer() {
        try {
            socket = new Socket(serverAddress, serverPort);
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            out.writeUTF("JOIN");
            out.writeUTF(username);
            out.flush();

            appendChat("He thong", "Da ket noi toi server " + serverAddress + ":" + serverPort);

            Thread listenerThread = new Thread(this::listenToServer);
            listenerThread.setDaemon(true);
            listenerThread.start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Khong the ket noi toi server: " + e.getMessage(),
                    "Loi ket noi", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void listenToServer() {
        try {
            while (true) {
                String type = in.readUTF();
                if ("MSG".equals(type)) {
                    String user = in.readUTF();
                    String msg = in.readUTF();
                    appendChat(user, msg);
                } else if ("FILE".equals(type)) {
                    String user = in.readUTF();
                    String filename = in.readUTF();
                    long size = in.readLong();
                    byte[] data = new byte[(int) size];
                    in.readFully(data);
                    saveReceivedFile(user, filename, data);
                }
            }
        } catch (IOException e) {
            appendChat("He thong", "Mat ket noi toi server.");
        }
    }

    private void sendMessage() {
        String msg = inputField.getText().trim();
        if (msg.isEmpty()) return;
        try {
            out.writeUTF("MSG");
            out.writeUTF(msg);
            out.flush();
            appendChat(username + " (ban)", msg);
            inputField.setText("");
        } catch (IOException e) {
            appendChat("He thong", "Gui tin nhan that bai: " + e.getMessage());
        }
    }

    private void chooseAndSendFile() {
        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            out.writeUTF("FILE");
            out.writeUTF(file.getName());
            out.writeLong(data.length);
            out.write(data);
            out.flush();
            appendChat("He thong", "Da gui file: " + file.getName() + " (" + data.length + " bytes)");
        } catch (IOException e) {
            appendChat("He thong", "Gui file that bai: " + e.getMessage());
        }
    }

    private void saveReceivedFile(String user, String filename, byte[] data) {
        try {
            File outFile = new File(receiveFolder, filename);
            int counter = 1;
            String baseName = filename;
            String ext = "";
            int dot = filename.lastIndexOf('.');
            if (dot > 0) {
                baseName = filename.substring(0, dot);
                ext = filename.substring(dot);
            }
            while (outFile.exists()) {
                outFile = new File(receiveFolder, baseName + "(" + counter + ")" + ext);
                counter++;
            }
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(data);
            }
            String savedName = outFile.getName();
            appendChat(user, "Da gui file [" + savedName + "] (" + data.length + " bytes)");
            SwingUtilities.invokeLater(() -> fileListModel.addElement(savedName));
        } catch (IOException e) {
            appendChat("He thong", "Nhan file that bai: " + e.getMessage());
        }
    }

    private void openReceivedFile(String filename) {
        try {
            File file = new File(receiveFolder, filename);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Khong the mo file: " + e.getMessage());
        }
    }

    private void appendChat(String user, String message) {
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        SwingUtilities.invokeLater(() ->
                chatArea.append("[" + time + "] " + user + ": " + message + "\n"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JTextField addressField = new JTextField("127.0.0.1");
            JTextField portField = new JTextField("12345");
            JTextField nameField = new JTextField("User" + (int) (Math.random() * 1000));

            JPanel panel = new JPanel(new GridLayout(3, 2, 5, 5));
            panel.add(new JLabel("Dia chi server:"));
            panel.add(addressField);
            panel.add(new JLabel("Cong (port):"));
            panel.add(portField);
            panel.add(new JLabel("Ten hien thi:"));

            panel.add(nameField);

            int result = JOptionPane.showConfirmDialog(null, panel, "Ket noi toi Chat Server",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

            if (result == JOptionPane.OK_OPTION) {
                String address = addressField.getText().trim();
                int port = Integer.parseInt(portField.getText().trim());
                String username = nameField.getText().trim();
                new ChatClient(address, port, username);
            }
        });
    }
}
