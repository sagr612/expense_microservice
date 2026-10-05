package com.greatest.notification_service.dtos;

import lombok.Data;

import java.util.List;

@Data
public class ExpoTicketResponse {
    private List<ExpoTicket> data;
}
