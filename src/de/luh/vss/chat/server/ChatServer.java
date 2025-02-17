package de.luh.vss.chat.server;

import java.io.*;
import java.net.*;
import java.util.*;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Scanner;

import de.luh.vss.chat.common.*;
import de.luh.vss.chat.common.Message.ClientQuery;
import de.luh.vss.chat.common.ClientRequestObject;

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
			System.out.println(e.getStackTrace());
			return;

		}
		while (true) {
			try {
				Message message = Message.parse(dataIn);
				System.out.println(message.getMessageType());
				handleMessage(socket, message);
			} catch (Exception e) {
				System.out.println(e.getMessage());
				System.out.println(e.getStackTrace());

			}

		}
	}

	public void handleMessage(Socket socket, Message message) throws Exception {

		switch (message.getMessageType()) {
			case MessageType.CLIENT:
				handleClientRequest(socket, message);
				break;
			case MessageType.HEARTBEAT:
				handleHeartbeat(socket, message);
				break;
			case MessageType.REGISTER_REQUEST:
				handleRegisterRequest(socket, message);
				break;
			case MessageType.REGISTER_RESPONSE:
				// TODO:
				break;
			case MessageType.ERROR_RESPONSE:
				// TODO:
				break;
			case MessageType.CHAT_MESSAGE:
				handleChatMessage(socket, message);
				break;

		}
	}

	public void handleRegisterRequest(Socket socket, Message message) throws Exception {
		Message.RegisterRequest request = (Message.RegisterRequest) message;
		System.out.println("Register request received: " + request.toString());
		Client client = new Client(true, LocalDateTime.now(),
				request.getUserId(), socket);
		System.out.println("adding client");
		clients.add(client);
		Message.RegisterResponse response = new Message.RegisterResponse();
		DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream());
		response.toStream(dataOut);

	}

	public void handleChatMessage(Socket socket, Message message) throws Exception {
		Message.ChatMessage chatMessage = (Message.ChatMessage) message;
		Client client = getClientById(chatMessage.getRecipient());
		if (client != null) {
			System.out.println("Found recipient " + client.userId.id());
			System.out.println(client.toString());
			Socket otherSocket = client.clientSocket;
			DataOutputStream otherDataOut = new DataOutputStream(
					otherSocket.getOutputStream());
			Message.ChatMessage forwardMessage = new Message.ChatMessage(
					chatMessage.getRecipient(),
					chatMessage.getMessage());
			System.out.println("sending to " + client.userId.id());
			forwardMessage.toStream(otherDataOut);
			System.out.println("done sending");
		}

	}

	public void handleClientRequest(Socket socket, Message message) throws Exception {
		DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream());
		ClientQuery query = (ClientQuery) message;
		System.out.println(query.toString());
		Client client = getClientById(query.obj.userId);
		ClientRequestObject obj;
		if (client == null) {
			obj = new ClientRequestObject(query.obj.userId, false,
					query.obj.isOnline);
		} else {
			obj = new ClientRequestObject(client.userId, true,
					client.isOnline);
		}
		ClientQuery returnQuery = new ClientQuery(obj);
		returnQuery.toStream(dataOut);

	}

	public void handleHeartbeat(Socket socket, Message message) {
		Message.HeartbeatMessage heartbeat = (Message.HeartbeatMessage) message;
		for (Client client : clients) {
			if (client.userId.id() == heartbeat.userId.id()) {
				updateOnline(client);
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
				System.out.println(e.getStackTrace());
			}
		}

	}

	public Client getClientById(User.UserId userId) {
		for (Client client : clients) {
			if (client.userId.id() == userId.id()) {
				return client;
			}
		}
		return null;
	}

	public Client getClientBySocket(User.UserId userId) {
		for (Client client : clients) {
			if (client.clientSocket.getLocalAddress().equals(client.clientSocket.getLocalAddress())
					&& client.clientSocket.getLocalPort() == client.clientSocket.getLocalPort()) {
				return client;
			}
		}
		return null;
	}

	public void checkOnline() {
		while (true) {
			for (Client client : clients) {
				System.out.println("Here");

				System.out.println(client.toString());
				Duration duration = Duration.between(client.lastOnline, LocalDateTime.now());
				System.out.println(duration.getSeconds());
				if (duration.getSeconds() > 20 && client.isOnline == true) {
					updateOffline(client);
				}
			}
		}
	}

	public void updateOffline(Client client) {
		System.out.println("Client " + client.userId + " is now offline.");
		client.isOnline = false;
	}

	public void updateOnline(Client client) {
		client.lastOnline = LocalDateTime.now();
		if (client.isOnline == false) {
			System.out.println("Client " + client.userId + " is now online.");
		}
		client.isOnline = true;
	}
}
