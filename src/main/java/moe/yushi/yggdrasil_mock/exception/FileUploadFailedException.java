package moe.yushi.yggdrasil_mock.exception;

import org.springframework.http.HttpStatus;

public class FileUploadFailedException extends YggdrasilException {
    public FileUploadFailedException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "FileUploadFailed", message);
    }
}
