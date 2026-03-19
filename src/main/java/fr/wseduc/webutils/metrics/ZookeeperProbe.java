package fr.wseduc.webutils.metrics;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.spi.cluster.zookeeper.ZookeeperClusterManager;
import org.apache.curator.framework.CuratorFramework;

import static io.vertx.core.Future.succeededFuture;

/**
 * Checks that Zookeeper is reachable and that a simple operation can be executed.
 */
public class ZookeeperProbe implements HealthCheckProbe {
  private static final Logger log = LoggerFactory.getLogger(ZookeeperProbe.class);
  private Vertx vertx;
  private ZookeeperClusterManager zookeeperClusterManager;

  @Override
  public Future<Void> init(final Vertx vertx, final JsonObject config) {
    this.vertx = vertx;
    this.zookeeperClusterManager = (ZookeeperClusterManager) ((VertxInternal) vertx).getClusterManager();
    return succeededFuture();
  }

  @Override
  public String getName() {
    return "zookeeper";
  }

  @Override
  public Vertx getVertx() {
    return vertx;
  }

  @Override
  public Future<HealthCheckProbeResult> probe() {
    if (zookeeperClusterManager == null) {
      return succeededFuture(new HealthCheckProbeResult(getName(), false, 
        new JsonObject().put("error", "Zookeeper cluster manager not available")));
    }

    final Promise<HealthCheckProbeResult> promise = Promise.promise();
    try {
      final CuratorFramework curatorFramework = zookeeperClusterManager.getCuratorFramework();
      
      if (curatorFramework == null || !curatorFramework.getZookeeperClient().isConnected()) {
        promise.complete(new HealthCheckProbeResult(getName(), false, 
          new JsonObject().put("error", "client.not.connected")));
        return promise.future();
      }

      // Perform a simple check operation (check if root path exists)
      curatorFramework.checkExists().inBackground((client, event) -> {
        if (event.getStat() != null || event.getResultCode() == 0) {
          promise.tryComplete(new HealthCheckProbeResult(getName(), true, null));
        } else {
          promise.tryComplete(new HealthCheckProbeResult(getName(), false, 
            new JsonObject().put("error", "Zookeeper check failed with code: " + event.getResultCode())));
        }
      }).forPath("/");
    } catch (Exception e) {
      log.error("Error while probing Zookeeper", e);
      promise.tryComplete(new HealthCheckProbeResult(getName(), false, 
        new JsonObject().put("error", e.getMessage())));
    }
    
    return promise.future();
  }
}
