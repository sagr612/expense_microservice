package sagar.userService.services;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

@Component
public class ImageValidator {
    private static final long MAX_SIZE = 5 * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum limit of 5MB");
        }
        if (file.getContentType() == null || !ALLOWED_TYPES.contains(file.getContentType().toLowerCase())) {
            throw new IllegalArgumentException("Only JPEG, PNG, and WebP formats are supported");
        }

        validateMagicBytes(file);
    }

    private void validateMagicBytes(MultipartFile file) {
        byte[] header = new byte[12];
        int bytesRead;
        try (InputStream is = file.getInputStream()) {
            bytesRead = is.read(header);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read image content for validation", e);
        }

        if (bytesRead < 4) {
            throw new IllegalArgumentException("Invalid file: file is too small to be a valid image");
        }

        if(isJpeg(header, bytesRead) || isPng(header, bytesRead) || isWebP(header, bytesRead)){
            return;
        }
        throw new IllegalArgumentException("File content does not match a valid image format (JPEG, PNG, WebP)");
    }

    // JPEG starts with: FF D8 FF
    private boolean isJpeg(byte[] h, int len) {
        return len >= 3
                && (h[0] & 0xFF) == 0xFF
                && (h[1] & 0xFF) == 0xD8
                && (h[2] & 0xFF) == 0xFF;
    }
    // PNG starts with: 89 50 4E 47 (ASCII: .PNG)
    private boolean isPng(byte[] h, int len) {
        return len >= 8
                && (h[0] & 0xFF) == 0x89
                && h[1] == 0x50 // 'P'
                && h[2] == 0x4E // 'N'
                && h[3] == 0x47 // 'G'
                && h[4] == 0x0D // '\r'
                && h[5] == 0x0A // '\n'
                && h[6] == 0x1A
                && h[7] == 0x0A; // '\n'
    }
    // WebP starts with 'RIFF' at index 0..3 and 'WEBP' at index 8..11
    private boolean isWebP(byte[] h, int len) {
        return len >= 12
                && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
    }
}

