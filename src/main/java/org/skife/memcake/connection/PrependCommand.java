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

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

class PrependCommand extends AppendCommand {
    PrependCommand(CompletableFuture<Version> result,
                   byte[] key,
                   byte[] value,
                   Version cas,
                   Duration timeout) {
        super(result, key, value, cas, timeout);
    }

    @Override
    byte opcode() {
        return Opcodes.prepend;
    }
}
