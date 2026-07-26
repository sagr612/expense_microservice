package sagar.userService.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import sagar.userService.dtos.UserInfoDto;
import sagar.userService.services.UserService;


@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceConsumer {

    private final UserService userService;


//    @KafkaListener(topics = "${spring.kafka.topic-json.name}", groupId = "${spring.kafka.consumer.group-id}")
//    public void listen(UserInfoDto eventData)  {
//
//        try {
//            // todo: make it transactional(can use redis lock hint:using userid  ) to handle idempotency & validate email phone number etc. race condition thing what if multiple instances are creating or updating same userdata
//
//            UserInfoDto created = userService.createOrUpdateUser(eventData);
//            log.info("Successfully processed user created event - userId: {}", created.getUserId());
//        } catch (IllegalArgumentException ex) {
//            log.warn("Invalid user data received: {}", ex.getMessage(), ex);
//        } catch (Exception ex) {
//            log.error("Unexpected error processing user created event for userId: {}",
//                    eventData.getUserId(), ex);
//            throw ex;
//        }
//    }

    @KafkaListener(topics = "${spring.kafka.topic-json.name}", groupId = "${spring.kafka.consumer.group-id}")
    public void listen(UserInfoDto eventData) {
        UserInfoDto created = userService.createOrUpdateUser(eventData);
        log.info("Successfully processed user event - userId: {}", created.getUserId());
    }

}
