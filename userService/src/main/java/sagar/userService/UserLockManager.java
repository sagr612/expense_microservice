package sagar.userService;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import sagar.userService.exceptions.UserBusyException;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class UserLockManager {
    private final RedissonClient redissonClient;

    public <T> T withUserLock(String userId, Supplier<T> action) {
        String lockKey = "user:lock:" + userId;
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired = false;
        try {
            acquired = lock.tryLock(5, TimeUnit.SECONDS);
            if (!acquired) {
                throw new UserBusyException("User is busy: " + userId);
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Lock interrupted for user " + userId, e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public void withUserLock(String userId, Runnable action) {
        withUserLock(userId, () -> {
            action.run();
            return null;
        });
    }

}
