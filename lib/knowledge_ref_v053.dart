import 'package:flutter/material.dart';
import 'main.dart';
import 'screens.dart' show SourceDetailPage, KnowledgeDetailPage;
import 'core/knowledge_index.dart';

/// A citation opens the exact retrieved local text span before the user
/// follows the source. This is not the same as claiming model conclusions are
/// all grounded.
class KnowledgeReferencePage extends StatelessWidget {
  final String chunkId;
  const KnowledgeReferencePage({super.key,required this.chunkId});
  @override
  Widget build(BuildContext context) {
    final store=CompassScope.of(context);
    return Scaffold(appBar:AppBar(title:const Text('知识证据原文')),
      body:FutureBuilder<KnowledgeHit?>(
        future:store.knowledge.lookup(chunkId),
        builder:(ctx,snap) {
          if(snap.hasError)return Center(child:Text('读取来源失败：'+snap.error.toString()));
          if(!snap.hasData)return const Center(child:CircularProgressIndicator());
          final hit=snap.data;
          if(hit==null)return const Center(
            child:Text('来源已被删除或索引发生变化，该引用已失效。'));
          final target=store.byId(hit.sourceId);
          return ListView(padding:const EdgeInsets.all(18),children:[
            Text(hit.title,style:const TextStyle(fontWeight:FontWeight.w700,fontSize:18)),
            const SizedBox(height:8),
            Text('第 '+hit.part.toString()+' 段 · 原文字符区间 '+
              hit.startOffset.toString()+'—'+hit.endOffset.toString(),
              style:const TextStyle(color:Colors.grey,fontSize:12)),
            const SizedBox(height:18),
            SelectableText(hit.body,
              style:const TextStyle(fontSize:16,height:1.7)),
            const SizedBox(height:20),
            const Text('这段文字来自你保存的本地资料。它不表示 AI 的全部推断均已核实。',
              style:TextStyle(fontSize:12,color:Colors.grey)),
            const SizedBox(height:12),
            if(target!=null)
              FilledButton.icon(
                onPressed:()=>openPage(context,target.kind=='card'
                    ? KnowledgeDetailPage(id:target.id)
                    : SourceDetailPage(id:target.id)),
                icon:const Icon(Icons.open_in_new),
                label:const Text('打开原始知识卡 / 来源记录')),
          ]);
        },
      ));
  }
}
