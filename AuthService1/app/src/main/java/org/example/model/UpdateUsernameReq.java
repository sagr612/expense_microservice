package org.example.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class UpdateUsernameReq{
    @NotBlank
    @Size(min = 3, max = 20)
    private String newUsername;

    @NotBlank
    @Size(min = 3, max = 20)
    private String currentUsername;


}