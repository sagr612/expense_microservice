package sagar.userService.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import sagar.userService.exceptions.CloudinaryOperationException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {
    private final Cloudinary cloudinary;
    private static final String UPLOAD_FOLDER = "/ExpenseAvatars/";

    public Map<String, Object> upload(MultipartFile file) {
        log.info("Uploading avatar to Cloudinary folder '{}' (size: {} bytes)", UPLOAD_FOLDER, file.getSize());
        try {
            @SuppressWarnings("unchecked") Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap("folder", UPLOAD_FOLDER));
            log.info("Cloudinary upload successful. publicId: {}", result.get("public_id"));
            return result;
        } catch (IOException e) {
            log.error("Cloudinary upload failed: {}", e.getMessage(), e);
            throw new CloudinaryOperationException("Cloudinary upload failed", e);
        }
    }

    public void delete(String publicId) {
        log.info("Deleting image from Cloudinary for publicId: {}", publicId);
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            log.info("Successfully destroyed Cloudinary image: {}", publicId);
        } catch (IOException e) {
            log.error("Failed to delete Cloudinary image: {}", publicId, e);
            throw new CloudinaryOperationException("Cloudinary delete failed", e);
        }
    }
}
