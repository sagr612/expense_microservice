package sagar.userService.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import sagar.userService.services.FailedEventService;
import sagar.userService.services.UserService;


@Service
@RequiredArgsConstructor
@Slf4j
public class UserDltConsumer {

    private final UserService userService;
    private final FailedEventService failedEventService;

    @KafkaListener(
            topics = "${spring.kafka.topic-json.name}.DLT",
            groupId = "userService-dlt-group",
            containerFactory = "dltKafkaListenerContainerFactory"
    )
    public void handleDlt(ConsumerRecord<String, String> record,
                          @Header(KafkaHeaders.DLT_ORIGINAL_TOPIC) String originalTopic,
                          @Header(KafkaHeaders.DLT_ORIGINAL_PARTITION) int partition,
                          @Header(KafkaHeaders.DLT_ORIGINAL_OFFSET) long offset,
                          @Header(KafkaHeaders.DLT_EXCEPTION_MESSAGE) String exceptionMessageString,
                          @Header(KafkaHeaders.DLT_EXCEPTION_STACKTRACE) String exceptionStackTrace
    ) {
        String rawPayload = record.value();


        log.error("DLT  | originalTopic={} | originalOffset={} | reason={}", originalTopic, offset, exceptionMessageString);


        failedEventService.saveRaw(rawPayload, originalTopic, partition, offset, exceptionMessageString, exceptionStackTrace);

    }


}