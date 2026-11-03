# Where chat files are kept

Photos, voice notes, documents and the receipts the assistant sends all go through `ChatMedia`. The bytes now live in one of three places,
chosen with `chat.media.store`:

| Setting | Where | Default retention | Use it for |
|---|---|---|---|
| `state` (default) | the agent's state store (Redis), as base64 | 24 hours | what you have today. Small files, no extra setup |
| `local` | a folder on the server | 30 days | one server. Point it at a mounted disk, NFS, SMB or EFS share to keep files outside the container |
| `s3` | an S3 compatible bucket over the network | 30 days (set a bucket lifecycle rule) | several servers, or files that must survive a redeploy: AWS S3, MinIO, Cloudflare R2, DigitalOcean Spaces, Wasabi |

Next to each file there is a small note (`<id>.meta`) with the owner's phone number, type, name, size and time. Whatever the store, a file
is only returned to the number that owns it, and it stops being returned once it is older than the retention time.

## Settings

```yaml
chat:
  media:
    store: ${CHAT_MEDIA_STORE:state}                  # state, local or s3
    retention-days: ${CHAT_MEDIA_RETENTION_DAYS:30}   # 0 keeps files until someone deletes them. Ignored by "state" (always 24 hours)
    local-dir: ${CHAT_MEDIA_LOCAL_DIR:./data/chat-media}
    s3:
      endpoint: ${CHAT_MEDIA_S3_ENDPOINT:}            # empty means AWS: https://s3.<region>.amazonaws.com
      bucket: ${CHAT_MEDIA_S3_BUCKET:}
      region: ${CHAT_MEDIA_S3_REGION:us-east-1}
      access-key: ${CHAT_MEDIA_S3_ACCESS_KEY:}
      secret-key: ${CHAT_MEDIA_S3_SECRET_KEY:}
      path-style: ${CHAT_MEDIA_S3_PATH_STYLE:true}    # false for AWS virtual-hosted style (bucket.s3.region.amazonaws.com)
      prefix: ${CHAT_MEDIA_S3_PREFIX:chat}
```

A wrong setting stops the service at start with a clear message instead of losing files quietly.

### A folder or a network share

```yaml
# docker-compose.yml, agent-service
    environment:
      CHAT_MEDIA_STORE: local
      CHAT_MEDIA_LOCAL_DIR: /data/chat-media
    volumes:
      - chat-media:/data/chat-media          # or a bind mount of your NFS/SMB share
volumes:
  chat-media:
```

The folder must be writable by the user the service runs as. Files are spread over 256 sub folders. Old files are removed by a sweep that
runs at most once an hour (started by an upload).

### AWS S3

1. Create a private bucket with "Block all public access" on. Turn on default encryption (SSE-S3 or SSE-KMS).
2. Add a lifecycle rule that expires objects under the prefix after your retention time (this class never lists or sweeps a bucket).
3. Create an access key for a user that can only do this, on `arn:aws:s3:::YOUR_BUCKET/chat/*`:
   `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject`.
4. `CHAT_MEDIA_STORE=s3`, `CHAT_MEDIA_S3_BUCKET`, `CHAT_MEDIA_S3_REGION`, the two keys, `CHAT_MEDIA_S3_PATH_STYLE=false`.

### MinIO, Cloudflare R2, Spaces, Wasabi

Set `CHAT_MEDIA_S3_ENDPOINT` (for example `https://ACCOUNT_ID.r2.cloudflarestorage.com` for R2, region `auto`) and keep `path-style: true`.
A plain `http://` endpoint is accepted for a MinIO on your private network; use https for anything else.

## What was and was not checked

- The request signing (AWS Signature V4) reproduces the worked example in the AWS documentation exactly, and uploads, downloads and deletes
  work against a local stand-in server that checks every signature.
- It has **not** been run against a real S3, R2, MinIO or Spaces service. Do one upload and download in a test bucket first.
- Switching `chat.media.store` does not move existing files. Files in the old store are no longer found.
- Files are held in memory while they are moved, so keep `agent.max-upload-bytes` modest (20 MB by default).
- There is no per-person quota and no virus scanning.
