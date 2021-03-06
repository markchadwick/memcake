/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.skife.memcake;

import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import net.jcip.annotations.NotThreadSafe;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skife.memcake.connection.Counter;
import org.skife.memcake.connection.StatusException;
import org.skife.memcake.connection.Value;
import org.skife.memcake.connection.Version;
import org.skife.memcake.testing.Entry;
import org.skife.memcake.testing.MemcachedRule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@NotThreadSafe
@RunWith(JUnitQuickcheck.class)
public class MemcakeTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    @ClassRule
    public static final MemcachedRule memcached = new MemcachedRule();

    private Memcake mc;

    @Before
    public void setUp() throws Exception {
        this.mc = Memcake.create(memcached.getAddress(), 1000, TIMEOUT);
        mc.flush().execute().get();
    }

    @After
    public void tearDown() throws Exception {
        mc.close();
    }

    @Test
    public void testSetWithStrings() throws Exception {
        CompletableFuture<Version> fs = mc.set("hello", "world")
                                          .expires(0)
                                          .flags(1)
                                          .timeout(Duration.ofHours(1))
                                          .cas(Version.NONE)
                                          .execute();
        Version ver = fs.get();

        Optional<Value> val = mc.get("hello").execute().get();
        assertThat(val).isPresent();
        val.ifPresent((v) -> {
            assertThat(v.getValue()).isEqualTo("world".getBytes(StandardCharsets.UTF_8));
            assertThat(v.getFlags()).isEqualTo(1);
            assertThat(v.getVersion()).isEqualTo(ver);
        });
    }

    @Test
    public void testGetWithStrings() throws Exception {
        CompletableFuture<Version> fs = mc.set("hello", "world")
                                          .expires(0)
                                          .flags(1)
                                          .timeout(Duration.ofHours(1))
                                          .cas(Version.NONE)
                                          .execute();
        Version ver = fs.get();

        CompletableFuture<Optional<Value>> rs = mc.get("hello".getBytes(StandardCharsets.UTF_8))
                                                  .timeout(Duration.ofHours(1))
                                                  .execute();
        Optional<Value> ov = rs.get();
        assertThat(ov).isPresent();
        ov.ifPresent((v) -> {
            assertThat(v.getVersion()).isEqualTo(ver);
            assertThat(v.getFlags()).isEqualTo(1);
            assertThat(v.getValue()).isEqualTo("world".getBytes(StandardCharsets.UTF_8));
        });
    }

    @Test
    public void testGetWithMappedResult() throws Exception {
        mc.set("hello", "world").execute().get();
        CompletableFuture<Optional<String>> rs = mc.get("hello")
                                                   .execute()
                                                   .thenApply(ov -> ov.map(v -> new String(v.getValue(),
                                                                                           StandardCharsets.UTF_8)));

        Optional<String> ov = rs.get();
        assertThat(ov).contains("world");
    }

    @Property
    public void checkGetKCorrect(Entry e) throws Exception {
        mc.set(e.key(), e.value()).execute().get();
        CompletableFuture<Optional<Value>> cf = mc.getk(e.key())
                                                  .timeout(TIMEOUT)
                                                  .execute();
        Optional<Value> ov = cf.get();
        assertThat(ov).isPresent();
        ov.ifPresent((v) -> {
            assertThat(v.getKey()).isPresent();
            v.getKey().ifPresent((k) -> {
                assertThat(k).isEqualTo(e.key());
            });
            assertThat(v.getValue()).isEqualTo(e.value());
        });
    }

    @Property
    public void checkAddSuccess(Entry e, int flags) throws ExecutionException, InterruptedException {
        CompletableFuture<Version> f = mc.add(e.key(), e.value())
                                         .flags(flags)
                                         .execute();
        Version vr = f.get();

        Value v = mc.get(e.key()).execute().get().get();
        assertThat(v.getValue()).isEqualTo(e.value());
        assertThat(v.getFlags()).isEqualTo(flags);
        assertThat(v.getVersion()).isEqualTo(vr);
    }

    @Property
    public void checkAddFailure(Entry e, int flags) throws ExecutionException, InterruptedException {
        mc.set(e.key(), e.value()).execute().get();

        CompletableFuture<Version> f = mc.add(e.key(), new byte[]{1, 2, 3})
                                         .flags(flags)
                                         .expires(8765)
                                         .execute();
        assertThatThrownBy(f::get).hasCauseInstanceOf(StatusException.class);
    }

    @Property
    public void checkAddQuiet(Entry e) throws Exception {
        mc.addq(e.key(), e.value())
          .timeout(TIMEOUT)
          .execute();
        Value v = mc.get(e.key()).execute().get().get();
        assertThat(v.getValue()).isEqualTo(e.value());
    }

    @Property
    public void checkAddQuietFails(Entry e) throws Exception {
        mc.set(e.key(), e.value()).execute().get();

        CompletableFuture<Void> f = mc.addq(e.key(), new byte[]{1, 3, 3})
                                      .timeout(TIMEOUT)
                                      .execute();

        Value v = mc.get(e.key()).execute().get().get();

        assertThat(v.getValue()).isEqualTo(e.value());
        assertThatThrownBy(f::get).hasCauseInstanceOf(StatusException.class);
    }

    @Test
    public void testAppend() throws Exception {
        Version ver = mc.set("hello", "wo").execute().get();
        mc.append("hello", "rld")
          .cas(ver)
          .timeout(Duration.ofSeconds(17))
          .execute();
        Optional<Value> ov = mc.get("hello").execute().get();
        assertThat(ov).isPresent();
        ov.ifPresent((v) -> {
            assertThat(v.getValue()).isEqualTo("world".getBytes(StandardCharsets.UTF_8));
        });
    }

    @Test
    public void testAppendQ() throws Exception {
        Version ver = mc.set("hello", "wo").execute().get();
        mc.appendq("hello", "rld")
          .cas(ver)
          .timeout(Duration.ofSeconds(17))
          .execute();
        Optional<Value> ov = mc.get("hello").execute().get();
        assertThat(ov).isPresent();
        ov.ifPresent((v) -> {
            assertThat(v.getValue()).isEqualTo("world".getBytes(StandardCharsets.UTF_8));
        });
    }

    @Test
    public void testDecrementSettingInitial() throws Exception {
        CompletableFuture<Counter> cf = mc.decrement("hello", 1)
                                          .initialValue(3)
                                          .execute();
        Counter c = cf.get();
        assertThat(c.getValue()).isEqualTo(3);
    }

    @Test
    public void testDecrementFailOnNotFoundByDefault() throws Exception {
        CompletableFuture<Counter> cf = mc.decrement("hello", 1)
                                          .execute();
        assertThatThrownBy(cf::get).hasCauseInstanceOf(StatusException.class);
    }

    @Test
    public void testDecrementAfterSet() throws Exception {
        mc.set("hello", "3").execute();
        Counter c = mc.decrement("hello", 1)
                      .execute()
                      .get();
        assertThat(c.getValue()).isEqualTo(2);
    }

    @Test
    public void testDecrementQSettingInitial() throws Exception {
        mc.decrementq("hello", 1)
          .initialValue(3)
          .execute();
        Value v = mc.get("hello").execute().get().get();
        assertThat(v.getValue()).isEqualTo("3".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testDecrementQFailOnNotFoundByDefault() throws Exception {
        CompletableFuture<Void> cf = mc.decrementq("hello", 1)
                                       .execute();
        assertThat(mc.get("hello").execute().get()).isEmpty();
        assertThatThrownBy(cf::get).hasCauseInstanceOf(StatusException.class);
    }

    @Test
    public void testDecrementQAfterSet() throws Exception {
        mc.set("hello", "3").execute();
        mc.decrementq("hello", 1)
          .execute();
        Value v = mc.get("hello").execute().get().get();
        assertThat(v.getValue()).isEqualTo("2".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testDeleteOp() throws Exception {
        mc.addq("jello", "mold").execute();
        mc.delete("jello").execute();
        assertThat(mc.get("jello").execute().get()).isEmpty();
    }

    @Test
    public void testDeleteOpMissing() throws Exception {
        CompletableFuture<Void> c = mc.delete("jello").execute();
        assertThatThrownBy(c::get).hasCauseInstanceOf(StatusException.class);
    }

    @Test
    public void testDeleteQOp() throws Exception {
        mc.addq("jello", "mold").execute();
        mc.deleteq("jello").execute();
        assertThat(mc.get("jello").execute().get()).isEmpty();
    }

    @Test
    public void testDeleteQOpMissing() throws Exception {
        CompletableFuture<Void> c = mc.deleteq("jello").execute();
        assertThat(mc.get("jello").execute().get()).isEmpty();
        assertThatThrownBy(c::get).hasCauseInstanceOf(StatusException.class);
    }

    @Test
    public void testFlush() throws Exception {
        mc.set("hello", "world").execute();
        mc.flush().execute();
        assertThat(mc.get("hello").execute().get()).isEmpty();
    }

    @Test
    public void testFlushQ() throws Exception {
        mc.set("hello", "world").execute();
        mc.flushq().execute();
        assertThat(mc.get("hello").execute().get()).isEmpty();
    }

    @Test
    public void testGetkq() throws Exception {
        mc.set("hello", "world").execute();
        Optional<Value> ov = mc.getkq("hello")
                               .execute()
                               .get();
        assertThat(ov).isPresent();
        ov.ifPresent((v) -> {
            assertThat(v.getKey().get()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
            assertThat(v.getValue()).isEqualTo("world".getBytes(StandardCharsets.UTF_8));
        });
    }

    @Test
    public void testGetKqMissing() throws Exception {
        CompletableFuture<Optional<Value>> f = mc.getkq("hello").execute();
        mc.add("woof", "meow").execute().get();
        assertThat(f).isCompletedWithValue(Optional.empty());
    }

    @Test
    public void testGetq() throws Exception {
        mc.set("hello", "world").execute();
        Optional<Value> ov = mc.getq("hello")
                               .execute()
                               .get();
        assertThat(ov).isPresent();
        ov.ifPresent((v) -> {
            assertThat(v.getKey()).isEmpty();
            assertThat(v.getValue()).isEqualTo("world".getBytes(StandardCharsets.UTF_8));
        });
    }

    @Test
    public void testGetqMissing() throws Exception {
        CompletableFuture<Optional<Value>> f = mc.getq("hello").execute();
        mc.add("woof", "meow").execute().get();
        assertThat(f).isCompletedWithValue(Optional.empty());
    }


    @Test
    public void testIncrementSetInitial() throws Exception {
        CompletableFuture<Counter> cf = mc.increment("hello", 1)
                                          .initialValue(3)
                                          .execute();
        Counter c = cf.get();
        assertThat(c.getValue()).isEqualTo(3);
    }

    @Test
    public void testIncrementFailOnNotFoundByDefault() throws Exception {
        CompletableFuture<Counter> cf = mc.increment("hello", 1)
                                          .execute();
        assertThatThrownBy(cf::get).hasCauseInstanceOf(StatusException.class);
    }

    @Test
    public void testIncrementAfterSet() throws Exception {
        mc.set("hello", "3").execute();
        Counter c = mc.increment("hello", 1)
                      .execute()
                      .get();
        assertThat(c.getValue()).isEqualTo(4);
    }

    @Test
    public void testIncrementQSettingInitial() throws Exception {
        mc.incrementq("hello", 1)
          .initialValue(3)
          .execute();
        Value v = mc.get("hello").execute().get().get();
        assertThat(v.getValue()).isEqualTo("3".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testIncrementQFailOnNotFoundByDefault() throws Exception {
        CompletableFuture<Void> cf = mc.incrementq("hello", 1)
                                       .execute();
        assertThat(mc.get("hello").execute().get()).isEmpty();
        assertThatThrownBy(cf::get).hasCauseInstanceOf(StatusException.class);
    }

    @Test
    public void testIncrementQAfterSet() throws Exception {
        mc.set("hello", "3").execute();
        mc.incrementq("hello", 1)
          .execute();
        Value v = mc.get("hello").execute().get().get();
        assertThat(v.getValue()).isEqualTo("4".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testNoOp() throws Exception {
        CompletableFuture<Void> a = mc.addq("joke", "haha").execute();
        CompletableFuture<Void> f = mc.noop().execute();
        f.get();

        // the noop forces completion of previous quiet commands
        assertThat(a).isCompleted();
    }

    @Test
    public void testPrepend() throws Exception {
        mc.add("hello", "rld").execute();
        Version ver = mc.prepend("hello", "wo").execute().get();
        Optional<Value> ov = mc.get("hello").execute().get();
        assertThat(ov).isPresent();
        ov.ifPresent((v) -> {
            assertThat(v.getVersion()).isEqualTo(ver);
            assertThat(v.getValue()).isEqualTo("world".getBytes(StandardCharsets.UTF_8));
        });
    }

    @Test
    public void testPrependQ() throws Exception {
        Version ver = mc.add("hello", "rld").execute().get();
        mc.prependq("hello", "wo").execute();
        Optional<Value> ov = mc.get("hello").execute().get();
        assertThat(ov).isPresent();
        ov.ifPresent((v) -> {
            assertThat(v.getVersion()).isNotEqualTo(ver);
            assertThat(v.getValue()).isEqualTo("world".getBytes(StandardCharsets.UTF_8));
        });
    }

    @Test
    public void testReplaceSuccess() throws Exception {
        Version v1 = mc.add("hello", "world").execute().get();
        Version v2 = mc.replace("hello", "le monde").execute().get();
        Optional<Value> ov = mc.get("hello").execute().get();
        assertThat(ov).isPresent();
        ov.ifPresent((v) -> {
            assertThat(v.getVersion()).isEqualTo(v2);
            assertThat(v.getValue()).isEqualTo("le monde".getBytes(StandardCharsets.UTF_8));
        });
    }

    @Test
    public void testReplaceFail() throws Exception {
        CompletableFuture<Version> cf = mc.replace("hello", "le monde").execute();
        assertThatThrownBy(cf::get).hasCauseInstanceOf(StatusException.class);
    }

    @Test
    public void testReplaceQSuccess() throws Exception {
        mc.add("hello", "world").execute().get();
        mc.replaceq("hello", "le monde").execute();
        Optional<Value> ov = mc.get("hello").execute().get();
        assertThat(ov).isPresent();
        ov.ifPresent((v) -> {
            assertThat(v.getValue()).isEqualTo("le monde".getBytes(StandardCharsets.UTF_8));
        });
    }

    @Test
    public void testReplaceQFail() throws Exception {
        CompletableFuture<Void> cf = mc.replaceq("hello", "le monde").execute();
        mc.noop().execute().get();
        assertThatThrownBy(cf::get).hasCauseInstanceOf(StatusException.class);
    }

    @Test
    public void testStat() throws Exception {
        CompletableFuture<Map<String, String>> f = mc.stat().execute();
        Map<String, String> stats = f.get();
        assertThat(stats).containsKey("pid")
                         .containsKey("total_items");
    }

    @Test
    public void testStatOnlyOne() throws Exception {
        mc.set("a", "1").execute();
        mc.set("b", "1").execute();
        mc.set("c", "1").execute();

        Map<String, String> stats = mc.stat().type("items").execute().get();
        assertThat(stats).containsEntry("items:1:number", "3");
    }

    @Test
    public void testVersion() throws Exception {
        String version = mc.version().execute().get();
        assertThat(version).isNotEmpty();
    }

    @Test
    public void testReconnect() throws Throwable {
        MemcachedRule mr = new MemcachedRule();
        mr.before();
        Memcake mk = Memcake.create(mr.getAddress(), 1000, TIMEOUT);
        mk.noop().execute().get();
        mr.stop();

        assertThatThrownBy(() -> mk.noop().execute().get()).hasRootCauseInstanceOf(IOException.class);

        mr.start();

        mk.noop().execute().get();
    }
}
