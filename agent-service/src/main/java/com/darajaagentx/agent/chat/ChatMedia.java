package com.darajaagentx.agent.chat;

import com.darajaagentx.agent.chat.media.MediaBlobStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Files in a chat, in both directions: what the customer attaches and what the assistant sends back (receipts,
 * voice replies). Each file belongs to one phone number. The bytes live in a {@link MediaBlobStore} (the state store,
 * a folder, or an S3 compatible bucket, see {@code chat.media.store}). Next to the bytes sits a small note with the
 * owner, type, name, size and time, so ownership is checked the same way whichever store is in use.
 *
 * <p>Files are kept for {@code chat.media.retention-days} (30 by default) with a local folder or S3, and for 24 hours
 * in the state store. Set {@code chat.media.retention-days=0} to keep them until someone deletes them.
 */
@Component
public class ChatMedia {

    private static final String META = ".meta";

    private final MediaBlobStore blobs;
    private final Duration retention;

    @Autowired
    public ChatMedia(MediaBlobStore blobs, @Value("${chat.media.retention-days:-1}") long retentionDays) {
        this.blobs = blobs;
        if (!"state".equals(blobs.kind()) && retentionDays >= 0) {
            this.retention = Duration.ofDays(retentionDays);
        } else {
            this.retention = blobs.defaultRetention();
        }
    }

    /** For tests and code that wants the store's own default retention. */
    public ChatMedia(MediaBlobStore blobs) {
        this.blobs = blobs;
        this.retention = blobs.defaultRetention();
    }

    /** Stores the file and returns its id. */
    public String put(String phone, byte[] bytes, String mimeType, String fileName) {
        String id = UUID.randomUUID().toString();
        String type = mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType;
        Duration keep = keepFor();
        blobs.put(id, bytes, keep);
        try {
            blobs.put(id + META, encode(new Meta(phone, type, fileName, bytes.length, Instant.now())).getBytes(StandardCharsets.UTF_8), keep);
        } catch (RuntimeException e) {
            blobs.delete(id);   // never leave bytes without an owner
            throw e;
        }
        return id;
    }

    /** The file, only when it belongs to this phone number and has not expired. */
    public Optional<Found> get(String phone, String id) {
        if (id == null || !id.matches("^[0-9a-fA-F-]{36}$")) {
            return Optional.empty();
        }
        Optional<byte[]> metaBytes = blobs.get(id + META);
        if (metaBytes.isEmpty()) {
            return Optional.empty();
        }
        Meta meta = decode(new String(metaBytes.get(), StandardCharsets.UTF_8));
        if (meta == null || phone == null || !phone.equals(meta.phone())) {
            return Optional.empty();
        }
        if (expired(meta)) {
            delete(id);
            return Optional.empty();
        }
        return blobs.get(id).map(b -> new Found(b, meta.mimeType(), meta.fileName()));
    }

    public boolean exists(String phone, String id) {
        return get(phone, id).isPresent();
    }

    /** Removes a file the customer owns. Returns false when it is not theirs or is already gone. */
    public boolean remove(String phone, String id) {
        if (id == null || !id.matches("^[0-9a-fA-F-]{36}$")) {
            return false;
        }
        Optional<byte[]> metaBytes = blobs.get(id + META);
        Meta meta = metaBytes.map(b -> decode(new String(b, StandardCharsets.UTF_8))).orElse(null);
        if (meta == null || phone == null || !phone.equals(meta.phone())) {
            return false;
        }
        delete(id);
        return true;
    }

    /** Where files are kept now: state, local or s3. */
    public String storeKind() {
        return blobs.kind();
    }

    private void delete(String id) {
        blobs.delete(id);
        blobs.delete(id + META);
    }

    private boolean expired(Meta m) {
        return !retention.isZero() && Instant.now().isAfter(m.createdAt().plus(retention));
    }

    private Duration keepFor() {
        // A little longer than the retention, so the reader (which enforces the exact time) is what decides.
        return retention.isZero() ? Duration.ofDays(3650) : retention.plusHours(2);
    }

    // ----------------------------------------------------------------- the note next to each file

    record Meta(String phone, String mimeType, String fileName, long size, Instant createdAt) {
    }

    static String encode(Meta m) {
        Base64.Encoder b = Base64.getEncoder();
        return String.join("\n", "v1",
                b64(b, m.phone()), b64(b, m.mimeType()), b64(b, m.fileName() == null ? "" : m.fileName()),
                Long.toString(m.size()), Long.toString(m.createdAt().toEpochMilli()));
    }

    static Meta decode(String text) {
        try {
            String[] p = text.split("\n", -1);
            if (p.length != 6 || !"v1".equals(p[0])) {
                return null;
            }
            Base64.Decoder d = Base64.getDecoder();
            String name = new String(d.decode(p[3]), StandardCharsets.UTF_8);
            return new Meta(new String(d.decode(p[1]), StandardCharsets.UTF_8), new String(d.decode(p[2]), StandardCharsets.UTF_8),
                    name.isEmpty() ? null : name, Long.parseLong(p[4]), Instant.ofEpochMilli(Long.parseLong(p[5])));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String b64(Base64.Encoder b, String s) {
        return b.encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    public record Found(byte[] bytes, String mimeType, String fileName) {
    }
}
