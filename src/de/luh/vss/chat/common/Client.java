package de.luh.vss.chat.common;

import java.time.LocalDateTime;
import java.net.Socket;

public class Client {
	public boolean isOnline;
	public LocalDateTime lastOnline;
	public User.UserId userId;
	public Socket clientSocket;

	public Client(boolean isOnline, LocalDateTime lastOnline,
			User.UserId userId, Socket clientSocket) {
		this.isOnline = isOnline;
		this.lastOnline = lastOnline;
		this.userId = userId;
		this.clientSocket = clientSocket;
	}

	@Override
	public String toString() {
		return clientSocket.getLocalAddress().toString() + " " + clientSocket.getLocalPort() + " "
				+ userId.id();
	}

	public void updateOffline() {
		System.out.println("Client " + this.userId + " is now offline.");
		this.isOnline = false;
	}

	public void updateOnline() {
		this.lastOnline = LocalDateTime.now();
		if (this.isOnline == false) {
			System.out.println("Client " + this.userId + " is now online.");
		}
		this.isOnline = true;
	}

}
