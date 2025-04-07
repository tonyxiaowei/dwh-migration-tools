package com.google.edwmigration.dbsync.common;

import com.codahale.metrics.SlidingTimeWindowReservoir;
import com.codahale.metrics.Timer;
import com.codahale.metrics.MetricRegistry;
import java.util.concurrent.TimeUnit;

public class RsyncMetrics {

  public static final MetricRegistry metrics = new MetricRegistry();
  public static final Timer reset = new Timer(
      new SlidingTimeWindowReservoir(10, TimeUnit.MINUTES));

  public static final Timer readBytes = new Timer(
      new SlidingTimeWindowReservoir(10, TimeUnit.MINUTES));

  public static final Timer checksumFlush = new Timer(
      new SlidingTimeWindowReservoir(10, TimeUnit.MINUTES));

  public static final Timer checksumLoop = new Timer(
      new SlidingTimeWindowReservoir(10, TimeUnit.MINUTES));

  public static final Timer getFromMap = new Timer();

  public static final Timer compareAndWriteInstruction = new Timer(
      new SlidingTimeWindowReservoir(10, TimeUnit.MINUTES));

  public static final Timer rollByte = new Timer(
      new SlidingTimeWindowReservoir(10, TimeUnit.MINUTES));

  public static final Timer reopenAndSeek = new Timer(
      new SlidingTimeWindowReservoir(10, TimeUnit.MINUTES));

  public static final Timer skip = new Timer(
      new SlidingTimeWindowReservoir(10, TimeUnit.MINUTES));

  public static final Timer copyData = new Timer(
      new SlidingTimeWindowReservoir(10, TimeUnit.MINUTES));

  public static final Timer flushData = new Timer(
      new SlidingTimeWindowReservoir(10, TimeUnit.MINUTES));

  public static final Timer copyLiteralData = new Timer(
      new SlidingTimeWindowReservoir(10, TimeUnit.MINUTES));

  // public static final Timer instructionTimer = metrics.timer("instruction-generation");
  // public static final Timer reconstructionTimer = metrics.timer("file-reconstruction");
}
