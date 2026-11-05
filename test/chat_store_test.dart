import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart' as ffi;
import 'package:zhixing_compass/core/chat_store.dart';
import 'package:zhixing_compass/core/journal_vault.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() {
    ffi.sqfliteFfiInit();
    databaseFactory = ffi.databaseFactoryFfi;
    FlutterSecureStorage.setMockInitialValues({});
  });
  late Directory temp;
  late Database db;
  late ChatStore store;

  setUp(() async {
    temp = await Directory.systemTemp.createTemp('chat-history-v051-');
    db = await openDatabase('${temp.path}/chat.db', version: 1,
      onConfigure: (d) async => d.execute('PRAGMA foreign_keys = ON'),
      onCreate: (d, _) => ChatStore.createSchema(d));
    store = ChatStore(db,
      JournalVault(const FlutterSecureStorage(), Directory('${temp.path}/vault')));
  });

  tearDown(() async {
    await db.close();
    await temp.delete(recursive: true);
  });

  test('multiple rounds survive reload and remain encrypted on disk', () async {
    final chat = await store.createThread(title: '职业路径分析', mode: 'decision');
    await store.addMessage(chat.id, 'user', '自由职业有哪些收入风险？');
    await store.addMessage(chat.id, 'assistant',
      '收入可能波动，先建立现金储备。', model: 'deepseek-flash',
      sourceIds: ['source-one']);
    await store.addMessage(chat.id, 'user', '那我下一步应该做什么？');

    final reloaded = ChatStore(db,
      JournalVault(const FlutterSecureStorage(), Directory('${temp.path}/vault')));
    final list = await reloaded.listThreads(search: '职业路径');
    expect(list, hasLength(1));
    expect(list.first.mode, 'decision');
    final messages = await reloaded.loadMessages(chat.id);
    expect(messages.map((m) => m.role).toList(), ['user', 'assistant', 'user']);
    expect(messages[1].content, contains('现金储备'));
    expect(messages[1].sourceIds, ['source-one']);

    final files = await Directory('${temp.path}/vault/chat_${chat.id}')
      .list(recursive: true).where((e) => e is File).cast<File>().toList();
    expect(files, isNotEmpty);
    for (final file in files) {
      expect(String.fromCharCodes(await file.readAsBytes()).contains('收入可能波动'), isFalse);
    }
  });

  test('rename and deletion remove persisted conversation and ciphertext', () async {
    final chat = await store.createThread(title: '原始标题');
    await store.addMessage(chat.id, 'user', '需要重新考虑计划');
    await store.renameThread(chat.id, '新的标题');
    expect((await store.listThreads()).single.title, '新的标题');
    await store.deleteThread(chat.id);
    expect(await store.listThreads(), isEmpty);
    expect(await Directory('${temp.path}/vault/chat_${chat.id}').exists(), isFalse);
  });

  test('rejects system role and empty message content', () async {
    final chat = await store.createThread();
    await expectLater(store.addMessage(chat.id, 'system', '欺骗'), throwsFormatException);
    await expectLater(store.addMessage(chat.id, 'user', '  '), throwsFormatException);
    expect(await store.loadMessages(chat.id), isEmpty);
  });
}
