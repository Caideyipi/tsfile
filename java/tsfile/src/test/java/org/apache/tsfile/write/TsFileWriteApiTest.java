/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tsfile.write;

import org.apache.tsfile.common.conf.TSFileDescriptor;
import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.exception.read.ReadProcessException;
import org.apache.tsfile.exception.write.WriteProcessException;
import org.apache.tsfile.file.MetaMarker;
import org.apache.tsfile.file.header.ChunkHeader;
import org.apache.tsfile.file.header.PageHeader;
import org.apache.tsfile.file.metadata.ChunkMetadata;
import org.apache.tsfile.file.metadata.ColumnSchema;
import org.apache.tsfile.file.metadata.IDeviceID;
import org.apache.tsfile.file.metadata.TableSchema;
import org.apache.tsfile.file.metadata.enums.TSEncoding;
import org.apache.tsfile.fileSystem.FSFactoryProducer;
import org.apache.tsfile.read.TsFileReader;
import org.apache.tsfile.read.TsFileSequenceReader;
import org.apache.tsfile.read.common.Chunk;
import org.apache.tsfile.read.common.Path;
import org.apache.tsfile.read.expression.QueryExpression;
import org.apache.tsfile.read.query.dataset.QueryDataSet;
import org.apache.tsfile.read.query.dataset.ResultSet;
import org.apache.tsfile.read.v4.ITsFileReader;
import org.apache.tsfile.read.v4.TsFileReaderBuilder;
import org.apache.tsfile.utils.TsFileGeneratorUtils;
import org.apache.tsfile.write.chunk.AlignedChunkWriterImpl;
import org.apache.tsfile.write.chunk.ChunkWriterImpl;
import org.apache.tsfile.write.record.TSRecord;
import org.apache.tsfile.write.record.Tablet;
import org.apache.tsfile.write.record.datapoint.DateDataPoint;
import org.apache.tsfile.write.record.datapoint.StringDataPoint;
import org.apache.tsfile.write.schema.IMeasurementSchema;
import org.apache.tsfile.write.schema.MeasurementSchema;
import org.apache.tsfile.write.v4.ITsFileWriter;
import org.apache.tsfile.write.v4.TsFileWriterBuilder;
import org.apache.tsfile.write.writer.TsFileIOWriter;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TsFileWriteApiTest {
  private final File f = FSFactoryProducer.getFSFactory().getFile("TsFileWriteTest.tsfile");
  private final String deviceId = "root.sg.d1";
  private final List<IMeasurementSchema> alignedMeasurementSchemas = new ArrayList<>();
  private final List<IMeasurementSchema> measurementSchemas = new ArrayList<>();
  private int oldChunkGroupSize = TSFileDescriptor.getInstance().getConfig().getGroupSizeInByte();
  private int oldMaxNumOfPointsInPage =
      TSFileDescriptor.getInstance().getConfig().getMaxNumberOfPointsInPage();

  @Before
  public void setUp() {
    if (f.exists() && !f.delete()) {
      throw new RuntimeException("can not delete " + f.getAbsolutePath());
    }
  }

  @After
  public void end() {
    if (f.exists()) f.delete();
    TSFileDescriptor.getInstance().getConfig().setMaxNumberOfPointsInPage(oldMaxNumOfPointsInPage);
    TSFileDescriptor.getInstance().getConfig().setGroupSizeInByte(oldChunkGroupSize);
  }

  private void setEnv(int chunkGroupSize, int pageSize) {
    TSFileDescriptor.getInstance().getConfig().setGroupSizeInByte(chunkGroupSize);
    TSFileDescriptor.getInstance().getConfig().setMaxNumberOfPointsInPage(pageSize);
  }

  public void registerAlignedTimeseries(TsFileWriter tsFileWriter) throws WriteProcessException {
    alignedMeasurementSchemas.add(new MeasurementSchema("s1", TSDataType.INT64, TSEncoding.PLAIN));
    alignedMeasurementSchemas.add(new MeasurementSchema("s2", TSDataType.INT64, TSEncoding.PLAIN));
    alignedMeasurementSchemas.add(new MeasurementSchema("s3", TSDataType.INT64, TSEncoding.PLAIN));
    alignedMeasurementSchemas.add(new MeasurementSchema("s4", TSDataType.INT64, TSEncoding.RLE));

    // register align timeseries
    tsFileWriter.registerAlignedTimeseries(new Path(deviceId), alignedMeasurementSchemas);
  }

  public void registerTimeseries(TsFileWriter tsFileWriter) {
    measurementSchemas.add(new MeasurementSchema("s1", TSDataType.INT64, TSEncoding.PLAIN));
    measurementSchemas.add(new MeasurementSchema("s2", TSDataType.INT64, TSEncoding.PLAIN));
    measurementSchemas.add(new MeasurementSchema("s3", TSDataType.INT64, TSEncoding.PLAIN));

    // register nonAlign timeseries
    tsFileWriter.registerTimeseries(new Path(deviceId), measurementSchemas);
  }

  @Test
  public void writeWithTsRecord() throws IOException, WriteProcessException {
    setEnv(100 * 1024 * 1024, 10 * 1024);
    try (TsFileWriter tsFileWriter = new TsFileWriter(f)) {
      registerTimeseries(tsFileWriter);

      List<IMeasurementSchema> writeMeasurementScheams = new ArrayList<>();
      // example 1
      writeMeasurementScheams.add(measurementSchemas.get(0));
      writeMeasurementScheams.add(measurementSchemas.get(1));
      TsFileGeneratorUtils.writeWithTsRecord(
          tsFileWriter, deviceId, writeMeasurementScheams, 10000, 0, 0, false);

      // example 2
      writeMeasurementScheams.clear();
      writeMeasurementScheams.add(measurementSchemas.get(2));
      writeMeasurementScheams.add(measurementSchemas.get(0));
      TsFileGeneratorUtils.writeWithTsRecord(
          tsFileWriter, deviceId, writeMeasurementScheams, 10000, 10000, 100, false);

      // example 3 : late data
      writeMeasurementScheams.clear();
      writeMeasurementScheams.add(measurementSchemas.get(2));
      TsFileGeneratorUtils.writeWithTsRecord(
          tsFileWriter, deviceId, writeMeasurementScheams, 10, 20000, 200000, false);
    }
  }

  @Test
  public void writeTSRecordWithAllDateType() throws IOException, WriteProcessException {
    setEnv(100 * 1024 * 1024, 10 * 1024);
    try (TsFileWriter writer = new TsFileWriter(f)) {
      List<IMeasurementSchema> measurementList =
          Arrays.asList(
              new MeasurementSchema("s1", TSDataType.INT32),
              new MeasurementSchema("s2", TSDataType.INT64),
              new MeasurementSchema("s3", TSDataType.BOOLEAN),
              new MeasurementSchema("s4", TSDataType.FLOAT),
              new MeasurementSchema("s5", TSDataType.DOUBLE),
              new MeasurementSchema("s6", TSDataType.DATE),
              new MeasurementSchema("s7", TSDataType.TIMESTAMP),
              new MeasurementSchema("s8", TSDataType.TEXT),
              new MeasurementSchema("s9", TSDataType.BLOB),
              new MeasurementSchema("s10", TSDataType.STRING));
      writer.registerAlignedTimeseries("root.test.d1", measurementList);

      TSRecord tsRecord = new TSRecord("root.test.d1", 1);
      tsRecord.addPoint("s1", 1);
      tsRecord.addPoint("s2", 1L);
      tsRecord.addPoint("s3", true);
      tsRecord.addPoint("s4", 1.0f);
      tsRecord.addPoint("s5", 1.0d);
      tsRecord.addPoint("s6", LocalDate.now());
      tsRecord.addPoint("s7", System.currentTimeMillis());
      tsRecord.addPoint("s8", "text value");
      tsRecord.addPoint("s9", "blob value");
      tsRecord.addPoint("s10", "string value");

      writer.writeRecord(tsRecord);
    }
  }

  @Test
  public void writeAlignedWithTsRecord() throws IOException, WriteProcessException {
    setEnv(100 * 1024 * 1024, 10 * 1024);
    try (TsFileWriter tsFileWriter = new TsFileWriter(f)) {
      registerAlignedTimeseries(tsFileWriter);

      List<IMeasurementSchema> writeMeasurementScheams = new ArrayList<>();
      // example1
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(0));
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(1));
      TsFileGeneratorUtils.writeWithTsRecord(
          tsFileWriter, deviceId, writeMeasurementScheams, 8, 0, 0, true);

      // example2
      writeMeasurementScheams.clear();
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(2));
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(0));
      TsFileGeneratorUtils.writeWithTsRecord(
          tsFileWriter, deviceId, writeMeasurementScheams, 20, 1000, 500, true);

      // example3 : late data
      writeMeasurementScheams.clear();
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(2));
      TsFileGeneratorUtils.writeWithTsRecord(
          tsFileWriter, deviceId, writeMeasurementScheams, 20, 300000, 50, true);
    }
  }

  @Test
  public void writeWithTablet() throws IOException, WriteProcessException {
    setEnv(100 * 1024 * 1024, 10 * 1024);
    try (TsFileWriter tsFileWriter = new TsFileWriter(f)) {
      registerTimeseries(tsFileWriter);

      List<IMeasurementSchema> writeMeasurementScheams = new ArrayList<>();
      // example 1
      writeMeasurementScheams.add(measurementSchemas.get(0));
      writeMeasurementScheams.add(measurementSchemas.get(1));
      TsFileGeneratorUtils.writeWithTablet(
          tsFileWriter, deviceId, writeMeasurementScheams, 1000, 0, 0, false);

      // example 2
      writeMeasurementScheams.clear();
      writeMeasurementScheams.add(measurementSchemas.get(2));
      writeMeasurementScheams.add(measurementSchemas.get(1));
      TsFileGeneratorUtils.writeWithTablet(
          tsFileWriter, deviceId, writeMeasurementScheams, 1000, 2000, 0, false);

      // example 3: late data
      writeMeasurementScheams.clear();
      writeMeasurementScheams.add(measurementSchemas.get(1));
      TsFileGeneratorUtils.writeWithTablet(
          tsFileWriter, deviceId, writeMeasurementScheams, 1000, 3111, 0, false);
    }
  }

  @Test
  public void writeAlignedWithTablet() throws IOException, WriteProcessException {
    setEnv(100 * 1024 * 1024, 10 * 1024);
    try (TsFileWriter tsFileWriter = new TsFileWriter(f)) {
      registerAlignedTimeseries(tsFileWriter);

      List<IMeasurementSchema> writeMeasurementScheams = new ArrayList<>();
      // example 1
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(0));
      TsFileGeneratorUtils.writeWithTablet(
          tsFileWriter, deviceId, writeMeasurementScheams, 10, 0, 0, true);

      // example 2
      writeMeasurementScheams.clear();
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(0));
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(1));
      TsFileGeneratorUtils.writeWithTablet(
          tsFileWriter, deviceId, writeMeasurementScheams, 200000, 10, 0, true);

      // example 3
      writeMeasurementScheams.clear();
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(2));
      TsFileGeneratorUtils.writeWithTablet(
          tsFileWriter, deviceId, writeMeasurementScheams, 10, 210000, 0, true);
    }
  }

  @Test
  public void writeNewAlignedMeasurementAfterFlushChunkGroup1() {
    setEnv(100, 30);
    try (TsFileWriter tsFileWriter = new TsFileWriter(f)) {
      registerAlignedTimeseries(tsFileWriter);

      List<IMeasurementSchema> writeMeasurementScheams = new ArrayList<>();
      // example 1
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(0));
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(1));
      TsFileGeneratorUtils.writeWithTablet(
          tsFileWriter, deviceId, writeMeasurementScheams, 100000, 0, 0, true);

      // example 2
      writeMeasurementScheams.clear();
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(2));
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(3));
      TsFileGeneratorUtils.writeWithTablet(
          tsFileWriter, deviceId, writeMeasurementScheams, 20, 1000000, 0, true);

    } catch (IOException | WriteProcessException e) {
      Assert.assertEquals(
          "TsFile has flushed chunk group and should not add new measurement s3 in device root.sg.d1",
          e.getMessage());
    }
  }

  @Test
  public void writeNewAlignedMeasurementAfterFlushChunkGroup2() {
    setEnv(100, 30);
    try (TsFileWriter tsFileWriter = new TsFileWriter(f)) {
      registerAlignedTimeseries(tsFileWriter);

      List<IMeasurementSchema> writeMeasurementScheams = new ArrayList<>();
      // example 1
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(0));
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(1));
      TsFileGeneratorUtils.writeWithTablet(
          tsFileWriter, deviceId, writeMeasurementScheams, 100000, 0, 0, true);

      // example 2
      writeMeasurementScheams.clear();
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(0));
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(3));
      TsFileGeneratorUtils.writeWithTablet(
          tsFileWriter, deviceId, writeMeasurementScheams, 20, 1000000, 0, true);
    } catch (IOException | WriteProcessException e) {
      Assert.assertEquals(
          "TsFile has flushed chunk group and should not add new measurement s4 in device root.sg.d1",
          e.getMessage());
    }
  }

  @Test
  public void writeOutOfOrderAlignedData() throws IOException, WriteProcessException {
    setEnv(100, 30);
    try (TsFileWriter tsFileWriter = new TsFileWriter(f)) {
      registerAlignedTimeseries(tsFileWriter);

      List<IMeasurementSchema> writeMeasurementScheams = new ArrayList<>();
      // example 1
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(0));
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(1));
      TsFileGeneratorUtils.writeWithTablet(
          tsFileWriter, deviceId, writeMeasurementScheams, 1000, 0, 0, true);

      // example 2
      writeMeasurementScheams.clear();
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(0));
      try {
        TsFileGeneratorUtils.writeWithTablet(
            tsFileWriter, deviceId, writeMeasurementScheams, 20, 100, 0, true);
        Assert.fail("Expected to throw writeProcessException due to write out-of-order data.");
      } catch (WriteProcessException e) {
        Assert.assertEquals(
            "Not allowed to write out-of-order data in timeseries root.sg.d1., time should later than 999",
            e.getMessage());
      }

      // example 3
      writeMeasurementScheams.clear();
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(1));
      try {
        TsFileGeneratorUtils.writeWithTsRecord(
            tsFileWriter, deviceId, writeMeasurementScheams, 20, 100, 0, true);
        Assert.fail("Expected to throw writeProcessException due to write out-of-order data.");
      } catch (WriteProcessException e) {
        Assert.assertEquals(
            "Not allowed to write out-of-order data in timeseries root.sg.d1., time should later than 999",
            e.getMessage());
      }
    }
  }

  @Test
  public void writeOutOfOrderData() throws IOException, WriteProcessException {
    setEnv(100, 30);
    try (TsFileWriter tsFileWriter = new TsFileWriter(f)) {
      registerTimeseries(tsFileWriter);

      List<IMeasurementSchema> writeMeasurementScheams = new ArrayList<>();
      // example 1
      writeMeasurementScheams.add(measurementSchemas.get(0));
      writeMeasurementScheams.add(measurementSchemas.get(1));
      TsFileGeneratorUtils.writeWithTablet(
          tsFileWriter, deviceId, writeMeasurementScheams, 1000, 0, 0, false);

      // example 2
      writeMeasurementScheams.clear();
      writeMeasurementScheams.add(measurementSchemas.get(0));
      try {
        TsFileGeneratorUtils.writeWithTablet(
            tsFileWriter, deviceId, writeMeasurementScheams, 20, 100, 0, false);
        Assert.fail("Expected to throw writeProcessException due to write out-of-order data.");
      } catch (WriteProcessException e) {
        Assert.assertEquals(
            "Not allowed to write out-of-order data in timeseries root.sg.d1.s1, time should later than 999",
            e.getMessage());
      }

      // example 3
      writeMeasurementScheams.clear();
      writeMeasurementScheams.add(measurementSchemas.get(1));
      try {
        TsFileGeneratorUtils.writeWithTsRecord(
            tsFileWriter, deviceId, writeMeasurementScheams, 20, 100, 0, false);
        Assert.fail("Expected to throw writeProcessException due to write out-of-order data.");
      } catch (WriteProcessException e) {
        Assert.assertEquals(
            "Not allowed to write out-of-order data in timeseries root.sg.d1.s2, time should later than 999",
            e.getMessage());
      }
    }
  }

  @Test
  public void writeNonAlignedWithTabletWithNullValue() {
    setEnv(100, 30);
    try (TsFileWriter tsFileWriter = new TsFileWriter(f)) {
      measurementSchemas.add(new MeasurementSchema("s1", TSDataType.TEXT, TSEncoding.PLAIN));
      measurementSchemas.add(new MeasurementSchema("s2", TSDataType.STRING, TSEncoding.PLAIN));
      measurementSchemas.add(new MeasurementSchema("s3", TSDataType.BLOB, TSEncoding.PLAIN));
      measurementSchemas.add(new MeasurementSchema("s4", TSDataType.DATE, TSEncoding.PLAIN));

      // register nonAligned timeseries
      tsFileWriter.registerTimeseries(new Path(deviceId), measurementSchemas);

      Tablet tablet = new Tablet(deviceId, measurementSchemas);
      int sensorNum = measurementSchemas.size();
      long startTime = 0;
      for (long r = 0; r < 10000; r++) {
        int row = tablet.getRowSize();
        tablet.addTimestamp(row, startTime++);
        for (int i = 0; i < sensorNum - 1; i++) {
          if (i == 1 && r > 1000) {
            tablet.getBitMaps()[i].mark((int) r % tablet.getMaxRowNumber());
            continue;
          }
          tablet.addValue(row, i, "testString.........");
        }
        if (r > 1000) {
          tablet.getBitMaps()[sensorNum - 1].mark((int) r % tablet.getMaxRowNumber());
        } else {
          tablet.addValue(row, sensorNum - 1, LocalDate.of(2024, 4, 1));
        }
        // write
        if (tablet.getRowSize() == tablet.getMaxRowNumber()) {
          tsFileWriter.writeTree(tablet);
          tablet.reset();
        }
      }
      // write
      if (tablet.getRowSize() != 0) {
        tsFileWriter.writeTree(tablet);
        tablet.reset();
      }

    } catch (Throwable e) {
      e.printStackTrace();
      Assert.fail("Meet errors in test: " + e.getMessage());
    }
  }

  @Test
  public void writeNonAlignedWithTabletWithNegativeTimestamps() {
    setEnv(100, 30);
    try (TsFileWriter tsFileWriter = new TsFileWriter(f)) {
      measurementSchemas.add(new MeasurementSchema("s1", TSDataType.TEXT, TSEncoding.PLAIN));
      measurementSchemas.add(new MeasurementSchema("s2", TSDataType.STRING, TSEncoding.PLAIN));
      measurementSchemas.add(new MeasurementSchema("s3", TSDataType.BLOB, TSEncoding.PLAIN));
      measurementSchemas.add(new MeasurementSchema("s4", TSDataType.DATE, TSEncoding.PLAIN));

      // register nonAligned timeseries
      tsFileWriter.registerTimeseries(new Path(deviceId), measurementSchemas);

      Tablet tablet = new Tablet(deviceId, measurementSchemas);
      tablet.initBitMaps();
      int sensorNum = measurementSchemas.size();
      long startTime = -100;
      for (long r = 0; r < 10000; r++) {
        int row = tablet.getRowSize();
        tablet.addTimestamp(row, startTime++);
        for (int i = 0; i < sensorNum - 1; i++) {
          if (i == 1 && r > 1000) {
            tablet.getBitMaps()[i].mark((int) r % tablet.getMaxRowNumber());
            continue;
          }
          tablet.addValue(row, i, "testString.........");
        }
        if (r > 1000) {
          tablet.getBitMaps()[sensorNum - 1].mark((int) r % tablet.getMaxRowNumber());
        } else {
          tablet.addValue(row, sensorNum - 1, LocalDate.of(2024, 4, 1));
        }
        // write
        if (tablet.getRowSize() == tablet.getMaxRowNumber()) {
          tsFileWriter.writeTree(tablet);
          tablet.reset();
        }
      }
      // write
      if (tablet.getRowSize() != 0) {
        tsFileWriter.writeTree(tablet);
        tablet.reset();
      }

    } catch (Throwable e) {
      e.printStackTrace();
      Assert.fail("Meet errors in test: " + e.getMessage());
    }
  }

  @Test
  public void writeAlignedWithTabletWithNullValue() {
    setEnv(100, 30);
    try (TsFileWriter tsFileWriter = new TsFileWriter(f)) {
      measurementSchemas.add(new MeasurementSchema("s1", TSDataType.TEXT, TSEncoding.PLAIN));
      measurementSchemas.add(new MeasurementSchema("s2", TSDataType.STRING, TSEncoding.PLAIN));
      measurementSchemas.add(new MeasurementSchema("s3", TSDataType.BLOB, TSEncoding.PLAIN));
      measurementSchemas.add(new MeasurementSchema("s4", TSDataType.DATE, TSEncoding.PLAIN));

      // register aligned timeseries
      tsFileWriter.registerAlignedTimeseries(new Path(deviceId), measurementSchemas);

      Tablet tablet = new Tablet(deviceId, measurementSchemas);
      tablet.initBitMaps();
      int sensorNum = measurementSchemas.size();
      long startTime = 0;
      for (long r = 0; r < 10000; r++) {
        int row = tablet.getRowSize();
        tablet.addTimestamp(row, startTime++);
        for (int i = 0; i < sensorNum - 1; i++) {
          if (i == 1 && r > 1000) {
            tablet.getBitMaps()[i].mark((int) r % tablet.getMaxRowNumber());
            continue;
          }
          tablet.addValue(row, i, "testString.........");
        }
        if (r > 1000) {
          tablet.getBitMaps()[sensorNum - 1].mark((int) r % tablet.getMaxRowNumber());
        } else {
          tablet.addValue(row, sensorNum - 1, LocalDate.of(2024, 4, 1));
        }
        // write
        if (tablet.getRowSize() == tablet.getMaxRowNumber()) {
          tsFileWriter.writeAligned(tablet);
          tablet.reset();
        }
      }
      // write
      if (tablet.getRowSize() != 0) {
        tsFileWriter.writeAligned(tablet);
        tablet.reset();
      }

    } catch (Throwable e) {
      e.printStackTrace();
      Assert.fail("Meet errors in test: " + e.getMessage());
    }
  }

  @Test
  public void writeRecordWithNullValue() {
    setEnv(100, 30);
    measurementSchemas.add(new MeasurementSchema("s1", TSDataType.TEXT, TSEncoding.PLAIN));
    measurementSchemas.add(new MeasurementSchema("s2", TSDataType.STRING, TSEncoding.PLAIN));
    measurementSchemas.add(new MeasurementSchema("s3", TSDataType.BLOB, TSEncoding.PLAIN));
    measurementSchemas.add(new MeasurementSchema("s4", TSDataType.DATE, TSEncoding.PLAIN));
    try (TsFileWriter tsFileWriter = new TsFileWriter(f)) {

      // register aligned timeseries
      tsFileWriter.registerAlignedTimeseries(new Path(deviceId), measurementSchemas);

      TSRecord record = new TSRecord(deviceId, 0);
      record.addTuple(new StringDataPoint("s1", null));
      record.addTuple(new StringDataPoint("s2", null));
      record.addTuple(new StringDataPoint("s3", null));
      record.addTuple(new DateDataPoint("s4", null));

      tsFileWriter.writeRecord(record);
    } catch (Throwable e) {
      e.printStackTrace();
      Assert.fail("Meet errors in test: " + e.getMessage());
    }
  }

  @Test
  public void writeDataToTabletsWithNegativeTimestamps() {
    setEnv(100, 30);
    try (TsFileWriter tsFileWriter = new TsFileWriter(f)) {
      measurementSchemas.add(new MeasurementSchema("s1", TSDataType.TEXT, TSEncoding.PLAIN));
      measurementSchemas.add(new MeasurementSchema("s2", TSDataType.STRING, TSEncoding.PLAIN));
      measurementSchemas.add(new MeasurementSchema("s3", TSDataType.BLOB, TSEncoding.PLAIN));
      measurementSchemas.add(new MeasurementSchema("s4", TSDataType.DATE, TSEncoding.PLAIN));

      // register aligned timeseries
      tsFileWriter.registerAlignedTimeseries(new Path(deviceId), measurementSchemas);

      Tablet tablet = new Tablet(deviceId, measurementSchemas);
      tablet.initBitMaps();
      int sensorNum = measurementSchemas.size();
      long startTime = -1000;
      for (long r = 0; r < 10000; r++) {
        int row = tablet.getRowSize();
        tablet.addTimestamp(row, startTime++);
        for (int i = 0; i < sensorNum - 1; i++) {
          if (i == 1 && r > 1000) {
            tablet.getBitMaps()[i].mark((int) r % tablet.getMaxRowNumber());
            continue;
          }
          tablet.addValue(row, i, "testString.........");
        }
        if (r > 1000) {
          tablet.getBitMaps()[sensorNum - 1].mark((int) r % tablet.getMaxRowNumber());
        } else {
          tablet.addValue(row, sensorNum - 1, LocalDate.of(2024, 4, 1));
        }
        // write
        if (tablet.getRowSize() == tablet.getMaxRowNumber()) {
          tsFileWriter.writeAligned(tablet);
          tablet.reset();
        }
      }
      // write
      if (tablet.getRowSize() != 0) {
        tsFileWriter.writeAligned(tablet);
        tablet.reset();
      }

    } catch (Throwable e) {
      e.printStackTrace();
      Assert.fail("Meet errors in test: " + e.getMessage());
    }
  }

  /** Write an empty page and then write a nonEmpty page. */
  @Test
  public void writeAlignedTimeseriesWithEmptyPage() throws IOException, WriteProcessException {
    TSFileDescriptor.getInstance().getConfig().setMaxNumberOfPointsInPage(30);
    try (TsFileWriter tsFileWriter = new TsFileWriter(f)) {
      registerAlignedTimeseries(tsFileWriter);

      List<IMeasurementSchema> writeMeasurementScheams = new ArrayList<>();
      // example1
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(0));
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(1));
      TsFileGeneratorUtils.writeWithTsRecord(
          tsFileWriter, deviceId, writeMeasurementScheams, 30, 0, 0, true);

      // example2
      writeMeasurementScheams.clear();
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(2));
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(1));
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(0));
      TsFileGeneratorUtils.writeWithTsRecord(
          tsFileWriter, deviceId, writeMeasurementScheams, 30, 1000, 500, true);

      // example3 : late data
      writeMeasurementScheams.clear();
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(2));
      TsFileGeneratorUtils.writeWithTsRecord(
          tsFileWriter, deviceId, writeMeasurementScheams, 60, 300000, 50, true);
    }

    TsFileReader tsFileReader = new TsFileReader(new TsFileSequenceReader(f.getAbsolutePath()));
    for (int i = 0; i < 3; i++) {
      QueryExpression queryExpression =
          QueryExpression.create(
              Collections.singletonList(
                  new Path(deviceId, alignedMeasurementSchemas.get(i).getMeasurementName(), true)),
              null);
      QueryDataSet queryDataSet = tsFileReader.query(queryExpression);

      int cnt = 0;
      while (queryDataSet.hasNext()) {
        cnt++;
        queryDataSet.next();
      }
      if (i < 2) {
        Assert.assertEquals(60, cnt);
      } else {
        Assert.assertEquals(90, cnt);
      }
    }
  }

  /** Write a nonEmpty page and then write an empty page. */
  @Test
  public void writeAlignedTimeseriesWithEmptyPage2() throws IOException, WriteProcessException {
    TSFileDescriptor.getInstance().getConfig().setMaxNumberOfPointsInPage(30);
    try (TsFileWriter tsFileWriter = new TsFileWriter(f)) {
      registerAlignedTimeseries(tsFileWriter);

      List<IMeasurementSchema> writeMeasurementScheams = new ArrayList<>();
      // example1
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(3));
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(2));
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(1));
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(0));
      TsFileGeneratorUtils.writeWithTsRecord(
          tsFileWriter, deviceId, writeMeasurementScheams, 30, 0, 0, true);

      // example2
      writeMeasurementScheams.clear();
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(0));
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(1));
      TsFileGeneratorUtils.writeWithTsRecord(
          tsFileWriter, deviceId, writeMeasurementScheams, 30, 1000, 500, true);
    }

    TsFileReader tsFileReader = new TsFileReader(new TsFileSequenceReader(f.getAbsolutePath()));
    for (int i = 0; i < 3; i++) {
      QueryExpression queryExpression =
          QueryExpression.create(
              Collections.singletonList(
                  new Path(deviceId, alignedMeasurementSchemas.get(i).getMeasurementName(), true)),
              null);
      QueryDataSet queryDataSet = tsFileReader.query(queryExpression);
      int cnt = 0;
      while (queryDataSet.hasNext()) {
        cnt++;
        queryDataSet.next();
      }
      if (i < 2) {
        Assert.assertEquals(60, cnt);
      } else {
        Assert.assertEquals(30, cnt);
      }
    }
  }

  /** Write a nonEmpty page and then write an empty page. */
  @Test
  public void writeAlignedTimeseriesWithEmptyPage3() throws IOException, WriteProcessException {
    try (TsFileWriter tsFileWriter = new TsFileWriter(f)) {
      registerAlignedTimeseries(tsFileWriter);

      List<IMeasurementSchema> writeMeasurementScheams = new ArrayList<>();
      // example1
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(0));
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(1));
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(2));
      writeMeasurementScheams.add(alignedMeasurementSchemas.get(3));

      TsFileIOWriter tsFileIOWriter = tsFileWriter.getIOWriter();
      tsFileIOWriter.startChunkGroup(IDeviceID.Factory.DEFAULT_FACTORY.create(deviceId));

      AlignedChunkWriterImpl alignedChunkWriter =
          new AlignedChunkWriterImpl(writeMeasurementScheams);

      // write one nonEmpty page
      for (long time = 0; time < 30; time++) {
        for (int i = 0; i < 4; i++) {
          alignedChunkWriter.getValueChunkWriterByIndex(i).write(time, time, false);
        }
        alignedChunkWriter.write(time);
      }
      alignedChunkWriter.sealCurrentPage();

      // write a nonEmpty page of s0 and s1, an empty page of s2 and s3
      for (long time = 30; time < 60; time++) {
        for (int i = 0; i < 2; i++) {
          alignedChunkWriter.getValueChunkWriterByIndex(i).write(time, time, false);
        }
      }
      for (int i = 2; i < 4; i++) {
        alignedChunkWriter.getValueChunkWriterByIndex(i).writeEmptyPageToPageBuffer();
      }
      for (long time = 30; time < 60; time++) {
        alignedChunkWriter.write(time);
      }
      alignedChunkWriter.writeToFileWriter(tsFileIOWriter);
      tsFileIOWriter.endChunkGroup();
    }

    // read file
    TsFileReader tsFileReader = new TsFileReader(new TsFileSequenceReader(f.getAbsolutePath()));
    for (int i = 0; i < 3; i++) {
      QueryExpression queryExpression =
          QueryExpression.create(
              Collections.singletonList(
                  new Path(deviceId, alignedMeasurementSchemas.get(i).getMeasurementName(), true)),
              null);
      QueryDataSet queryDataSet = tsFileReader.query(queryExpression);
      int cnt = 0;
      while (queryDataSet.hasNext()) {
        cnt++;
        queryDataSet.next();
      }
      if (i < 2) {
        Assert.assertEquals(60, cnt);
      } else {
        Assert.assertEquals(30, cnt);
      }
    }
  }

  @Test
  public void writeTsFileByFlushingPageDirectly() throws IOException, WriteProcessException {
    TSFileDescriptor.getInstance().getConfig().setMaxNumberOfPointsInPage(30);

    // create a tsfile with four pages in one timeseries
    try (TsFileWriter tsFileWriter = new TsFileWriter(f)) {
      registerTimeseries(tsFileWriter);

      List<IMeasurementSchema> writeMeasurementSchemas = new ArrayList<>();
      writeMeasurementSchemas.add(measurementSchemas.get(0));

      TsFileGeneratorUtils.writeWithTsRecord(
          tsFileWriter, deviceId, writeMeasurementSchemas, 30, 0, 0, false);
      TsFileGeneratorUtils.writeWithTsRecord(
          tsFileWriter, deviceId, writeMeasurementSchemas, 30, 30, 30, false);
      TsFileGeneratorUtils.writeWithTsRecord(
          tsFileWriter, deviceId, writeMeasurementSchemas, 30, 60, 60, false);
      TsFileGeneratorUtils.writeWithTsRecord(
          tsFileWriter, deviceId, writeMeasurementSchemas, 30, 90, 90, false);
    }

    ChunkWriterImpl chunkWriter = new ChunkWriterImpl(measurementSchemas.get(0));

    // rewrite a new tsfile by flushing page directly
    File file = FSFactoryProducer.getFSFactory().getFile("test.tsfile");
    try (TsFileSequenceReader reader = new TsFileSequenceReader(f.getAbsolutePath());
        TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(file)) {
      tsFileIOWriter.startChunkGroup(IDeviceID.Factory.DEFAULT_FACTORY.create(deviceId));
      for (List<ChunkMetadata> chunkMetadatas :
          reader
              .readChunkMetadataInDevice(IDeviceID.Factory.DEFAULT_FACTORY.create(deviceId))
              .values()) {
        for (ChunkMetadata chunkMetadata : chunkMetadatas) {
          Chunk chunk = reader.readMemChunk(chunkMetadata);
          ByteBuffer chunkDataBuffer = chunk.getData();
          ChunkHeader chunkHeader = chunk.getHeader();
          int pageNum = 0;
          while (chunkDataBuffer.remaining() > 0) {
            // deserialize a PageHeader from chunkDataBuffer
            PageHeader pageHeader;
            if (((byte) (chunkHeader.getChunkType() & 0x3F))
                == MetaMarker.ONLY_ONE_PAGE_CHUNK_HEADER) {
              pageHeader = PageHeader.deserializeFrom(chunkDataBuffer, chunk.getChunkStatistic());
            } else {
              pageHeader = PageHeader.deserializeFrom(chunkDataBuffer, chunkHeader.getDataType());
            }

            // read compressed page data
            int compressedPageBodyLength = pageHeader.getCompressedSize();
            byte[] compressedPageBody = new byte[compressedPageBodyLength];
            chunkDataBuffer.get(compressedPageBody);
            chunkWriter.writePageHeaderAndDataIntoBuff(
                ByteBuffer.wrap(compressedPageBody), pageHeader);
            if (++pageNum % 2 == 0) {
              chunkWriter.writeToFileWriter(tsFileIOWriter);
            }
          }
        }
      }
      tsFileIOWriter.endChunkGroup();
      tsFileIOWriter.endFile();

      // read file
      TsFileReader tsFileReader =
          new TsFileReader(new TsFileSequenceReader(file.getAbsolutePath()));

      QueryExpression queryExpression =
          QueryExpression.create(
              Collections.singletonList(
                  new Path(deviceId, measurementSchemas.get(0).getMeasurementName(), true)),
              null);
      QueryDataSet queryDataSet = tsFileReader.query(queryExpression);
      int cnt = 0;
      while (queryDataSet.hasNext()) {
        cnt++;
        // Assert.assertEquals(queryDataSet);
        queryDataSet.next();
      }

      Assert.assertEquals(120, cnt);

    } catch (Throwable throwable) {
      if (file.exists()) {
        file.delete();
      }
      throw throwable;
    }
  }

  @Test
  public void writeTreeTsFileWithUpperCaseColumns() throws IOException, WriteProcessException {
    setEnv(100 * 1024 * 1024, 10 * 1024);
    String d1 = "root.TEST.D1";
    try (TsFileWriter writer = new TsFileWriter(f)) {
      writer.registerTimeseries(d1, new MeasurementSchema("MEASUREMENT1", TSDataType.BOOLEAN));
      TSRecord record = new TSRecord(d1, 1);
      record.addPoint("MEASUREMENT1", true);
      writer.writeRecord(record);
    }
    try (TsFileSequenceReader reader = new TsFileSequenceReader(f.getPath())) {
      Assert.assertTrue(
          reader.getAllDevices().contains(IDeviceID.Factory.DEFAULT_FACTORY.create(d1)));
      Assert.assertTrue(reader.getAllMeasurements().containsKey("MEASUREMENT1"));
    }

    Tablet tablet =
        new Tablet(d1, Arrays.asList(new MeasurementSchema("MEASUREMENT1", TSDataType.BOOLEAN)));
    tablet.addTimestamp(0, 0);
    tablet.addValue("MEASUREMENT1", 0, true);
    try (TsFileWriter writer = new TsFileWriter(f)) {
      writer.registerTimeseries(d1, new MeasurementSchema("MEASUREMENT1", TSDataType.BOOLEAN));
      writer.writeTree(tablet);
    }

    try (TsFileSequenceReader reader = new TsFileSequenceReader(f.getPath())) {
      Assert.assertTrue(
          reader.getAllDevices().contains(IDeviceID.Factory.DEFAULT_FACTORY.create(d1)));
      Assert.assertTrue(reader.getAllMeasurements().containsKey("MEASUREMENT1"));
    }
  }

  @Test
  public void writeTableTsFileWithUpperCaseColumns() throws IOException, WriteProcessException {
    setEnv(100 * 1024 * 1024, 10 * 1024);
    Tablet tablet =
        new Tablet(
            "TABLE1",
            Arrays.asList("IdColumn", "MeasurementColumn"),
            Arrays.asList(TSDataType.STRING, TSDataType.BOOLEAN),
            Arrays.asList(Tablet.ColumnCategory.TAG, Tablet.ColumnCategory.FIELD));
    tablet.addTimestamp(0, 0);
    tablet.addValue("IdColumn", 0, "id_field");
    tablet.addValue("MeasurementColumn", 0, true);
    TableSchema tableSchema =
        new TableSchema(
            "Table1",
            Arrays.asList(
                new ColumnSchema("IDCOLUMN", TSDataType.STRING, Tablet.ColumnCategory.TAG),
                new ColumnSchema(
                    "MeasurementColumn", TSDataType.BOOLEAN, Tablet.ColumnCategory.FIELD)));
    Assert.assertEquals("table1", tableSchema.getTableName());
    try (TsFileWriter writer = new TsFileWriter(f)) {
      writer.registerTableSchema(tableSchema);
      writer.writeTable(tablet);
    }
    try (TsFileSequenceReader reader = new TsFileSequenceReader(f.getPath())) {
      Map<String, TableSchema> tableSchemaMap = reader.readFileMetadata().getTableSchemaMap();
      TableSchema tableSchemaInTsFile = tableSchemaMap.get("table1");
      Assert.assertNotNull(tableSchemaInTsFile);
      for (IMeasurementSchema columnSchema : tableSchemaInTsFile.getColumnSchemas()) {
        Assert.assertEquals(
            columnSchema.getMeasurementName().toLowerCase(), columnSchema.getMeasurementName());
      }
      Assert.assertTrue(reader.getAllMeasurements().containsKey("measurementcolumn"));
    }
  }

  @Test
  public void writeAllNullValueTablet()
      throws IOException, WriteProcessException, ReadProcessException {
    setEnv(100 * 1024 * 1024, 10 * 1024);
    Tablet tablet =
        new Tablet(
            "table1",
            Arrays.asList("tag1", "field1"),
            Arrays.asList(TSDataType.STRING, TSDataType.BOOLEAN),
            Arrays.asList(Tablet.ColumnCategory.TAG, Tablet.ColumnCategory.FIELD));
    tablet.addTimestamp(0, 0);
    tablet.addTimestamp(1, 1);
    TableSchema tableSchema =
        new TableSchema(
            "Table1",
            Arrays.asList(
                new ColumnSchema("tag1", TSDataType.STRING, Tablet.ColumnCategory.TAG),
                new ColumnSchema("field1", TSDataType.BOOLEAN, Tablet.ColumnCategory.FIELD)));
    Assert.assertEquals("table1", tableSchema.getTableName());
    try (ITsFileWriter writer =
        new TsFileWriterBuilder().file(f).tableSchema(tableSchema).build()) {
      writer.write(tablet);
    }
    try (ITsFileReader reader = new TsFileReaderBuilder().file(f).build();
        ResultSet resultSet =
            reader.query(
                "table1", Arrays.asList("tag1", "field1"), Long.MIN_VALUE, Long.MAX_VALUE)) {
      Assert.assertTrue(resultSet.next());
      Assert.assertEquals(0, resultSet.getLong(1));
      Assert.assertTrue(resultSet.isNull(2));
      Assert.assertTrue(resultSet.isNull(3));
      Assert.assertTrue(resultSet.next());
      Assert.assertEquals(1, resultSet.getLong(1));
      Assert.assertTrue(resultSet.isNull(2));
      Assert.assertTrue(resultSet.isNull(3));
    }
  }
}
