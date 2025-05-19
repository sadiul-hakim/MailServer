package xyz.sadiulhakim;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Server {

    private static final int PORT = 2525;
    private static final String ACCEPTED_DOMAIN = "@hk.com";
    private static final Path MAILBOX = Paths.get("F:\\mailbox");

    public static void main(String[] args) throws IOException {
        if (!Files.exists(MAILBOX)) Files.createDirectories(MAILBOX);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("SMTP server listening on port " + PORT);

            while (!Thread.interrupted()) {

                Socket clientSocket = serverSocket.accept();
                Thread.ofVirtual().name("Mail Server Connection").start(() -> handleClient(clientSocket));
            }
        }
    }

    private static void handleClient(Socket socket) {

        try (
                socket;
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        ) {

            out.write("220 Simple SMTP Server Ready\r\n");
            out.flush();

            String line;
            String sender = null;
            String recipient = null;
            StringBuilder data = new StringBuilder();
            boolean readingData = false;

            while ((line = in.readLine()) != null) {

                if (readingData) {
                    if (line.equals(".")) {
                        saveEmail(sender, recipient, data.toString());
                        out.write("250 OK\r\n");
                        out.flush();
                        readingData = false;
                        data.setLength(0); // reset
                    } else {
                        data.append(line).append("\r\n");
                    }
                    continue;
                }

                if (line.startsWith("HELO") || line.startsWith("EHLO")) {
                    out.write("250 Hello\r\n");
                } else if (line.startsWith("MAIL FROM:")) {
                    sender = line.substring(10).replaceAll("[<>]", "").trim();
                    out.write("250 OK\r\n");
                } else if (line.startsWith("RCPT TO:")) {
                    String rcpt = line.substring(8).replaceAll("[<>]", "").trim();
                    if (rcpt.endsWith(ACCEPTED_DOMAIN)) {
                        recipient = rcpt;
                        out.write("250 OK\r\n");
                    } else {
                        out.write("550 Unsupported recipient domain\r\n");
                    }
                } else if (line.equals("DATA")) {
                    out.write("354 End data with <CR><LF>.<CR><LF>\r\n");
                    readingData = true;
                } else if (line.equals("QUIT")) {
                    out.write("221 Bye\r\n");
                    break;
                } else {
                    out.write("500 Unrecognized command\r\n");
                }
                out.flush();
            }

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    private static void saveEmail(String from, String to, String content) throws IOException {

        // Sanitize recipient to use as folder name (e.g., you@hk.com)
        String recipientFolder = to.toLowerCase();
        Path recipientDir = MAILBOX.resolve(recipientFolder);

        if (!Files.exists(recipientDir)) {
            Files.createDirectories(recipientDir);
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String filename = "mail_" + timestamp + ".eml";
        Path file = recipientDir.resolve(filename);

        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write("From: " + from + "\r\n");
            writer.write("To: " + to + "\r\n");
            writer.write("Date: " + LocalDateTime.now() + "\r\n");
            writer.write("\r\n");
            writer.write(content);
        }

        System.out.println("Saved email to " + file.toAbsolutePath());
    }

}
