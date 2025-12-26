package ai.greycos.solver.spring.boot.autoconfigure;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
public class GreycosSolverBannerBean implements InitializingBean {

  private static final Log LOG = LogFactory.getLog(GreycosSolverBannerBean.class);

  @Override
  public void afterPropertiesSet() {
    LOG.info("Using Greycos Solver.");
  }
}
