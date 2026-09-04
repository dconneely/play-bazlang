package com.davidconneely.bazlang;

/** A runtime error, carrying its {@link ReportCode} and the location it occurred at. */
public class ReportException extends RuntimeException {
  /** The error's report code. */
  private final ReportCode reportCode;

  /** The line the error occurred at. */
  private final int lineLabel;

  /** The flat statement index the error occurred at. */
  private final int statementIndex;

  /**
   * Creates a report exception.
   *
   * @param reportCode the error's report code.
   * @param lineLabel the line the error occurred at.
   * @param statementIndex the flat statement index the error occurred at.
   * @param message a message describing the error; may differ from {@code reportCode}'s own.
   */
  public ReportException(ReportCode reportCode, int lineLabel, int statementIndex, String message) {
    super(message);
    this.reportCode = reportCode;
    this.lineLabel = lineLabel;
    this.statementIndex = statementIndex;
  }

  /**
   * The error's report code.
   *
   * @return the report code.
   */
  public ReportCode reportCode() {
    return reportCode;
  }

  /**
   * The line the error occurred at.
   *
   * @return the line number.
   */
  public int lineLabel() {
    return lineLabel;
  }

  /**
   * The flat statement index the error occurred at.
   *
   * @return the flat statement index.
   */
  public int statementIndex() {
    return statementIndex;
  }

  /**
   * Formats this error as {@code "<code> <message>, <line>:<statement>"}, with a parenthetical
   * custom message appended when it differs from the report code's own standard message.
   *
   * @return the formatted error text.
   */
  public String format() {
    final String codeStr = (reportCode != null) ? String.valueOf(reportCode.getCode()) : "-";
    final String stdMsg = (reportCode != null) ? reportCode.getMessage() : "";
    final String customMsg = getMessage();

    final var sb = new StringBuilder();
    sb.append(codeStr).append(' ');
    if (!stdMsg.isEmpty()) {
      sb.append(stdMsg);
    } else {
      sb.append(customMsg);
    }
    sb.append(", ").append(lineLabel).append(':').append(statementIndex);

    if (reportCode != null && customMsg != null && !customMsg.equals(stdMsg)) {
      sb.append(" (").append(customMsg).append(')');
    }
    return sb.toString();
  }
}
