package de.luh.vss.chat.common;

public class ClientRequestObject {
	public User.UserId userId;
	public boolean exists;
	public boolean isOnline;

	public ClientRequestObject(User.UserId userId, boolean exists, boolean isOnline) {
		this.userId = userId;
		this.exists = exists;
		this.isOnline = isOnline;
	}

	@Override
	public String toString() {
		return userId.id() + " exists: " + exists + " isOnline: " + isOnline;
	}

}
