/*
 * Copyright 2004-2014 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.lealone.db.value;

import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import org.lealone.common.exceptions.DbException;
import org.lealone.common.util.MathUtils;
import org.lealone.common.util.StringUtils;
import org.lealone.common.util.Utils;
import org.lealone.db.DataBuffer;
import org.lealone.db.api.ErrorCode;
import org.lealone.storage.type.StorageDataTypeBase;

/**
 * Implementation of the UUID data type.
 */
public class ValueUuid extends Value {

    /**
     * The precision of this value in number of bytes.
     */
    private static final int PRECISION = 16;

    /**
     * The display size of the textual representation of a UUID.
     * Example: cd38d882-7ada-4589-b5fb-7da0ca559d9a
     */
    private static final int DISPLAY_SIZE = 36;

    private final long high, low;

    private ValueUuid(long high, long low) {
        this.high = high;
        this.low = low;
    }

    @Override
    public int hashCode() {
        return (int) ((high >>> 32) ^ high ^ (low >>> 32) ^ low);
    }

    /**
     * Create a new UUID using the pseudo random number generator.
     *
     * @return the new UUID
     */
    public static ValueUuid getNewRandom() {
        long high = MathUtils.secureRandomLong();
        long low = MathUtils.secureRandomLong();
        // version 4 (random)
        high = (high & (~0xf000L)) | 0x4000L;
        // variant (Leach-Salz)
        low = (low & 0x3fffffffffffffffL) | 0x8000000000000000L;
        return new ValueUuid(high, low);
    }

    /**
     * Get or create a UUID for the given 16 bytes.
     *
     * @param binary the byte array (must be at least 16 bytes long)
     * @return the UUID
     */
    public static ValueUuid get(byte[] binary) {
        if (binary.length < 16) {
            return get(StringUtils.convertBytesToHex(binary));
        }
        long high = Utils.readLong(binary, 0);
        long low = Utils.readLong(binary, 8);
        return (ValueUuid) Value.cache(new ValueUuid(high, low));
    }

    /**
     * Get or create a UUID for the given high and low order values.
     *
     * @param high the most significant bits
     * @param low the least significant bits
     * @return the UUID
     */
    public static ValueUuid get(long high, long low) {
        return (ValueUuid) Value.cache(new ValueUuid(high, low));
    }

    /**
     * Get or create a UUID for the given text representation.
     *
     * @param s the text representation of the UUID
     * @return the UUID
     */
    public static ValueUuid get(String s) {
        long low = 0, high = 0;
        for (int i = 0, j = 0, length = s.length(); i < length; i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                low = (low << 4) | (c - '0');
            } else if (c >= 'a' && c <= 'f') {
                low = (low << 4) | (c - 'a' + 0xa);
            } else if (c == '-') {
                continue;
            } else if (c >= 'A' && c <= 'F') {
                low = (low << 4) | (c - 'A' + 0xa);
            } else if (c <= ' ') {
                continue;
            } else {
                throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1, s);
            }
            if (j++ == 15) {
                high = low;
                low = 0;
            }
        }
        return (ValueUuid) Value.cache(new ValueUuid(high, low));
    }

    public static ValueUuid get(UUID uuid) {
        return get(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
    }

    @Override
    public String getSQL() {
        return StringUtils.quoteStringSQL(getString());
    }

    @Override
    public int getType() {
        return Value.UUID;
    }

    @Override
    public long getPrecision() {
        return PRECISION;
    }

    private static void appendHex(StringBuilder buff, long x, int bytes) {
        for (int i = bytes * 8 - 4; i >= 0; i -= 8) {
            buff.append(Integer.toHexString((int) (x >> i) & 0xf))
                    .append(Integer.toHexString((int) (x >> (i - 4)) & 0xf));
        }
    }

    @Override
    public String getString() {
        StringBuilder buff = new StringBuilder(36);
        appendHex(buff, high >> 32, 4);
        buff.append('-');
        appendHex(buff, high >> 16, 2);
        buff.append('-');
        appendHex(buff, high, 2);
        buff.append('-');
        appendHex(buff, low >> 48, 2);
        buff.append('-');
        appendHex(buff, low, 6);
        return buff.toString();
    }

    @Override
    protected int compareSecure(Value o, CompareMode mode) {
        if (o == this) {
            return 0;
        }
        ValueUuid v = (ValueUuid) o;
        if (high == v.high) {
            return MathUtils.compareLong(low, v.low);
        }
        return high > v.high ? 1 : -1;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ValueUuid && compareSecure((Value) other, null) == 0;
    }

    @Override
    public Object getObject() {
        return new UUID(high, low);
    }

    @Override
    public UUID getUuid() {
        return new UUID(high, low);
    }

    @Override
    public byte[] getBytes() {
        byte[] buff = new byte[16];
        for (int i = 0; i < 8; i++) {
            buff[i] = (byte) ((high >> (8 * (7 - i))) & 255);
            buff[8 + i] = (byte) ((low >> (8 * (7 - i))) & 255);
        }
        return buff;
    }

    @Override
    public void set(PreparedStatement prep, int parameterIndex) throws SQLException {
        prep.setBytes(parameterIndex, getBytes());
    }

    /**
     * Get the most significant 64 bits of this UUID.
     *
     * @return the high order bits
     */
    public long getHigh() {
        return high;
    }

    /**
     * Get the least significant 64 bits of this UUID.
     *
     * @return the low order bits
     */
    public long getLow() {
        return low;
    }

    @Override
    public int getDisplaySize() {
        return DISPLAY_SIZE;
    }

    public static final StorageDataTypeBase type = new StorageDataTypeBase() {

        @Override
        public int getType() {
            return UUID;
        }

        @Override
        public int compare(Object aObj, Object bObj) {
            UUID a = (UUID) aObj;
            UUID b = (UUID) bObj;
            return a.compareTo(b);
        }

        @Override
        public int getMemory(Object obj) {
            return 32;
        }

        @Override
        public void write(DataBuffer buff, Object obj) {
            UUID a = (UUID) obj;
            write0(buff, a.getMostSignificantBits(), a.getLeastSignificantBits());
        }

        @Override
        public void writeValue(DataBuffer buff, Value v) {
            ValueUuid uuid = (ValueUuid) v;
            write0(buff, uuid.getHigh(), uuid.getLow());
        }

        private void write0(DataBuffer buff, long high, long low) {
            buff.put((byte) UUID).putLong(high).putLong(low);
        }

        @Override
        public Value readValue(ByteBuffer buff) {
            return ValueUuid.get(buff.getLong(), buff.getLong());
        }
    };
}
