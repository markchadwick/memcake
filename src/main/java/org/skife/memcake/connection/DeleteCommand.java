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
package org.skife.memcake.connection;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

class DeleteCommand extends Command {

    private final CompletableFuture<Void> result;
    private final byte[] key;
    private final Version cas;

    DeleteCommand(CompletableFuture<Void> result,
                  byte[] key,
                  Version cas,
                  Duration timeout) {
        super(timeout);
        this.result = result;
        this.key = key;
        this.cas = cas;
    }

    @Override
    Responder createResponder(int opaque) {
        return Responder.voidResponder(this, result, opaque);
    }

    @Override
    char keyLength() {
        return (char) key.length;
    }

    @Override
    void writeBody(ByteBuffer buffer) {
        buffer.put(key);
    }

    @Override
    byte opcode() {
        return Opcodes.delete;
    }

    @Override
    long cas() {
        return cas.token();
    }
}
