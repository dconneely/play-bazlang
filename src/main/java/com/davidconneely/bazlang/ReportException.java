package com.davidconneely.bazlang;

public class ReportException extends RuntimeException {
  private final ReportCode reportCode;
  private final int lineLabel;

  public ReportException(ReportCode reportCode, int lineLabel, String message) {
    super(message);
    this.reportCode = reportCode;
    this.lineLabel = lineLabel;
  }

  public ReportCode reportCode() {
    return reportCode;
  }

  public int lineLabel() {
    return lineLabel;
  }

  public String prefix() {
    return String.valueOf(reportCode != null ? reportCode.getCode() : '-') + '/' + lineLabel;
  }
}
