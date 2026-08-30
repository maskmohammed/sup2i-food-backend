package com.sup2i.food.canteen.exception;

public class CanteenException
    extends RuntimeException {

    private final CanteenErrorCode errorCode;

    public CanteenException(
        CanteenErrorCode errorCode,
        String message
    ) {
        super(message);

        this.errorCode =
            errorCode;
    }

    public CanteenErrorCode getErrorCode() {
        return errorCode;
    }
}
