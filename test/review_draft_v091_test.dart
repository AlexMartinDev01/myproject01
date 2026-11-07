import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart' as ffi;
import 'package:path/path.dart';
import 'package:zhixing_compass/core/store.dart';
import 'package:zhixing_compass/core/knowledge_candidates.dart';
import 'package:zhixing_compass/core/review_draft_v091.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late String dbPath;
  setUpAll(() {
    ffi.sqfliteFfiInit();
    databaseFactory = ffi.databaseFactoryFfi;
    FlutterSecureStorage.setMockInitialValues({});
  });
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    dbPath = join(await getDatabasesPath(), 'zhixing_compass_v3.db');
    await deleteDatabase(dbPath);
  });
  tearDown(() async {
    await Future<void>.delayed(const Duration(milliseconds: 35));
    await deleteDatabase(dbPath);
  });

  test('draft preserves edits, selection, and merge intent after app restart',
      () async {
    final store = await CompassStore.open();
    final source = await store.add(kind:'source',
      title:'视频笔记',body:'可以验证的原文');
    final existing = await store.add(kind:'card',
      title:'以前的知识',body:'旧内容');
    final draft = KnowledgeReviewDraft(sourceId:source.id,points:[
      KnowledgeDraftPoint(
        candidate:const KnowledgeCandidate(title:'行动成本',
          explanation:'我改写的解释',topic:'职业发展'),
        selected:false),
      KnowledgeDraftPoint(
        candidate:const KnowledgeCandidate(title:'复盘',
          explanation:'复盘才能知道结果',topic:'自我成长'),
        mergeTargetId:existing.id),
    ]);
    await store.saveKnowledgeReviewDraft(draft);
    await store.closeForTests();

    final reopened=await CompassStore.open();
    final restored=await reopened.loadKnowledgeReviewDraft(source.id);
    expect(restored, isNotNull);
    expect(restored!.points,hasLength(2));
    expect(restored.points[0].selected,false);
    expect(restored.points[0].candidate.explanation,'我改写的解释');
    expect(restored.points[1].mergeTargetId,existing.id);
    expect(reopened.byId(source.id)?.body,'可以验证的原文');
    await reopened.closeForTests();
  });

  test('confirmed human selection atomically saves cards and clears draft',
      () async {
    final store=await CompassStore.open();
    final source=await store.add(kind:'source',title:'资料',body:'真实正文');
    await store.saveKnowledgeReviewDraft(
      KnowledgeReviewDraft.fromCandidates(source.id,[
        const KnowledgeCandidate(title:'需要验证',explanation:'先做小实验'),
      ]));
    final ids=await store.saveKnowledgeCandidates([
      const KnowledgeSaveDraft(title:'需要验证',
        explanation:'先做小实验'),
    ],originalSourceId:source.id,offeredSourceIds:[source.id],
      reviewedDraftSourceId:source.id);
    expect(ids,hasLength(1));
    expect(await store.loadKnowledgeReviewDraft(source.id),isNull);
    expect(store.byId(ids.single)?.meta['source_id'],source.id);
    expect(store.byId(source.id)?.body,'真实正文');
    await store.closeForTests();
  });

  test('invalid save keeps draft and leaves knowledge library unchanged',
      () async {
    final store=await CompassStore.open();
    final source=await store.add(kind:'source',title:'保留草稿',body:'原文');
    await store.saveKnowledgeReviewDraft(
      KnowledgeReviewDraft.fromCandidates(source.id,[
        const KnowledgeCandidate(title:'草稿',explanation:'解释'),
      ]));
    await expectLater(store.saveKnowledgeCandidates([
      const KnowledgeSaveDraft(title:'',explanation:'不合法'),
    ],originalSourceId:source.id,reviewedDraftSourceId:source.id),
      throwsFormatException);
    expect(await store.loadKnowledgeReviewDraft(source.id),isNotNull);
    expect(store.ofKind('card'),isEmpty);
    await store.closeForTests();
  });

  test('private journal cannot become a knowledge review draft', () async {
    final store=await CompassStore.open();
    final journal=await store.add(kind:'journal',
      title:'私密',body:'不可泄漏的正文');
    final draft=KnowledgeReviewDraft.fromCandidates(journal.id,[
      const KnowledgeCandidate(title:'不允许',explanation:'内容'),
    ]);
    await expectLater(store.saveKnowledgeReviewDraft(draft),
      throwsFormatException);
    expect(await store.loadKnowledgeReviewDraft(journal.id),isNull);
    await store.closeForTests();
  });

  test('deleting a source cascades and removes its unsaved review', () async {
    final store=await CompassStore.open();
    final source=await store.add(kind:'source',title:'待删除',body:'文本');
    await store.saveKnowledgeReviewDraft(
      KnowledgeReviewDraft.fromCandidates(source.id,[
        const KnowledgeCandidate(title:'想法',explanation:'解释'),
      ]));
    await store.remove(source);
    expect(await store.loadKnowledgeReviewDraft(source.id),isNull);
    await store.closeForTests();
  });
}
