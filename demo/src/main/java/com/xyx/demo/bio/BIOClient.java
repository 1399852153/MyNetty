package com.xyx.demo.bio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class BIOClient {

    public static void main(String[] args) throws IOException {
        String hostname = "127.0.0.1";
        int port = 8080;

        try (Socket socket = new Socket(hostname, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println("Connected to server. Type messages (type 'exit' to quit)");

            String userInput;
            while ((userInput = stdIn.readLine()) != null) {
                if ("exit".equalsIgnoreCase(userInput)) {
                    break;
                }

                out.println(userInput);
                System.out.println("Server response: " + in.readLine());
            }
        }
    }
}
