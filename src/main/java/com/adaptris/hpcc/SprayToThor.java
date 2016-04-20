package com.adaptris.hpcc;

import java.io.File;
import java.io.IOException;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileCleaningTracker;
import org.apache.commons.io.FileUtils;

import com.adaptris.annotation.DisplayOrder;
import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.ProduceDestination;
import com.adaptris.core.ProduceException;
import com.adaptris.core.lms.FileBackedMessage;
import com.adaptris.core.util.ExceptionHelper;
import com.adaptris.security.password.Password;
import com.adaptris.util.stream.Slf4jLoggingOutputStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;

@XStreamAlias("spray-to-thor")
@DisplayOrder(order = {"dfuplusCommand", "format", "maxRecordSize", "server", "cluster", "username", "password", "overwrite"})
public class SprayToThor extends SprayToThorImpl {

  public enum FORMAT { CSV, FIXED; }

  private FORMAT format;
  private int maxRecordSize = 8192;

  private transient final FileCleaningTracker tracker = new FileCleaningTracker();
  
  @Override
  public void produce(AdaptrisMessage msg, ProduceDestination destination) throws ProduceException {
    File sourceFile;
    Object marker = new Object();
    
    if(msg instanceof FileBackedMessage) {
      sourceFile = ((FileBackedMessage)msg).currentSource();
    } else {
      // If the message is not file-backed, write it to a temp file
      try {
        sourceFile = File.createTempFile("adp", ".dat");
        tracker.track(sourceFile, marker);
        FileUtils.writeByteArrayToFile(sourceFile, msg.getPayload());
      } catch (IOException e) {
        throw new ProduceException("Unable to write temporary file", e);
      }
    }
    int exit = 0;
    // Create DFU command
    // String cmd = "dfuplus action=%s format=%s maxrecordsize=%d sourcefile=%s dstname=%s server=%s dstcluster=%s username=%s
    // password=%s overwrite=%d";
    try (Slf4jLoggingOutputStream out = new Slf4jLoggingOutputStream(log, "DEBUG")) {
      Executor cmd = new DefaultExecutor();
      ExecuteWatchdog watchdog = new ExecuteWatchdog(timeoutMs());
      cmd.setWatchdog(watchdog);
      CommandLine commandLine = new CommandLine(getDfuplusCommand());
      commandLine.addArgument("action=spray");
      commandLine.addArgument(String.format("format=%s", getFormat().name().toLowerCase()));
      commandLine.addArgument(String.format("maxrecordsize=%d", getMaxRecordSize()));
      commandLine.addArgument(String.format("srcfile=%s", sourceFile.getCanonicalPath()));
      commandLine.addArgument(String.format("dstname=%s", destination.getDestination(msg)));
      commandLine.addArgument(String.format("server=%s", getServer()));
      commandLine.addArgument(String.format("dstcluster=%s", getCluster()));
      commandLine.addArgument(String.format("username=%s", getUsername()));
      commandLine.addArgument(String.format("password=%s", Password.decode(getPassword())));
      commandLine.addArgument(String.format("overwrite=%d", overwrite() ? 1 : 0));
      PumpStreamHandler pump = new PumpStreamHandler(out);
      cmd.setStreamHandler(pump);
      log.trace("Executing {}", commandLine);
      exit = cmd.execute(commandLine);
    } catch (Exception e) {
      throw ExceptionHelper.wrapProduceException(e);
    }
    if (exit != 0) {
      throw new ProduceException("Spray failed with exit code " + exit);
    }
  }

  public FORMAT getFormat() {
    return format;
  }

  public void setFormat(FORMAT format) {
    this.format = format;
  }

  public int getMaxRecordSize() {
    return maxRecordSize;
  }

  public void setMaxRecordSize(int maxRecordSize) {
    this.maxRecordSize = maxRecordSize;
  }
}
