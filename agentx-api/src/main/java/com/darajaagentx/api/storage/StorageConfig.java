package com.darajaagentx.api.storage;

import com.darajaagentx.api.config.StorageProperties;
import com.darajaagentx.api.repository.StoredFileRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Locale;

/** Chooses where files go from agentx.storage.type: local, network, database or cloudinary. A wrong setting stops the service at start with a clear message. */
@Configuration(proxyBeanMethods = false)
public class StorageConfig {

    @Bean
    FileStore fileStore(StorageProperties props, ObjectProvider<StoredFileRepository> files) {
        return create(props, files.getIfAvailable());
    }

    /** For settings that need no database. */
    public static FileStore create(StorageProperties props) {
        return create(props, null);
    }

    public static FileStore create(StorageProperties props, StoredFileRepository files) {
        String type = props.type() == null ? "local" : props.type().trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "local" -> DiskFileStore.local(Path.of(props.local().path()));
            case "network" -> DiskFileStore.network(Path.of(props.network().path()), props.network().subfolder(), props.network().createIfMissing());
            case "database" -> {
                if (files == null) {
                    throw new StorageException("agentx.storage.type is database but there is no database to keep files in.");
                }
                yield new DatabaseFileStore(files, props.database().maxFileBytes(), Clock.systemUTC());
            }
            case "cloudinary" -> {
                StorageProperties.Cloudinary c = props.cloudinary();
                yield c.url() != null && !c.url().isBlank()
                        ? CloudinaryFileStore.fromUrl(c.url(), c.folder())
                        : new CloudinaryFileStore(c.cloudName(), c.apiKey(), c.apiSecret(), c.folder());
            }
            default -> throw new StorageException("agentx.storage.type must be local, network, database or cloudinary, not \"" + props.type() + "\".");
        };
    }
}
