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
package org.apache.tsfile.utils;

import org.apache.tsfile.common.conf.TSFileConfig;
import org.apache.tsfile.common.conf.TSFileDescriptor;
import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.exception.write.WriteProcessException;
import org.apache.tsfile.file.metadata.IDeviceID.Factory;
import org.apache.tsfile.file.metadata.enums.TSEncoding;
import org.apache.tsfile.read.common.Path;
import org.apache.tsfile.write.TsFileWriter;
import org.apache.tsfile.write.record.TSRecord;
import org.apache.tsfile.write.schema.IMeasurementSchema;
import org.apache.tsfile.write.schema.MeasurementSchema;
import org.apache.tsfile.write.schema.Schema;

import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class FileGenerator {

  private static final Logger LOG = LoggerFactory.getLogger(FileGenerator.class);
  public static String outputDataFile =
      TsFileGeneratorForTest.getTestTsFilePath("root.sg1", 0, 0, 0);
  public static Schema schema;
  private static int ROW_COUNT = 1000;
  private static TsFileWriter innerWriter;
  private static String inputDataFile;
  private static String errorOutputDataFile;
  private static final TSFileConfig config = TSFileDescriptor.getInstance().getConfig();

  public static void generateFile(int rowCount, int maxNumberOfPointsInPage) throws IOException {
    ROW_COUNT = rowCount;
    int oldMaxNumberOfPointsInPage = config.getMaxNumberOfPointsInPage();
    config.setMaxNumberOfPointsInPage(maxNumberOfPointsInPage);

    prepare();
    write();
    config.setMaxNumberOfPointsInPage(oldMaxNumberOfPointsInPage);
  }

  public static void generateFile(int rowCount, int maxNumberOfPointsInPage, String filePath)
      throws IOException {
    ROW_COUNT = rowCount;
    int oldMaxNumberOfPointsInPage = config.getMaxNumberOfPointsInPage();
    config.setMaxNumberOfPointsInPage(maxNumberOfPointsInPage);

    prepare();
    write(filePath);
    config.setMaxNumberOfPointsInPage(oldMaxNumberOfPointsInPage);
  }

  public static void generateFile(int maxNumberOfPointsInPage, int deviceNum, int measurementNum)
      throws IOException {
    ROW_COUNT = 1;
    int oldMaxNumberOfPointsInPage = config.getMaxNumberOfPointsInPage();
    config.setMaxNumberOfPointsInPage(maxNumberOfPointsInPage);

    prepare(deviceNum, measurementNum);
    write();
    config.setMaxNumberOfPointsInPage(oldMaxNumberOfPointsInPage);
  }

  public static void generateFile() throws IOException {
    generateFile(1000, 10);
  }

  public static void prepare() throws IOException {
    File file = new File(outputDataFile);
    if (!file.getParentFile().exists()) {
      Assert.assertTrue(file.getParentFile().mkdirs());
    }
    inputDataFile = TsFileGeneratorForTest.getTestTsFilePath("root.sg1", 0, 0, 1);
    file = new File(inputDataFile);
    if (!file.getParentFile().exists()) {
      Assert.assertTrue(file.getParentFile().mkdirs());
    }
    errorOutputDataFile = TsFileGeneratorForTest.getTestTsFilePath("root.sg1", 0, 0, 2);
    file = new File(errorOutputDataFile);
    if (!file.getParentFile().exists()) {
      Assert.assertTrue(file.getParentFile().mkdirs());
    }
    generateTestSchema();
    generateSampleInputDataFile();
  }

  public static void prepare(int deviceNum, int measurementNum) throws IOException {
    File file = new File(outputDataFile);
    if (!file.getParentFile().exists()) {
      Assert.assertTrue(file.getParentFile().mkdirs());
    }
    inputDataFile = TsFileGeneratorForTest.getTestTsFilePath("root.sg1", 0, 0, 1);
    file = new File(inputDataFile);
    if (!file.getParentFile().exists()) {
      Assert.assertTrue(file.getParentFile().mkdirs());
    }
    errorOutputDataFile = TsFileGeneratorForTest.getTestTsFilePath("root.sg1", 0, 0, 2);
    file = new File(errorOutputDataFile);
    if (!file.getParentFile().exists()) {
      Assert.assertTrue(file.getParentFile().mkdirs());
    }
    generateTestSchema(deviceNum, measurementNum);
    generateSampleInputDataFile(deviceNum, measurementNum);
  }

  public static void after() {
    after(outputDataFile);
  }

  public static void after(String filePath) {
    File file = new File(inputDataFile);
    if (file.exists()) {
      file.delete();
    }
    file = new File(filePath);
    if (file.exists()) {
      file.delete();
    }
    file = new File(errorOutputDataFile);
    if (file.exists()) {
      file.delete();
    }
  }

  private static void generateSampleInputDataFile() throws IOException {
    File file = new File(inputDataFile);
    if (file.exists()) {
      file.delete();
    }
    file.getParentFile().mkdirs();
    FileWriter fw = new FileWriter(file);

    long startTime = 1480562618000L;
    startTime = startTime - startTime % 1000;
    for (int i = 0; i < ROW_COUNT; i++) {
      // write d1
      String d1 = "d1," + (startTime + i) + ",s1," + (i * 10 + 1) + ",s2," + (i * 10 + 2);
      if (i % 20 < 10) {
        // LOG.info("write null to d1:" + (startTime + i));
        d1 = "d1," + (startTime + i) + ",s1,,s2," + (i * 10 + 2);
      }
      if (i % 5 == 0) {
        d1 += ",s3," + (i * 10 + 3);
      }
      if (i % 8 == 0) {
        d1 += ",s4," + "dog" + i;
      }
      if (i % 9 == 0) {
        d1 += ",s5," + "false";
      }
      if (i % 10 == 0) {
        d1 += ",s6," + ((int) (i / 9.0) * 100) / 100.0;
      }
      if (i % 11 == 0) {
        d1 += ",s7," + ((int) (i / 10.0) * 100) / 100.0;
      }
      fw.write(d1 + "\r\n");

      // write d2
      String d2 = "d2," + (startTime + i) + ",s2," + (i * 10 + 2) + ",s3," + (i * 10 + 3);
      if (i % 20 < 5) {
        // LOG.info("write null to d2:" + (startTime + i));
        d2 = "d2," + (startTime + i) + ",s2,,s3," + (i * 10 + 3);
      }
      if (i % 5 == 0) {
        d2 += ",s1," + (i * 10 + 1);
      }
      if (i % 8 == 0) {
        d2 += ",s4," + "dog" + i % 4;
      }
      fw.write(d2 + "\r\n");
    }
    // write error
    String d =
        "d2,3,"
            + (startTime + ROW_COUNT)
            + ",s2,"
            + (ROW_COUNT * 10 + 2)
            + ",s3,"
            + (ROW_COUNT * 10 + 3);
    fw.write(d + "\r\n");
    d = "d2," + (startTime + ROW_COUNT + 1) + ",2,s-1," + (ROW_COUNT * 10 + 2);
    fw.write(d + "\r\n");
    fw.close();
  }

  private static void generateSampleInputDataFile(int deviceNum, int measurementNum)
      throws IOException {
    File file = new File(inputDataFile);
    if (file.exists()) {
      Files.delete(file.toPath());
    }
    if (!file.getParentFile().mkdirs()) {
      LOG.info("Failed to create file folder {}", file.getParentFile());
    }
    FileWriter fw = new FileWriter(file);

    long startTime = 1480562618000L;
    for (int i = 0; i < deviceNum; i++) {
      for (int j = 0; j < measurementNum; j++) {
        String d =
            "d"
                + generateIndexString(i, deviceNum)
                + ","
                + startTime
                + ",s"
                + generateIndexString(j, measurementNum)
                + ","
                + 1;
        fw.write(d + "\r\n");
      }
    }
    fw.close();
  }

  public static void write() throws IOException {
    write(outputDataFile);
  }

  public static void write(String filePath) throws IOException {
    File file = new File(filePath);
    File errorFile = new File(errorOutputDataFile);
    if (file.exists()) {
      file.delete();
    }
    if (errorFile.exists()) {
      errorFile.delete();
    }

    innerWriter = new TsFileWriter(file, schema, config);

    // write
    try {
      writeToTsFile(schema);
    } catch (WriteProcessException e) {
      LOG.warn("Write to tsfile error", e);
    }
    LOG.info("write to file successfully!!");
  }

  private static void generateTestSchema() {
    schema = new Schema();
    List<IMeasurementSchema> schemaList = new ArrayList<>();
    schemaList.add(
        new MeasurementSchema(
            "s1", TSDataType.INT32, TSEncoding.valueOf(config.getValueEncoder())));
    schemaList.add(
        new MeasurementSchema(
            "s2", TSDataType.INT64, TSEncoding.valueOf(config.getValueEncoder())));
    schemaList.add(
        new MeasurementSchema(
            "s3", TSDataType.INT64, TSEncoding.valueOf(config.getValueEncoder())));
    schemaList.add(new MeasurementSchema("s4", TSDataType.TEXT, TSEncoding.PLAIN));
    schemaList.add(new MeasurementSchema("s5", TSDataType.BOOLEAN, TSEncoding.PLAIN));
    schemaList.add(new MeasurementSchema("s6", TSDataType.FLOAT, TSEncoding.RLE));
    schemaList.add(new MeasurementSchema("s7", TSDataType.DOUBLE, TSEncoding.RLE));
    MeasurementGroup measurementGroup = new MeasurementGroup(false, schemaList);
    schema.registerMeasurementGroup(new Path("d1"), measurementGroup);

    schemaList.clear();
    schemaList.add(
        new MeasurementSchema(
            "s1", TSDataType.INT32, TSEncoding.valueOf(config.getValueEncoder())));
    schemaList.add(
        new MeasurementSchema(
            "s2", TSDataType.INT64, TSEncoding.valueOf(config.getValueEncoder())));
    schemaList.add(
        new MeasurementSchema(
            "s3", TSDataType.INT64, TSEncoding.valueOf(config.getValueEncoder())));
    schemaList.add(new MeasurementSchema("s4", TSDataType.TEXT, TSEncoding.PLAIN));
    measurementGroup = new MeasurementGroup(false, schemaList);
    schema.registerMeasurementGroup(new Path("d2"), measurementGroup);
  }

  private static void generateTestSchema(int deviceNum, int measurementNum) {
    schema = new Schema();
    for (int i = 0; i < deviceNum; i++) {
      for (int j = 0; j < measurementNum; j++) {
        schema.registerTimeseries(
            Factory.DEFAULT_FACTORY.create("d" + generateIndexString(i, deviceNum)),
            new MeasurementSchema(
                "s" + generateIndexString(j, measurementNum),
                TSDataType.INT32,
                TSEncoding.valueOf(config.getValueEncoder())));
      }
    }
  }

  private static void writeToTsFile(Schema schema) throws IOException, WriteProcessException {
    Scanner in = getDataFile(inputDataFile);
    long lineCount = 0;
    long startTime = System.currentTimeMillis();
    long endTime = System.currentTimeMillis();
    assert in != null;
    while (in.hasNextLine()) {
      if (lineCount % 1000000 == 0) {
        LOG.info("write line:{},use time:{}s", lineCount, (endTime - startTime) / 1000);
      }
      String str = in.nextLine();
      TSRecord record = RecordUtils.parseSimpleTupleRecord(str, schema);
      innerWriter.writeRecord(record);
      lineCount++;
    }
    endTime = System.currentTimeMillis();
    LOG.info("write line:{},use time:{}s", lineCount, (endTime - startTime) / 1000);
    innerWriter.close();
    in.close();
  }

  private static Scanner getDataFile(String path) {
    File file = new File(path);
    try {
      return new Scanner(file);
    } catch (FileNotFoundException e) {
      LOG.warn("Get data from file {} error, will return null Scanner", path, e);
      return null;
    }
  }

  /**
   * generate curIndex string, use "0" on left to make sure align
   *
   * @param curIndex current index
   * @param maxIndex max index
   * @return curIndex's string
   */
  public static String generateIndexString(int curIndex, int maxIndex) {
    StringBuilder res = new StringBuilder(String.valueOf(curIndex));
    String target = String.valueOf(maxIndex);
    while (res.length() < target.length()) {
      res.insert(0, "0");
    }
    return res.toString();
  }
}
