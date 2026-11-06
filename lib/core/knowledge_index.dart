import 'dart:convert';
import 'dart:math' as math;
import 'package:crypto/crypto.dart';
import 'package:sqflite/sqflite.dart';
import 'store.dart';

class KnowledgeHit {
  final String chunkId, ownerId, sourceId, title, kind, body;
  final int part, startOffset, endOffset;
  final double score;
  const KnowledgeHit(this.chunkId,this.ownerId,this.sourceId,this.title,
      this.kind,this.body,this.part,this.startOffset,this.endOffset,[this.score=0]);

  factory KnowledgeHit.fromRow(Map<String, Object?> r, [double score=0]) =>
    KnowledgeHit(r['chunk_id'] as String,r['owner_id'] as String,
      r['source_id'] as String,r['title'] as String,r['kind'] as String,
      r['body'] as String,r['part'] as int,r['start_offset'] as int,
      r['end_offset'] as int,score);

  CompassEntry toEvidence() => CompassEntry(
    id:chunkId,kind:'source',title:title+' · 第'+part.toString()+'段',
    body:body,status:'已检索',origin:'',createdAt:DateTime.now(),
    updatedAt:DateTime.now(),meta:{'source_id':sourceId,
      'start_offset':startOffset,'end_offset':endOffset});
}

class _Doc {
  final String id, sourceId, kind, title, body;
  const _Doc(this.id,this.sourceId,this.kind,this.title,this.body);
}

/// Persistent local keyword RAG: no journal, no AI chat, no uploaded binary
/// is indexed. Citations map to original records and exact character offsets.
class KnowledgeIndex {
  final Database db;
  KnowledgeIndex(this.db);

  static Future<void> createSchema(Database db) async {
    await db.execute('''CREATE TABLE IF NOT EXISTS knowledge_docs (
      id TEXT PRIMARY KEY, fingerprint TEXT NOT NULL)''');
    await db.execute('''CREATE TABLE IF NOT EXISTS knowledge_chunks (
      chunk_id TEXT PRIMARY KEY, owner_id TEXT NOT NULL,
      source_id TEXT NOT NULL, title TEXT NOT NULL, kind TEXT NOT NULL,
      part INTEGER NOT NULL, start_offset INTEGER NOT NULL,
      end_offset INTEGER NOT NULL, body TEXT NOT NULL)''');
    await db.execute('CREATE INDEX IF NOT EXISTS idx_kchunks_owner ON knowledge_chunks(owner_id)');
    await db.execute('CREATE INDEX IF NOT EXISTS idx_kchunks_source ON knowledge_chunks(source_id)');
  }
  String hash(String s) => sha256.convert(utf8.encode(s)).toString();

  List<KnowledgeHit> segment(String owner,String source,String title,
      String kind,String text) {
    if(text.length>6000000) throw const FormatException('素材超过本机索引上限，请分章导入');
    final chunks=<KnowledgeHit>[];
    var start=0,part=0;
    while(start<text.length) {
      var end=math.min(start+1100,text.length);
      if(end<text.length) {
        final split=text.lastIndexOf('\n',end);
        if(split>start+500)end=split+1;
      }
      final fragment=text.substring(start,end).trim();
      if(fragment.isNotEmpty) {
        part++;
        final id='k_'+hash(owner).substring(0,16)+'_'+part.toString();
        chunks.add(KnowledgeHit(id,owner,source,title,kind,
          fragment,part,start,end));
      }
      if(end==text.length)break;
      start=end<=start+120?end:end-120;
    }
    return chunks;
  }

  Future<void> sync(List<CompassEntry> items,List<SourceNote> notes) async {
    final docs=<_Doc>[
      for(final e in items)
        if((e.kind=='source'||e.kind=='card')&&e.body.trim().isNotEmpty)
          _Doc(e.id,e.id,e.kind,e.title,e.body),
      for(final n in notes)
        if(n.body.trim().isNotEmpty &&
          items.any((e)=>e.id==n.sourceId&&e.kind=='source'))
          _Doc('note_'+n.id,n.sourceId,'note',
            items.firstWhere((e)=>e.id==n.sourceId).title+' · '+
              (n.chapter.isEmpty?n.title:n.chapter),n.body),
    ];
    final previous=await db.query('knowledge_docs');
    final old={for(final x in previous)
      x['id'] as String:x['fingerprint'] as String};
    final active=<String>{};
    await db.transaction((tx) async {
      for(final d in docs) {
        active.add(d.id);
        final fingerprint=hash(d.sourceId+'|'+d.kind+'|'+d.title+'|'+d.body);
        if(old[d.id]==fingerprint)continue;
        await tx.delete('knowledge_chunks',where:'owner_id=?',whereArgs:[d.id]);
        for(final c in segment(d.id,d.sourceId,d.title,d.kind,d.body)) {
          await tx.insert('knowledge_chunks',{
            'chunk_id':c.chunkId,'owner_id':c.ownerId,
            'source_id':c.sourceId,'title':c.title,'kind':c.kind,
            'part':c.part,'start_offset':c.startOffset,
            'end_offset':c.endOffset,'body':c.body});
        }
        await tx.insert('knowledge_docs',{'id':d.id,'fingerprint':fingerprint},
          conflictAlgorithm:ConflictAlgorithm.replace);
      }
      for(final id in old.keys.where((id)=>!active.contains(id))) {
        await tx.delete('knowledge_chunks',where:'owner_id=?',whereArgs:[id]);
        await tx.delete('knowledge_docs',where:'id=?',whereArgs:[id]);
      }
    });
  }

  static Set<String> terms(String input) {
    final matches=RegExp(r'[\u4e00-\u9fff]+|[a-z0-9]{2,}')
      .allMatches(input.toLowerCase());
    final result=<String>{};
    for(final m in matches) {
      final word=m.group(0)!;
      if(RegExp(r'^[a-z0-9]').hasMatch(word)||word.length==1) {
        result.add(word);continue;
      }
      for(var i=0;i+1<word.length;i++) {
        result.add(word.substring(i,i+2));
        if(i+3<=word.length)result.add(word.substring(i,i+3));
      }
    }
    return result.take(100).toSet();
  }

  Future<List<KnowledgeHit>> search(String question,{int limit=10}) async {
    if(question.trim().isEmpty)return [];
    final tokens=terms(question);
    if(tokens.isEmpty)return [];
    final rows=await db.query('knowledge_chunks',limit:30000);
    final ranked=<KnowledgeHit>[];
    final normalized=question.toLowerCase().trim();
    for(final row in rows) {
      final content=(row['body'] as String).toLowerCase();
      final title=(row['title'] as String).toLowerCase();
      var score=0.0,covered=0;
      for(final token in tokens) {
        if(content.contains(token)){score+=token.length>=3?2:1;covered++;}
        if(title.contains(token))score+=2.8;
      }
      if(score==0)continue;
      score+=10*covered/tokens.length;
      if(content.contains(normalized))score+=15;
      ranked.add(KnowledgeHit.fromRow(row,score));
    }
    ranked.sort((a,b)=>b.score.compareTo(a.score));
    final result=<KnowledgeHit>[],counts=<String,int>{};
    for(final c in ranked) {
      if((counts[c.sourceId]??0)>=3)continue;
      result.add(c);counts[c.sourceId]=(counts[c.sourceId]??0)+1;
      if(result.length>=limit.clamp(1,12))break;
    }
    return result;
  }

  Future<KnowledgeHit?> lookup(String id) async {
    if(!RegExp(r'^k_[0-9a-f]{16}_[0-9]+$').hasMatch(id))return null;
    final rows=await db.query('knowledge_chunks',where:'chunk_id=?',whereArgs:[id],limit:1);
    return rows.isEmpty?null:KnowledgeHit.fromRow(rows.first);
  }
  Future<int> count() async => Sqflite.firstIntValue(
    await db.rawQuery('SELECT COUNT(*) FROM knowledge_chunks'))??0;
}
