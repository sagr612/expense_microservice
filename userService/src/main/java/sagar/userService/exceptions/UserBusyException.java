package sagar.userService.exceptions;

public class UserBusyException extends RuntimeException {
    public UserBusyException(String message) {
        super(message);
    }
}
