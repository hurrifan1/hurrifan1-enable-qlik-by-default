/*
 * Copyright (C) 2017-2019 Dremio Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dremio.exec.store.parquet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.ValueVector;

import com.dremio.common.exceptions.UserException;
import com.dremio.common.types.TypeProtos;
import com.dremio.exec.exception.SchemaChangeException;
import com.dremio.exec.proto.UserBitShared;
import com.dremio.exec.proto.UserBitShared.QueryResult.QueryState;
import com.dremio.exec.record.RecordBatchLoader;
import com.dremio.exec.record.VectorWrapper;
import com.dremio.exec.rpc.ConnectionThrottle;
import com.dremio.exec.rpc.RpcException;
import com.dremio.sabot.rpc.user.QueryDataBatch;
import com.dremio.sabot.rpc.user.UserResultsListener;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.SettableFuture;

public class ParquetResultListener implements UserResultsListener {
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ParquetResultListener.class);

  private final SettableFuture<Void> future = SettableFuture.create();
  private int count = 0;
  private int totalRecords;

  private boolean testValues;
  private final BufferAllocator allocator;

  private int batchCounter = 1;
  private final Map<String, Integer> valuesChecked = new HashMap<>();
  private final ParquetTestProperties props;

  ParquetResultListener(BufferAllocator allocator, ParquetTestProperties props,
      int numberOfTimesRead, boolean testValues) {
    this.allocator = allocator;
    this.props = props;
    this.totalRecords = props.recordsPerRowGroup * props.numberRowGroups * numberOfTimesRead;
    this.testValues = testValues;
  }

  @Override
  public void submissionFailed(UserException ex) {
    logger.error("Submission failed.", ex);
    future.setException(ex);
  }

  @Override
  public void queryCompleted(QueryState state) {
    checkLastChunk();
  }

  private <T> void assertField(ValueVector valueVector, int index,
      TypeProtos.MinorType expectedMinorType, Object value, String name) {
    assertField(valueVector, index, expectedMinorType, value, name, 0);
  }

  @SuppressWarnings("unchecked")
  private <T> void assertField(ValueVector valueVector, int index,
      TypeProtos.MinorType expectedMinorType, T value, String name, int parentFieldId) {

    if (expectedMinorType == TypeProtos.MinorType.STRUCT) {
      return;
    }

    final T val;
    try {
      val = (T) valueVector.getObject(index);
    } catch (Throwable ex) {
      throw ex;
    }

    if (val instanceof byte[]) {
      assertTrue(Arrays.equals((byte[]) value, (byte[]) val));
    } else {
      assertEquals(value, val);
    }
  }

  @Override
  public synchronized void dataArrived(QueryDataBatch result, ConnectionThrottle throttle) {
    logger.debug("result arrived in test batch listener.");
    int columnValCounter = 0;
    FieldInfo currentField;
    count += result.getHeader().getRowCount();
    boolean schemaChanged = false;
    final RecordBatchLoader batchLoader = new RecordBatchLoader(allocator);
    try {
      schemaChanged = batchLoader.load(result.getHeader().getDef(), result.getData());
      // TODO:  Clean:  DRILL-2933:  That load(...) no longer throws
      // SchemaChangeException, so check/clean catch clause below.
    } catch (SchemaChangeException e) {
      throw new RuntimeException(e);
    }

    // used to make sure each vector in the batch has the same number of records
    int valueCount = batchLoader.getRecordCount();

    // print headers.
    if (schemaChanged) {
      // do not believe any change is needed for when the schema changes, with the current mock scan use case
    }

    for (final VectorWrapper vw : batchLoader) {
      final ValueVector vv = vw.getValueVector();
      currentField = props.fields.get(vv.getField().getName());
      if (!valuesChecked.containsKey(vv.getField().getName())) {
        valuesChecked.put(vv.getField().getName(), 0);
        columnValCounter = 0;
      } else {
        columnValCounter = valuesChecked.get(vv.getField().getName());
      }
      printColumnMajor(vv);

      if (testValues) {
        for (int j = 0; j < vv.getValueCount(); j++) {
          assertField(vv, j, currentField.type,
              currentField.values[columnValCounter % 3], currentField.name + "/");
          columnValCounter++;
        }
      } else {
        columnValCounter += vv.getValueCount();
      }

      valuesChecked.remove(vv.getField().getName());
      assertEquals("Mismatched value count for vectors in the same batch.", valueCount, vv.getValueCount());
      valuesChecked.put(vv.getField().getName(), columnValCounter);
    }

    if (ParquetRecordReaderTest.VERBOSE_DEBUG){
      printRowMajor(batchLoader);
    }
    batchCounter++;

    batchLoader.clear();
    result.release();
  }

  @SuppressWarnings("AssertionFailureIgnored")
  private void checkLastChunk() {
    int recordsInBatch = -1;
    // ensure the right number of columns was returned, especially important to ensure selective column read is working
    if (testValues) {
      assertEquals( "Unexpected number of output columns from parquet scan.", props.fields.keySet().size(), valuesChecked.keySet().size() );
    }
    assertTrue(valuesChecked.keySet().size() > 0);
    for (final String s : valuesChecked.keySet()) {
      try {
        int recordCount = valuesChecked.get(s).intValue();
        if (recordsInBatch == -1 ){
          recordsInBatch = recordCount;
        } else {
          assertEquals("Mismatched record counts in vectors.", recordsInBatch, recordCount);
        }
        assertEquals("Record count incorrect for column: " + s, totalRecords, recordCount);
      } catch (AssertionError e) {
        submissionFailed(UserException.systemError(e).build(logger));
      }
    }
    future.set(null);
  }

  public void printColumnMajor(ValueVector vv) {
    if (ParquetRecordReaderTest.VERBOSE_DEBUG){
      System.out.println("\n" + vv.getField().getName());
    }
    for (int j = 0; j < vv.getValueCount(); j++) {
      if (ParquetRecordReaderTest.VERBOSE_DEBUG){
        Object o = vv.getObject(j);
        if (o instanceof byte[]) {
          try {
            o = new String((byte[])o, "UTF-8");
          } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
          }
        }
        System.out.print(Strings.padStart(o + "", 20, ' ') + " ");
        System.out.print(", " + (j % 25 == 0 ? "\n batch:" + batchCounter + " v:" + j + " - " : ""));
      }
    }
    if (ParquetRecordReaderTest.VERBOSE_DEBUG) {
      System.out.println("\n" + vv.getValueCount());
    }
  }

  public void printRowMajor(RecordBatchLoader batchLoader) {
    for (int i = 0; i < batchLoader.getRecordCount(); i++) {
      if (i % 50 == 0) {
        System.out.println();
        for (VectorWrapper vw : batchLoader) {
          ValueVector v = vw.getValueVector();
          System.out.print(Strings.padStart(v.getField().getName(), 20, ' ') + " ");

        }
        System.out.println();
        System.out.println();
      }

      for (final VectorWrapper vw : batchLoader) {
        final ValueVector v = vw.getValueVector();
        Object o = v.getObject(i);
        if (o instanceof byte[]) {
          try {
            // TODO - in the dictionary read error test there is some data that does not look correct
            // the output of our reader matches the values of the parquet-mr cat/head tools (no full comparison was made,
            // but from a quick check of a few values it looked consistent
            // this might have gotten corrupted by pig somehow, or maybe this is just how the data is supposed ot look
            // TODO - check this!!
//              for (int k = 0; k < ((byte[])o).length; k++ ) {
//                // check that the value at each position is a valid single character ascii value.
//
//                if (((byte[])o)[k] > 128) {
//                  System.out.println("batch: " + batchCounter + " record: " + recordCount);
//                }
//              }
            o = new String((byte[])o, "UTF-8");
          } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
          }
        }
        System.out.print(Strings.padStart(o + "", 20, ' ') + " ");
      }
      System.out.println();
    }
  }

  public void getResults() throws RpcException {
    try {
      future.get();
    } catch(Throwable t) {
      throw RpcException.mapException(t);
    }
  }

  @Override
  public void queryIdArrived(UserBitShared.QueryId queryId) {
  }
}
