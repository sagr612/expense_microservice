package sagar.userService.deserializer;

import org.junit.jupiter.api.Test;
import sagar.userService.dtos.UserInfoDto;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class  UserInfoDeserializerTest {

    private final UserInfoDeserializer deserializer = new UserInfoDeserializer();

    @Test
    void deserialize_returnsDto_whenJsonIsValid() {
        String json = """
            {
              "user_id": "user-1",
              "first_name": "Sagar",
              "last_name": "Sharma",
              "phone_number": 9999999999,
              "email": "sagar@example.com"
            }
            """;

        UserInfoDto result = deserializer.deserialize("user_service", json.getBytes(StandardCharsets.UTF_8));

        assertThat(result.getUserId()).isEqualTo("user-1");
        assertThat(result.getFirstName()).isEqualTo("Sagar");
        assertThat(result.getEmail()).isEqualTo("sagar@example.com");
    }

    @Test
    void deserialize_returnsNull_whenDataIsNull() {
        UserInfoDto result = deserializer.deserialize("user_service", null);

        assertThat(result).isNull();
    }

    @Test
    void deserialize_throwsRuntimeException_whenJsonIsMalformed() {
        // this is exactly the "poison pill" scenario the DLT/error handler is meant to catch —
        // a message that will NEVER deserialize successfully no matter how many times it's retried
        byte[] malformedJson = "{ this is not valid json ".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> deserializer.deserialize("user_service", malformedJson))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Deserialization failed for topic: user_service");
    }

    @Test
    void deserialize_ignoresUnknownFields_dueToJsonIgnoreProperties() {
        // UserInfoDto is annotated @JsonIgnoreProperties(ignoreUnknown = true), so an extra
        // field from a newer producer schema must not break older consumers
        String jsonWithExtraField = """
            {
              "user_id": "user-1",
              "first_name": "Sagar",
              "last_name": "Sharma",
              "phone_number": 9999999999,
              "email": "sagar@example.com",
              "some_new_field_from_a_future_release": "unexpected value"
            }
            """;

        UserInfoDto result = deserializer.deserialize(
                "user_service", jsonWithExtraField.getBytes(StandardCharsets.UTF_8));

        assertThat(result.getUserId()).isEqualTo("user-1");
    }

    @Test
    void deserialize_throwsRuntimeException_whenRequiredTypeMismatch() {
        // phone_number expects a Long — sending a non-numeric string should fail
        // deserialization rather than silently coercing to something wrong
        String badTypeJson = """
            {
              "user_id": "user-1",
              "first_name": "Sagar",
              "last_name": "Sharma",
               "phone_number": ["not", "a", "valid", "string"],
              "email": "sagar@example.com"
            }
            """;

        assertThatThrownBy(() -> deserializer.deserialize(
                "user_service", badTypeJson.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(RuntimeException.class);
    }
}
