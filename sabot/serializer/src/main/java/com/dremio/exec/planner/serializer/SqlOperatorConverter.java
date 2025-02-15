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
package com.dremio.exec.planner.serializer;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dremio.exec.expr.fn.FunctionImplementationRegistry;
import com.dremio.exec.planner.sql.DremioSqlOperatorTable;
import com.dremio.exec.planner.sql.OperatorTable;
import com.dremio.exec.planner.sql.SqlFunctionImpl;
import com.dremio.plan.serialization.PSqlOperator;
import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;

/**
 * Serialize TO <> FROM SqlOperator to Protobuf.
 */
public class SqlOperatorConverter {

  private static final Logger logger = LoggerFactory.getLogger(SqlOperatorConverter.class);

  private static BiMap<String, SyEq<SqlOperator>> SQL_STD_OPERATORS = populate();
  private BiMap<FunctionKey, SyEq<SqlOperator>> operators;

  public SqlOperatorConverter(FunctionImplementationRegistry registry) {
    if (registry == null) {
      operators = ImmutableBiMap.of();
    } else {
      operators = populate(new OperatorTable(registry));
    }
  }

  public SqlOperator fromProto(PSqlOperator o) {
    switch(o.getSqlOperatorTypeCase()) {
    case NAME:
      // DATETIME_MINUS is not available in calcite upstream.
      // It has been removed from dremio/calcite.
      // MINUS_DATE should be used instead.
      String operatorName = o.getName();
      switch (operatorName) {
        case "DATETIME_MINUS":
          operatorName = "MINUS_DATE";
          break;
        default:
          break;
      }
      return SQL_STD_OPERATORS.get(operatorName).value;

    case DNAME:
      return operators.get(new FunctionKey(o.getDname(), o.getClassName(), o.getMinOperands(), o.getMaxOperands())).value;

    case SQLOPERATORTYPE_NOT_SET:
    default:
      throw new UnsupportedOperationException(String.format("Unable to handle operator case %s.", o.getSqlOperatorTypeCase()));
    }
  }

  public PSqlOperator toProto(SqlOperator o) {
    SyEq<SqlOperator> wrapped = new SyEq<>(o);
    String key = SQL_STD_OPERATORS.inverse().get(wrapped);
    if(key != null) {
      return PSqlOperator.newBuilder().setName(key).build();
    }

    final String functionName = o.getName();
    final String className = o.getClass().getName();
    int minOperands = -1;
    int maxOperands = -1;
    if (o.getOperandTypeChecker() != null) {
      minOperands = o.getOperandTypeChecker().getOperandCountRange().getMin();
      maxOperands = o.getOperandTypeChecker().getOperandCountRange().getMax();
    }
    if(operators.get(new FunctionKey(functionName, className, minOperands, maxOperands)) != null) {
      return PSqlOperator.newBuilder()
        .setDname(functionName)
        .setClassName(className)
        .setMinOperands(minOperands)
        .setMaxOperands(maxOperands).build();
    }

    if( !(o instanceof SqlFunctionImpl)) {
      throw new UnsupportedOperationException(String.format("Unable to serialize operator [%s] of type [%s]", o.getClass().getName(), o.getName()));
    }

    throw new UnsupportedOperationException("Unable to support Dremio functions yet.");

  }

  private static BiMap<String, SyEq<SqlOperator>> populate() {
    ImmutableBiMap.Builder<String, SyEq<SqlOperator>> builder = ImmutableBiMap.builder();
    Set<String> names = new HashSet<>();
    Stream.concat(
        Arrays.stream(DremioSqlOperatorTable.class.getDeclaredFields()),
        Arrays.stream(SqlStdOperatorTable.class.getDeclaredFields()))
      .filter(field -> {
        final int modifiers = field.getModifiers();
        final Class<?> type = field.getType();
        return Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers)
            && SqlOperator.class.isAssignableFrom(type);
      })
      .filter(f -> names.add(f.getName())) //remove duplicates
      .forEach(operatorField -> {
      try {

        builder.put(operatorField.getName(), new SyEq<>((SqlOperator) operatorField.get(null)));
      } catch (final IllegalAccessException ex) {
        logger.warn("Unable to retrieve SqlOperator {} from table {}.", operatorField.getName(),
            operatorField.getDeclaringClass(), ex);
      }
    });

    return builder.build();
  }

  private static BiMap<FunctionKey, SyEq<SqlOperator>> populate(final OperatorTable operatorTable) {
    ImmutableBiMap.Builder<FunctionKey, SyEq<SqlOperator>> builder = ImmutableBiMap.builder();
    Set<FunctionKey> keys = new HashSet<>();

    for (SqlOperator operator : operatorTable.getOperatorList()) {
      int minOperands = -1;
      int maxOperands = -1;
      if (operator.getOperandTypeChecker() != null) {
        minOperands = operator.getOperandTypeChecker().getOperandCountRange().getMin();
        maxOperands = operator.getOperandTypeChecker().getOperandCountRange().getMax();
      }
      final FunctionKey key = new FunctionKey(operator.getName(), operator.getClass().getName(), minOperands, maxOperands);
      keys.add(key);
      builder.put(key, new SyEq<>(operator));
    }

    return builder.build();
  }

  /**
   * FunctionKey
   */
  private static class FunctionKey {
    private String name;
    private String className;
    private int min;
    private int max;

    public FunctionKey(String name, String className, int min, int max) {
      this.name = name;
      this.className = className;
      this.min = min;
      this.max = max;
    }

    @Override
    public boolean equals(Object obj) {
      if (! (obj instanceof FunctionKey) ) {
        return false;
      }

      FunctionKey that = (FunctionKey) obj;
      return this.name.equals(that.name)
        && this.className.equals(that.className)
        && this.min == that.min
        && this.max == that.max;
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, className, min, max);
    }
  }

  /**
   * A wrapper around a class that uses the object reference for hashing and checking for equality.
   * @param <T>
   */
  private static class SyEq<T> {
    private T value;

    public SyEq(T value) {
      super();
      this.value = Preconditions.checkNotNull(value);
    }

    @Override
    public boolean equals(Object obj) {
      if (! (obj instanceof SyEq) ) {
        return false;
      }

      return value == ((SyEq<?>)obj).value;
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }
  }
}
