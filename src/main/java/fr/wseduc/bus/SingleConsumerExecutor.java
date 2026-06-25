package fr.wseduc.bus;

import fr.wseduc.webutils.collections.SharedDataHelper;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * Ensures that a task identified by a lock name is executed by only one
 * consumer across the cluster. Other consumers silently skip execution.
 */
public class SingleConsumerExecutor {

    private static final Logger log = LoggerFactory.getLogger(SingleConsumerExecutor.class);
    public static final long LOCK_RELEASE_TIMEOUT = 500L;
    public static final long LOCK_RELEASE_DELAY = 2 * LOCK_RELEASE_TIMEOUT;
    private final SharedDataHelper sharedData;
    private final long lockTimeout;
    private final long releaseDelay;

    /**
     * @param lockTimeout  max ms to wait for the lock (keep short — losers should skip, not queue)
     * @param releaseDelay ms to hold the lock after task completes (prevents late duplicates)
     */
    public SingleConsumerExecutor(long lockTimeout, long releaseDelay) {
        this.sharedData = SharedDataHelper.getInstance();
        this.lockTimeout = lockTimeout;
        this.releaseDelay = releaseDelay;
    }

    public SingleConsumerExecutor() {
        this(LOCK_RELEASE_TIMEOUT, LOCK_RELEASE_DELAY);
    }

    /**
     * Acquire a cluster-wide lock, run the task, then release.
     * If the lock can't be acquired (another instance won), the returned future
     * succeeds with null — the task is simply not executed.
     *
     * @param lockName unique name for this operation (e.g. "search_" + searchId)
     * @param task     returns a Future with the result of the work
     * @return Future<T> — the task result, or null if skipped
     */
    public <T> Future<T> ensureSingle(String lockName, java.util.function.Supplier<Future<T>> task) {
    return sharedData.getLock(lockName, lockTimeout)
        .recover(th -> {
            // Lock not acquired → another instance handles it
            log.debug("Skipping task, another instance holds lock: " + lockName);
            return Future.succeededFuture(null);
        })
        .compose(lock -> {
            if (lock == null) {
                return Future.succeededFuture(null);
            }
            try {
                return task.get().onComplete(ar -> sharedData.releaseLockAfterDelay(lock, releaseDelay));
            } catch (Exception e) {
                sharedData.releaseLockAfterDelay(lock, releaseDelay);
                return Future.failedFuture(e);
            }
        });
}

    /**
     * Fire-and-forget variant for handlers that don't return a Future.
     * Runs the action only if the lock is acquired; silently skips otherwise.
     */
    public void ensureSingle(String lockName, Runnable action) {
        sharedData.getLock(lockName, lockTimeout).onSuccess(lock -> {
            try {
                action.run();
            } finally {
                sharedData.releaseLockAfterDelay(lock, releaseDelay);
            }
        }).onFailure(th -> log.debug("Skipping task, another instance holds lock: " + lockName));
    }
}