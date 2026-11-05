import 'dart:io';
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
  });

  test('schema 3 old encrypted journal migrates losslessly to chunked vault', () async {
    final text = '真实记录和隐私，只能我自己决定是否给 AI 看。' * 15000;
    FlutterSecureStorage.setMockInitialValues({'journal:legacy_01': text});
    SharedPreferences.setMockInitialValues({});
    final dir = await getDatabasesPath();
    final dbPath = join(dir, 'zhixing_compass_v3.db');
    await deleteDatabase(dbPath);
    final vaultDir = Directory(join(dir, 'journal_vault_v1'));
    if (await vaultDir.exists()) await vaultDir.delete(recursive: true);

    final old = await openDatabase(dbPath, version: 3, onCreate: (db, version) async {
      await db.execute('''CREATE TABLE entries (
        id TEXT PRIMARY KEY, kind TEXT NOT NULL, title TEXT NOT NULL,
        body TEXT NOT NULL, status TEXT NOT NULL, origin TEXT NOT NULL,
        created_at TEXT NOT NULL, updated_at TEXT NOT NULL, meta TEXT NOT NULL)''');
      await db.execute('''CREATE TABLE source_notes (
        id TEXT PRIMARY KEY, source_id TEXT NOT NULL, title TEXT NOT NULL,
        body TEXT NOT NULL, chapter TEXT NOT NULL, revision INTEGER NOT NULL,
        created_at TEXT NOT NULL, updated_at TEXT NOT NULL, deleted_at TEXT)''');
      await db.insert('entries', {
        'id': 'legacy_01', 'kind': 'journal', 'title': '旧日记',
        'body': '', 'status': '私密', 'origin': '',
        'created_at': '2026-01-01T00:00:00',
        'updated_at': '2026-01-01T00:00:00', 'meta': '{}',
      });
    });
    await old.close();
    final store = await CompassStore.open();
    expect(await store.privateJournalBody('legacy_01'), text);
    expect(await store.journalHistory('legacy_01'), hasLength(1));
    expect(await const FlutterSecureStorage().read(key: 'journal:legacy_01'), isNull);
    expect(store.byId('legacy_01')?.body, isEmpty);
    final again = await store.privateJournalBody('legacy_01');
    expect(again, text);
    await store.closeForTests();
    await deleteDatabase(dbPath);
    if (await vaultDir.exists()) await vaultDir.delete(recursive: true);
  });
}
