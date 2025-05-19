
## Connect 
> telnet localhost 2525

## Template

```
HELO mypc
MAIL FROM:<me@hk.com>
RCPT TO:<you@hk.com>
DATA
Subject: Hello from CLI

This is a test email sent from the terminal.
Best,
Me
.
QUIT
```

---

## Simple Java SMTP Server Documentation

This document walks through a minimal SMTP-like mail server written in Java. It covers:

1. **Protocols** and how they work
2. **Ports** and why we need them
3. **Mail servers** and their operation
4. **Requirements** for a mail server
5. **Project overview**
6. **Code walkthrough**
7. **Running and testing**

---

### 1. What is a Protocol and How Does It Work?

A **protocol** is a set of rules that define how data is formatted and transmitted between devices on a network. In our case:

* **SMTP (Simple Mail Transfer Protocol)** is the standard protocol for sending email messages across TCP/IP networks.
* SMTP defines commands (e.g., `HELO`, `MAIL FROM`, `RCPT TO`, `DATA`, `QUIT`) and corresponding numeric response codes (e.g., `220`, `250`, `354`, `550`).
* Communication is **text-based** over a **TCP** connection: the client sends commands, the server responds, and the message body is transmitted in plain text.

> **How it works:**
>
> 1. Client connects to the server’s TCP port.
> 2. Server sends a `220` greeting.
> 3. Client issues `HELO` (or `EHLO`) to identify itself.
> 4. Client declares sender and recipient with `MAIL FROM:` and `RCPT TO:`.
> 5. Client sends `DATA` and then the message content, ending with a line containing only `.`.
> 6. Server responds with `250 OK` and, eventually, `221 Bye` when client issues `QUIT`.

---

### 2. What Are Ports? Why We Need Them?

* A **port** is a numeric identifier (0–65535) that distinguishes different network services on the same host.
* Common ports:

    * **25** for SMTP (default)
    * **465** for SMTP over SSL
    * **587** for SMTP with STARTTLS
    * **80** for HTTP
    * **443** for HTTPS

**Why ports?**

* They allow multiple services (web servers, mail servers, databases) to run on a single IP address without conflicts.

In our example, we use port **2525** to avoid requiring root privileges (low ports <1024 often need elevated rights) and to prevent conflicts with any existing mail service.

---

### 3. What is a Mail Server and How Does It Work?

A **mail server** handles the sending, receiving, and storage of email messages. Key components:

1. **SMTP server**: Receives and relays outgoing mail.
2. **POP3/IMAP server**: Allows clients to fetch and manage received mail.
3. **Mailbox storage**: Where email files or database entries are kept.

**Our server focus**: A simple SMTP server that:

* Accepts SMTP commands
* Processes one domain (`@hk.com`)
* Saves messages in a local filesystem mailbox
* Does not implement POP3/IMAP or security layers (TLS, auth)

---

### 4. Requirements of a Mail Server

To build a functional mail server, you need:

* **Networking**: Listen on a TCP port, accept connections
* **Protocol support**: Parse SMTP commands, send responses
* **Message handling**: Queue, retry logic, bounce handling
* **Storage**: File system or database for mailboxes
* **Security**: TLS/SSL, authentication, spam prevention (SPF/DKIM/DMARC)
* **DNS integration**: MX record lookup for outbound delivery
* **Logging and monitoring**

Our minimal implementation covers the first three items in a basic form.

---

### 5. Project Overview

* **Language**: Java 17+
* **Dependencies**: None (uses standard library)
* **Supported domain**: `@hk.com`
* **Storage**: Files under `MAILBOX` directory, one folder per recipient
* **Port**: 2525

The server listens for incoming SMTP-like sessions, processes commands, and writes each message as a `.eml` file in the appropriate recipient folder.

---

### 6. Code Walkthrough

```java
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
                Thread.ofVirtual().name("Mail Server Connection")
                      .start(() -> handleClient(clientSocket));
            }
        }
    }

    private static void handleClient(Socket socket) {
        try (
            socket;
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        ) {
            // SMTP greeting
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
                        data.setLength(0);
                    } else {
                        data.append(line).append("\r\n");
                    }
                    continue;
                }

                // Handle commands
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
        String recipientFolder = to.toLowerCase();
        Path recipientDir = MAILBOX.resolve(recipientFolder);

        if (!Files.exists(recipientDir)) {
            Files.createDirectories(recipientDir);
        }

        String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String filename = "mail_" + timestamp + ".eml";
        Path file = recipientDir.resolve(filename);

        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write("From: " + from + "\r\n");
            writer.write("To: " + to + "\r\n");
            writer.write("Date: " + LocalDateTime.now() + "\r\n");
            writer.write("Subject: (No Subject)\r\n");
            writer.write("\r\n");
            writer.write(content);
        }

        System.out.println("Saved email to " + file.toAbsolutePath());
    }
}
```

* **`main`**: Initializes mailbox directory and listens on port.
* **`handleClient`**: Processes SMTP commands in a loop.
* **`saveEmail`**: Writes each message to a timestamped `.eml` file in a folder named after the recipient.

---

### 7. Running and Testing

1. **Compile**

   ```bash
   javac -d out src/xyz/sadiulhakim/Server.java
   ```

2. **Run**

   ```bash
   java -cp out xyz.sadiulhakim.Server
   ```

3. **Test via Telnet**

   ```bash
   telnet localhost 2525
   HELO test
   MAIL FROM:<me@hk.com>
   RCPT TO:<you@hk.com>
   DATA
   Subject: Test Email

   Hello from the simple SMTP server!
   .
   QUIT
   ```

4. **Verify**

   Check `F:\mailbox\you@hk.com\` for a new `.eml` file.

---

*End of documentation.*