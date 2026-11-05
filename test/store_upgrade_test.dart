import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart' as ffi;
import 'package:path/path.dart';
import 'package:zhixing_compass/core/store.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() {
    ffi.sqfliteFfiInit();
    databaseFactory = ffi.databaseFactoryFfi;
    FlutterSecureStorage.setMockInitialValues({});
  });
  late String dbPath;
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    dbPath = join(await getDatabasesPath(), 'zhixing_compass_v3.db');
    await deleteDatabase(dbPath);
  });
  tearDown(() async {
    await Future<void>.delayed(const Duration(milliseconds: 35));
    await deleteDatabase(dbPath);
  });

  test('upgrade schema 1 preserves legacy source body and ID', () async {
    final db = await openDatabase(dbPath, version: 1, onCreate: (db, version) async {
      await db.execute('''CREATE TABLE entries (
        id TEXT PRIMARY KEY, kind TEXT NOT NULL, title TEXT NOT NULL,
        body TEXT NOT NULL, status TEXT NOT NULL, origin TEXT NOT NULL,
        created_at TEXT NOT NULL, updated_at TEXT NOT NULL, meta TEXT NOT NULL)''');
      await db.insert('entries', {
        'id': 'legacy-book', 'kind': 'source', 'title': '原有书籍',
        'body': '从 v1 保存的旧摘录', 'status': '待处理', 'origin': '',
        'created_at': '2026-01-01T00:00:00',
        'updated_at': '2026-01-01T00:00:00', 'meta': '{}',
      });
    });
    await db.close();
    final store = await CompassStore.open();
    expect(store.byId('legacy-book')?.body, '从 v1 保存的旧摘录');
    final versions = await store.sourceVersions('legacy-book');
    expect(versions, hasLength(1));
    expect(versions.single['body'], '从 v1 保存的旧摘录');
    await store.addNote(sourceId: 'legacy-book', title: '第一章',
      body: '新的补充理解', chapter: '第 1 章');
    expect(store.notesForSource('legacy-book'), hasLength(1));
    store.dispose();
  });

  test('append, edit, revision history, restore and delete are persistent', () async {
    final store = await CompassStore.open();
    final source = await store.add(kind: 'source', title: '一本书', body: '原始摘录');
    final note = await store.addNote(sourceId: source.id,
      title: '第 1 天', body: '我的理解 A', chapter: '第一章');
    expect(note.revision, 1);
    await store.updateNote(note, title: '第 2 天', body: '我的理解 B', chapter: '第二章');
    final second = store.notesForSource(source.id).single;
    expect(second.revision, 2);
    expect(await store.noteHistory(note.id), hasLength(2));
    await store.restoreNote(second, 1);
    final restored = store.notesForSource(source.id).single;
    expect(restored.body, '我的理解 A');
    expect(restored.revision, 3);
    expect(await store.noteHistory(note.id), hasLength(3));
    await expectLater(
      store.updateNote(note, title: '旧版本', body: '错误覆盖', chapter: ''),
      throwsStateError,
    );
    expect(store.notesForSource(source.id).single.body, '我的理解 A');
    await store.removeNote(restored);
    expect(store.notesForSource(source.id), isEmpty);
    expect(store.byId(source.id)?.body, '原始摘录');
    store.dispose();
  });

  test('V2 backup imports note history atomically and rejects private journals', () async {
    final store = await CompassStore.open();
    final source = await store.add(kind: 'source', title: '资料', body: '可公开摘录');
    await store.addNote(sourceId: source.id, title: '笔记', body: '后来添加的想法');
    final backup = await store.exportJson();
    expect(backup, contains('zhixing-compass-v2'));
    expect(backup, isNot(contains('私密的日记正文')));
    store.dispose();
    await Future<void>.delayed(const Duration(milliseconds: 35));
    await deleteDatabase(dbPath);

    final fresh = await CompassStore.open();
    expect(await fresh.importJson(backup), 1);
    expect(fresh.notesForSource(source.id), hasLength(1));
    expect(fresh.notesForSource(source.id).single.body, '后来添加的想法');
    expect(await fresh.importJson(backup), 0); // idempotent, no duplicate
    final tampered = jsonDecode(backup) as Map<String, dynamic>;
    (tampered['entries'] as List).add({
      'id': 'hidden', 'kind': 'journal', 'title': '私密', 'body': '',
      'status': '私密', 'origin': '',
      'created_at': '2026-01-01T00:00:00',
      'updated_at': '2026-01-01T00:00:00', 'meta': '{}'
    });
    await expectLater(fresh.importJson(jsonEncode(tampered)), throwsFormatException);
    expect(fresh.byId('hidden'), isNull);
    fresh.dispose();
  });
}