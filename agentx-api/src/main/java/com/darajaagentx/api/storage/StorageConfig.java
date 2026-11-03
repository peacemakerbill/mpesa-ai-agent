package com.darajaagentx.api.storage;

import com.darajaagentx.api.config.StorageProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.Locale;

/** Chooses where files go from agentx.storage.type: local or network. A wrong setting stops the service at start with a clear message. */
@Configuration(proxyBeanMethods = false)
public class StorageConfig {

    @Bean
    FileStore fileStore(StorageProperties props) {
        return create(props);
    }

    public static FileStore create(StorageProperties props) {
        String type = props.type() == null ? "local" : props.type().trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "local" -> DiskFileStore.local(Path.of(props.local().path()));
            case "network" -> DiskFileStore.network(Path.of(props.network().path()), props.network().subfolder(), props.network().createIfMissing());
            default -> throw new StorageException("agentx.storage.type must be local or network, not \"" + props.type() + "\".");
        };
    }
}
