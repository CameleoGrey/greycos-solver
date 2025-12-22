/**
 * Classes which represent the XML Benchmark configuration of Greycos Benchmark.
 *
 * <p>The XML Benchmark configuration is backwards compatible for all elements, except for elements
 * that require the use of non-public API classes.
 */
@XmlSchema(
    namespace = PlannerBenchmarkConfig.XML_NAMESPACE,
    elementFormDefault = XmlNsForm.QUALIFIED,
    xmlns = {
      @XmlNs(
          namespaceURI = SolverConfig.XML_NAMESPACE,
          prefix = PlannerBenchmarkConfig.SOLVER_NAMESPACE_PREFIX)
    })
package ai.greycos.solver.benchmark.config;

import ai.greycos.solver.core.config.solver.SolverConfig;
import jakarta.xml.bind.annotation.XmlNs;
import jakarta.xml.bind.annotation.XmlNsForm;
import jakarta.xml.bind.annotation.XmlSchema;
