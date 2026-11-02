FROM alpine:3.21
RUN apk add --no-cache postgresql16-client rclone ca-certificates tzdata
COPY backup-loop.sh /usr/local/bin/backup-loop.sh
RUN chmod 0755 /usr/local/bin/backup-loop.sh
ENTRYPOINT ["/usr/local/bin/backup-loop.sh"]
