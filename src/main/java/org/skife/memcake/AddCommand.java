package org.skife.memcake;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

class AddCommand extends SetCommand {
    AddCommand(CompletableFuture<Version> result,
               byte[] key,
               int flags,
               int expires,
               byte[] value,
               long timeout,
               TimeUnit unit) {
        super(result, key, flags, expires, value, Version.ZERO, timeout, unit);
    }

    @Override
    protected byte opcode() {
        return Opcodes.add;
    }
}
