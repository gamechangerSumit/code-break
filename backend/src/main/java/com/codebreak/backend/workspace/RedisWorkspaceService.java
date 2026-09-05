package com.codebreak.backend.workspace;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RedisWorkspaceService {

    private static final String PREFIX =
            "codebreak:workspace:";

    private static final String ACTIVE =
            ":active";

    private static final String DIRTY_PREFIX =
            ":dirty:";

    private static final String DIRTY_SET =
            ":dirty-files";

    private static final String ACTIVE_PROJECTS =
            "codebreak:workspace:active-projects";


    /*
     * Project is considered active for 2 hours after
     * the latest activity.
     */
    private static final Duration ACTIVE_TTL =
            Duration.ofHours(2);


    /*
     * A dirty editor value stays in Redis for at most
     * 30 minutes without another update.
     */
    private static final Duration DIRTY_TTL =
            Duration.ofMinutes(30);


    private final StringRedisTemplate redisTemplate;


    // =====================================================
    // MARK PROJECT ACTIVE
    // =====================================================

    public void markActive(
            Long projectId
    ) {

        if (projectId == null) {
            return;
        }


        redisTemplate.opsForValue().set(
                activeKey(projectId),
                "true",
                ACTIVE_TTL
        );


        redisTemplate.opsForSet().add(
                ACTIVE_PROJECTS,
                String.valueOf(projectId)
        );
    }


    // =====================================================
    // CHECK ACTIVE
    // =====================================================

    public boolean isActive(
            Long projectId
    ) {

        if (projectId == null) {
            return false;
        }


        return Boolean.TRUE.equals(
                redisTemplate.hasKey(
                        activeKey(projectId)
                )
        );
    }


    // =====================================================
    // GET ACTIVE PROJECTS
    //
    // Also removes stale project IDs from the
    // ACTIVE_PROJECTS Redis set.
    // =====================================================

    public Set<String> getActiveProjects() {

        Set<String> projects =
                redisTemplate.opsForSet()
                        .members(
                                ACTIVE_PROJECTS
                        );


        if (
                projects == null ||
                        projects.isEmpty()
        ) {
            return Set.of();
        }


        Set<String> active =
                new HashSet<>();

        Set<String> stale =
                new HashSet<>();


        for (
                String projectIdValue
                : projects
        ) {

            Long projectId =
                    parseProjectId(
                            projectIdValue
                    );


            if (projectId == null) {

                stale.add(
                        projectIdValue
                );

                continue;
            }


            if (
                    isActive(projectId)
            ) {

                active.add(
                        projectIdValue
                );

            } else {

                stale.add(
                        projectIdValue
                );
            }
        }


        /*
         * Remove expired/invalid project IDs from
         * the index set.
         */
        if (!stale.isEmpty()) {

            redisTemplate.opsForSet()
                    .remove(
                            ACTIVE_PROJECTS,
                            stale.toArray()
                    );
        }


        return active;
    }


    // =====================================================
    // SAVE DIRTY CONTENT
    // =====================================================

    public void saveDirtyContent(
            Long projectId,
            String path,
            String content
    ) {

        if (
                projectId == null ||
                        path == null ||
                        path.isBlank()
        ) {
            return;
        }


        String normalizedPath =
                normalizePath(path);


        if (
                normalizedPath.isBlank()
        ) {
            return;
        }


        redisTemplate.opsForValue().set(

                dirtyKey(
                        projectId,
                        normalizedPath
                ),

                content == null
                        ? ""
                        : content,

                DIRTY_TTL
        );


        redisTemplate.opsForSet().add(
                dirtySetKey(projectId),
                normalizedPath
        );


        markActive(
                projectId
        );
    }


    // =====================================================
    // GET DIRTY CONTENT
    // =====================================================

    public String getDirtyContent(
            Long projectId,
            String path
    ) {

        if (
                projectId == null ||
                        path == null ||
                        path.isBlank()
        ) {
            return null;
        }


        String normalizedPath =
                normalizePath(path);


        return redisTemplate.opsForValue().get(
                dirtyKey(
                        projectId,
                        normalizedPath
                )
        );
    }


    // =====================================================
    // GET DIRTY FILES
    //
    // Redis set can contain a path whose actual value
    // already expired. Such paths are removed here.
    // =====================================================

    public Set<String> getDirtyFiles(
            Long projectId
    ) {

        if (projectId == null) {
            return Set.of();
        }


        Set<String> paths =
                redisTemplate.opsForSet()
                        .members(
                                dirtySetKey(projectId)
                        );


        if (
                paths == null ||
                        paths.isEmpty()
        ) {
            return Set.of();
        }


        Set<String> valid =
                new HashSet<>();

        Set<String> expired =
                new HashSet<>();


        for (
                String path
                : paths
        ) {

            if (
                    path == null ||
                            path.isBlank()
            ) {
                expired.add(path);
                continue;
            }


            if (
                    redisTemplate.hasKey(
                            dirtyKey(
                                    projectId,
                                    path
                            )
                    )
            ) {

                valid.add(path);

            } else {

                expired.add(path);
            }
        }


        /*
         * Keep dirty-files set clean when individual
         * dirty values expire naturally.
         */
        if (!expired.isEmpty()) {

            redisTemplate.opsForSet()
                    .remove(
                            dirtySetKey(projectId),
                            expired.toArray()
                    );
        }


        return valid;
    }


    // =====================================================
    // CHECK DIRTY
    // =====================================================

    public boolean isDirty(
            Long projectId,
            String path
    ) {

        if (
                projectId == null ||
                        path == null ||
                        path.isBlank()
        ) {
            return false;
        }


        String normalizedPath =
                normalizePath(path);


        return Boolean.TRUE.equals(
                redisTemplate.hasKey(
                        dirtyKey(
                                projectId,
                                normalizedPath
                        )
                )
        );
    }


    // =====================================================
    // CLEAR ONE DIRTY FILE
    // =====================================================

    public void clearDirty(
            Long projectId,
            String path
    ) {

        if (
                projectId == null ||
                        path == null ||
                        path.isBlank()
        ) {
            return;
        }


        String normalizedPath =
                normalizePath(path);


        if (
                normalizedPath.isBlank()
        ) {
            return;
        }


        redisTemplate.delete(
                dirtyKey(
                        projectId,
                        normalizedPath
                )
        );


        redisTemplate.opsForSet()
                .remove(
                        dirtySetKey(projectId),
                        normalizedPath
                );
    }


    // =====================================================
    // CLEAR COMPLETE PROJECT REDIS STATE
    // =====================================================

    public void clear(
            Long projectId
    ) {

        if (projectId == null) {
            return;
        }


        Set<String> dirtyFiles =
                redisTemplate.opsForSet()
                        .members(
                                dirtySetKey(projectId)
                        );


        if (
                dirtyFiles != null &&
                        !dirtyFiles.isEmpty()
        ) {

            for (
                    String path
                    : dirtyFiles
            ) {

                if (
                        path == null ||
                                path.isBlank()
                ) {
                    continue;
                }


                redisTemplate.delete(
                        dirtyKey(
                                projectId,
                                path
                        )
                );
            }
        }


        redisTemplate.delete(
                dirtySetKey(projectId)
        );


        redisTemplate.delete(
                activeKey(projectId)
        );


        redisTemplate.opsForSet()
                .remove(
                        ACTIVE_PROJECTS,
                        String.valueOf(projectId)
                );
    }


    // =====================================================
    // REDIS KEYS
    // =====================================================

    private String activeKey(
            Long projectId
    ) {

        return PREFIX +
                projectId +
                ACTIVE;
    }


    private String dirtyKey(
            Long projectId,
            String path
    ) {

        return PREFIX +
                projectId +
                DIRTY_PREFIX +
                normalizePath(path);
    }


    private String dirtySetKey(
            Long projectId
    ) {

        return PREFIX +
                projectId +
                DIRTY_SET;
    }


    // =====================================================
    // PATH NORMALIZATION
    // =====================================================

    private String normalizePath(
            String path
    ) {

        if (path == null) {
            return "";
        }


        String normalized =
                path
                        .trim()
                        .replace(
                                "\\",
                                "/"
                        );


        while (
                normalized.startsWith("/")
        ) {

            normalized =
                    normalized.substring(1);
        }


        while (
                normalized.endsWith("/")
        ) {

            normalized =
                    normalized.substring(
                            0,
                            normalized.length() - 1
                    );
        }


        return normalized;
    }


    // =====================================================
    // PROJECT ID PARSER
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