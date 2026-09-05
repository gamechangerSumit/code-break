package com.codebreak.backend.workspace;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class WorkspacePersistenceScheduler {

    private final RedisWorkspaceService redisWorkspaceService;

    private final MinioWorkspaceService minioWorkspaceService;

    private final WorkspaceFileMetadataRepository fileMetadataRepository;


    // =====================================================
    // FLUSH DIRTY FILES
    //
    // Runs every 2 seconds.
    //
    // Redis:
    //   temporary latest editor content
    //
    // MinIO:
    //   persistent actual file content
    //
    // PostgreSQL:
    //   metadata + version
    // =====================================================

    @Scheduled(fixedDelay = 2000)
    public void flushDirtyFiles() {

        Set<String> activeProjects =
                redisWorkspaceService.getActiveProjects();


        if (
                activeProjects == null ||
                        activeProjects.isEmpty()
        ) {
            return;
        }


        for (
                String projectIdValue
                : activeProjects
        ) {

            Long projectId =
                    parseProjectId(
                            projectIdValue
                    );


            if (projectId == null) {
                continue;
            }


            /*
             * Project activity can expire between
             * getActiveProjects() and this check.
             */
            if (
                    !redisWorkspaceService.isActive(
                            projectId
                    )
            ) {

                redisWorkspaceService.clear(
                        projectId
                );

                continue;
            }


            flushProject(
                    projectId
            );
        }
    }


    // =====================================================
    // FLUSH ONE PROJECT
    // =====================================================

    private void flushProject(
            Long projectId
    ) {

        Set<String> dirtyFiles =
                redisWorkspaceService.getDirtyFiles(
                        projectId
                );


        if (
                dirtyFiles == null ||
                        dirtyFiles.isEmpty()
        ) {
            return;
        }


        /*
         * Work on a snapshot of the Redis set.
         *
         * Editor can continue modifying the original
         * Redis set while this loop is running.
         */
        Set<String> filesToFlush =
                Set.copyOf(
                        dirtyFiles
                );


        for (
                String path
                : filesToFlush
        ) {

            if (
                    path == null ||
                            path.isBlank()
            ) {
                continue;
            }


            try {

                flushFile(
                        projectId,
                        path
                );

            } catch (
                    Exception exception
            ) {

                /*
                 * IMPORTANT:
                 *
                 * Do NOT clear dirty state when persistence
                 * fails.
                 *
                 * The next scheduler cycle can retry it.
                 */

                System.err.println(
                        "[WORKSPACE FLUSH] Failed: " +
                                "Project=" + projectId +
                                " Path=" + path +
                                " Error=" +
                                exception.getMessage()
                );
            }
        }
    }


    // =====================================================
    // FLUSH ONE FILE
    // =====================================================

    private void flushFile(
            Long projectId,
            String path
    ) {

        /*
         * Read the latest value from Redis immediately
         * before writing to MinIO.
         */
        String content =
                redisWorkspaceService.getDirtyContent(
                        projectId,
                        path
                );


        /*
         * Dirty key may have expired naturally.
         */
        if (content == null) {

            redisWorkspaceService.clearDirty(
                    projectId,
                    path
            );

            return;
        }


        /*
         * Find metadata BEFORE writing.
         *
         * Normally every editable file should already
         * have metadata because files are created through
         * WorkspaceService.
         */
        WorkspaceFileMetadata metadata =
                fileMetadataRepository
                        .findByProjectIdAndPath(
                                projectId,
                                path
                        )
                        .orElse(null);


        /*
         * If metadata does not exist, don't create an
         * incomplete file silently here.
         *
         * Structural creation should happen through
         * WorkspaceService.
         */
        if (metadata == null) {

            System.err.println(
                    "[WORKSPACE FLUSH] Metadata missing: " +
                            "Project=" + projectId +
                            " Path=" + path
            );

            /*
             * Keep dirty state so that once metadata exists,
             * the next scheduler cycle can persist it.
             */
            return;
        }


        // =================================================
        // WRITE CONTENT TO MINIO
        // =================================================

        minioWorkspaceService.saveFile(
                projectId,
                path,
                content
        );


        // =================================================
        // UPDATE METADATA
        // =================================================

        long size =
                content.getBytes(
                        StandardCharsets.UTF_8
                ).length;


        Long currentVersion =
                metadata.getVersion() == null
                        ? 0L
                        : metadata.getVersion();


        metadata.setSize(
                size
        );


        metadata.setVersion(
                currentVersion + 1
        );


        fileMetadataRepository.save(
                metadata
        );


        // =================================================
        // CLEAR REDIS DIRTY STATE
        //
        // Only clear AFTER both:
        //   1. MinIO write succeeded
        //   2. PostgreSQL metadata save succeeded
        // =================================================

        redisWorkspaceService.clearDirty(
                projectId,
                path
        );


        System.out.println(
                "[WORKSPACE FLUSH] Saved: " +
                        "Project=" + projectId +
                        " Path=" + path +
                        " Version=" +
                        metadata.getVersion()
        );
    }


    // =====================================================
    // PARSE PROJECT ID
    // =====================================================

    private Long parseProjectId(
            String value
    ) {

        if (
                value == null ||
                        value.isBlank()
        ) {
            return null;
        }


        try {

            return Long.parseLong(
                    value
            );

        } catch (
                NumberFormatException exception
        ) {

            return null;
        }
    }
}