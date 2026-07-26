package sagar.userService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import sagar.userService.exceptions.UserBusyException;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserLockManagerTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @InjectMocks
    private UserLockManager userLockManager;

    @Test
    void withUserLock_executesAction_whenLockAcquired() throws InterruptedException {
        when(redissonClient.getLock("user:lock:user-1")).thenReturn(lock);
        when(lock.tryLock(5, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        String result = userLockManager.withUserLock("user-1", () -> "done");

        assertThat(result).isEqualTo("done");
        verify(lock).unlock();
    }

    @Test
    void withUserLock_throwsUserBusy_whenLockNotAcquired() throws InterruptedException {
        when(redissonClient.getLock("user:lock:user-1")).thenReturn(lock);
        when(lock.tryLock(5, TimeUnit.SECONDS)).thenReturn(false);

        assertThatThrownBy(() -> userLockManager.withUserLock("user-1", () -> "unreachable"))
                .isInstanceOf(UserBusyException.class)
                .hasMessageContaining("user-1");

        // never acquired, so unlock must never be called
        verify(lock, never()).unlock();
    }

    @Test
    void withUserLock_releasesLock_evenWhenActionThrows() throws InterruptedException {
        when(redissonClient.getLock("user:lock:user-1")).thenReturn(lock);
        when(lock.tryLock(5, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        assertThatThrownBy(() -> userLockManager.withUserLock("user-1", () -> {
            throw new RuntimeException("boom");
        })).isInstanceOf(RuntimeException.class).hasMessage("boom");

        // this is the important assertion: even on failure, the lock is released
        verify(lock).unlock();
    }

    @Test
    void withUserLock_wrapsInterruptedException_asIllegalState() throws InterruptedException {
        when(redissonClient.getLock("user:lock:user-1")).thenReturn(lock);
        when(lock.tryLock(5, TimeUnit.SECONDS)).thenThrow(new InterruptedException());

        assertThatThrownBy(() -> userLockManager.withUserLock("user-1", () -> "unreachable"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user-1");

        // thread interrupt status should have been restored
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted(); // clear it so it doesn't bleed into other tests
    }

    @Test
    void withUserLock_doesNotUnlock_whenNotHeldByCurrentThread() throws InterruptedException {
        // defensive case: acquired flag true but somehow not held by this thread
        // (e.g. watchdog lease expired) — must not call unlock and throw IllegalMonitorStateException
        when(redissonClient.getLock("user:lock:user-1")).thenReturn(lock);
        when(lock.tryLock(5, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(false);

        String result = userLockManager.withUserLock("user-1", () -> "done");

        assertThat(result).isEqualTo("done");
        verify(lock, never()).unlock();
    }

    @Test
    void withUserLock_runnableOverload_delegatesToSupplierOverload() throws InterruptedException {
        when(redissonClient.getLock("user:lock:user-1")).thenReturn(lock);
        when(lock.tryLock(5, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        Runnable action = mock(Runnable.class);
        userLockManager.withUserLock("user-1", action);

        verify(action).run();
        verify(lock).unlock();
    }
}
