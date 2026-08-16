package sagar.userService.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
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
public class CloudinaryService {
    private final Cloudinary cloudinary;
private static final String UPLOAD_FOLDER = "/ExpenseAvatars/";
    public Map<String, Object> upload(MultipartFile file) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader()
                    .upload(file.getBytes(), ObjectUtils.asMap("folder", UPLOAD_FOLDER));
            return result;
        } catch (IOException e) {
            throw new CloudinaryOperationException("Cloudinary upload failed", e);
        }
    }

    public void delete(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (IOException e) {
            throw new CloudinaryOperationException("Cloudinary delete failed", e);
        }
    }
}
