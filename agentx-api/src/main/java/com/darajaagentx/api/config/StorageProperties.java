package com.darajaagentx.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where files that belong to this service are kept, today the profile pictures.
 *
 * @param type            local (a folder on this server), network (a shared folder mounted on this server: NFS, SMB/CIFS, a NAS),
 *                        database (the database this service already uses) or cloudinary
 * @param local           the folder used when type is local
 * @param network         the mounted share used when type is network
 * @param database        the limits of the database store, used when type is database
 * @param cloudinary      the account used when type is cloudinary
 * @param maxPictureBytes the biggest profile picture accepted, before it is shrunk
 */
@ConfigurationProperties(prefix = "agentx.storage")
public record StorageProperties(
        @DefaultValue("local") String type,
        @DefaultValue Local local,
        @DefaultValue Network network,
        @DefaultValue Database database,
        @DefaultValue Cloudinary cloudinary,
        @DefaultValue("3145728") int maxPictureBytes
) {

    /** @param maxFileBytes the biggest file kept in the database. MongoDB documents stop at 16 MB, so stay below that. */
    public record Database(@DefaultValue("15728640") long maxFileBytes) {
    }

    /**
     * @param url       the address from the Cloudinary dashboard (cloudinary://API_KEY:API_SECRET@CLOUD_NAME). When set, the three values below are not needed.
     * @param cloudName the cloud name
     * @param apiKey    the API key
     * @param apiSecret the API secret. Keep it out of the repository.
     * @param folder    a folder in the cloud, so one account can serve several apps
     */
    public record Cloudinary(@DefaultValue("") String url, @DefaultValue("") String cloudName, @DefaultValue("") String apiKey,
                             @DefaultValue("") String apiSecret, @DefaultValue("agentx") String folder) {
    }

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
