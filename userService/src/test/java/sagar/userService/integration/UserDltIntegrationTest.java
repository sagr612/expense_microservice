package sagar.userService.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import sagar.userService.dtos.UserInfoDto;
import sagar.userService.entities.FailedEventStatus;
import sagar.userService.entities.FailedEvents;
import sagar.userService.repository.FailedEventRepository;
import sagar.userService.services.CloudinaryService;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@EmbeddedKafka(
        partitions = 1,
        topics = {"user.events", "user.events.DLT"}
)
@ActiveProfiles("integration")
public class UserDltIntegrationTest {
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired
    private FailedEventRepository failedEventRepository;
    @MockitoBean
    private RedissonClient redissonClient;
    @MockitoBean
    private CloudinaryService cloudinaryService;
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> System.getProperty("spring.embedded.kafka.brokers"));
    }


    @BeforeEach
    void setUp() throws InterruptedException {
        failedEventRepository.deleteAll();
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), any())).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
    }

    @Test
    void poisonPillEvent_routesToDlt_andPersistsToFailedEventsTable() throws Exception {
        UserInfoDto invalidEvent = UserInfoDto.builder()
                .eventId(UUID.randomUUID().toString())
                .userId("")
                .email("poison@example.com")
                .phoneNumber("1234567890")
                .build();

        kafkaTemplate.send("user.events", "posion-key", invalidEvent).get(5, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(10))
                .until(() -> failedEventRepository.count() > 0);

        List<FailedEvents> failedList = failedEventRepository.findAll();
        assertThat(failedList).hasSize(1);

        FailedEvents recorded = failedList.get(0);
        assertThat(recorded.getStatus()).isEqualTo(FailedEventStatus.FAILED);
        assertThat(recorded.getTopic()).isEqualTo("user.events");
        assertThat(recorded.getPayload()).isNotNull();
        assertThat(recorded.getErrorMessage()).contains("User ID cannot be blank");
        assertThat(recorded.getStackTrace()).isNotBlank();
    }

}
