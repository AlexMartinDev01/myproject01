import 'package:flutter/material.dart';
import 'main.dart';
import 'core/store.dart';
import 'core/decision_evidence_v094.dart';

class DecisionEvidencePickerPage extends StatefulWidget {
  final String decisionId;
  const DecisionEvidencePickerPage({super.key,required this.decisionId});
  @override
  State<DecisionEvidencePickerPage> createState()=>_DecisionEvidencePickerPageState();
}

class _DecisionEvidencePickerPageState extends State<DecisionEvidencePickerPage> {
  final search=TextEditingController();
  final chosen=<String,DecisionCardEvidence>{};
  bool initialized=false;
  bool saving=false;
  @override
  void dispose(){search.dispose();super.dispose();}

  Future<void> save() async {
    if(saving)return;
    setState(()=>saving=true);
    try{
      await saveDecisionEvidence(CompassScope.of(context),widget.decisionId,
        chosen.values.toList());
      if(mounted){
        notice(context,'已保存 ${chosen.length} 张知识卡作为决策依据');
        Navigator.pop(context);
      }
    }catch(e){
      if(mounted)notice(context,'保存依据失败：$e');
    }finally{
      if(mounted)setState(()=>saving=false);
    }
  }

  @override
  Widget build(BuildContext context){
    final store=CompassScope.of(context);
    final decision=store.byId(widget.decisionId);
    if(decision==null||decision.kind!='decision'){
      return const PageFrame(title:'选择决策依据',children:[Text('决策已不存在')]);
    }
    if(!initialized){
      for(final e in decisionCardEvidence(decision)){
        if(store.byId(e.cardId)?.kind=='card')chosen[e.cardId]=e;
      }
      initialized=true;
    }
    final query=search.text.trim().toLowerCase();
    final cards=store.ofKind('card').where((e)=>
      query.isEmpty||('${e.title} ${e.body}').toLowerCase().contains(query)).toList()
        ..sort((a,b){
          final ac=chosen.containsKey(a.id),bc=chosen.containsKey(b.id);
          if(ac!=bc)return ac?-1:1;
          return a.title.compareTo(b.title);
        });
    return PageFrame(title:'选择知识卡证据',
      subtitle:'自己选择依据并判断支持或反对；分类不代表事实已验证。',
      children:[
        SoftCard(color:const Color(0xFFF2ECF8),child:Column(
          crossAxisAlignment:CrossAxisAlignment.start,children:[
          Text('已选择 ${chosen.length} / 12 张',
            style:const TextStyle(fontWeight:FontWeight.w700)),
          const SizedBox(height:6),
          const Text('只保存卡片关联和你的分类，不复制原文。'
            '可以随时返回修改。',style:TextStyle(height:1.5)),
        ])),
        TextField(controller:search,onChanged:(_)=>setState((){}),
          decoration:const InputDecoration(prefixIcon:Icon(Icons.search),
            hintText:'搜索知识卡')),
        if(cards.isEmpty)const SoftCard(child:Text('还没有匹配的知识卡。先从收藏中提炼、保存知识卡。')),
        for(final card in cards) SoftCard(child:Column(
          crossAxisAlignment:CrossAxisAlignment.start,children:[
          CheckboxListTile(
            contentPadding:EdgeInsets.zero,
            title:Text(card.title),
            subtitle:Text(card.body.length>90 ?
              '${card.body.substring(0,90)}…':card.body,
              maxLines:3,overflow:TextOverflow.ellipsis),
            value:chosen.containsKey(card.id),
            onChanged:saving?null:(checked){
              if(checked==true&&chosen.length>=12){
                notice(context,'最多选择 12 张知识卡');return;
              }
              setState((){
                if(checked==true){
                  chosen[card.id]=DecisionCardEvidence(card.id);
                }else{
                  chosen.remove(card.id);
                }
              });
            },
          ),
          if(chosen.containsKey(card.id))...[
            const SizedBox(height:6),
            DropdownButtonFormField<String>(
              key:ValueKey('stance_${card.id}'),
              initialValue:chosen[card.id]!.stance,
              decoration:const InputDecoration(labelText:'这张卡与当前决策的关系'),
              items:decisionEvidenceTypes.map((type)=>
                DropdownMenuItem(value:type,child:Text(type))).toList(),
              onChanged:saving?null:(value){
                final current=chosen[card.id]!;
                setState(()=>chosen[card.id]=DecisionCardEvidence(card.id,
                  stance:value??current.stance,note:current.note));
              },
            ),
            const SizedBox(height:8),
            TextFormField(
              key:ValueKey('note_${card.id}'),
              initialValue:chosen[card.id]!.note,
              maxLength:400,
              maxLines:2,
              decoration:const InputDecoration(labelText:'我为什么这样判断（可选）'),
              onChanged:(value){
                final current=chosen[card.id];
                if(current!=null)chosen[card.id]=DecisionCardEvidence(card.id,
                  stance:current.stance,note:value);
              }),
          ],
        ])),
        const SizedBox(height:10),
        SizedBox(width:double.infinity,child:FilledButton.icon(
          onPressed:saving?null:save,
          icon:const Icon(Icons.save_outlined),
          label:Text(saving?'正在保存…':'保存决策依据'))),
      ]);
  }
}
