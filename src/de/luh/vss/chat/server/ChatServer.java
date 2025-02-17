package de.luh.vss.chat.server;

import java.io.*;
import java.net.*;
import java.util.*;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Scanner;
import de.luh.vss.chat.common.*;

public class ChatServer {

	List<Client> clients = new ArrayList<>();
	ServerSocket socket;
	Boolean terminated = false;
	int port = 8081;

	public static void main(String[] args) throws IOException {
		new ChatServer().startServer();

	}

	public void listenForStop() {
		Scanner scanner = new Scanner(System.in);
		while (true) {
			String message = scanner.nextLine();
			if (message.equals("q")) {
				scanner.close();
				try {
					socket.close();
				} catch (Exception e) {
					System.out.println("failed to close socket, stopping program anyways");
				}
				terminated = true;
				return;
			} else {
				System.out.println("unknown input, tpye q to stop the server");
			}

		}

	}

	public void listenClient(Socket socket) {
		DataInputStream dataIn;
		try {
			dataIn = new DataInputStream(socket.getInputStream());
		} catch (Exception e) {
			System.out.println(e.getMessage());
			System.out.println(e.getStackTrace().toString());
			return;

		}
		while (true) {
			try {
				Message message = Message.parse(dataIn);
				System.out.println(message.getMessageType());
				new MessageHandler().handleMessage(socket, message, clients);
			} catch (Exception e) {
				System.out.println(e.getMessage());
				System.out.println(e.getStackTrace().toString());

				System.out.println("Error communicating with Client, shutting down thread.");
				return;

			}

		}
	}

	public void startServer() {
		try {
			socket = new ServerSocket(port);
		} catch (Exception e) {
			System.out.println("Couldn't create socket for port " + port);
			return;
		}

		new Thread(() -> listenForStop()).start();
		new Thread(() -> checkOnline()).start();

		while (!terminated) {
			try {
				Socket clientSocket = socket.accept();
				new Thread(() -> listenClient(clientSocket)).start();
			} catch (SocketException s) {
				System.out.println("Quit application");
				return;
			} catch (Exception e) {
				System.out.println("Error in start()");
				System.out.println(e.getMessage());
				System.out.println(e.getStackTrace().toString());
			}
		}

	}

	public void checkOnline() {
		while (true) {
			for (Client client : clients) {
				System.out.println("Here");

				System.out.println(client.toString());
				Duration duration = Duration.between(client.lastOnline, LocalDateTime.now());
				System.out.println(duration.getSeconds());
				if (duration.getSeconds() > 20 && client.isOnline == true) {
					client.updateOffline();
				}
			}
		}
	}

}
