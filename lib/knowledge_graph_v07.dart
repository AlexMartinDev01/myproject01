import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'main.dart';
import 'core/store.dart';
import 'screens.dart';

/// Explicit-only knowledge graph: authored card links and verified source IDs.
/// Private journals and AI-generated speculative relationships never enter it.
class GraphNode {
  final String id;
  final String title;
  final String topic;
  final bool isSource;
  const GraphNode(this.id, this.title, this.topic, {this.isSource = false});
}

class GraphEdge {
  final String from;
  final String to;
  final bool isSource;
  final String relation;
  const GraphEdge(this.from, this.to, {this.isSource = false,
    this.relation = '相关'});
}

class KnowledgeGraphSnapshot {
  final List<GraphNode> nodes;
  final List<GraphEdge> edges;
  final int hiddenCards;
  const KnowledgeGraphSnapshot(this.nodes, this.edges, this.hiddenCards);

  static const relationTypes = ['相关', '支持', '反例', '延伸', '对比'];

  static String relationType(CompassEntry card, String targetId) {
    final raw = card.meta['related_card_types'];
    if (raw is Map && raw[targetId] is String &&
        relationTypes.contains(raw[targetId])) {
      return raw[targetId] as String;
    }
    return '相关';
  }

  static List<String> relations(CompassEntry card) {
    final raw = card.meta['related_card_ids'];
    if (raw is! List) return const [];
    return raw.whereType<String>().where((id) => id != card.id).toSet().toList();
  }

  factory KnowledgeGraphSnapshot.build(List<CompassEntry> all, {
    String topic = '全部', String query = '', int limit = 64,
  }) {
    final cards = all.where((e) => e.kind == 'card').toList()
      ..sort((a,b) => a.title.compareTo(b.title));
    final filtered = cards.where((e) =>
      (topic == '全部' || e.meta['topic'] == topic) &&
      ('${e.title} ${e.body}'.toLowerCase().contains(query.trim().toLowerCase())))
        .toList();
    final selected = filtered.take(limit).toList();
    final selectedIds = selected.map((e) => e.id).toSet();
    final nodes = selected.map((e) => GraphNode(e.id, e.title,
      e.meta['topic']?.toString() ?? '未分类')).toList();
    final edges = <GraphEdge>[];
    final dedup = <String>{};
    final nodeIds = nodes.map((n) => n.id).toSet();
    for (final card in selected) {
      for (final id in relations(card)) {
        if (!selectedIds.contains(id)) continue;
        final sorted = [card.id,id]..sort();
        final key = sorted.join('|');
        if (dedup.add(key)) edges.add(GraphEdge(sorted.first,sorted.last,
          relation: relationType(card,id)));
      }
    }
    final sources = {for (final e in all.where((e) => e.kind == 'source')) e.id: e};
    for (final card in selected) {
      final sourceId = card.meta['source_id'];
      if (sourceId is! String || !sources.containsKey(sourceId)) continue;
      if (nodeIds.add(sourceId)) {
        final source = sources[sourceId]!;
        nodes.add(GraphNode(source.id, source.title, '原始来源', isSource: true));
      }
      edges.add(GraphEdge(card.id, sourceId, isSource: true,
        relation: '来源'));
    }
    return KnowledgeGraphSnapshot(nodes, edges,
      filtered.length - selected.length);
  }
}

class KnowledgeGraphPage extends StatefulWidget {
  const KnowledgeGraphPage({super.key});
  @override
  State<KnowledgeGraphPage> createState() => _KnowledgeGraphPageState();
}
class _KnowledgeGraphPageState extends State<KnowledgeGraphPage> {
  final search = TextEditingController();
  final graphController = TransformationController();
  String? focusedId;
  String topic = '全部';
  bool showSources = true;
  String selectedRelation = '全部';
  @override
  void dispose() {
    search.dispose(); graphController.dispose(); super.dispose();
  }

  void resetView() {
    graphController.value = Matrix4.identity();
    setState(() => focusedId = null);
  }

  void focusNode(String id, Offset position) {
    const zoom = 0.85;
    const viewportCenter = Offset(165, 235);
    graphController.value = Matrix4.identity()
      ..translate(viewportCenter.dx - position.dx * zoom,
                 viewportCenter.dy - position.dy * zoom)
      ..scale(zoom);
    setState(() => focusedId = id);
  }

  Map<String, Offset> positions(List<GraphNode> nodes) {
    final result = <String, Offset>{};
    final groups = <String,List<GraphNode>>{};
    for (final node in nodes) {
      (groups[node.topic] ??= []).add(node);
    }
    final topics = groups.keys.toList()..sort();
    // Each topic has an independent cell, avoiding inter-topic overlap.
    final columns = math.max(1, math.sqrt(topics.length).ceil());
    const cellWidth = 650.0;
    const cellHeight = 570.0;
    for (var t=0;t<topics.length;t++) {
      final cluster = groups[topics[t]]!;
      final center = Offset(325.0 + (t % columns) * cellWidth,
        285.0 + (t ~/ columns) * cellHeight);
      // Stable concentric hexagonal rows with increasing capacity.
      // Ring r has 6*r slots; spacing remains proportional as nodes grow.
      for (var i=0;i<cluster.length;i++) {
        if (i==0) {result[cluster[i].id]=center;continue;}
        var ring=1;
        var completed=1;
        while (completed+6*ring <= i) {
          completed+=6*ring;
          ring++;
        }
        final slot=i-completed;
        final angle=2*math.pi*slot/(6*ring)-math.pi/2;
        result[cluster[i].id] = center+Offset(
          ring*122.0*math.cos(angle),ring*94.0*math.sin(angle));
      }
    }
    return result;
  }

  @override
  Widget build(BuildContext context) {
    final store = CompassScope.of(context);
    final all = store.items;
    final topics = all.where((e) => e.kind == 'card')
      .map((e) => e.meta['topic']?.toString() ?? '未分类').toSet().toList()..sort();
    final snapshot = KnowledgeGraphSnapshot.build(all,
      topic: topic, query: search.text);
    final nodes = snapshot.nodes.where((n) => showSources || !n.isSource).toList();
    final visibleIds = nodes.map((n) => n.id).toSet();
    final edges = snapshot.edges.where((e) =>
      visibleIds.contains(e.from) && visibleIds.contains(e.to) &&
      (selectedRelation == '全部' || e.relation == selectedRelation)).toList();
    final coords = positions(nodes);
    final maxX = coords.values.fold<double>(980, (m,p) => math.max(m,p.dx+100));
    final maxY = coords.values.fold<double>(800, (m,p) => math.max(m,p.dy+90));
    return PageFrame(title: '知识网络',
      subtitle: '真实来源与手动关联 · 全部在本机显示',
      children: [
        TextField(controller: search, onChanged: (_) => setState(() {}),
          decoration: const InputDecoration(prefixIcon: Icon(Icons.search),
            hintText: '按标题或内容筛选知识节点')),
        const SizedBox(height: 8),
        DropdownButtonFormField<String>(
          initialValue: topic,
          decoration: const InputDecoration(labelText: '知识专题'),
          items: ['全部', ...topics].map((v) =>
            DropdownMenuItem(value: v, child: Text(v))).toList(),
          onChanged: (v) => setState(() => topic = v ?? '全部')),
        Wrap(spacing: 7, runSpacing: 6, children: [
          '全部', ...KnowledgeGraphSnapshot.relationTypes, '来源'
        ].map((v) => ChoiceChip(label: Text(v),
          selected: selectedRelation == v,
          onSelected: (_) => setState(() => selectedRelation=v))).toList()),
        SwitchListTile.adaptive(
          contentPadding: EdgeInsets.zero,
          title: const Text('显示素材来源'),
          subtitle: const Text('手动关系可分类筛选；来源关系以虚线显示'),
          value: showSources,
          onChanged: (v) => setState(() => showSources = v)),
        if (snapshot.hiddenCards>0)
          Text('当前最多展示 64 张知识卡，另有 ${snapshot.hiddenCards} 张。'
            '请缩小专题或使用搜索。', style: const TextStyle(color: grape)),
        if (nodes.isEmpty)
          const SoftCard(child: Text('当前没有匹配的知识卡。先创建知识卡，再将它们关联起来。')),
        if (nodes.isNotEmpty) ...[
          Text('${nodes.where((n)=>!n.isSource).length} 张知识卡 · '
            '${edges.where((e)=>!e.isSource).length} 条手动关系 · '
            '${edges.where((e)=>e.isSource).length} 条来源关系',
            style: const TextStyle(fontSize: 12, color: Colors.grey)),
          const SizedBox(height: 8),
          Row(children: [
            Expanded(child: DropdownButtonFormField<String>(
              key: ValueKey('node_focus_${topic}_${search.text}'),
              isExpanded: true,
              decoration: const InputDecoration(labelText: '快速定位节点'),
              items: nodes.map((n) => DropdownMenuItem(
                value: n.id, child: Text(n.title, overflow: TextOverflow.ellipsis))).toList(),
              onChanged: (id) {
                if (id != null && coords.containsKey(id)) focusNode(id, coords[id]!);
              })),
            const SizedBox(width: 8),
            IconButton(tooltip: '重置视图', onPressed: resetView,
              icon: const Icon(Icons.center_focus_strong)),
          ]),
          const SizedBox(height: 8),
          SizedBox(height: 470, child: ClipRRect(
            borderRadius: BorderRadius.circular(18),
            child: Container(
              color: const Color(0xFFF9F6FC),
              child: InteractiveViewer(
                transformationController: graphController,
                constrained: false, minScale: 0.25, maxScale: 2.5,
                boundaryMargin: const EdgeInsets.all(260),
                child: SizedBox(width: maxX, height: maxY, child: Stack(children: [
                  Positioned.fill(child: RepaintBoundary(
                    child: CustomPaint(painter: _EdgePainter(coords,edges)))), 
                  ...nodes.map((node) {
                    final p = coords[node.id]!;
                    return Positioned(left: p.dx-55, top: p.dy-23,
                      child: InkWell(
                        borderRadius: BorderRadius.circular(14),
                        onTap: () => openPage(context, node.isSource
                          ? SourceDetailPage(id: node.id)
                          : KnowledgeDetailPage(id: node.id)),
                        child: Container(width: 110, height: 46,
                          alignment: Alignment.center,
                          padding: const EdgeInsets.symmetric(horizontal: 7),
                          decoration: BoxDecoration(
                            color: focusedId == node.id ? const Color(0xFFFFE9B8) :
                              node.isSource ? const Color(0xFFEAF3FC) : Colors.white,
                            border: Border.all(color: node.isSource
                              ? const Color(0xFF90B7DD) : grape),
                            borderRadius: BorderRadius.circular(13)),
                          child: Text(node.title, maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                            textAlign: TextAlign.center,
                            style: const TextStyle(fontSize: 11,
                              fontWeight: FontWeight.w600,color: ink)))));
                  }),
                ])),
              ),
            ),
          )),
          const SizedBox(height: 6),
          const Text('双指缩放、拖动浏览，点击节点查看详情。'
            '仅绘制有记录依据的连接；不会自动推测知识之间的联系。',
            style: TextStyle(fontSize: 12, color: Colors.grey)),
        ],
      ]);
  }
}

class _EdgePainter extends CustomPainter {
  final Map<String,Offset> positions;
  final List<GraphEdge> edges;
  const _EdgePainter(this.positions,this.edges);
  @override
  void paint(Canvas canvas,Size size) {
    for (final edge in edges) {
      final a=positions[edge.from], b=positions[edge.to];
      if (a == null || b == null) continue;
      final paint=Paint()
        ..color=edge.isSource ? const Color(0xFF92AFCB) : const Color(0xFF9974B4)
        ..strokeWidth=edge.isSource ? 1.5 : 2.5;
      if (edge.isSource) {
        final delta=b-a;
        final len=delta.distance;
        if (len==0) continue;
        final unit=delta/len;
        for (var d=0.0;d<len;d+=13) {
          canvas.drawLine(a+unit*d,a+unit*math.min(d+6,len),paint);
        }
      } else {
        canvas.drawLine(a,b,paint);
        final labelAt=Offset((a.dx+b.dx)/2,(a.dy+b.dy)/2);
        final painter=TextPainter(
          text: TextSpan(text:edge.relation,style: const TextStyle(
            color: Color(0xFF76548E),fontSize:10,
            backgroundColor: Color(0xFFF9F6FC))),
          textDirection: TextDirection.ltr)..layout(maxWidth:80);
        painter.paint(canvas,labelAt-Offset(painter.width/2,0));
      }
    }
  }
  @override
  bool shouldRepaint(covariant _EdgePainter old) {
    if (old.edges.length != edges.length ||
        old.positions.length != positions.length) return true;
    for (var i = 0; i < edges.length; i++) {
      final a = edges[i], b = old.edges[i];
      if (a.from != b.from || a.to != b.to ||
          a.relation != b.relation || a.isSource != b.isSource) return true;
    }
    for (final entry in positions.entries) {
      if (old.positions[entry.key] != entry.value) return true;
    }
    return false;
  }
}

class RelatedCardsEditor extends StatefulWidget {
  const RelatedCardsEditor({super.key,required this.cardId});
  final String cardId;
  @override
  State<RelatedCardsEditor> createState()=>_RelatedCardsEditorState();
}
class _RelatedCardsEditorState extends State<RelatedCardsEditor> {
  final links=<String>{};
  final types=<String,String>{};
  bool initialized=false;
  bool saving=false;
  @override
  Widget build(BuildContext context) {
    final store=CompassScope.of(context);
    final card=store.byId(widget.cardId);
    if (card==null) {
      return const PageFrame(title:'知识关联',children:[Text('知识卡不存在')]);
    }
    if (!initialized) {
      links.addAll(KnowledgeGraphSnapshot.relations(card));
      for (final id in links) {
        types[id]=KnowledgeGraphSnapshot.relationType(card,id);
      }
      initialized=true;
    }
    final candidates=store.ofKind('card').where((e)=>e.id!=card.id).toList()
      ..sort((a,b)=>a.title.compareTo(b.title));
    return PageFrame(title:'关联知识卡',
      subtitle:'手动选择有真实关联的知识卡，不会生成虚构关系',
      children:[
        SoftCard(child:Text('当前知识卡：${card.title}')),
        if(candidates.isEmpty)
          const SoftCard(child:Text('还没有其他知识卡可以关联。')),
        ...candidates.map((e)=>Column(children:[
          CheckboxListTile(
            value:links.contains(e.id),
            title:Text(e.title),
            subtitle:Text(e.meta['topic']?.toString()??'未分类'),
            onChanged:saving?null:(value)=>setState((){
              if(value==true){links.add(e.id);types[e.id]??='相关';}
              else{links.remove(e.id);types.remove(e.id);}
            })),
          if(links.contains(e.id)) Padding(
            padding:const EdgeInsets.fromLTRB(24,0,12,12),
            child:DropdownButtonFormField<String>(
              key:ValueKey('relation_'+e.id),
              initialValue:types[e.id]??'相关',
              decoration:const InputDecoration(labelText:'关联类型'),
              items:KnowledgeGraphSnapshot.relationTypes.map((v)=>
                DropdownMenuItem(value:v,child:Text(v))).toList(),
              onChanged:saving?null:(v)=>setState(()=>types[e.id]=v??'相关'))),
        ])), 
        const SizedBox(height:12),
        FilledButton.icon(
          onPressed:saving?null:() async {
            setState(()=>saving=true);
            try {
              await store.update(card.copyWith(meta:{
                ...card.meta,'related_card_ids':(links.toList()..sort()),
                'related_card_types':{
                  for(final id in links) id:types[id]??'相关',
                },
              }));
              if(!context.mounted)return;
              notice(context,'知识关联已保存');
              Navigator.of(context).pop();
            }catch(e){
              if(context.mounted)notice(context,'保存失败：$e');
            }finally{
              if(mounted)setState(()=>saving=false);
            }
          },
          icon:const Icon(Icons.link),
          label:Text(saving?'保存中…':'保存关联')),
      ]);
  }
}
