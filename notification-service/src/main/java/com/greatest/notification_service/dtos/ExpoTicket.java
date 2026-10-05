package com.greatest.notification_service.dtos;

import lombok.Data;

@Data
public class ExpoTicket {
    private String status;
    private String id;
    private String message;
    private Details details;

    @Data
    public static class Details {
        private String error;
    }

}
