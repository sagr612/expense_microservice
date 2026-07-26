package org.example.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class SignUpResponse{
    Boolean isSignedUp;
    String userId;
}
