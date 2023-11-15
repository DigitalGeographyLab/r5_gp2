package com.conveyal.r5.rastercost;

public class CustomCostFieldException extends RuntimeException {
    
    public CustomCostFieldException (String message) {
        super(message);
    }

    public CustomCostFieldException (String message, Throwable cause) {
        super(message, cause);
    }
}