import {
	readFileSync
} from 'node:fs';
import {
	before,
	after,
	beforeEach,
	test
} from 'node:test';
import {
	initializeTestEnvironment,
	assertSucceeds,
	assertFails
} from '@firebase/rules-unit-testing';
import {
	doc,
	setDoc,
	getDoc,
	deleteDoc,
	collection,
	getDocs,
	query,
	orderBy,
	documentId,
	limit
} from 'firebase/firestore';
// Proyecto demo reservado: los SDK nunca deben dirigirse a un proyecto real durante estas pruebas.
let env;
before(async () => {
	env = await initializeTestEnvironment({
		projectId: 'demo-energym',
		firestore: {
			host: '127.0.0.1',
			port: Number(process.env.FIRESTORE_EMULATOR_HOST?.split(':').at(-1) || '18080'),
			rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8')
		}
	});
});
after(async () => {
	if (env) await env.cleanup();
});
beforeEach(async () => {
	await env.clearFirestore();
});
function data(kind = 'PROFILE', id = 'profile') {
	return {
		schemaVersion: 1,
		ownerUid: 'alice',
		kind,
		syncId: id,
		payload: '{"fixture":true}',
		revision: 1,
		updatedAt: 1000,
		cloudVersion: 1,
		identity: kind === 'PROFILE' ? '' : 'a'.repeat(64),
		terminal: kind === 'ROUTINE' || kind === 'TRAINING_ARCHIVE'
	};
}
function db(uid) {
	return uid ? env.authenticatedContext(uid).firestore() : env.unauthenticatedContext().firestore();
}
test('own profile create read and versioned update succeed', async () => {
	const own = db('alice');
	const ref = doc(own, 'users/alice/profile/current');
	await assertSucceeds(setDoc(ref, data()));
	await assertSucceeds(getDoc(ref));
	await assertSucceeds(setDoc(ref, {
		...data(),
		payload: '{"changed":true}',
		cloudVersion: 2,
		updatedAt: 1001
	}));
});
test('unauthenticated reads and writes denied', async () => {
	await assertFails(getDoc(doc(db(), 'users/alice/profile/current')));
	await assertFails(setDoc(doc(db(), 'users/alice/profile/current'), data()));
});
test('cross-account reads writes queries denied', async () => {
	await setDoc(doc(db('alice'), 'users/alice/profile/current'), data());
	const other = db('bob');
	await assertFails(getDoc(doc(other, 'users/alice/profile/current')));
	await assertFails(setDoc(doc(other, 'users/alice/profile/current'), data()));
	await assertFails(getDocs(collection(other, 'users/alice/profile')));
});
test('owner spoof schema extra fields wrong path and oversized UTF8 denied', async () => {
	const ref = doc(db('alice'), 'users/alice/profile/current');
	for (const invalid of [{
		...data(),
		ownerUid: 'bob'
	}, {
		...data(),
		schemaVersion: 2
	}, {
		...data(),
		extra: true
	}, {
		...data(),
		revision: 0
	}, {
		...data(),
		payload: '€'.repeat(200001)
	}])
		await assertFails(setDoc(ref, invalid));
	await assertFails(setDoc(doc(db('alice'), 'users/alice/profile/another'), data()));
});
test('old version and non-increasing timestamp update denied', async () => {
	const ref = doc(db('alice'), 'users/alice/profile/current');
	await setDoc(ref, data());
	await assertFails(setDoc(ref, {
		...data(),
		payload: 'changed',
		updatedAt: 1001
	}));
	await assertFails(setDoc(ref, {
		...data(),
		cloudVersion: 2
	}));
});
test('routines immutable and all cloud deletes denied', async () => {
	const ref = doc(db('alice'), 'users/alice/routines/routine-1');
	await assertSucceeds(setDoc(ref, data('ROUTINE', 'routine-1')));
	await assertSucceeds(getDocs(query(collection(db('alice'), 'users/alice/routines'), orderBy(documentId()), limit(100))));
	await assertFails(getDocs(collection(db('bob'), 'users/alice/routines')));
	await assertFails(setDoc(ref, {
		...data('ROUTINE', 'routine-1'),
		cloudVersion: 2,
		updatedAt: 1001
	}));
	await assertFails(deleteDoc(ref));
	const profile = doc(db('alice'), 'users/alice/profile/current');
	await setDoc(profile, data());
	await assertFails(deleteDoc(profile));
});
test('session can finish once without changing historical identity', async () => {
	const ref = doc(db('alice'), 'users/alice/sessions/session-1');
	const draft = data('SESSION', 'session-1');
	await assertSucceeds(setDoc(ref, draft));
	await assertFails(setDoc(ref, {
		...draft,
		identity: 'b'.repeat(64),
		cloudVersion: 2,
		updatedAt: 1001
	}));
	await assertSucceeds(setDoc(ref, {
		...draft,
		terminal: true,
		cloudVersion: 2,
		updatedAt: 1001
	}));
	await assertFails(setDoc(ref, {
		...draft,
		cloudVersion: 3,
		updatedAt: 1002
	}));
	await assertFails(deleteDoc(ref));
});
test('unknown collections denied even for own UID', async () => {
	await assertFails(setDoc(doc(db('alice'), 'users/alice/conversations/id'), data()));
});
function nutrition(kind, id, extra = {}) {
	return {
		...data(kind, id),
		identity: kind === 'FOOD' ? 'b'.repeat(64) : '',
		terminal: false,
		...extra
	};
}
test('own nutrition body weights and food sync with versioned updates', async () => {
	const own = db('alice');
	const body = doc(own, 'users/alice/body/current');
	await assertSucceeds(setDoc(body, nutrition('BODY', 'body')));
	await assertSucceeds(setDoc(body, nutrition('BODY', 'body', {cloudVersion: 2, updatedAt: 1001})));
	const weight = doc(own, 'users/alice/weights/2026-10-15');
	await assertSucceeds(setDoc(weight, nutrition('WEIGHT', '2026-10-15')));
	await assertSucceeds(setDoc(weight, nutrition('WEIGHT', '2026-10-15', {cloudVersion: 2, updatedAt: 1001})));
	await assertFails(setDoc(doc(own, 'users/alice/weights/ayer'), nutrition('WEIGHT', 'ayer')));
	await assertFails(deleteDoc(weight));
	await assertFails(getDoc(doc(db('bob'), 'users/alice/body/current')));
});
test('extras sync only for the owner, with known types and versioned updates', async () => {
	const own = db('alice');
	const ref = doc(own, 'users/alice/extras/measures_all');
	await assertSucceeds(setDoc(ref, nutrition('EXTRA', 'measures_all')));
	await assertSucceeds(setDoc(ref, nutrition('EXTRA', 'measures_all', {cloudVersion: 2, updatedAt: 1001})));
	await assertFails(setDoc(ref, nutrition('EXTRA', 'measures_all', {cloudVersion: 2, updatedAt: 1002})));
	await assertFails(setDoc(doc(own, 'users/alice/extras/photos_all'), nutrition('EXTRA', 'photos_all')));
	await assertFails(setDoc(doc(own, 'users/alice/extras/meals_all'), nutrition('EXTRA', 'meals_all', {terminal: true})));
	await assertFails(deleteDoc(ref));
	await assertFails(getDoc(doc(db('bob'), 'users/alice/extras/measures_all')));
	await assertFails(setDoc(doc(db('bob'), 'users/alice/extras/meals_all'), nutrition('EXTRA', 'meals_all')));
});
test('food can only move once from active to terminal tombstone', async () => {
	const ref = doc(db('alice'), 'users/alice/food/meal-1');
	await assertSucceeds(setDoc(ref, nutrition('FOOD', 'meal-1')));
	await assertFails(setDoc(ref, nutrition('FOOD', 'meal-1', {cloudVersion: 2, updatedAt: 1001, identity: 'c'.repeat(64)})));
	await assertSucceeds(setDoc(ref, nutrition('FOOD', 'meal-1', {cloudVersion: 2, updatedAt: 1001, terminal: true})));
	await assertFails(setDoc(ref, nutrition('FOOD', 'meal-1', {cloudVersion: 3, updatedAt: 1002, terminal: false})));
	await assertFails(deleteDoc(ref));
	await assertFails(setDoc(doc(db('alice'), 'users/alice/food/meal-2'), nutrition('FOOD', 'meal-2', {identity: ''})));
});
test('goals follow the same active to tombstone lifecycle', async () => {
	const ref = doc(db('alice'), 'users/alice/goals/goal-1');
	const goal = (extra = {}) => ({...data('GOAL', 'goal-1'), identity: 'd'.repeat(64), terminal: false, ...extra});
	await assertSucceeds(setDoc(ref, goal()));
	await assertSucceeds(setDoc(ref, goal({cloudVersion: 2, updatedAt: 1001, terminal: true})));
	await assertFails(setDoc(ref, goal({cloudVersion: 3, updatedAt: 1002})));
	await assertFails(getDoc(doc(db('bob'), 'users/alice/goals/goal-1')));
});

test('training archive owner can create read and query immutable tombstones', async () => {
	const own = db('alice');
	const ref = doc(own, 'users/alice/trainingArchive/Routine_archived-1');
	const archive = data('TRAINING_ARCHIVE', 'Routine_archived-1');
	await assertSucceeds(setDoc(ref, archive));
	await assertSucceeds(getDoc(ref));
	await assertSucceeds(getDocs(query(collection(own, 'users/alice/trainingArchive'), orderBy(documentId()), limit(100))));
	await assertFails(setDoc(ref, {...archive, cloudVersion: 2, revision: 2, updatedAt: 1001}));
	await assertFails(deleteDoc(ref));
});

test('training archive denies foreign and unauthenticated reads creates queries and deletes', async () => {
	const path = 'users/alice/trainingArchive/Session_archived-1';
	await setDoc(doc(db('alice'), path), data('TRAINING_ARCHIVE', 'Session_archived-1'));
	for (const uid of [undefined, 'bob']) {
		const client = db(uid);
		await assertFails(getDoc(doc(client, path)));
		await assertFails(getDocs(collection(client, 'users/alice/trainingArchive')));
		await assertFails(setDoc(doc(client, 'users/alice/trainingArchive/Session_new-1'),
			data('TRAINING_ARCHIVE', 'Session_new-1')));
		await assertFails(deleteDoc(doc(client, path)));
	}
});

test('training archive rejects invalid envelope payload owner identity and initial version', async () => {
	const ref = doc(db('alice'), 'users/alice/trainingArchive/Routine_invalid-1');
	const archive = data('TRAINING_ARCHIVE', 'Routine_invalid-1');
	for (const invalid of [
		{...archive, ownerUid: 'bob'}, {...archive, kind: 'ROUTINE'}, {...archive, syncId: 'different'},
		{...archive, terminal: false}, {...archive, identity: ''}, {...archive, identity: 'g'.repeat(64)},
		{...archive, cloudVersion: 2}, {...archive, revision: 0}, {...archive, updatedAt: 0},
		{...archive, payload: ''}, {...archive, payload: {target: 'routine'}},
		{...archive, payload: '€'.repeat(200001)}, {...archive, extra: true}
	]) await assertFails(setDoc(ref, invalid));
});

test('food edits keep identity until the terminal tombstone then never resurrect', async () => {
	const ref = doc(db('alice'), 'users/alice/food/meal-edited');
	const original = nutrition('FOOD', 'meal-edited');
	await assertSucceeds(setDoc(ref, original));
	await assertSucceeds(setDoc(ref, {...original, payload: '{"amount":150}', cloudVersion: 2, revision: 2, updatedAt: 1001}));
	await assertFails(setDoc(ref, {...original, identity: 'c'.repeat(64), cloudVersion: 3, revision: 3, updatedAt: 1002}));
	await assertSucceeds(setDoc(ref, {...original, terminal: true, cloudVersion: 3, revision: 3, updatedAt: 1002}));
	await assertFails(setDoc(ref, {...original, cloudVersion: 4, revision: 4, updatedAt: 1003}));
});

test('quota documents deny mobile reads writes deletes and queries for every identity', async () => {
	await env.withSecurityRulesDisabled(async (context) => {
		await setDoc(doc(context.firestore(), 'assistant_quotas/energym_global'), {
			day: '2026-10-15', dayCount: 1, minutePeriod: 29869680, minuteCount: 1
		});
	});
	for (const uid of [undefined, 'alice', 'bob']) {
		const client = db(uid);
		await assertFails(getDoc(doc(client, 'assistant_quotas/energym_global')));
		await assertFails(setDoc(doc(client, 'assistant_quotas/energym_global'), {dayCount: 0}));
		await assertFails(setDoc(doc(client, 'assistant_quotas/energym_u_fixture'), {dayCount: 0}));
		await assertFails(deleteDoc(doc(client, 'assistant_quotas/energym_global')));
		await assertFails(getDocs(collection(client, 'assistant_quotas')));
		await assertFails(setDoc(doc(client, 'assistant_quotas/energym_global/nested/counter'), {dayCount: 0}));
	}
});
