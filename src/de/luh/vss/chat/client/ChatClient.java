package de.luh.vss.chat.client;

import java.io.OutputStream;
import java.io.InputStream;
import java.io.*;
import java.net.*;
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
import java.util.*;

public class ChatClient {
	UserId userId;
	Socket socket;
	DataInputStream dataIn;
	DataOutputStream dataOut;

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
		server.createContext("/clicked", exchange -> {
			System.out.println("Sending Message ");
			String query = exchange.getRequestURI().getQuery();
			String[] strings = query.split("&");
			List<String> result = new ArrayList<String>();
			for (String s : strings) {
				String[] a = s.split("=");
				result.add(a[1]);
			}
			int i = Integer.parseInt(result.get(1));
			UserId u = new UserId(i);
			ChatMessage message = new ChatMessage(u, userId.id() + ": " + result.get(0));
			String response = "<div>Message Sent!</div>";
			exchange.getResponseHeaders().set("Content-Type", "text/html");
			byte[] responseBytes = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, responseBytes.length);
			exchange.getResponseBody().write(responseBytes);
			exchange.getResponseBody().close();
			sendChatMessage(message);
		});

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
					    <script src="https://unpkg.com/htmx.org@2.0.4" integrity="sha384-HGfztofotfshcF7+8n44JQL2oJmowVChPTg48S+jvZoztPfvwD79OC/LTtG6dMp+" crossorigin="anonymous"></script>
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
					    <button hx-get="/clicked"
					    hx-trigger="click"
					    hx-target="#parent-div"
					    hx-include="#parent-div, #other-div">
					    Click Me!
					    </button>
					    <div>
						<textarea id="other-div" name="message" rows="4" cols="30">Message</textarea>
						<textarea id="parent-div" name="message2" rows="1" cols="8">UserId</textarea>

					    </div>
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

	public void sendHeartbeatMessage() {
		try {
			while (true) {
				HeartbeatMessage message = new HeartbeatMessage(userId);
				message.toStream(dataOut);
				Thread.sleep(10000);

			}

		} catch (Exception e) {
		}

	}

	public void sendChatMessage(ChatMessage message) {
		try {
			System.out.println("sending" + message.toString());
			message.toStream(dataOut);
			updateMessage(message.getMessage());
		} catch (Exception e) {
			System.out.println(e.getMessage());
			System.out.println(e.getStackTrace().toString());
		}
	}

	public ClientRequestObject getClientRequestObject(UserId userId) throws Exception {
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
		socket = new Socket("127.0.0.1", 8081);
		dataOut = new DataOutputStream(socket.getOutputStream());
		dataIn = new DataInputStream(socket.getInputStream());
		do {
			System.out.println("What UserId?");
			UserId id = new UserId(scanner.nextInt());
			obj = getClientRequestObject(id);
			if (obj.exists) {
				System.out.println("Sorry, UserId is already taken");
			}

		} while (obj.exists == true);
		scanner.close();
		userId = obj.userId;

		startWebsite();

		System.out.println(socket.getLocalPort());
		try {
			RegisterRequest request = new RegisterRequest(userId,
					InetAddress.getByName("127.0.0.1"), 8081);
			request.toStream(dataOut);

		} catch (Exception e) {
			System.out.println(e.getMessage());
			System.out.println(e.getStackTrace().toString());
		}
		Message response = Message.parse(dataIn);
		if (response.getMessageType() == MessageType.REGISTER_RESPONSE) {
		}
		new Thread(() -> scanMessages()).start();
		new Thread(() -> sendHeartbeatMessage()).start();
	}

	public void scanMessages() {
		try {
			while (true) {
				Message message = Message.parse(dataIn);
				System.out.println("Got Message");
				if (message.getMessageType() == MessageType.CHAT_MESSAGE) {
					ChatMessage chatMessage = (ChatMessage) message;
					System.out.println(chatMessage.toString());
					updateMessage(chatMessage.getMessage());
				}

			}
		} catch (Exception e) {
			System.out.println("Something went wrong");
			System.out.println(e.getMessage());
			System.out.println(e.getStackTrace().toString());
			return;
		}
	}
}
