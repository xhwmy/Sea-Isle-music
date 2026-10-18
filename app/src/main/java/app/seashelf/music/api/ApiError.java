package app.seashelf.music.api;

import java.io.IOException;

public final class ApiError extends IOException {
    private final int code;

    public ApiError(int code, String message) {
        super(message);
        this.code = code;
    }

    public ApiError(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int code() { return code; }
}
