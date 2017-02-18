package org.skife.memcake;

import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class Response {
    private final Connection conn;
    private final ByteBuffer headerBuffer;

    // header fields
    private final byte magic;
    private final byte opcode;
    private final char keyLength;
    private final byte extrasLength;
    private final byte dataType;
    private final int totalBodyLength;
    private final int opaque;
    private final long cas;
    private final ByteBuffer bodyBuffer;
    private final char status;

    // body fields
    private final AtomicInteger flags = new AtomicInteger(0);
    private final AtomicReference<byte[]> value = new AtomicReference<>();
    private final AtomicReference<byte[]> key = new AtomicReference<>();
    private final AtomicReference<String> error = new AtomicReference<>();

    Response(Connection conn, ByteBuffer buf) {
        // read the header fields
        this.conn = conn;
        this.headerBuffer = buf;
        this.magic = buf.get();
        this.opcode = buf.get();
        this.keyLength = buf.getChar();
        this.extrasLength = buf.get();
        this.dataType = buf.get();
        this.status = buf.getChar();
        this.totalBodyLength = buf.getInt();
        this.opaque = buf.getInt();
        this.cas = buf.getLong();

        // allocate buffer for reading the body
        this.bodyBuffer = ByteBuffer.allocate(totalBodyLength);
    }

    int getOpaque() {
        return opaque;
    }

    long getVersion() {
        return cas;
    }

    Integer getFlags() {
        return flags.get();
    }

    byte[] getValue() {
        return value.get();
    }

    public char getStatus() {
        return status;
    }

    public char getKeyLength() {
        return keyLength;
    }

    public void setKey(byte[] key) {
        this.key.set(key);
    }

    public void setFlags(int flags) {
        this.flags.set(flags);
    }

    public int getTotalBodyLength() {
        return totalBodyLength;
    }

    public byte getExtrasLength() {
        return extrasLength;
    }

    public void setValue(byte[] value) {
        this.value.set(value);
    }

    public byte[] getKey() {
        return key.get();
    }

    public String getError() {
        return error.get();
    }

    void readBody() {
        conn.getChannel().read(bodyBuffer, bodyBuffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer bytesRead, ByteBuffer bodyBuffer) {
                if (bodyBuffer.remaining() != 0) {
                    readBody();
                    return;
                }

                bodyBuffer.flip();

                if (status == 0) {
                    // success, process the body per message type
                    switch (opcode) {
                        case Opcodes.set:
                            SetCommand.parseBody(Response.this, conn, bodyBuffer);
                            break;
                        case Opcodes.get:
                            GetCommand.parseBody(Response.this, conn, bodyBuffer);
                            break;
                        case Opcodes.add:
                            GetCommand.parseBody(Response.this, conn, bodyBuffer);
                            break;
                        case Opcodes.flush:
                            FlushCommand.parseBody(Response.this, conn, bodyBuffer);
                            break;
                        default:
                            conn.receive(Response.this);
                    }
                } else {
                    // error, body will be textual error description
                    error.set(new String(bodyBuffer.array(), StandardCharsets.US_ASCII));
                    conn.receive(Response.this);
                }

                // we finished reading body, so start next read loop
                headerBuffer.clear();
                conn.nextResponse(headerBuffer);
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                conn.networkFailure(exc);
            }
        });
    }

    private void consumeError(Connection conn, ByteBuffer bodyBuffer) {

    }
}