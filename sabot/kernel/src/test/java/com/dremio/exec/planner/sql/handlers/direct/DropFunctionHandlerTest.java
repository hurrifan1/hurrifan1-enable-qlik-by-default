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
package com.dremio.exec.planner.sql.handlers.direct;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlLiteral;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.junit.Assert;
import org.junit.Test;

import com.dremio.common.exceptions.UserException;
import com.dremio.exec.catalog.Catalog;
import com.dremio.exec.ops.QueryContext;
import com.dremio.exec.planner.sql.parser.SqlDropFunction;
import com.dremio.exec.store.sys.udf.UserDefinedFunction;
import com.dremio.service.namespace.NamespaceKey;
import com.google.common.collect.ImmutableList;

public class DropFunctionHandlerTest {

  @Test public void testDropFunction () throws Exception {
    //SETUP
    Subject subject = new Subject()
      .withUserDefinedFunction("foo");

    String sql = "DROP FUNCTION foo";
    SqlDropFunction sqlNode = createDropFunction("foo", false);


    //TEST
    List<SimpleCommandResult> result = subject.dropFunctionHandler.toResult(sql, sqlNode);

    //ASSERT
    verify(subject.catalog, times(1))
      .dropFunction(eq(new NamespaceKey("foo")));
    Assert.assertEquals(ImmutableList.of(
      new SimpleCommandResult(true, "Function, foo, is dropped.")),
      result);
  }

  @Test public void testDropFunctionIfExists () throws Exception {
    //SETUP
    Subject subject = new Subject()
      .withUserDefinedFunction("foo");

    String sql = "DROP FUNCTION IF EXISTS foo";
    SqlDropFunction sqlNode = createDropFunction("foo", true);

    //TEST
    List<SimpleCommandResult> result = subject.dropFunctionHandler.toResult(sql, sqlNode);

    //ASSERT
    verify(subject.catalog, times(1))
      .dropFunction(eq(new NamespaceKey("foo")));
    Assert.assertEquals(ImmutableList.of(
        new SimpleCommandResult(true, "Function, foo, is dropped.")),
      result);
  }

  @Test public void testDropFunctionIfExistsMissing () throws Exception {
    //SETUP
    Subject subject = new Subject();

    String sql = "DROP FUNCTION IF EXISTS foo";
    SqlDropFunction sqlNode = createDropFunction("foo", true);

    //TEST
    List<SimpleCommandResult> result = subject.dropFunctionHandler.toResult(sql, sqlNode);

    //ASSERT
    verify(subject.catalog, never())
      .dropFunction(eq(new NamespaceKey("foo")));
    Assert.assertEquals(ImmutableList.of(
        new SimpleCommandResult(true, "Function, foo, does not exists.")),
      result);
  }

  @Test public void testDropFunctionMissing () throws Exception {
    //SETUP
    Subject subject = new Subject();

    String sql = "DROP FUNCTION foo";
    SqlDropFunction sqlNode = createDropFunction("foo", false);

    //TEST
    try {
      subject.dropFunctionHandler.toResult(sql, sqlNode);
      Assert.fail();
    } catch (UserException userException) {
      Assert.assertEquals("Function, foo, does not exists.", userException.getMessage());
    }

    //ASSERT
    verify(subject.catalog, never())
      .dropFunction(eq(new NamespaceKey("foo")));
  }

  private SqlDropFunction createDropFunction(String name, boolean ifExists) {
    return new SqlDropFunction(SqlParserPos.ZERO,
      SqlLiteral.createBoolean(ifExists, SqlParserPos.ZERO),
      new SqlIdentifier(name, SqlParserPos.ZERO));
  }

}

class Subject {
  final QueryContext context = mock(QueryContext.class);
  final Catalog catalog = mock(Catalog.class);
  final DropFunctionHandler dropFunctionHandler = new DropFunctionHandler(context);

  public Subject() {
    when(context.getCatalog()).thenReturn(catalog);
    when(catalog.resolveSingle(any())).thenReturn(new NamespaceKey("foo"));
  }

  public Subject withUserDefinedFunction(String key) throws IOException {
    when(catalog.getFunction(new NamespaceKey(key)))
      .thenReturn(mock(UserDefinedFunction.class));
    return this;
  }
}
