package ai.greycos.solver.jackson.preview.api.cotwin.solution.diff;

import static org.assertj.core.api.Assertions.assertThat;

import ai.greycos.solver.core.testcotwin.equals.TestdataEqualsByCodeSolution;
import ai.greycos.solver.jackson.api.GreyCOSJacksonModule;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

class PlanningSolutionDiffTest {

  @Test
  void serialize() throws JacksonException {
    var oldSolution = TestdataEqualsByCodeSolution.generateSolution("A", 2, 2);
    oldSolution.getEntityList().get(0).setValue(oldSolution.getValueList().get(1));
    var newSolution = TestdataEqualsByCodeSolution.generateSolution("B", 3, 3);
    var solutionDescriptor = TestdataEqualsByCodeSolution.buildSolutionDescriptor();
    var diff = solutionDescriptor.diff(oldSolution, newSolution);

    var objectMapper =
        JsonMapper.builder()
            .addModule(GreyCOSJacksonModule.createModule())
            .disable(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST)
            .build();

    var serialized = objectMapper.writeValueAsString(diff);
    assertThat(serialized)
        .isEqualToIgnoringWhitespace(
            """
                        {
                           "added_entities": [
                             {
                               "code": "Generated Entity 2",
                               "value": {
                                 "code": "Generated Value 2"
                               }
                             }
                           ],
                           "entity_diffs": [
                             {
                               "entity": {
                                 "code": "Generated Entity 0",
                                 "value": {
                                   "code": "Generated Value 1"
                                 }
                               },
                               "entity_class": "ai.greycos.solver.core.testcotwin.equals.TestdataEqualsByCodeEntity",
                               "variable_diffs": [
                                 {
                                   "name": "value",
                                   "new_value": {
                                     "code": "Generated Value 0"
                                   },
                                   "old_value": {
                                     "code": "Generated Value 1"
                                   }
                                 }
                               ]
                             }
                           ],
                           "removed_entities": []
                         }""");
  }
}
