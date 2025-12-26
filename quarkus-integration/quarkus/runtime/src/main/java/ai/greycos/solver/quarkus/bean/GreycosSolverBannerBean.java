package ai.greycos.solver.quarkus.bean;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.jboss.logging.Logger;

import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class GreycosSolverBannerBean {

  private static final Logger LOGGER = Logger.getLogger(GreycosSolverBannerBean.class);

  void onStart(@Observes StartupEvent ev) {
    LOGGER.info("Using Greycos Solver.");
  }
}
