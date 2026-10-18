package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;

import de.dukkan.support.IntegrationTest;
import de.dukkan.support.OpenApiContract;
import de.dukkan.support.OpenApiContract.Operation;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Every operation in {@code packages/shared-contracts/openapi.yaml} has a Spring handler, and no
 * handler under {@code /api/v1/**} is undocumented.
 */
class ContractMappingTest extends IntegrationTest {

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  private Set<String> implementedOperations(String basePath) {
    Set<String> implemented = new TreeSet<>();
    handlerMapping
        .getHandlerMethods()
        .keySet()
        .forEach(
            info ->
                info.getPatternValues().stream()
                    .filter(path -> path.startsWith(basePath + "/") || path.equals(basePath))
                    .forEach(
                        path ->
                            info.getMethodsCondition()
                                .getMethods()
                                .forEach(m -> implemented.add(new Operation(m.name(), OpenApiContract.normalise(path)).toString()))));
    return implemented;
  }

  @Test
  void implementationMatchesTheContractExactly() {
    OpenApiContract contract = OpenApiContract.load();
    Set<String> documented = new TreeSet<>();
    contract.operations().forEach(op -> documented.add(op.toString()));
    Set<String> implemented = implementedOperations(contract.basePath());

    assertThat(documented).as("contract operations").hasSizeGreaterThanOrEqualTo(20);
    assertThat(implemented).as("operations without a handler").containsAll(documented);
    assertThat(documented).as("undocumented /api/v1 handlers").containsAll(implemented);
  }
}
