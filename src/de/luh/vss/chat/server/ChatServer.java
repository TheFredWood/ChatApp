package de.luh.vss.chat.server;

import java.io.*;
import java.net.*;
import java.util.*;

import java.time.LocalDateTime;
import java.time.Duration;

import de.luh.vss.chat.common.*;
import de.luh.vss.chat.common.Message.ClientQuery;
import de.luh.vss.chat.common.ClientRequestObject;

public class ChatServer {

	List<Client> clients = new ArrayList<>();

	public static void main(String[] args) throws IOException {
		new ChatServer().startServer();

	}

	public void startServer() {
		int port = 8081;
		ServerSocket serverSocket;
		try {
			serverSocket = new ServerSocket(port);
		} catch (Exception e) {
			System.out.println("Couldn't create socket for port " + port);
			return;
		}

		while (true) {
			checkOnline();
			try {
				serverSocket.setSoTimeout(1000);
				Socket clientSocket = serverSocket.accept();
				System.out.println("accepted");
				OutputStream out = clientSocket.getOutputStream();
				DataOutputStream dataOut = new DataOutputStream(out);
				InputStream in = clientSocket.getInputStream();
				DataInputStream dataIn = new DataInputStream(in);

				System.out.println("parsing");
				Message message = Message.parse(dataIn);
				System.out.println("done");
				InetAddress address = clientSocket.getInetAddress();
				int clientPort = clientSocket.getPort();

				System.out.println(message.getMessageType());
				if (message.getMessageType() == MessageType.REGISTER_REQUEST) {
					Message.RegisterRequest request = (Message.RegisterRequest) message;
					System.out.println("Register request received: " + request.toString());
					Client client = new Client(true, LocalDateTime.now(), address,
							clientPort, request.getUserId());
					clients.add(client);
					Message.RegisterResponse response = new Message.RegisterResponse();
					response.toStream(dataOut);

				}
				if (message.getMessageType() == MessageType.CLIENT) {
					ClientQuery query = (ClientQuery) message;
					System.out.println(message.getMessageType());
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
				clientSocket.close();

				if (message.getMessageType() == MessageType.HEARTBEAT) {
					Message.HeartbeatMessage heartbeat = (Message.HeartbeatMessage) message;
					System.out.println("Heartbeat received " + heartbeat.toString());
					for (int i = 0; i < this.clients.size(); i++) {
						Client client = this.clients.get(i);
						if (client.userId.id() == heartbeat.userId.id()) {
							updateOnline(client);
						}
					}
				}
				if (message.getMessageType() == MessageType.CHAT_MESSAGE) {
					System.out.println("Address: " + address.toString() + clientPort);
					Message.ChatMessage chatMessage = (Message.ChatMessage) message;
					System.out.println("ChatMessage received " + chatMessage.toString());
					Client client = getClientById(chatMessage.getRecipient());
					if (client != null) {
						System.out.println("Found recipient " + client.userId.id());
						System.out.println(client.toString());
						Socket socket = new Socket(client.address, client.port);
						out = socket.getOutputStream();
						dataOut = new DataOutputStream(out);
						in = socket.getInputStream();
						dataIn = new DataInputStream(in);
						Message.ChatMessage forwardMessage = new Message.ChatMessage(
								chatMessage.getRecipient(),
								chatMessage.getMessage());
						System.out.println("sending to " + client.userId.id());
						forwardMessage.toStream(dataOut);
						Thread.sleep(3000);
						socket.close();
					}

				}
				Thread.sleep(100);
			} catch (SocketTimeoutException e) {
				System.out.println("no Message gotten");
			} catch (Exception e) {
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

	public void checkOnline() {
		for (int i = 0; i < this.clients.size(); i++) {
			Client client = clients.get(i);
			Duration duration = Duration.between(client.lastOnline, LocalDateTime.now());
			if (duration.getSeconds() > 3 && client.isOnline == true) {
				updateOffline(client);
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
