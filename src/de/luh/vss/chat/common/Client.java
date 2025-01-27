package de.luh.vss.chat.common;

import java.time.LocalDateTime;
import java.net.InetAddress;

public class Client {
	public boolean isOnline;
	public LocalDateTime lastOnline;
	public InetAddress address;
	public int port;
	public User.UserId userId;

	public Client(boolean isOnline, LocalDateTime lastOnline, InetAddress address, int port,
			User.UserId userId) {
		this.isOnline = isOnline;
		this.lastOnline = lastOnline;
		this.address = address;
		this.port = port;
		this.userId = userId;
	}

	@Override
	public String toString() {
		return address.toString() + " " + port + " " + userId.id();
	}

}
