package ai.greycos.solver.spring.boot.autoconfigure;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
public class GreyCOSSolverBannerBean implements InitializingBean {

  private static final Log LOG = LogFactory.getLog(GreyCOSSolverBannerBean.class);

  @Override
  public void afterPropertiesSet() {
    LOG.info("Using GreyCOS Solver.");
  }
}
