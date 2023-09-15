package moe.yushi.yggdrasil_mock.exception;

import org.springframework.http.HttpStatus;

public class TooManyCharacterException extends YggdrasilException {
    public TooManyCharacterException(String message) {
        super(HttpStatus.FORBIDDEN, "TooManyCharacter", message);
    }
}
