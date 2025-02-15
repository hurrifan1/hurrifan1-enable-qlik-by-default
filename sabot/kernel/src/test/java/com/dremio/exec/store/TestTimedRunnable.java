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
package com.dremio.exec.store;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.dremio.common.exceptions.UserException;
import com.dremio.common.util.TestTools;
import com.dremio.test.DremioTest;
import com.google.common.collect.Lists;

/**
 * Unit testing for {@link TimedRunnable}.
 */
@org.junit.Ignore("takes way too long...")
public class TestTimedRunnable extends DremioTest {
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TestTimedRunnable.class);

  @Rule
  public final TestRule timeoutRule = TestTools.getTimeoutRule(3, TimeUnit.MINUTES); // 3mins

  private static class TestTask extends TimedRunnable<Void> {
    final long sleepTime; // sleep time in ms

    public TestTask(final long sleepTime) {
      this.sleepTime = sleepTime;
    }

    @Override
    protected Void runInner() throws Exception {
      try {
        Thread.sleep(sleepTime);
      } catch (InterruptedException e) {
        throw e;
      }
      return null;
    }

    @Override
    protected IOException convertToIOException(Exception e) {
      return new IOException("Failure while trying to sleep for sometime", e);
    }
  }

  @Test
  public void withoutAnyTasksTriggeringTimeout() throws Exception {
    List<TimedRunnable<Void>> tasks = Lists.newArrayList();

    for(int i=0; i<100; i++){
      tasks.add(new TestTask(2000));
    }

    TimedRunnable.run("Execution without triggering timeout", logger, tasks, 16);
  }

  @Test
  public void withTasksExceedingTimeout() {
    List<TimedRunnable<Void>> tasks = Lists.newArrayList();

    for (int i = 0; i < 100; i++) {
      if ((i & (i + 1)) == 0) {
        tasks.add(new TestTask(2000));
      } else {
        tasks.add(new TestTask(20000));
      }
    }

    assertThatThrownBy(
      () -> TimedRunnable.run("Execution with some tasks triggering timeout", logger, tasks, 16))
      .isInstanceOf(UserException.class)
      .hasMessageContaining(
        "Waited for 93750ms, but tasks for 'Execution with some tasks triggering timeout' are not "
          +
          "complete. Total runnable size 100, parallelism 16.");
  }

  @Test
  public void withManyTasks() throws Exception {

    List<TimedRunnable<Void>> tasks = Lists.newArrayList();

    for (int i = 0; i < 150000; i++) {
      tasks.add(new TestTask(0));
    }

    TimedRunnable.run("Execution with lots of tasks", logger, tasks, 16);
  }

  @Test
  public void withOverriddenHighTimeout() throws Exception {
    List<TimedRunnable<Void>> tasks = Lists.newArrayList();

    for(int i=0; i<10; i++){
      tasks.add(new TestTask(20_000));
    }

    TimedRunnable.run("Execution without triggering timeout", logger, tasks, 2, 150_000);
  }
}
