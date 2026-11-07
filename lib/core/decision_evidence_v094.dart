import 'store.dart';

const decisionEvidenceTypes = <String>['支持','反对','待核实'];

class DecisionCardEvidence {
  final String cardId;
  final String stance;
  final String note;
  const DecisionCardEvidence(this.cardId,{this.stance='待核实',this.note=''});

  Map<String,dynamic> toJson()=>{
    'card_id':cardId,'stance':stance,'note':note,
  };

  static DecisionCardEvidence? fromJson(Object? row) {
    if(row is! Map || row['card_id'] is! String) return null;
    final id=row['card_id'] as String;
    if(id.isEmpty) return null;
    final raw=row['stance'];
    final stance=raw is String && decisionEvidenceTypes.contains(raw)
        ? raw : '待核实';
    final note=row['note'] is String ? row['note'] as String : '';
    return DecisionCardEvidence(id,stance:stance,note:note);
  }
}

List<DecisionCardEvidence> decisionCardEvidence(CompassEntry decision) {
  if(decision.kind!='decision') return const [];
  final raw=decision.meta['evidence_cards'];
  if(raw is! List) return const [];
  final seen=<String>{};
  return raw.map(DecisionCardEvidence.fromJson).whereType<DecisionCardEvidence>()
    .where((e)=>seen.add(e.cardId)).take(12).toList();
}

List<CompassEntry> availableDecisionCards(CompassStore store,
    CompassEntry decision) {
  return decisionCardEvidence(decision)
    .map((e)=>store.byId(e.cardId))
    .whereType<CompassEntry>()
    .where((e)=>e.kind=='card' && e.body.trim().isNotEmpty).toList();
}

Future<void> saveDecisionEvidence(CompassStore store,String decisionId,
    List<DecisionCardEvidence> evidence) async {
  final decision=store.byId(decisionId);
  if(decision==null || decision.kind!='decision') {
    throw StateError('决策记录不存在');
  }
  if(evidence.length>12 || evidence.map((e)=>e.cardId).toSet().length!=evidence.length) {
    throw const FormatException('每条决策最多关联 12 张不重复的知识卡');
  }
  for(final e in evidence) {
    final card=store.byId(e.cardId);
    if(card==null || card.kind!='card') {
      throw const FormatException('只能引用仍然存在的知识卡');
    }
    if(!decisionEvidenceTypes.contains(e.stance)||e.note.length>400) {
      throw const FormatException('证据分类或说明不合法');
    }
  }
  await store.update(decision.copyWith(meta:{
    ...decision.meta,
    'evidence_cards':evidence.map((e)=>e.toJson()).toList(),
  }));
}

/// User-authored classifications are not independently verified statements.
String decisionEvidenceBrief(CompassEntry decision,CompassStore store) {
  final links=decisionCardEvidence(decision);
  final lines=<String>[];
  for(final item in links) {
    final card=store.byId(item.cardId);
    if(card?.kind!='card') continue;
    final note=item.note.trim();
    lines.add('【${item.stance}观点】${card!.title}'
      '${note.isEmpty ? '' : '；用户备注：$note'}');
  }
  return lines.join('\n');
}
