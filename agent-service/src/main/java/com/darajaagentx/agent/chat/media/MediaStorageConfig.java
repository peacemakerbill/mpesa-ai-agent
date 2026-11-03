package com.darajaagentx.agent.chat.media;

import com.darajaagentx.agent.store.StateStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Chooses where chat files are kept: {@code chat.media.store} = state (default), local or s3.
 * A wrong setting stops the service at start with a clear message, so files are never silently lost.
 */
@Configuration
public class MediaStorageConfig {

    @Bean
    MediaBlobStore mediaBlobStore(
            StateStore state,
            @Value("${chat.media.store:state}") String store,
            @Value("${chat.media.local-dir:./data/chat-media}") String localDir,
            @Value("${chat.media.retention-days:30}") long retentionDays,
            @Value("${chat.media.s3.endpoint:}") String endpoint,
            @Value("${chat.media.s3.bucket:}") String bucket,
            @Value("${chat.media.s3.region:us-east-1}") String region,
            @Value("${chat.media.s3.access-key:}") String accessKey,
            @Value("${chat.media.s3.secret-key:}") String secretKey,
            @Value("${chat.media.s3.path-style:true}") boolean pathStyle,
            @Value("${chat.media.s3.prefix:chat}") String prefix) {
        Duration retention = Duration.ofDays(Math.max(0, retentionDays));
        return switch (store.trim().toLowerCase()) {
            case "state" -> new StateBlobStore(state);
            case "local" -> new LocalDiskBlobStore(Path.of(localDir), retention);
            case "s3" -> new S3BlobStore(endpoint, bucket, region, accessKey, secretKey, pathStyle, prefix, retention);
            default -> throw new IllegalStateException("chat.media.store must be state, local or s3, not '" + store + "'");
        };
    }
}