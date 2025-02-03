import java.io.*;
import java.net.*;
import java.util.*;

public class ByzantineNode implements Runnable {
    private String name;
    private int port;
    private int basePort;
    private boolean faulty = false;

    public ByzantineNode(String name, int port, int basePort) {
        this.name = name;
        this.port = port;
        this.basePort = basePort;
    }

    public void simulateFailure() {
            this.faulty = true;
        
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println(name + " is running on port: " + port);
            Map<String, String> receivedMessages = new HashMap<>();

            // Send initial message to all other processes
            for (int i = 0; i < 4; i++) {
                if (basePort + i != port) {
                    try (Socket socket = new Socket("localhost", basePort + i)) {
                        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                        String message = faulty ? generateFaultyMessage() : "Attack";
                        out.println(name + ":" + message);
                    }
                }
            }

            // Receive messages from other processes
            while (receivedMessages.size() < 3) { // Expect 3 messages from other processes
                try (Socket clientSocket = serverSocket.accept();
                     BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
                    String message = in.readLine();
                    String[] parts = message.split(":");
                    receivedMessages.put(parts[0], parts[1]);
                    System.out.println(name + " received: " + message);
                }
            }

            // Package received messages and send to others
            String summaryMessage = packageMessages(receivedMessages);
            for (int i = 0; i < 4; i++) {
                if (basePort + i != port) {
                    try (Socket socket = new Socket("localhost", basePort + i)) {
                        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                        out.println(name + ":SUMMARY:" + summaryMessage);
                    }
                }
            }

            // Collect summaries from others
            List<String> receivedSummaries = new ArrayList<>();
            while (receivedSummaries.size() < 3) { // Expect summaries from other processes
                try (Socket clientSocket = serverSocket.accept();
                     BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
                    String message = in.readLine();
                    if (message.contains(":SUMMARY:")) {
                        receivedSummaries.add(message);
                        System.out.println(name + " received summary: " + message);
                    }
                }
            }

            // Identify nodes with discrepancies
            Set<String> discrepantNodes = identifyDiscrepantNodes(receivedMessages, receivedSummaries);
            System.out.println(name + " found discrepant nodes: " + discrepantNodes);

            // Perform majority voting excluding discrepant nodes
            Map<String, Integer> counts = new HashMap<>();
            for (Map.Entry<String, String> entry : receivedMessages.entrySet()) {
                if (!discrepantNodes.contains(entry.getKey())) {
                    counts.put(entry.getValue(), counts.getOrDefault(entry.getValue(), 0) + 1);
                }
            }

            // Determine final decision
            String decision = counts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElseThrow()
                    .getKey();

            System.out.println(name + "'s final decision is: " + decision);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String generateFaultyMessage() {
        return new Random().nextBoolean() ? "Attack" : "Retreat";
    }

    private String packageMessages(Map<String, String> messages) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : messages.entrySet()) {
            sb.append(entry.getKey()).append(":").append(entry.getValue()).append(",");
        }
        // Remove trailing comma
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }
    
    private Set<String> identifyDiscrepantNodes(Map<String, String> receivedMessages, List<String> summaries) {
        Map<String, Map<String, String>> nodeMessages = new HashMap<>();

        // Parse summaries
        for (String summary : summaries) {
            String[] parts = summary.split(":SUMMARY:");
            if (parts.length > 1) {
                String sender = parts[0];
                String[] messages = parts[1].split(",");
                for (String message : messages) {
                    String[] msgParts = message.split(":");
                    if (msgParts.length == 2) {
                        String node = msgParts[0];
                        String value = msgParts[1];
                        nodeMessages.putIfAbsent(node, new HashMap<>());
                        nodeMessages.get(node).put(sender, value);
                    }
                }
            }
        }

        // Compare reconstructed messages
        Set<String> discrepantNodes = new HashSet<>();
        for (Map.Entry<String, Map<String, String>> entry : nodeMessages.entrySet()) {
            String node = entry.getKey();
            Map<String, String> messages = entry.getValue();
            Set<String> uniqueValues = new HashSet<>(messages.values());
            if (uniqueValues.size() > 1) {
                discrepantNodes.add(node);
            }
        }

        return discrepantNodes;
    }

    public static void main(String[] args) {
        int basePort = 5000;
        String[] names = {"Q", "R", "S", "P"};
        Thread[] nodes = new Thread[names.length];

        for (int i = 0; i < names.length; i++) {
            ByzantineNode node = new ByzantineNode(names[i], basePort + i, basePort);
            if (names[i].equals("P")) {
                node.simulateFailure();
            }
            nodes[i] = new Thread(node);
            nodes[i].start();
        }

        for (Thread node : nodes) {
            try {
                node.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
