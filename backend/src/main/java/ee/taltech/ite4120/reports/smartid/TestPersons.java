package ee.taltech.ite4120.reports.smartid;

import java.util.List;
import java.util.Locale;

/**
 * The public Smart-ID demo persons the application offers. They are invented by the
 * provider. A production version would ask for a real person instead.
 */
public final class TestPersons {

    public record TestPerson(String document, String label) {}

    public static final List<TestPerson> ALL = List.of(
            new TestPerson("PNOEE-40504040001-DEM2-Q", "Demo person 1, signs"),
            new TestPerson("PNOEE-50001029996-DEMO-Q", "Demo person 2, signs"),
            new TestPerson("PNOEE-39901012239-MOCK-Q", "Demo person 3, signs"),
            new TestPerson("PNOEE-30403039917-MOCK-Q", "Demo person 4, refuses"));

    private TestPersons() {}

    public static int index(String document) {
        for (int i = 0; i < ALL.size(); i++) {
            if (ALL.get(i).document().equals(document)) {
                return i;
            }
        }
        return -1;
    }

    public static String document(int index) {
        return ALL.get(index).document();
    }

    /** PNOEE-40504040001-DEM2-Q is a device of the person PNOEE-40504040001. */
    public static String person(String document) {
        String[] parts = document.toUpperCase(Locale.ROOT).split("-");
        return parts[0] + "-" + parts[1];
    }
}
