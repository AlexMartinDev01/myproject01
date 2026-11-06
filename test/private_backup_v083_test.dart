import 'dart:convert';
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
  late String dbPath;
  late Directory vaultDir;
  setUp(() async {
    FlutterSecureStorage.setMockInitialValues({});
    SharedPreferences.setMockInitialValues({});
    final path=await getDatabasesPath();
    dbPath=join(path,'zhixing_compass_v3.db');
    vaultDir=Directory(join(path,'journal_vault_v1'));
    await deleteDatabase(dbPath);
    if(await vaultDir.exists())await vaultDir.delete(recursive:true);
  });
  tearDown(() async {
    await deleteDatabase(dbPath);
    if(await vaultDir.exists())await vaultDir.delete(recursive:true);
  });

  test('password-protected export restores revisions and attachments on new device',
      () async {
    final store=await CompassStore.open();
    final journal=await store.add(kind:'journal',title:'只属于我的秘密',
      body:'这是第一版正文',status:'私密');
    await store.update(journal.copyWith(),privateBody:'这是第二版正文');
    final src=File(join(await getDatabasesPath(),'private_test_file.pdf'));
    await src.writeAsBytes(utf8.encode('%PDF-1.7\n私密附件内容'));
    await store.addJournalAttachment(journal.id,src,'私密.pdf');
    final originalHistory=await store.journalHistory(journal.id);
    expect(originalHistory.length,2);
    final archive=await store.exportPrivateBackup('correct-horse-2026');
    expect(utf8.decode(archive),isNot(contains('只属于我的秘密')));
    expect(utf8.decode(archive),isNot(contains('这是第二版正文')));
    await expectLater(
      store.inspectPrivateBackup(archive,'wrong-password-2026'),
      throwsA(anything));
    final info=await store.inspectPrivateBackup(archive,'correct-horse-2026');
    expect(info['journals'],1);
    expect(info['revisions'],2);
    expect(info['attachments'],1);
    await store.closeForTests();
    await deleteDatabase(dbPath);
    if(await vaultDir.exists())await vaultDir.delete(recursive:true);
    FlutterSecureStorage.setMockInitialValues({}); // new device key
    final restored=await CompassStore.open();
    expect(await restored.importPrivateBackup(archive,'correct-horse-2026'),1);
    expect(restored.byId(journal.id)?.title,'只属于我的秘密');
    expect(await restored.privateJournalBody(journal.id),'这是第二版正文');
    expect((await restored.journalHistory(journal.id)).length,2);
    final asset=(await restored.journalAttachments(journal.id)).single;
    expect(utf8.decode(await restored.readJournalAttachmentBytes(journal.id,
      asset['id'] as String)),'%PDF-1.7\n私密附件内容');
    expect(await restored.importPrivateBackup(archive,'correct-horse-2026'),0);
    expect((await restored.journalHistory(journal.id)).length,2);
    await restored.closeForTests();
    if(await src.exists())await src.delete();
  });

  test('wrong password and modified archive do not change existing journals', () async {
    final store=await CompassStore.open();
    final entry=await store.add(kind:'journal',title:'原有日记',
      body:'不能覆盖原文',status:'私密');
    final backup=await store.exportPrivateBackup('test-passphrase-123');
    await expectLater(
      store.importPrivateBackup(backup,'not-the-password-456'),
      throwsA(anything));
    final tampered=backup.toList();
    tampered[tampered.length-12] ^= 1;
    await expectLater(
      store.importPrivateBackup(
        // Modified JSON may be malformed, which must also be rejected.
        Uri.dataFromBytes(tampered).contentAsBytes() as dynamic,
        'test-passphrase-123'),throwsA(anything));
    expect(store.ofKind('journal').length,1);
    expect(await store.privateJournalBody(entry.id),'不能覆盖原文');
    await store.closeForTests();
  });
}
