package com.codebreak.backend.websocket;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WorkspaceStore {

    private final Map<Long, Map<String, WorkspaceFile>> projectFiles =
            new ConcurrentHashMap<>();

    private final Map<Long, Set<String>> projectFolders =
            new ConcurrentHashMap<>();


    // =====================================================
    // STORED FILE
    // =====================================================

    public record WorkspaceFile(
            String path,
            String content
    ) {
    }


    // =====================================================
    // SNAPSHOT
    // =====================================================

    public record WorkspaceSnapshot(
            Long projectId,
            List<WorkspaceFile> files,
            List<String> folders
    ) {
    }


    // =====================================================
    // PUT SINGLE FILE
    // =====================================================

    public void putFile(
            Long projectId,
            String path,
            String content
    ) {

        if (projectId == null) {
            return;
        }

        String normalizedPath =
                normalize(path);

        if (normalizedPath.isBlank()) {
            return;
        }

        projectFiles
                .computeIfAbsent(
                        projectId,
                        id -> new ConcurrentHashMap<>()
                )
                .put(
                        normalizedPath,
                        new WorkspaceFile(
                                normalizedPath,
                                content == null
                                        ? ""
                                        : content
                        )
                );


        /*
         * File ke parent folders bhi RAM workspace
         * me maintain karte hain.
         *
         * Example:
         *
         * src/components/App.tsx
         *
         * automatically creates:
         *
         * src
         * src/components
         */

        addParentFolders(
                projectId,
                normalizedPath
        );
    }


    // =====================================================
    // DELETE SINGLE FILE
    // =====================================================

    public void deleteFile(
            Long projectId,
            String path
    ) {

        if (projectId == null) {
            return;
        }

        Map<String, WorkspaceFile> files =
                projectFiles.get(projectId);

        if (files == null) {
            return;
        }

        files.remove(
                normalize(path)
        );

        cleanupProject(
                projectId
        );
    }


    // =====================================================
    // PUT FOLDER
    // =====================================================

    public void putFolder(
            Long projectId,
            String path
    ) {

        if (projectId == null) {
            return;
        }

        String normalizedPath =
                normalize(path);

        if (normalizedPath.isBlank()) {
            return;
        }

        projectFolders
                .computeIfAbsent(
                        projectId,
                        id -> ConcurrentHashMap.newKeySet()
                )
                .add(
                        normalizedPath
                );

        addParentFolders(
                projectId,
                normalizedPath
        );
    }


    // =====================================================
    // DELETE FOLDER
    // =====================================================

    public void deleteFolder(
            Long projectId,
            String path
    ) {

        if (projectId == null) {
            return;
        }

        String normalizedPath =
                normalize(path);

        if (normalizedPath.isBlank()) {
            return;
        }


        Map<String, WorkspaceFile> files =
                projectFiles.get(projectId);

        if (files != null) {

            String prefix =
                    normalizedPath + "/";

            files.keySet().removeIf(
                    filePath ->
                            filePath.equals(normalizedPath)
                                    ||
                                    filePath.startsWith(prefix)
            );
        }


        Set<String> folders =
                projectFolders.get(projectId);

        if (folders != null) {

            String prefix =
                    normalizedPath + "/";

            folders.removeIf(
                    folderPath ->
                            folderPath.equals(normalizedPath)
                                    ||
                                    folderPath.startsWith(prefix)
            );
        }


        cleanupProject(
                projectId
        );
    }


    // =====================================================
    // COMPLETE WORKSPACE REPLACE
    //
    // Owner local folder -> Backend RAM
    //
    // IMPORTANT:
    // NO DATABASE STORAGE
    // =====================================================

    public void replaceWorkspace(
            Long projectId,
            List<CollaborationMessage.WorkspaceFilePayload> files,
            List<String> folders
    ) {

        if (projectId == null) {
            return;
        }


        Map<String, WorkspaceFile> newFiles =
                new ConcurrentHashMap<>();


        Set<String> newFolders =
                ConcurrentHashMap.newKeySet();


        // =================================================
        // FILES
        // =================================================

        if (files != null) {

            for (
                    CollaborationMessage.WorkspaceFilePayload file :
                    files
            ) {

                if (file == null) {
                    continue;
                }


                String path =
                        normalize(
                                file.getPath()
                        );


                if (path.isBlank()) {
                    continue;
                }


                newFiles.put(
                        path,
                        new WorkspaceFile(
                                path,
                                file.getContent() == null
                                        ? ""
                                        : file.getContent()
                        )
                );


                /*
                 * File ke parent folders automatically add.
                 */

                addParentFoldersToSet(
                        newFolders,
                        path
                );
            }
        }


        // =================================================
        // EXPLICIT FOLDERS
        // =================================================

        if (folders != null) {

            for (
                    String folder :
                    folders
            ) {

                String normalizedFolder =
                        normalize(folder);


                if (normalizedFolder.isBlank()) {
                    continue;
                }


                addFolderAndParentsToSet(
                        newFolders,
                        normalizedFolder
                );
            }
        }


        // =================================================
        // REPLACE OLD WORKSPACE
        // =================================================

        if (
                newFiles.isEmpty()
                        &&
                        newFolders.isEmpty()
        ) {

            clearProject(
                    projectId
            );

            System.out.println(
                    "[WS STORE] Empty workspace received. " +
                            "Project=" + projectId
            );

            return;
        }


        projectFiles.put(
                projectId,
                newFiles
        );


        projectFolders.put(
                projectId,
                newFolders
        );


        System.out.println(
                "[WS STORE] Workspace replaced: " +
                        "Project=" + projectId +
                        " Files=" + newFiles.size() +
                        " Folders=" + newFolders.size()
        );
    }


    // =====================================================
    // GET SNAPSHOT
    // =====================================================

    public WorkspaceSnapshot getSnapshot(
            Long projectId
    ) {

        Map<String, WorkspaceFile> files =
                projectFiles.get(projectId);

        Set<String> folders =
                projectFolders.get(projectId);


        List<WorkspaceFile> fileList =
                files == null
                        ? new ArrayList<>()
                        : new ArrayList<>(
                        files.values()
                );


        List<String> folderList =
                folders == null
                        ? new ArrayList<>()
                        : new ArrayList<>(
                        folders
                );


        fileList.sort(
                Comparator.comparing(
                        WorkspaceFile::path
                )
        );


        folderList.sort(
                String::compareTo
        );


        return new WorkspaceSnapshot(
                projectId,
                fileList,
                folderList
        );
    }


    // =====================================================
    // CLEAR PROJECT
    // =====================================================

    public void clearProject(
            Long projectId
    ) {

        if (projectId == null) {
            return;
        }

        projectFiles.remove(
                projectId
        );

        projectFolders.remove(
                projectId
        );
    }


    // =====================================================
    // IS EMPTY
    // =====================================================

    public boolean isEmpty(
            Long projectId
    ) {

        WorkspaceSnapshot snapshot =
                getSnapshot(projectId);

        return snapshot.files().isEmpty()
                &&
                snapshot.folders().isEmpty();
    }


    // =====================================================
    // ADD PARENT FOLDERS
    // =====================================================

    private void addParentFolders(
            Long projectId,
            String path
    ) {

        Set<String> folders =
                projectFolders.computeIfAbsent(
                        projectId,
                        id -> ConcurrentHashMap.newKeySet()
                );


        addParentFoldersToSet(
                folders,
                path
        );
    }


    // =====================================================
    // ADD PARENT FOLDERS TO SET
    // =====================================================

    private void addParentFoldersToSet(
            Set<String> folders,
            String path
    ) {

        String[] parts =
                path.split("/");


        /*
         * Agar path itself folder hai to usko bhi include
         * karna hai.
         *
         * File ke case me last part filename hota hai,
         * isliye caller context ke according parents
         * handle hote hain.
         */

        if (parts.length <= 1) {
            return;
        }


        String current = "";


        for (
                int index = 0;
                index < parts.length - 1;
                index++
        ) {

            String part =
                    parts[index];


            if (part.isBlank()) {
                continue;
            }


            current =
                    current.isBlank()
                            ? part
                            : current + "/" + part;


            folders.add(
                    current
            );
        }
    }


    // =====================================================
    // ADD FOLDER + PARENTS
    // =====================================================

    private void addFolderAndParentsToSet(
            Set<String> folders,
            String folderPath
    ) {

        String[] parts =
                folderPath.split("/");


        String current = "";


        for (
                String part :
                parts
        ) {

            if (part.isBlank()) {
                continue;
            }


            current =
                    current.isBlank()
                            ? part
                            : current + "/" + part;


            folders.add(
                    current
            );
        }
    }


    // =====================================================
    // CLEANUP
    // =====================================================

    private void cleanupProject(
            Long projectId
    ) {

        Map<String, WorkspaceFile> files =
                projectFiles.get(projectId);

        Set<String> folders =
                projectFolders.get(projectId);


        boolean filesEmpty =
                files == null ||
                        files.isEmpty();


        boolean foldersEmpty =
                folders == null ||
                        folders.isEmpty();


        if (
                filesEmpty &&
                        foldersEmpty
        ) {

            clearProject(
                    projectId
            );
        }
    }


    // =====================================================
    // NORMALIZE PATH
    // =====================================================

    private String normalize(
            String path
    ) {

        if (path == null) {
            return "";
        }


        return path
                .replace("\\", "/")
                .replaceAll("^/+", "")
                .replaceAll("/+$", "");
    }
}