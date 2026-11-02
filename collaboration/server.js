const { Server } = require("@hocuspocus/server");
const Redis = require("ioredis");
const Y = require("yjs");

const redis = new Redis(process.env.REDIS_URL || "redis://redis:6379", {
  maxRetriesPerRequest: 3,
  enableReadyCheck: true,
});
const backendUrl = process.env.BACKEND_INTERNAL_URL || "http://backend:8088";
const port = Number(process.env.PORT || 1234);

function parseDocumentName(name) {
  const match = /^project-(\\d+)-file-([A-Za-z0-9_-]+)$/.exec(name || "");
  if (!match) throw new Error("Invalid collaboration document name");
  const projectId = Number(match[1]);
  const filePath = Buffer.from(match[2], "base64url").toString("utf8");
  if (!Number.isSafeInteger(projectId) || projectId <= 0 ||
      !filePath || filePath.startsWith("/") || filePath.includes("\\") ||
      filePath.split("/").some(part => part === ".." || part === ".")) {
    throw new Error("Invalid collaboration document path");
  }
  return { projectId, filePath };
}

function stateKey(documentName) {
  return `codebreak:yjs:${documentName}`;
}

const server = Server.configure({
  port,
  address: "0.0.0.0",
  path: "/collaboration",
  debounce: 1500,
  maxDebounce: 5000,

  async onAuthenticate({ token, documentName, connection }) {
    if (!token || token.length > 8192) throw new Error("Authentication required");
    const { projectId, filePath } = parseDocumentName(documentName);
    const response = await fetch(
      `${backendUrl}/api/projects/${projectId}/collaboration-access`,
      { headers: { Authorization: `Bearer ${token}`, Accept: "application/json" } },
    );
    if (!response.ok) throw new Error("You do not have access to this workspace");
    const access = await response.json();
    if (!access || !access.username) throw new Error("Invalid workspace access response");
    connection.readOnly = access.canWrite !== true;
    return {
      projectId,
      filePath,
      username: access.username,
      canWrite: access.canWrite === true,
      token,
    };
  },

  async onLoadDocument({ documentName }) {
    const document = new Y.Doc();
    const state = await redis.get(stateKey(documentName));
    if (state) Y.applyUpdate(document, Buffer.from(state, "base64"));
    return document;
  },

  async onStoreDocument({ documentName, document, context }) {
    const { projectId, filePath } = parseDocumentName(documentName);
    const encodedState = Buffer.from(Y.encodeStateAsUpdate(document)).toString("base64");
    await redis.set(stateKey(documentName), encodedState);

    // Keep the existing Spring persistence scheduler as the single MinIO writer.
    // Only accepted Yjs updates reach this hook; stage the latest document content
    // in the dirty-file Redis keys consumed by that scheduler.
    const content = document.getText("monaco").toString();
    const prefix = `codebreak:workspace:${projectId}`;
    await redis
      .multi()
      .set(`${prefix}:dirty:${filePath}`, content, "EX", 1800)
      .sadd(`${prefix}:dirty-files`, filePath)
      .set(`${prefix}:active`, "true", "EX", 7200)
      .sadd("codebreak:workspace:active-projects", String(projectId))
      .exec();
  },

  async onDestroy() {
    await redis.quit();
  },
});

server.listen();
console.log(`Code Break Yjs collaboration listening on :${port}`);
