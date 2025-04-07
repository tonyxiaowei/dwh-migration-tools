package com.google.edwmigration.dbsync.gcsync;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

/**
 * Utility class to generate and modify large test files for rsync-like testing.
 */
public class RsyncTestUtility {

  // Hard-coded file name
  private static final String FILE_NAME = "test-file.bin";
  // We'll use a 16 KB block size
  private static final int BLOCK_SIZE = Constants.BLOCK_SIZE;
  // For generating random data
  private static final Random RAND = new Random();

  /**
   * Generates a file of size xGiB (gibibytes) at the path test-file.bin. Data is written in 16 KB
   * blocks of random bytes.
   *
   * @param xGiB How many gibibytes to write. E.g., generateFile(1) => 1 GiB
   * @throws IOException if an I/O error occurs.
   */
  public static void generateFile(int xGiB) throws IOException {
    Path filePath = Paths.get(FILE_NAME);

    long totalBytes = (long) xGiB * 1024L * 1024L * 1024L;
    try (OutputStream out = Files.newOutputStream(filePath)) {
      byte[] buffer = new byte[BLOCK_SIZE];
      long bytesWritten = 0;
      while (bytesWritten < totalBytes) {
        RAND.nextBytes(buffer);
        long remaining = totalBytes - bytesWritten;
        int toWrite = (int) Math.min(BLOCK_SIZE, remaining);
        out.write(buffer, 0, toWrite);
        bytesWritten += toWrite;
      }
    }
    System.out.printf("Generated file '%s' (size=%d GiB).%n", FILE_NAME, xGiB);
  }

  /**
   * Modifies ~1% of the blocks in test-file.bin by overwriting them with random data. The file must
   * already exist, and we assume the same 16 KB block size.
   *
   * @throws IOException if an I/O error occurs.
   */
  public static void modifyFile() throws IOException {
    Path filePath = Paths.get(FILE_NAME);
    if (!Files.exists(filePath)) {
      throw new IOException("File " + FILE_NAME + " does not exist. Generate it first.");
    }

    long fileSize = Files.size(filePath);
    long totalBlocks = (fileSize + BLOCK_SIZE - 1) / BLOCK_SIZE;  // round up
    long blocksToMutate = totalBlocks / 100;  // ~1%

    if (blocksToMutate == 0) {
      // If the file is small, mutate at least 1 block
      blocksToMutate = 1;
    }

    try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "rw")) {
      for (long i = 0; i < blocksToMutate; i++) {
        // Pick a random block index to mutate
        long blockIndex = (long) (RAND.nextDouble() * totalBlocks);
        long blockOffset = blockIndex * BLOCK_SIZE;

        raf.seek(blockOffset);

        long bytesRemaining = fileSize - blockOffset;
        int toWrite = (int) Math.min(BLOCK_SIZE, bytesRemaining);

        byte[] randomData = new byte[toWrite];
        RAND.nextBytes(randomData);
        raf.write(randomData);
      }
    }
    System.out.printf("Modified ~1%% of the blocks in '%s'.%n", FILE_NAME);
  }

  // Optional main method for quick demonstration
  public static void main(String[] args) throws IOException {
    // Example usage: generate 1 GiB, then modify 1% of blocks
    // generateFile(10);
    modifyFile();
  }
}
