import java.io.*;
import java.net.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChatServer {
    private static final int PORT = 12345;
    private static final CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        int port = PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }

        System.out.println("CHAT SERVER");
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server dang lang nghe tai cong " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket);
                clients.add(handler);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.out.println("Loi server: " + e.getMessage());
        }
    }

    static void broadcastMessage(ClientHandler sender, String username, String message) {
        for (ClientHandler c : clients) {
            if (c != sender) {
                c.sendMessage(username, message);
            }
        }
    }

    static void broadcastFile(ClientHandler sender, String username, String filename, byte[] data) {
        for (ClientHandler c : clients) {
            if (c != sender) {
                c.sendFile(username, filename, data);
            }
        }
    }

    static void broadcastSystem(String message) {
        for (ClientHandler c : clients) {
            c.sendMessage("SERVER", message);
        }
    }

    static void removeClient(ClientHandler handler) {
        clients.remove(handler);
    }

    static class ClientHandler implements Runnable {
        private final Socket socket;
        private DataInputStream in;
        private DataOutputStream out;
        private String username = "Unknown";

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

                String type = in.readUTF();
                if ("JOIN".equals(type)) {
                    username = in.readUTF();
                    System.out.println("[+] " + username + " da ket noi (" + socket.getInetAddress() + ")");
                    broadcastSystem(username + " da tham gia phong chat.");
                }

                while (true) {
                    type = in.readUTF();
                    if ("MSG".equals(type)) {
                        String msg = in.readUTF();
                        System.out.println(username + ": " + msg);
                        broadcastMessage(this, username, msg);
                    } else if ("FILE".equals(type)) {
                        String filename = in.readUTF();
                        long size = in.readLong();
                        byte[] data = new byte[(int) size];
                        in.readFully(data);
                        System.out.println(username + " gui file: " + filename + " (" + size + " bytes)");
                        broadcastFile(this, username, filename, data);
                    }
                }
            } catch (IOException e) {
                System.out.println("[-] " + username + " da ngat ket noi.");
            } finally {
                removeClient(this);
                broadcastSystem(username + " da roi phong chat.");
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }

        synchronized void sendMessage(String fromUser, String message) {
            try {
                out.writeUTF("MSG");
                out.writeUTF(fromUser);
                out.writeUTF(message);
                out.flush();
            } catch (IOException e) {

            }
        }

        synchronized void sendFile(String fromUser, String filename, byte[] data) {
            try {
                out.writeUTF("FILE");
                out.writeUTF(fromUser);
                out.writeUTF(filename);
                out.writeLong(data.length);
                out.write(data);
                out.flush();
            } catch (IOException e) {
            }
        }
    }
}
