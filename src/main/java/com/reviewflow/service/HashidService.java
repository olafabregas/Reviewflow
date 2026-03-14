package com.reviewflow.service;

import jakarta.annotation.PostConstruct;
import org.hashids.Hashids;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HashidService {

    @Value("${hashids.salt}")
    private String salt;

    @Value("${hashids.min-length:8}")
    private int minLength;

    private Hashids hashids;

    @PostConstruct
    public void init() {
        this.hashids = new Hashids(salt, minLength);
    }

    /**
     * Encode a single Long ID to an opaque hash string.
     * Returns null if id is null.
     */
    public String encode(Long id) {
        if (id == null) return null;
        return hashids.encode(id);
    }

    /**
     * Decode a hash string back to a Long ID.
     * Returns null if the hash is invalid or empty.
     * Never throws — callers handle null as "not found".
     */
    public Long decode(String hash) {
        if (hash == null || hash.isBlank()) return null;
        long[] decoded = hashids.decode(hash);
        if (decoded == null || decoded.length == 0) return null;
        return decoded[0];
    }

    /**
     * Decode and throw InvalidHashException if invalid.
     * Use in controllers where an invalid hash should be a 400, not a 404.
     */
    public Long decodeOrThrow(String hash) {
        Long id = decode(hash);
        if (id == null) throw new com.reviewflow.exception.InvalidHashException(hash);
        return id;
    }
}
