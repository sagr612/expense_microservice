package sagar.userService.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudinaryServiceTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    @InjectMocks
    private CloudinaryService cloudinaryService;

    @Test
    void upload_returnsCloudinaryResponse_onSuccess() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "image", "avatar.png", "image/png", "fake-bytes".getBytes());

        Map<String, Object> expectedResponse = Map.of(
                "secure_url", "https://cloudinary.com/avatar.png",
                "public_id", "ExpenseAvatars/avatar123");

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), any(Map.class))).thenReturn(expectedResponse);

        Map result = cloudinaryService.upload(file);

        assertThat(result.get("secure_url")).isEqualTo("https://cloudinary.com/avatar.png");
        assertThat(result.get("public_id")).isEqualTo("ExpenseAvatars/avatar123");
    }

    @Test
    void upload_wrapsIOException_asRuntimeException() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "image", "avatar.png", "image/png", "fake-bytes".getBytes());

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), any(Map.class)))
                .thenThrow(new IOException("network timeout"));

        assertThatThrownBy(() -> cloudinaryService.upload(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cloudinary upload failed")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void delete_callsDestroy_withGivenPublicId() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(eq("ExpenseAvatars/avatar123"), any(Map.class)))
                .thenReturn(Map.of("result", "ok"));

        cloudinaryService.delete("ExpenseAvatars/avatar123");
        // no exception thrown = success; verifying interaction happened is implicit via the stub above
        verify(uploader).destroy(eq("ExpenseAvatars/avatar123"), any(Map.class));
    }

    @Test
    void delete_wrapsIOException_asRuntimeException() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(any(String.class), any(Map.class)))
                .thenThrow(new IOException("network timeout"));

        assertThatThrownBy(() -> cloudinaryService.delete("some-public-id"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cloudinary delete failed")
                .hasCauseInstanceOf(IOException.class);
    }
}
