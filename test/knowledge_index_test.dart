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
    databaseFactory=ffi.databaseFactoryFfi;
    FlutterSecureStorage.setMockInitialValues({});
  });
  late String path;
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    path=join(await getDatabasesPath(),'zhixing_compass_v3.db');
    await deleteDatabase(path);
  });
  tearDown(() async {
    await Future<void>.delayed(const Duration(milliseconds:35));
    await deleteDatabase(path);
  });

  test('retrieves relevant knowledge from the MIDDLE of a long document', () async {
    final store=await CompassStore.open();
    final body=('普通背景记录及阅读笔记。\n'*400)+
      '关键实验：氮掺杂石墨烯的蜂窝形貌使钠离子扩散势垒明显降低。\n'+
      ('后续常规章节。\n'*450);
    final source=await store.add(kind:'source',title:'长篇新能源材料报告',body:body);
    final hits=await store.findKnowledge('氮掺杂石墨烯 蜂窝形貌 钠离子扩散');
    expect(hits,isNotEmpty);
    expect(hits.first.sourceId,source.id);
    expect(hits.first.body,contains('蜂窝形貌'));
    expect(hits.first.startOffset,greaterThan(1000));
    final citation=await store.knowledge.lookup(hits.first.chunkId);
    expect(citation?.sourceId,source.id);
    expect(citation?.body,contains('钠离子扩散'));
    await store.closeForTests();
  });

  test('incremental note edits and deletion refresh evidence index', () async {
    final store=await CompassStore.open();
    final source=await store.add(kind:'source',title:'阅读记录',body:'已开始阅读');
    final note=await store.addNote(sourceId:source.id,title:'第一天',
      body:'双钙钛矿拓扑声子模式决定局部响应');
    expect((await store.findKnowledge('双钙钛矿拓扑声子模式')).first.sourceId,source.id);
    await store.updateNote(note,title:'第二天',body:'新结论：二维电解质界面原子重排',chapter:'新章节');
    expect(await store.findKnowledge('双钙钛矿拓扑声子模式'),isEmpty);
    expect(await store.findKnowledge('电解质界面原子重排'),isNotEmpty);
    await store.removeNote(store.notesForSource(source.id).single);
    expect(await store.findKnowledge('电解质界面原子重排'),isEmpty);
    await store.closeForTests();
  });

  test('private journal is never indexed but ordinary knowledge cards are', () async {
    final store=await CompassStore.open();
    final before=await store.knowledge.count();
    await store.add(kind:'journal',title:'完全私密',
      body:'蓝色小熊私密日记重要口令，绝对不能被 AI 搜到',status:'私密');
    expect(await store.knowledge.count(),before);
    expect(await store.findKnowledge('蓝色小熊私密日记'),isEmpty);
    await store.add(kind:'card',title:'可索引知识卡',
      body:'可公开的实验验证思路和边界条件');
    expect(await store.findKnowledge('实验验证思路'),isNotEmpty);
    await store.closeForTests();
  });

  test('deleting source removes its citation and body from index', () async {
    final store=await CompassStore.open();
    final source=await store.add(kind:'source',title:'被删除的材料',
      body:'特殊的高熵尖晶石表面氧空位动力学');
    final hit=(await store.findKnowledge('高熵尖晶石表面氧空位')).first;
    await store.remove(source);
    expect(await store.findKnowledge('高熵尖晶石表面氧空位'),isEmpty);
    expect(await store.knowledge.lookup(hit.chunkId),isNull);
    await store.closeForTests();
  });
}
