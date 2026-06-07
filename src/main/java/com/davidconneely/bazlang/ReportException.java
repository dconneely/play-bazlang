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
    String codeStr = (reportCode != null) ? String.valueOf(reportCode.getCode()) : "-";
    return codeStr + " " + getMessage() + ", " + lineLabel + ":" + statementIndex;
  }
}
