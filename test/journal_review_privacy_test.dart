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
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await deleteDatabase(join(await getDatabasesPath(), 'zhixing_compass_v3.db'));
  });
  tearDown(() async {
    await deleteDatabase(join(await getDatabasesPath(), 'zhixing_compass_v3.db'));
  });

  test('private local review saves encrypted and is excluded from ordinary export', () async {
    final store = await CompassStore.open();
    const content = '重要的事：完成小结\\n\\n继续保持：阅读\\n\\n下一步：坚持记录';
    final entry = await store.add(kind: 'journal',
      title: '每周小结 · 11月2日—11月8日',
      body: content, status: '私密', meta: {'local_review': true});
    expect(entry.body, isEmpty, reason: 'Private journal plaintext is never stored in entry rows');
    expect(await store.privateJournalBody(entry.id), content);
    final backup = await store.exportJson();
    expect(backup, isNot(contains('完成小结')));
    expect(backup, isNot(contains('阅读')));
    await store.closeForTests();
  });
}
