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
package com.dremio.exec.store.easy.json;

import java.io.IOException;
import java.io.InputStream;

import org.apache.arrow.vector.complex.writer.BaseWriter;
import org.apache.calcite.util.Pair;

import com.dremio.common.exceptions.UserException;
import com.fasterxml.jackson.databind.JsonNode;

public interface JsonProcessor {

  enum ReadState {
    END_OF_STREAM,
    WRITE_SUCCEED
  }

  ReadState write(BaseWriter.ComplexWriter writer) throws IOException;

  void setSource(InputStream is) throws IOException;
  void setSource(JsonNode node);

  void ensureAtLeastOneField(BaseWriter.ComplexWriter writer);

  UserException.Builder getExceptionWithContext(UserException.Builder exceptionBuilder, String field);

  UserException.Builder getExceptionWithContext(Throwable exception, String field);

  /**
   * Reset the data size counter.
   */
  void resetDataSizeCounter();

  /**
   * Get the approximate size of data read since the start or last {@link #resetDataSizeCounter()} (in bytes).
   * @return
   */
  long getDataSizeCounter();

  Pair<String, Long> getScrollAndTotalSizeThenSeekToHits() throws IOException;
}
