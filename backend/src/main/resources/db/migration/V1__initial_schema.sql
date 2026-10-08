CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS projects (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    owner_id BIGINT NOT NULL,
    join_code VARCHAR(8) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_projects_owner
        FOREIGN KEY (owner_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_projects_owner
    ON projects(owner_id);

CREATE TABLE IF NOT EXISTS project_members (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    CONSTRAINT uk_project_members_project_user
        UNIQUE(project_id, user_id),
    CONSTRAINT fk_project_members_project
        FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_project_members_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_project_members_user
    ON project_members(user_id);

CREATE TABLE IF NOT EXISTS workspace_files (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    path VARCHAR(1000) NOT NULL,
    name VARCHAR(500) NOT NULL,
    object_key VARCHAR(1000) NOT NULL,
    size BIGINT NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_workspace_file_project_path
        UNIQUE(project_id, path),
    CONSTRAINT fk_workspace_files_project
        FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_workspace_file_project
    ON workspace_files(project_id);

CREATE TABLE IF NOT EXISTS workspace_folders (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    path VARCHAR(1000) NOT NULL,
    name VARCHAR(500) NOT NULL,
    parent_path VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_workspace_folder_project_path
        UNIQUE(project_id, path),
    CONSTRAINT fk_workspace_folders_project
        FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_workspace_folder_project
    ON workspace_folders(project_id);

CREATE TABLE IF NOT EXISTS project_files (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    path VARCHAR(255) NOT NULL,
    content TEXT,
    project_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_project_file_project_path
        UNIQUE(project_id, path),
    CONSTRAINT fk_project_files_project
        FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS project_folders (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    path VARCHAR(255) NOT NULL,
    project_id BIGINT NOT NULL,
    parent_id BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_project_folder_project_path
        UNIQUE(project_id, path),
    CONSTRAINT fk_project_folders_project
        FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_project_folders_parent
        FOREIGN KEY (parent_id) REFERENCES project_folders(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_project_folders_project
    ON project_folders(project_id);

CREATE INDEX IF NOT EXISTS idx_project_folders_parent
    ON project_folders(parent_id);
