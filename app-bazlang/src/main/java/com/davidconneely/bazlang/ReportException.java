package com.davidconneely.bazlang;

public class ReportException extends RuntimeException {
  private final ReportCode reportCode;
  private final int lineLabel;
  private final int statementIndex;

  public ReportException(ReportCode reportCode, int lineLabel, int statementIndex, String message) {
    super(message);
    this.reportCode = reportCode;
    this.lineLabel = lineLabel;
    this.statementIndex = statementIndex;
  }

  public ReportException(ReportCode reportCode, int lineLabel, String message) {
    this(reportCode, lineLabel, 1, message);
  }

  public ReportCode reportCode() {
    return reportCode;
  }

  public int lineLabel() {
    return lineLabel;
  }

  public int statementIndex() {
    return statementIndex;
  }

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
