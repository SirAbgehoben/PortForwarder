import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

public class MinecraftProxy {
    private static final String TARGET_HOST = "192.168.178.104";
    private static final int TARGET_PORT = 25565;
    private static final int LISTEN_PORT = 25565;
    private static volatile boolean running = true;
    private static final Logger logger = Logger.getLogger(MinecraftProxy.class.getName());

    public static void main(String[] args) {
        // Filtering JVM arguments related to memory
        String[] memoryArgs = filterMemoryArguments(args);

        logger.info("Starting MinecraftProxy with arguments: ");
        for (String arg : memoryArgs) {
            logger.info(arg);
        }

        try (ServerSocket serverSocket = new ServerSocket(LISTEN_PORT)) {
            logger.info("Proxy listening on port " + LISTEN_PORT);

            ExecutorService executor = Executors.newCachedThreadPool();

            // Thread to handle console input
            new Thread(() -> {
                try (var reader = new BufferedReader(new InputStreamReader(System.in))) {
                    while (running) {
                        var command = reader.readLine();
                        if ("stop".equalsIgnoreCase(command)) {
                            running = false;
                            serverSocket.close();
                            logger.info("Proxy stopped.");
                            break;
                        }
                    }
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Error reading console input", e);
                }
            }).start();

            while (running) {
                try {
                    var clientSocket = serverSocket.accept();
                    clientSocket.setSoTimeout(30000); // Set a timeout of 30 seconds
                    executor.submit(() -> handleClient(clientSocket));
                } catch (IOException e) {
                    if (running) {
                        logger.log(Level.SEVERE, "Error accepting client connection", e);
                    }
                }
            }

            executor.shutdown();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error starting proxy", e);
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (var serverSocket = new Socket(TARGET_HOST, TARGET_PORT);
             var clientIn = clientSocket.getInputStream();
             var clientOut = clientSocket.getOutputStream();
             var serverIn = serverSocket.getInputStream();
             var serverOut = serverSocket.getOutputStream()) {

            serverSocket.setSoTimeout(30000); // Set a timeout of 30 seconds

            // Forward data from client to server
            new Thread(() -> forwardData(clientIn, serverOut)).start();
            // Forward data from server to client
            forwardData(serverIn, clientOut);

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error handling client connection", e);
        }
    }

    private static void forwardData(InputStream in, OutputStream out) {
        try {
            var buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                out.flush();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error forwarding data", e);
        }
    }

    private static String[] filterMemoryArguments(String[] args) {
        return Arrays.stream(args)
                .filter(arg -> arg.startsWith("-Xms") || arg.startsWith("-Xmx"))
                .toArray(String[]::new);
    }
}
