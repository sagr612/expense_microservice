package sagar.userService.deserializer;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Deserializer;
import sagar.userService.dtos.UserInfoDto;

import java.util.Map;

@Slf4j
public class UserInfoDeserializer implements Deserializer<UserInfoDto> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void close() {
    }

    @Override
    public void configure(Map<String, ?> arg0, boolean arg1) {
    }

    @Override
    public UserInfoDto deserialize(String topic, byte[] data) {
        if (data == null) {
            log.warn("Null data received for deserialization on topic: {}", topic);
            return null;
        }
        try {
            UserInfoDto user = MAPPER.readValue(data, UserInfoDto.class);
            log.debug("Successfully deserialized UserInfoDto from topic: {}", topic);
            return user;
        }  catch (Exception ex) {
            log.error("Failed to deserialize UserInfoDto from topic: {}", topic, ex);
            throw new org.springframework.kafka.support.serializer.DeserializationException(
                    "Deserialization failed for topic: " + topic, data, false, ex);
        }

    }
}
