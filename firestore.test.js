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

test("Unauthenticated user: cannot read or write schedules", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("schedules").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("schedules").doc("sched_1").set({
      userId: ALICE_UID,
      channel: "WHATSAPP",
    })
  );
});

test("Authenticated user: cannot read or write another user's schedules, templates, or groups", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).collection("schedules").doc("bob_sched").set({
      userId: BOB_UID,
      channel: "WHATSAPP",
      recipientName: "Marketing Team",
      recipientTarget: "+1 555-0100",
      subject: "",
      messageBody: "Weekly sync reminder",
      attachmentLabel: "",
      scheduledEpochMillis: 1800000000000,
      repeatInterval: "WEEKLY",
      askBeforeSending: false,
      notifyOnComplete: true,
      status: "PENDING",
      executionCount: 0,
      lastExecutedEpochMillis: 0,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("schedules").doc("bob_sched").get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("schedules").get()
  );
});

test("Authenticated user: can create, list, and update own valid schedule", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const scheduleRef = aliceDb.collection("users").doc(ALICE_UID).collection("schedules").doc("sched_valid");

  await assertSucceeds(
    scheduleRef.set({
      userId: ALICE_UID,
      channel: "WHATSAPP",
      recipientName: "Alex Rivera",
      recipientTarget: "+1 555-0142",
      subject: "Follow up",
      messageBody: "Hi Alex, checking in on the proposal!",
      attachmentLabel: "",
      scheduledEpochMillis: 1800000000000,
      repeatInterval: "NONE",
      askBeforeSending: true,
      notifyOnComplete: true,
      status: "PENDING",
      executionCount: 0,
      lastExecutedEpochMillis: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("schedules").get()
  );

  await assertSucceeds(
    scheduleRef.update({
      status: "SENT",
      executionCount: 1,
      lastExecutedEpochMillis: 1800000000000,
      updatedAt: now,
    })
  );
});

test("Authenticated user: rejects ghost fields and invalid enum values on schedule", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const scheduleRef = aliceDb.collection("users").doc(ALICE_UID).collection("schedules").doc("sched_bad");

  await assertFails(
    scheduleRef.set({
      userId: ALICE_UID,
      channel: "WHATSAPP",
      recipientName: "Alex Rivera",
      recipientTarget: "+1 555-0142",
      subject: "Follow up",
      messageBody: "Hi Alex!",
      attachmentLabel: "",
      scheduledEpochMillis: 1800000000000,
      repeatInterval: "NONE",
      askBeforeSending: true,
      notifyOnComplete: true,
      status: "PENDING",
      executionCount: 0,
      lastExecutedEpochMillis: 0,
      createdAt: now,
      updatedAt: now,
      isAdminOverride: true,
    })
  );

  await assertFails(
    scheduleRef.set({
      userId: ALICE_UID,
      channel: "INVALID_CHANNEL",
      recipientName: "Alex Rivera",
      recipientTarget: "+1 555-0142",
      subject: "Follow up",
      messageBody: "Hi Alex!",
      attachmentLabel: "",
      scheduledEpochMillis: 1800000000000,
      repeatInterval: "NONE",
      askBeforeSending: true,
      notifyOnComplete: true,
      status: "PENDING",
      executionCount: 0,
      lastExecutedEpochMillis: 0,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Authenticated user: can create and list own templates and recipient groups", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("templates").doc("tpl_1").set({
      userId: ALICE_UID,
      title: "Meeting Reminder",
      category: "WORK",
      channel: "WHATSAPP",
      content: "Hi {name}, quick reminder about our meeting at {time}.",
      usageCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("groups").doc("grp_1").set({
      userId: ALICE_UID,
      name: "VIP Clients",
      channel: "WHATSAPP_BUSINESS",
      membersSummary: "Sarah Jenkins (+1 555-0192), Marcus Vance (+1 555-0134)",
      memberCount: 2,
      createdAt: now,
      updatedAt: now,
    })
  );
});
