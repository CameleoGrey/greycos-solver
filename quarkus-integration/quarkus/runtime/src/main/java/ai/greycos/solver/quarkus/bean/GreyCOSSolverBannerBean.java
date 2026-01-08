package ai.greycos.solver.quarkus.bean;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.jboss.logging.Logger;

import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class GreyCOSSolverBannerBean {

  private static final Logger LOGGER = Logger.getLogger(GreyCOSSolverBannerBean.class);

  void onStart(@Observes StartupEvent ev) {
    LOGGER.info("Using GreyCOS Solver.");
  }
}
