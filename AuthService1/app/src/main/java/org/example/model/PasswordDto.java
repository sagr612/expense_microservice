package org.example.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PasswordDto {
    String oldPassword;
    String newPassword;

}
