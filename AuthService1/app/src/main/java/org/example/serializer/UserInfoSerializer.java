package org.example.serializer;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serializer;
import org.example.eventProducer.UserInfoEvent;
import org.example.model.UserInfoDto;

import java.util.Map;


public class UserInfoSerializer implements Serializer<UserInfoEvent> {
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // intentionally left blank
    }

    @Override
    public byte[] serialize(String arg0, UserInfoEvent arg1) {
        byte[] retval = null;
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            retval = objectMapper.writeValueAsString(arg1).getBytes();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return retval;
    }

    @Override
    public void close() {
    }
}
