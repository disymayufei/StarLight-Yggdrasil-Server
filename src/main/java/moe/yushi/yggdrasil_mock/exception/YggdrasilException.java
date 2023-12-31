package moe.yushi.yggdrasil_mock.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class YggdrasilException extends ResponseStatusException {

	private static final long serialVersionUID = 1L;

	private final String error;
	private final String message;

	public YggdrasilException(HttpStatus status, String error, String message) {
		super(status, error);
		this.error = error;
		this.message = message;
	}

	public String getYggdrasilMessage() {
		return message;
	}

	public String getYggdrasilError() {
		return error;
	}

	public static YggdrasilException newForbiddenOperationException(String message) {
		return new YggdrasilException(HttpStatus.FORBIDDEN, "ForbiddenOperationException", message);
	}

	public static YggdrasilException newIllegalArgumentException(String message) {
		return new YggdrasilException(HttpStatus.BAD_REQUEST, "IllegalArgumentException", message);
	}

	public static final String m_invalid_token = "Invalid token.";
	public static final String m_invalid_credentials = "Invalid credentials. Invalid username or password.";
	public static final String m_token_already_assigned = "Access token already has a profile assigned.";
	public static final String m_access_denied = "Access denied.";
	public static final String m_profile_not_found = "No such profile.";

}
