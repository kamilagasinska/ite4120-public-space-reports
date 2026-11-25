package ee.taltech.ite4120.reports.model;

/** Points and trust level of a reporter. The last level has no next one. */
public record Standing(int points, int level, Integer nextLevelAt) {}
