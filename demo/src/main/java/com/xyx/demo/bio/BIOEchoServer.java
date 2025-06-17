package com.xyx.demo.bio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BIOEchoServer {

    private static final ExecutorService threadPool = Executors.newCachedThreadPool();

    public static void main(String[] args) throws IOException {
        int port = 8080;
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("BIOServer started on port " + port);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("New client connected: " + clientSocket.getInetAddress());

            // 每个新的连接都启用一个线程去处理
            threadPool.execute(
                () -> handleClientConnect(clientSocket)
            );
        }
    }

    private static void handleClientConnect(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println("Received from client: " + inputLine);

                // echo message
                String responseMessage = "server echo: " + inputLine;
                out.println(responseMessage);
                System.out.println("Sent response: " + responseMessage);
            }
        } catch (IOException e) {
            System.out.println("Client connection closed: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
                System.out.println("clientSocket closed! " + clientSocket.getInetAddress());
            } catch (IOException e) {
                System.err.println("Error closing client socket: " + e.getMessage());
            }
        }
    }
}
