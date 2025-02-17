package de.luh.vss.chat.server;

import de.luh.vss.chat.common.*;
import java.net.*;
import java.time.*;
import java.io.*;
import java.util.*;
import de.luh.vss.chat.common.Message.ClientQuery;

public class MessageHandler {

	public void handleMessage(Socket socket, Message message, List<Client> clients) throws Exception {

		switch (message.getMessageType()) {
			case MessageType.CLIENT:
				handleClientRequest(socket, message, clients);
				break;
			case MessageType.HEARTBEAT:
				handleHeartbeat(socket, message, clients);
				break;
			case MessageType.REGISTER_REQUEST:
				handleRegisterRequest(socket, message, clients);
				break;
			case MessageType.REGISTER_RESPONSE:
				// TODO:
				break;
			case MessageType.ERROR_RESPONSE:
				// TODO:
				break;
			case MessageType.CHAT_MESSAGE:
				handleChatMessage(socket, message, clients);
				break;

		}
	}

	public void handleRegisterRequest(Socket socket, Message message, List<Client> clients) throws Exception {
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

	public void handleChatMessage(Socket socket, Message message, List<Client> clients) throws Exception {
		Message.ChatMessage chatMessage = (Message.ChatMessage) message;
		Client client = chatMessage.getRecipient().getClientById(clients);
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

	public void handleClientRequest(Socket socket, Message message, List<Client> clients) throws Exception {
		DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream());
		ClientQuery query = (ClientQuery) message;
		System.out.println(query.toString());
		Client client = query.obj.userId.getClientById(clients);
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

	public void handleHeartbeat(Socket socket, Message message, List<Client> clients) {
		Message.HeartbeatMessage heartbeat = (Message.HeartbeatMessage) message;
		for (Client client : clients) {
			if (client.userId.id() == heartbeat.userId.id()) {
				client.updateOnline();
			}
		}

	}

}
