/**
 * Configuration for island model phase.
 *
 * <p>The island model runs multiple independent island agents in parallel, each running same phases
 * independently. Agents periodically exchange their best solutions through migration in a ring
 * topology.
 */
@jakarta.xml.bind.annotation.XmlSchema(
    namespace = "https://greycos.ai/xsd/solver",
    elementFormDefault = jakarta.xml.bind.annotation.XmlNsForm.QUALIFIED)
package ai.greycos.solver.core.config.islandmodel;
