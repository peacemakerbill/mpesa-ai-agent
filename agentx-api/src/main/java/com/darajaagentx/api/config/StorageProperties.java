package com.darajaagentx.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where files that belong to this service are kept, today the profile pictures.
 *
 * @param type            local (a folder on this server) or network (a shared folder mounted on this server: NFS, SMB/CIFS, a NAS)
 * @param local           the folder used when type is local
 * @param network         the mounted share used when type is network
 * @param maxPictureBytes the biggest profile picture accepted, before it is shrunk
 */
@ConfigurationProperties(prefix = "agentx.storage")
public record StorageProperties(
        @DefaultValue("local") String type,
        @DefaultValue Local local,
        @DefaultValue Network network,
        @DefaultValue("3145728") int maxPictureBytes
) {

    /** @param path a folder, created when missing */
    public record Local(@DefaultValue("./data/files") String path) {
    }

    /**
     * @param path            the mount point of the share, for example /mnt/agentx or on Windows \\\\nas\\agentx. It must already exist.
     * @param createIfMissing create the folder when it is not there. Leave false so a share that failed to mount is noticed, not silently replaced by an empty local folder.
     * @param subfolder       a folder inside the share, so several services can use one share
     */
    public record Network(@DefaultValue("") String path, @DefaultValue("false") boolean createIfMissing,
                          @DefaultValue("agentx-api") String subfolder) {
    }
}
