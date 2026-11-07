import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart' as ffi;
import 'package:path/path.dart';
import 'package:zhixing_compass/core/store.dart';
import 'package:zhixing_compass/core/decision_evidence_v094.dart';
import 'package:zhixing_compass/core/decision_feedback_v096.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() {
    ffi.sqfliteFfiInit();
    databaseFactory=ffi.databaseFactoryFfi;
    FlutterSecureStorage.setMockInitialValues({});
  });
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await deleteDatabase(join(await getDatabasesPath(),'zhixing_compass_v3.db'));
  });
  tearDown(() async {
    await Future<void>.delayed(const Duration(milliseconds:35));
    await deleteDatabase(join(await getDatabasesPath(),'zhixing_compass_v3.db'));
  });
  test('a new similar decision can reuse a user-approved past lesson',
    () async {
    final store=await CompassStore.open();
    final old=await store.add(kind:'decision',title:'是否开始自媒体创作',
      meta:{'goal':'创作视频验证内容方向',
        'lesson':'先低成本验证视频方向'});
    final lesson=await store.add(kind:'card',title:'创作复盘',
      body:'先小范围验证视频选题',
      meta:{'generated_from':'decision_retrospective','decision_id':old.id});
    await store.update(old.copyWith(meta:{...old.meta,
      'lesson_card_id':lesson.id}));
    final current=await store.add(kind:'decision',title:'怎样开展自媒体视频创作',
      meta:{'goal':'优化视频创作方向'});
    final suggestions=recommendPastExperiences(store,current);
    expect(suggestions,isNotEmpty);
    expect(suggestions.first.lessonCard.id,lesson.id);
    expect(decisionCardEvidence(store.byId(current.id)!),isEmpty);
    expect(await attachExperienceToDecision(store,current.id,lesson.id),true);
    expect(await attachExperienceToDecision(store,current.id,lesson.id),false);
    final links=decisionCardEvidence(store.byId(current.id)!);
    expect(links,hasLength(1));
    expect(links.single.stance,'待核实');
    await store.closeForTests();
  });
  test('private journals, fake provenance, and unrelated topics are excluded',
    () async {
    final store=await CompassStore.open();
    final old=await store.add(kind:'decision',title:'职业发展道路',
      meta:{'goal':'职业成长规划','lesson':'验证后再转型'});
    final fake=await store.add(kind:'card',title:'未经确认的笔记',
      body:'不是复盘',meta:{'generated_from':'decision_retrospective',
        'decision_id':'different-id'});
    await store.update(old.copyWith(meta:{...old.meta,'lesson_card_id':fake.id}));
    await store.add(kind:'journal',title:'职业发展私密日记',
      body:'绝对不能出现在推荐里');
    final current=await store.add(kind:'decision',title:'职业发展成长规划',
      meta:{'goal':'职业成长'});
    expect(recommendPastExperiences(store,current),isEmpty);
    await expectLater(attachExperienceToDecision(store,current.id,
      'not-an-existing-card'),throwsFormatException);
    expect(decisionCardEvidence(store.byId(current.id)!),isEmpty);
    await store.closeForTests();
  });
  test('deleted lessons cannot be suggested or attached',() async {
    final store=await CompassStore.open();
    final old=await store.add(kind:'decision',title:'研究论文方向',
      meta:{'goal':'研究论文的选择','lesson':'实验可重复'});
    final lesson=await store.add(kind:'card',title:'研究论文复盘',
      body:'选择可复现论文',meta:{
        'generated_from':'decision_retrospective','decision_id':old.id});
    await store.update(old.copyWith(meta:{...old.meta,'lesson_card_id':lesson.id}));
    final current=await store.add(kind:'decision',title:'研究论文课题',
      meta:{'goal':'研究论文选题'});
    expect(recommendPastExperiences(store,current),isNotEmpty);
    await store.remove(lesson);
    expect(recommendPastExperiences(store,current),isEmpty);
    await expectLater(attachExperienceToDecision(store,current.id,lesson.id),
      throwsFormatException);
    await store.closeForTests();
  });
}
