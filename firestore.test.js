const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);
const ts = () => new Date(Date.now() - 1000);

function validProject(userId) {
  return {
    userId,
    title: "Cyber Sakura Chronicles",
    synopsis: "A rogue android samurai searches Neo-Kyoto.",
    visualStyle: "3D_CEL_ANIME",
    targetDurationMinutes: 45,
    aspectRatio: "16:9",
    defaultLanguage: "JA",
    videoModelId: "animeflow-motion-3d",
    imageModelId: "gemini-nano-banana-2.1",
    storyModelId: "gemini-3.8-flash",
    status: "draft",
    createdAt: ts(),
    updatedAt: ts(),
  };
}

function validCharacter(userId) {
  return {
    userId,
    name: "Ren Tachibana",
    role: "Protagonist",
    visualDna: "Silver messy hair, crimson cybernetic eye, navy haori jacket",
    voiceLanguage: "JA",
    voicePitch: 1.0,
    voiceSpeed: 1.05,
    defaultEmotion: "Determined",
    referenceImagePath: "",
    createdAt: ts(),
    updatedAt: ts(),
  };
}

function validScene(userId, projectId) {
  return {
    userId,
    projectId,
    orderIndex: 0,
    actNumber: 1,
    title: "Rain over Shibuya Spire",
    visualPrompt: "3D anime cyberpunk rooftop in heavy neon rain",
    dialogueText: "We move before dawn.",
    dialogueLanguage: "EN",
    speakerCharacterId: "char_1",
    characterIds: ["char_1"],
    emotion: "Determined",
    cameraMovement: "DOLLY_FORWARD",
    soundEffect: "RAIN",
    bgmMood: "CYBER_SYNTH",
    durationSeconds: 6,
    keyframePath: "",
    videoClipPath: "",
    audioPath: "",
    renderStatus: "pending",
    createdAt: ts(),
    updatedAt: ts(),
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write projects or characters", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/projects`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/characters`).get());
  await assertFails(unauthDb.doc(`users/${ALICE_UID}/projects/proj_1`).set(validProject(ALICE_UID)));
});

test("Owner can create, read, update, and list their own projects and characters", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.doc(`users/${ALICE_UID}/projects/proj_1`).set(validProject(ALICE_UID)));
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/projects`).get());
  await assertSucceeds(aliceDb.doc(`users/${ALICE_UID}/projects/proj_1`).update({
    title: "Cyber Sakura Chronicles II",
    updatedAt: ts(),
  }));
  await assertSucceeds(aliceDb.doc(`users/${ALICE_UID}/characters/char_1`).set(validCharacter(ALICE_UID)));
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/characters`).get());
});

test("Cross-user access is denied for projects, characters, and scenes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.doc(`users/${ALICE_UID}/projects/proj_1`).set(validProject(ALICE_UID)));
  await assertSucceeds(aliceDb.doc(`users/${ALICE_UID}/projects/proj_1/scenes/scene_1`).set(validScene(ALICE_UID, "proj_1")));

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.doc(`users/${ALICE_UID}/projects/proj_1`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/projects`).get());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/projects/proj_1/scenes/scene_1`).get());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/projects/proj_1`).update({ title: "Hacked", updatedAt: ts() }));
});

test("Scene creation requires existing parent project (Parent Gate)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.doc(`users/${ALICE_UID}/projects/missing_proj/scenes/scene_1`).set(validScene(ALICE_UID, "missing_proj")));
});

test("Shadow update with ghost field and invalid duration are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.doc(`users/${ALICE_UID}/projects/proj_1`).set(validProject(ALICE_UID)));

  // Ghost field
  await assertFails(aliceDb.doc(`users/${ALICE_UID}/projects/proj_1`).update({
    isAdmin: true,
    updatedAt: ts(),
  }));

  // Out-of-bounds targetDurationMinutes (< 15)
  const badProj = validProject(ALICE_UID);
  badProj.targetDurationMinutes = 5;
  await assertFails(aliceDb.doc(`users/${ALICE_UID}/projects/proj_bad`).set(badProj));
});
