package sagar.userService.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import sagar.userService.dtos.request.CreateUserRequest;
import sagar.userService.dtos.request.UpdateUserRequest;
import sagar.userService.entities.UserInfo;
import sagar.userService.repository.UserRepository;
import sagar.userService.services.CloudinaryService;
import sagar.userService.services.UserService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration")
public class UserConcurrencyIntegrationTest {
    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RedissonClient redissonClient;
    @MockitoBean
    private CloudinaryService cloudinaryService;
    @MockitoBean
    private KafkaTemplate<String, Object> kafkaTemplate;


    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void concurrentUpdates_areSerializedByRedisson_withoutLostUpdates() throws Exception {
        String userId = "user-concurrent-1";
        userService.createUser(CreateUserRequest.builder()
                .userId(userId)
                .firstName("test1")
                .lastName("user")
                .email("x@gmail.com")
                .phoneNumber("+919999999991")
                .build());

        int noOfThreads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(noOfThreads);

        CountDownLatch readyLatch = new CountDownLatch(noOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successfulUpdates = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < noOfThreads; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();
                    UpdateUserRequest update = UpdateUserRequest.builder()
                            .firstName("Name-" + index)
                            .lastName("Last-" + index)
                            .email("concurrent" + index + "@example.com")
                            .phoneNumber("+91999999999" + index)
                            .build();

                    userService.updateUser(userId, update);
                    successfulUpdates.incrementAndGet();
                } catch (Exception e) {

                }
            }));
        }
        readyLatch.await();
        startLatch.countDown();
        for (Future<?> future : futures) {
            future.get(15, TimeUnit.SECONDS);
        }
        executor.shutdown();
        UserInfo finalUser = userRepository.findByUserId(userId).orElseThrow();
        assertThat(finalUser.getFirstName()).startsWith("Name-");
        assertThat(successfulUpdates.get()).isEqualTo(noOfThreads);

        RLock lock = redissonClient.getLock("user:lock:" + userId);
        assertThat(lock.isLocked()).isFalse();
    }

    @Test
    void concurrentUpdateAndDelete_serializesCleanly() throws Exception {
        String userId = "user-concurrent-2";
        userService.createUser(CreateUserRequest.builder()
                .userId(userId)
                .firstName("To")
                .lastName("Delete")
                .email("delete.concurrent@example.com")
                .phoneNumber("+918888888888")
                .build());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        //thread 1: update
        Future<Boolean> updateFuture = executor.submit(() -> {
            readyLatch.countDown();
            startLatch.await();
            try {
                userService.updateUser(userId, UpdateUserRequest.builder()
                        .firstName("Updated")
                        .lastName("Name")
                        .email("updated.concurrent@example.com")
                        .phoneNumber("+918888888888")
                        .build());
                return true;
            } catch (Exception e) {
                return false;
            }
        });

        // thread 2: delete
        Future<Boolean> deleteFuture = executor.submit(() -> {
            readyLatch.countDown();
            startLatch.await();
            try {
                userService.deleteUser(userId);
                return true;
            }catch (Exception e){
                return false;
            }
        });

        readyLatch.await();
        startLatch.countDown();

        updateFuture.get(10, TimeUnit.SECONDS);
        deleteFuture.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        RLock lock = redissonClient.getLock("user:lock:" + userId);
        assertThat(lock.isLocked()).isFalse();

    }

}
