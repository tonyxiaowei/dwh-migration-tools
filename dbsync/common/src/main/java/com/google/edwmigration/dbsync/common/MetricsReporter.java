package com.google.edwmigration.dbsync.common;

import com.codahale.metrics.ConsoleReporter;
import java.util.concurrent.TimeUnit;

public class MetricsReporter {

  public static void startConsoleReporter() {
    ConsoleReporter reporter = ConsoleReporter.forRegistry(RsyncMetrics.metrics)
        .convertRatesTo(TimeUnit.SECONDS)
        .convertDurationsTo(TimeUnit.MICROSECONDS)
        .build();
    reporter.start(3, TimeUnit.SECONDS); // Print every 30 sec
  }
}
