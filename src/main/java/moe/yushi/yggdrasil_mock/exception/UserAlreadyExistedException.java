package moe.yushi.yggdrasil_mock.exception;

import org.springframework.http.HttpStatus;

public class UserAlreadyExistedException extends YggdrasilException {
    public UserAlreadyExistedException(String message) {
        super(HttpStatus.FORBIDDEN, "UserAlreadyExisted", message);
    }
}
