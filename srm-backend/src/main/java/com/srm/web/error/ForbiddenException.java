package com.srm.web.error;

/** 门户或受控资源：当前主体无权执行该操作（HTTP 403）。 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
