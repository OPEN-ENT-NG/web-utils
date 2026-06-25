package fr.wseduc.bus;

import fr.wseduc.webutils.collections.SharedDataHelper;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class SingleConsumerExecutorTest {

    private Vertx vertx;

    @Before
    public void setUp() {
        vertx = Vertx.vertx();
        SharedDataHelper.getInstance().init(vertx);
    }

    @After
    public void tearDown(TestContext ctx) {
        vertx.close(ctx.asyncAssertSuccess());
    }

    @Test
    public void testEnsureSingleFutureExecutesTask(TestContext ctx) {
        Async async = ctx.async();
        SingleConsumerExecutor executor = new SingleConsumerExecutor();
        AtomicInteger counter = new AtomicInteger(0);
        executor.<String>ensureSingle("test-exec", () -> {
            counter.incrementAndGet();
            return Future.succeededFuture("result");
        }).onComplete(ctx.asyncAssertSuccess(res -> {
            ctx.assertEquals("result", res);
            ctx.assertEquals(1, counter.get());
            async.complete();
        }));
    }

    @Test
    public void testEnsureSingleFutureReturnsFailedFuture(TestContext ctx) {
        Async async = ctx.async();
        SingleConsumerExecutor executor = new SingleConsumerExecutor();
        RuntimeException expected = new RuntimeException("boom");
        executor.<String>ensureSingle("test-fail", () -> Future.failedFuture(expected))
            .onComplete(ar -> {
                ctx.assertTrue(ar.failed());
                ctx.assertEquals("boom", ar.cause().getMessage());
                async.complete();
            });
    }

    @Test
    public void testEnsureSingleFutureHandlesTaskException(TestContext ctx) {
        Async async = ctx.async();
        SingleConsumerExecutor executor = new SingleConsumerExecutor();
        executor.<String>ensureSingle("test-throw", () -> {
            throw new RuntimeException("supplier-boom");
        }).onComplete(ar -> {
            ctx.assertTrue(ar.failed());
            ctx.assertEquals("supplier-boom", ar.cause().getMessage());
            async.complete();
        });
    }

    @Test
    public void testEnsureSingleFutureOnlyOneExecutes(TestContext ctx) {
        Async async = ctx.async();
        String[] footprints = new String[]{"noone"};
        // Use a long release delay so the lock is still held when the second call tries
        SingleConsumerExecutor executor = new SingleConsumerExecutor(500, 3000);

        String lockName = "test-only-one";

        vertx.setTimer(1,e -> {
            executor.<String>ensureSingle(lockName, () -> {
                footprints[0] = "first";
                return Future.succeededFuture("first");
            });
        });
        vertx.setTimer(100,e -> {
            executor.<String>ensureSingle(lockName, () -> {
                footprints[0] = "second";
                return Future.succeededFuture("second");
            });
        });
        vertx.setTimer(2000, e -> {
            ctx.assertEquals("first", footprints[0]);
            async.complete();
        });
    }

}
