package ee.taltech.ite4120.reports.model;

/** Someone who reports, known only by a keyed hash. */
public record Reporter(Kind kind, String keyHash) {

    public enum Kind { DEVICE, SIGNED }
}
