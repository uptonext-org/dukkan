package de.dukkan;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

/**
 * Module boundaries of the modular monolith. Modules are the top-level packages below {@code
 * de.dukkan} (except {@code shared}). A module may use another module only through its {@code
 * application} package (services, read models and ports); API layers may additionally reuse
 * another module's response DTOs from its {@code api} package. {@code domain} and {@code
 * infrastructure} are private to their module. Ports such as {@code catalog.application.PurchaseHistory}
 * are implemented by higher modules, which keeps the module graph acyclic.
 */
class ArchitectureTest {

  private static final Set<String> MODULES =
      Set.of(
          "identity",
          "i18n",
          "geo",
          "market",
          "catalog",
          "promotion",
          "favorite",
          "cart",
          "fulfilment",
          "address",
          "order",
          "merchant",
          "admin",
          "media",
          "capture",
          "translation",
          "privacy",
          "notification");
  private static final Pattern MODULE_PACKAGE = Pattern.compile("^de\\.dukkan\\.([a-z]+)\\.([a-z]+)(\\..*)?$");

  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes =
        new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("de.dukkan");
  }

  private record Location(String module, String layer) {}

  private static Optional<Location> locate(JavaClass javaClass) {
    Matcher matcher = MODULE_PACKAGE.matcher(javaClass.getPackageName());
    if (!matcher.matches() || !MODULES.contains(matcher.group(1))) {
      return Optional.empty();
    }
    return Optional.of(new Location(matcher.group(1), matcher.group(2)));
  }

  private static final ArchCondition<JavaClass> RESPECT_MODULE_BOUNDARIES =
      new ArchCondition<>("only use other modules through their application (or api DTO) packages") {
        @Override
        public void check(JavaClass origin, ConditionEvents events) {
          Optional<Location> from = locate(origin);
          if (from.isEmpty()) {
            return;
          }
          for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
            Optional<Location> to = locate(dependency.getTargetClass());
            if (to.isEmpty() || to.get().module().equals(from.get().module())) {
              continue;
            }
            boolean allowed =
                to.get().layer().equals("application")
                    || (from.get().layer().equals("api") && to.get().layer().equals("api"));
            if (!allowed) {
              events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
            }
          }
        }
      };

  @Test
  void modulesOnlyTalkThroughApplicationInterfaces() {
    classes().should(RESPECT_MODULE_BOUNDARIES).check(classes);
  }

  @Test
  void modulesAreFreeOfCycles() {
    slices().matching("de.dukkan.(*)..").should().beFreeOfCycles().check(classes);
  }

  @Test
  void everyModuleClassLivesInAKnownLayer() {
    classes()
        .that()
        .resideInAnyPackage(MODULES.stream().map(m -> "de.dukkan." + m + "..").toArray(String[]::new))
        .should()
        .resideInAnyPackage("..api..", "..application..", "..domain..", "..infrastructure..")
        .check(classes);
  }

  @Test
  void controllersLiveInApiPackages() {
    classes().that().areAnnotatedWith(RestController.class).should().resideInAPackage("..api..").check(classes);
  }

  @Test
  void domainDoesNotDependOnWebOrInfrastructure() {
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..api..", "..infrastructure..", "org.springframework.web..")
        .check(classes);
  }

  @Test
  void sharedDoesNotDependOnModules() {
    noClasses()
        .that()
        .resideInAPackage("de.dukkan.shared..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(MODULES.stream().map(m -> "de.dukkan." + m + "..").toArray(String[]::new))
        .check(classes);
  }
}
