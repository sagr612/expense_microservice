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
import org.testcontainers.shaded.org.checkerframework.checker.units.qual.A;
import sagar.userService.dtos.UserInfoDto;
import sagar.userService.dtos.response.UserProfileImageResponse;
import sagar.userService.entities.UserInfo;
import sagar.userService.repository.ProcessedEventRepository;
import sagar.userService.repository.UserRepository;
import sagar.userService.services.CloudinaryService;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

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
public class AuthServiceConsumerIntegrationTest {
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProcessedEventRepository processedEventRepository;
    @MockitoBean
    private RedissonClient redissonClient;
    @MockitoBean
    private CloudinaryService cloudinaryService;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        // Points Spring & Flyway to the dynamic Testcontainers PostgreSQL instance
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Kafka: dynamically bind to wherever @EmbeddedKafka started
        registry.add("spring.kafka.bootstrap-servers", () -> System.getProperty("spring.embedded.kafka.brokers"));
//        registry.add("cloudinary.url", () -> "cloudinary://key:secret@demo");
//        registry.add("jwt.secret", () -> "testdjwtdsecretdkeydfordintegrationdtestsdonly");

    }

    @BeforeEach
    void setUp() throws InterruptedException {
        userRepository.deleteAll();
        processedEventRepository.deleteAll();

        RLock lock = mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), any())).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
    }

    @Test
    void consumer_processesFirstDelivery_andSkipsDuplicateEventId() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String userId= "user-idempotent-1";

        UserInfoDto event= UserInfoDto.builder()
                .eventId(eventId)
                .userId(userId)
                .firstName("Sagar")
                .lastName("Sharma")
                .email("sagar.test@gmail.com")
                .phoneNumber("9988775511")
                .build();

        kafkaTemplate.send("user.events", userId, event).get(5, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(5)).until(()-> processedEventRepository.existsById(eventId));
        assertThat(userRepository.findByUserId(userId)).isPresent();

        kafkaTemplate.send("user.events", userId, event).get(5, TimeUnit.SECONDS);

        Thread.sleep(1500);

        assertThat(processedEventRepository.count()).isEqualTo(1);
        assertThat(userRepository.count()).isEqualTo(1);
    }

}
