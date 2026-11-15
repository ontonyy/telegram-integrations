package example.telegram.tdlib;

public enum AuthState {
    WAIT_TDLIB_PARAMETERS,
    WAIT_PHONE_NUMBER,
    WAIT_CODE,
    WAIT_PASSWORD,
    READY,
    LOGGING_OUT,
    CLOSING,
    CLOSED,
    UNSUPPORTED
}
