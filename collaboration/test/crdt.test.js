const test = require("node:test");
const assert = require("node:assert/strict");
const Y = require("yjs");

test("concurrent edits converge without overwriting each other", () => {
  const alice = new Y.Doc();
  const bob = new Y.Doc();
  const aliceText = alice.getText("monaco");
  const bobText = bob.getText("monaco");

  const aliceUpdates = [];
  const bobUpdates = [];
  alice.on("update", update => aliceUpdates.push(update));
  bob.on("update", update => bobUpdates.push(update));

  aliceText.insert(0, "alpha");
  bobText.insert(0, "beta");

  aliceUpdates.forEach(update => Y.applyUpdate(bob, update));
  bobUpdates.forEach(update => Y.applyUpdate(alice, update));

  assert.equal(aliceText.toString(), bobText.toString());
  assert.ok(aliceText.toString().includes("alpha"));
  assert.ok(aliceText.toString().includes("beta"));

  alice.destroy();
  bob.destroy();
});

test("Yjs state update can be persisted and restored", () => {
  const original = new Y.Doc();
  original.getText("monaco").insert(0, "persistent workspace");
  const encoded = Y.encodeStateAsUpdate(original);

  const restored = new Y.Doc();
  Y.applyUpdate(restored, encoded);
  assert.equal(restored.getText("monaco").toString(), "persistent workspace");

  original.destroy();
  restored.destroy();
});
