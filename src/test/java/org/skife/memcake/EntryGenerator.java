package org.skife.memcake;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

public class EntryGenerator extends Generator<Entry> {

    public EntryGenerator() {
        super(Entry.class);
    }

    @Override
    public Entry generate(SourceOfRandomness random, GenerationStatus status) {
        return new Entry(key(random), value(random));
    }

    private byte[] key(SourceOfRandomness random) {
        int len = random.nextInt(1, 128);
        byte[] rs = new byte[len];
        for (int i = 0; i < len; i++) {
            rs[i] = random.nextByte(Byte.MIN_VALUE, Byte.MAX_VALUE);
        }
        return rs;
    }

    private byte[] value(SourceOfRandomness random) {
        int len = random.nextInt(1, 8192);
        byte[] rs = new byte[len];
        for (int i = 0; i < len; i++) {
            rs[i] = random.nextByte(Byte.MIN_VALUE, Byte.MAX_VALUE);
        }
        return rs;
    }
}