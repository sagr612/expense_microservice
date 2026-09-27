package sagar.userService.services;


import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ImageValidatorTest {
    private final ImageValidator imageValidator = new ImageValidator();

    @Test
    void validate_acceptsValidPng() {
        byte[] pngBytes = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("image", "avatar.png", "image/png", pngBytes);

        assertThatCode(() -> imageValidator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void validate_acceptsValidJpeg() {
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("image", "avatar.png", "image/png", jpegBytes);

        assertThatCode(() -> imageValidator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void validate_rejectsSpoofedScriptDisguisedAsImage() {
        // Content-Type says image/png, but bytes are a shell script!
        byte[] scriptBytes = "#!/bin/bash\nrm -rf /".getBytes();
        MockMultipartFile fakePng= new MockMultipartFile("image", "avatar.png", "image/png", scriptBytes);

        assertThatThrownBy(() -> imageValidator.validate(fakePng))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File content does not match a valid image format");
    }

    @Test
    void validate_rejectsEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile("image", "avatar.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> imageValidator.validate(emptyFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File must not be empty");
    }

}
