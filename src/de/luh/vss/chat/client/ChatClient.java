package de.luh.vss.chat.client;

import java.io.OutputStream;
import java.io.InputStream;
import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Scanner;

import de.luh.vss.chat.common.ClientRequestObject;
import de.luh.vss.chat.common.Message;
import de.luh.vss.chat.common.Message.*;
import de.luh.vss.chat.common.MessageType;
import de.luh.vss.chat.common.User.*;

import com.sun.net.httpserver.HttpServer;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class ChatClient {
	UserId userId;
	int port;
	private static final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();

	public static void main(String... args) throws Exception {

		try {
			new ChatClient().start();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public void startWebsite() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress(userId.id()), 0);

		server.createContext("/sse", exchange -> {
			exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
			exchange.getResponseHeaders().set("Cache-Control", "no-cache");
			exchange.getResponseHeaders().set("Connection", "keep-alive");

			exchange.sendResponseHeaders(200, 0);
			OutputStream outputStream = exchange.getResponseBody();

			try {
				while (true) {
					String message = messageQueue.take();
					String sseMessage = "data: " + message + "\n\n";
					outputStream.write(sseMessage.getBytes());
					outputStream.flush();
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			} finally {
				outputStream.close();
			}
		});

		server.createContext("/", exchange -> {
			String response = """
					<!DOCTYPE html>
					<html>
					<head>
					    <title>Chatroom</title>
					</head>
					<body>
					    <h1>Chatroom</h1>
					    <div id="messages"></div>
					    <script>
					        const eventSource = new EventSource('/sse');
					        eventSource.onmessage = function(event) {
					            const messages = document.getElementById('messages');
					            const newMessage = document.createElement('div');
					            newMessage.textContent = event.data;
					            messages.appendChild(newMessage);
					        };
					    </script>
					</body>
					</html>
					""";
			exchange.getResponseHeaders().set("Content-Type", "text/html");
			exchange.sendResponseHeaders(200, response.length());
			exchange.getResponseBody().write(response.getBytes());
			exchange.close();
		});

		// Server starten
		ExecutorService executor = Executors.newFixedThreadPool(4);
		server.setExecutor(executor);
		server.start();

		System.out.println("Server läuft auf http://localhost:" + userId.id());

	}

	public static void updateMessage(String message) {
		messageQueue.add(message);
	}

	public void sendHeartbeatMessage() throws Exception {
		HeartbeatMessage message = new HeartbeatMessage(userId);
		Socket socket = new Socket("127.0.0.1", 8081);
		OutputStream out = socket.getOutputStream();
		DataOutputStream dataOut = new DataOutputStream(out);
		message.toStream(dataOut);
		socket.close();
	}

	public void sendChatMessage(ChatMessage message) {
		try {
			Socket socket = new Socket("127.0.0.1", 8081);
			OutputStream out = socket.getOutputStream();
			DataOutputStream dataOut = new DataOutputStream(out);
			message.toStream(dataOut);
			updateMessage(message.getMessage());
			socket.close();
		} catch (Exception e) {
			System.out.println(e.getMessage());
			System.out.println(e.getStackTrace());
		}
	}

	public ClientRequestObject getClientRequestObject(UserId userId) throws Exception {
		Socket socket = new Socket("127.0.0.1", 8081);
		OutputStream out = socket.getOutputStream();
		DataOutputStream dataOut = new DataOutputStream(out);
		InputStream in = socket.getInputStream();
		DataInputStream dataIn = new DataInputStream(in);
		ClientRequestObject obj = new ClientRequestObject(userId, false, false);
		ClientQuery query = new ClientQuery(obj);
		query.toStream(dataOut);

		Message returnMessage = Message.parse(dataIn);
		if (returnMessage.getMessageType() == MessageType.CLIENT) {
			ClientQuery returnQuery = (ClientQuery) returnMessage;
			return returnQuery.obj;
		}
		throw new Exception("Expected ClientRequestObject, got " + returnMessage.getMessageType());

	}

	public void start() throws Exception {
		Scanner scanner = new Scanner(System.in);
		ClientRequestObject obj;
		do {
			System.out.println("What UserId?");
			UserId id = new UserId(scanner.nextInt());
			obj = getClientRequestObject(id);
			if (obj.exists) {
				System.out.println("Sorry, UserId is already taken");
			}

		} while (obj.exists == true);
		userId = obj.userId;

		startWebsite();

		Socket socket = new Socket("127.0.0.1", 8081);
		OutputStream out = socket.getOutputStream();
		DataOutputStream dataOut = new DataOutputStream(out);
		InputStream in = socket.getInputStream();
		DataInputStream dataIn = new DataInputStream(in);
		port = socket.getLocalPort();
		try {
			RegisterRequest request = new RegisterRequest(userId,
					InetAddress.getByName("127.0.0.1"), 8081);
			request.toStream(dataOut);

		} catch (Exception e) {
			System.out.println(e.getMessage());
			System.out.println(e.getStackTrace());
		}
		Message response = Message.parse(dataIn);
		if (response.getMessageType() == MessageType.REGISTER_RESPONSE) {
			socket.close();
		}
		new Thread(() -> scanMessages()).start();

		while (true) {
			System.out.println("Send Message?");
			scanner.nextLine();
			String text = scanner.nextLine();
			System.out.println("Recipient:");
			int recipientId = scanner.nextInt();
			ClientRequestObject requestObject = getClientRequestObject(new UserId(recipientId));
			if (requestObject.exists == false) {
				System.out.println("Sorry, there is no such User");
				continue;
			}
			if (requestObject.isOnline == false) {
				System.out.println("Sorry, recipient is no longer online");
				continue;
			}
			ChatMessage message = new ChatMessage(new UserId(recipientId), userId.id() + ": " + text);
			sendChatMessage(message);
		}
	}

	public void scanMessages() {
		LocalDateTime heartbeatTime = LocalDateTime.now();

		while (true) {
			try {
				tryGetMessage();
				if (Duration.between(heartbeatTime, LocalDateTime.now()).getSeconds() > 1) {
					sendHeartbeatMessage();
					heartbeatTime = LocalDateTime.now();
				}

			} catch (Exception e) {
				System.out.println("Something went wrong");
				System.out.println(e.getMessage());
				System.out.println(e.getStackTrace());
				return;
			}
		}

	}

	public void tryGetMessage() throws Exception {
		ServerSocket serverSocket = new ServerSocket(port);
		serverSocket.setSoTimeout(3000);
		try {
			Socket socket = serverSocket.accept();
			InputStream in = socket.getInputStream();
			DataInputStream dataIn = new DataInputStream(in);
			Message message = Message.parse(dataIn);
			if (message.getMessageType() == MessageType.CHAT_MESSAGE) {
				ChatMessage chatMessage = (ChatMessage) message;
				updateMessage(chatMessage.getMessage());
			}
			socket.close();

		} catch (SocketTimeoutException e) {
			// System.out.println("no message apparently");
		} catch (Exception e) {
			System.out.println("Something went wrong");
			System.out.println(e.getMessage());
			System.out.println(e.getStackTrace());
		}
		serverSocket.close();

	}
}
