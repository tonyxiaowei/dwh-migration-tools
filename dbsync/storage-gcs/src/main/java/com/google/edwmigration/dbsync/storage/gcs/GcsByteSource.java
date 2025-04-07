package com.google.edwmigration.dbsync.storage.gcs;

import com.codahale.metrics.Timer;
import com.google.cloud.ReadChannel;
import com.google.common.base.Optional;
import com.google.common.io.ByteSink;
import com.google.edwmigration.dbsync.common.RsyncMetrics;
import com.google.edwmigration.dbsync.common.storage.AbstractRemoteByteSource;
import com.google.edwmigration.dbsync.common.storage.Slice;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.io.ByteSource;
import com.google.protobuf.ByteString;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GcsByteSource extends AbstractRemoteByteSource {

  private static final boolean DEBUG = false;

  private static final Logger LOG = LoggerFactory.getLogger(GcsByteSource.class);

  private final Storage storage;
  private final BlobId blobId;
  private InputStreamCache inputStreamCache;

  private GcsByteSource(Storage storage, BlobId blobId, @Nullable Slice slice) {
    super(slice);
    this.storage = storage;
    this.blobId = blobId;
    this.inputStreamCache = new InputStreamCache(
        Channels.newInputStream(storage.reader(blobId)), 0);
  }

  private GcsByteSource(Storage storage, BlobId blobId, @Nullable Slice slice,
      InputStreamCache inputStreamCache) {
    super(slice);
    this.storage = storage;
    this.blobId = blobId;
    this.inputStreamCache = inputStreamCache;
  }

  public GcsByteSource(Storage storage, BlobId blobId) {
    this(storage, blobId, null);
  }

  @Override
  protected ByteSource slice(Slice slice) {
    return new GcsByteSource(storage, blobId, slice, inputStreamCache);
  }

  @Override
  public InputStream openStream() throws IOException {
    ReadChannel channel = storage.reader(blobId);
    Slice slice = getSlice();
    if (slice != null) {
      channel.seek(slice.getOffset());
      channel = channel.limit(slice.getLength() + slice.getOffset());
    }
    return Channels.newInputStream(channel);
  }

  @Override
  public InputStream openBufferedStream() throws IOException {
    LOG.info("opening buffered stream");
    return new BufferedInputStream(openStream(), 8 * 1024 * 1024);
  }

  @Override
  public long copyTo(OutputStream outputStream) throws IOException {
    Slice slice = getSlice();
    if (slice == null) {
      return super.copyTo(outputStream);
    }

    if (slice.getOffset() < inputStreamCache.getCurrentOffset()) {
      try (Timer.Context reopenAndSeek = RsyncMetrics.reopenAndSeek.time()) {
        if (DEBUG) {
          LOG.info(String.format("Target offset %d smaller than current offset %d, reopen stream",
              slice.getOffset(), inputStreamCache.currentOffset));
        }
        inputStreamCache.getSourceStream().close();
        inputStreamCache.setSourceStream(Channels.newInputStream(storage.reader(blobId)));
        inputStreamCache.setCurrentOffset(0);
      }
    }

    // Skip forward to the desired start.
    long toSkip = slice.getOffset() - inputStreamCache.getCurrentOffset();
    try (Timer.Context skip = RsyncMetrics.skip.time()) {
      skip(toSkip);
    }
    long copied;
    try (Timer.Context copyData = RsyncMetrics.copyData.time()) {
      copied = copy(slice.getLength(), outputStream);
    }

    return copied;
  }

  @Override
  public long copyTo(ByteSink byteSink) throws IOException {
    try (OutputStream outputStream = byteSink.openBufferedStream()) {
      return copyTo(outputStream);
    }
  }

  @Override
  protected MoreObjects.ToStringHelper toStringHelper(ToStringHelper helper) {
    return super.toStringHelper(helper)
        .add("blobId", blobId);
  }

  @Override
  public Optional<Long> sizeIfKnown() {
    return Optional.of(storage.get(blobId).asBlobInfo().getSize());
  }

  private void skip(long length) throws IOException {
    long remaining = length;
    while (remaining > 0) {
      long skipped = inputStreamCache.getSourceStream().skip(remaining);
      if (skipped == 0) {
        throw new EOFException("Unexpected end of stream while skipping.");
      }
      remaining -= skipped;
      inputStreamCache.setCurrentOffset(inputStreamCache.getCurrentOffset() + skipped);
    }
  }

  private long copy(long copyLength, OutputStream out) throws IOException {
    // Now copy exactly copyLength bytes.
    byte[] buffer = new byte[32 * 8192];
    long remaining = copyLength;
    while (remaining > 0) {
      int bytesToRead = (int) Math.min(buffer.length, remaining);
      int read = inputStreamCache.getSourceStream().read(buffer, 0, bytesToRead);
      if (read == -1) {
        throw new EOFException("Unexpected end of stream while copying " + copyLength
            + " bytes starting at offset " + getSlice().getOffset());
      }
      try (Timer.Context copyData = RsyncMetrics.flushData.time()) {
        ByteString.copyFrom(buffer).writeTo(out);
      }
      remaining -= read;
      inputStreamCache.setCurrentOffset(inputStreamCache.getCurrentOffset() + read);
    }

    // As we always read until there's no remaining bytes or throw.
    return copyLength;
  }

  /**
   * A cache with an {@link InputStream} and a mark of the current offset within the stream. This
   * cache will be shared by this {@link ByteSource} and the copies created by
   * {@link #slice(long, long)} to efficiently implement the {@link #copyTo(OutputStream)} method.
   */
  private static class InputStreamCache {

    private InputStream sourceStream;
    private long currentOffset;

    private InputStreamCache(InputStream inputStream, long currentOffSet) {
      this.sourceStream = inputStream;
      this.currentOffset = currentOffSet;
    }

    private InputStream getSourceStream() {
      return sourceStream;
    }

    public long getCurrentOffset() {
      return currentOffset;
    }

    public void setSourceStream(InputStream inputStream) {
      this.sourceStream = inputStream;
    }

    public void setCurrentOffset(long currentOffset) {
      this.currentOffset = currentOffset;
    }
  }
}
