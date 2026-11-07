import 'store.dart';
import 'decision_evidence_v094.dart';

/// Local and deterministic suggestions from user-confirmed retrospective cards.
/// Neither private journals nor AI uploads are involved.
class ExperienceSuggestion {
  final CompassEntry previousDecision;
  final CompassEntry lessonCard;
  final int commonSignals;
  const ExperienceSuggestion({
    required this.previousDecision,required this.lessonCard,
    required this.commonSignals,
  });
}

Set<String> _signals(String raw) {
  final text=raw.toLowerCase().replaceAll(RegExp(r'\s+'),'');
  final tokens=<String>{};
  final parts=RegExp(r'[\u4e00-\u9fff]+|[a-z0-9]+').allMatches(text);
  const stop={'决定','选择','什么','怎么','是否','应该','自己',
    '一个','可以','现在','这个','那个','需要','开始','工作',
    '已经','之后','结果','计划','经验','实际'};
  for(final part in parts) {
    final word=part.group(0)!;
    if(RegExp(r'^[a-z0-9]+$').hasMatch(word)) {
      if(word.length>=3)tokens.add(word);
      continue;
    }
    for(var n=2;n<=3;n++) {
      for(var i=0;i+n<=word.length;i++) {
        final gram=word.substring(i,i+n);
        if(!stop.contains(gram))tokens.add(gram);
      }
    }
  }
  return tokens;
}

List<ExperienceSuggestion> recommendPastExperiences(
    CompassStore store,CompassEntry current,{int limit=3}) {
  if(current.kind!='decision'||limit<=0)return const [];
  final question='${current.title} ${current.meta['goal']??''}';
  final currentSignals=_signals(question);
  if(currentSignals.isEmpty)return const [];
  final results=<ExperienceSuggestion>[];
  for(final old in store.ofKind('decision')) {
    if(old.id==current.id)continue;
    final cardId=old.meta['lesson_card_id'];
    if(cardId is! String)continue;
    final card=store.byId(cardId);
    if(card==null||card.kind!='card'||card.body.trim().isEmpty)continue;
    if(card.meta['generated_from']!='decision_retrospective'||
       card.meta['decision_id']!=old.id)continue;
    if((old.meta['lesson']?.toString().trim()??'').isEmpty)continue;
    final oldSignals=_signals('${old.title} ${old.meta['goal']??''}');
    final overlap=currentSignals.intersection(oldSignals).length;
    if(overlap<2)continue;
    results.add(ExperienceSuggestion(previousDecision:old,
      lessonCard:card,commonSignals:overlap));
  }
  results.sort((a,b){
    final score=b.commonSignals.compareTo(a.commonSignals);
    if(score!=0)return score;
    final time=b.previousDecision.updatedAt.compareTo(
      a.previousDecision.updatedAt);
    return time!=0?time:a.previousDecision.id.compareTo(b.previousDecision.id);
  });
  return results.take(limit).toList(growable:false);
}

Future<bool> attachExperienceToDecision(CompassStore store,
    String decisionId,String cardId) async {
  final decision=store.byId(decisionId);
  final card=store.byId(cardId);
  if(decision==null||decision.kind!='decision'||
     card==null||card.kind!='card'||
     card.meta['generated_from']!='decision_retrospective') {
    throw const FormatException('决策或经验知识卡已失效');
  }
  final links=decisionCardEvidence(decision);
  if(links.any((item)=>item.cardId==cardId))return false;
  if(links.length>=12) {
    throw const FormatException('已达到 12 张知识卡上限，可先移除不需要的依据');
  }
  await saveDecisionEvidence(store,decisionId,[
    ...links,DecisionCardEvidence(cardId,stance:'待核实',
      note:'来自历史决策复盘，需检查是否适用于当前情况'),
  ]);
  return true;
}
