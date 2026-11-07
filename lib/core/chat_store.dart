import 'dart:convert';
import 'package:sqflite/sqflite.dart';
import 'journal_vault.dart';

class ChatThread {
  final String id, title, mode;
  final DateTime createdAt, updatedAt;
  const ChatThread(this.id, this.title, this.mode, this.createdAt, this.updatedAt);
  factory ChatThread.fromRow(Map<String, Object?> r) => ChatThread(
    r['id'] as String, r['title'] as String, r['mode'] as String,
    DateTime.parse(r['created_at'] as String),
    DateTime.parse(r['updated_at'] as String));
}

class ChatLine {
  final String id, conversationId, role, content, model;
  final DateTime createdAt;
  final List<String> sourceIds;
  const ChatLine({required this.id, required this.conversationId, required this.role,
    required this.content, required this.createdAt,
    this.sourceIds = const [], this.model = ''});
}

/// One encrypted revision file per chat message. Chat text is never written to
/// SQLite or included in the ordinary JSON backup. Private diary is NOT stored
/// in chats. Metadata (titles and dates) stays in the app's SQLite sandbox.
class ChatStore {
  final Database _db;
  final JournalVault _vault;
  ChatStore(this._db, this._vault);

  static Future<void> createSchema(Database db) async {
    await db.execute('''CREATE TABLE IF NOT EXISTS ai_conversations (
      id TEXT PRIMARY KEY, title TEXT NOT NULL, mode TEXT NOT NULL,
      created_at TEXT NOT NULL, updated_at TEXT NOT NULL
    )''');
    await db.execute('''CREATE TABLE IF NOT EXISTS ai_messages (
      id TEXT PRIMARY KEY, conversation_id TEXT NOT NULL,
      role TEXT NOT NULL CHECK(role IN ('user','assistant')),
      revision TEXT NOT NULL, created_at TEXT NOT NULL,
      source_ids TEXT NOT NULL DEFAULT '[]', model TEXT NOT NULL DEFAULT '',
      FOREIGN KEY(conversation_id) REFERENCES ai_conversations(id) ON DELETE CASCADE
    )''');
    await db.execute('CREATE INDEX IF NOT EXISTS idx_ai_threads_updated ON ai_conversations(updated_at DESC)');
    await db.execute('CREATE INDEX IF NOT EXISTS idx_ai_lines_thread ON ai_messages(conversation_id, created_at, id)');
  }

  /// Context is stored as IDs only, never private text or file bytes.
  static Future<void> createContextSchema(Database db) async {
    await db.execute('''CREATE TABLE IF NOT EXISTS ai_conversation_context (
      conversation_id TEXT PRIMARY KEY,
      evidence_ids TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      FOREIGN KEY(conversation_id)
        REFERENCES ai_conversations(id) ON DELETE CASCADE
    )''');
  }

  Future<void> setContext(String threadId, List<String> evidenceIds) async {
    if (await getThread(threadId) == null) {
      throw StateError('会话不存在，不能附加来源');
    }
    final unique = evidenceIds.toSet().take(12).toList();
    await _db.insert('ai_conversation_context', {
      'conversation_id':threadId,
      'evidence_ids':jsonEncode(unique),
      'updated_at':DateTime.now().toUtc().toIso8601String(),
    }, conflictAlgorithm:ConflictAlgorithm.replace);
  }

  Future<List<String>> getContext(String threadId) async {
    final rows=await _db.query('ai_conversation_context',
      where:'conversation_id=?',whereArgs:[threadId],limit:1);
    if (rows.isEmpty) return const [];
    final parsed=jsonDecode(rows.single['evidence_ids'] as String);
    if (parsed is! List) return const [];
    return parsed.whereType<String>().take(12).toList();
  }

  String newId() => 'c_${DateTime.now().microsecondsSinceEpoch}_${DateTime.now().hashCode.abs()}';

  Future<ChatThread> createThread({String title = '新对话', String mode = 'ask'}) async {
    const modes = ['ask', 'brief', 'learn', 'challenge', 'decision', 'review'];
    if (!modes.contains(mode)) throw const FormatException('不支持的对话模式');
    final id = newId();
    final now = DateTime.now().toUtc().toIso8601String();
    final safeTitle = title.trim().isEmpty ? '新对话' :
      title.trim().substring(0, title.trim().length > 90 ? 90 : title.trim().length);
    await _db.insert('ai_conversations', {
      'id': id, 'title': safeTitle, 'mode': mode,
      'created_at': now, 'updated_at': now,
    });
    return ChatThread(id, safeTitle, mode, DateTime.parse(now), DateTime.parse(now));
  }

  Future<List<ChatThread>> listThreads({String search = ''}) async {
    final rows = await _db.query('ai_conversations', orderBy: 'updated_at DESC', limit: 500);
    final threads = rows.map(ChatThread.fromRow).toList();
    return search.trim().isEmpty ? threads :
      threads.where((c) => c.title.toLowerCase().contains(search.trim().toLowerCase())).toList();
  }

  Future<ChatThread?> getThread(String id) async {
    final rows = await _db.query('ai_conversations',
      where: 'id=?', whereArgs: [id], limit: 1);
    return rows.isEmpty ? null : ChatThread.fromRow(rows.first);
  }

  Future<void> renameThread(String id, String title) async {
    if (title.trim().isEmpty) throw const FormatException('会话标题不能为空');
    final trimmed = title.trim();
    final changed = await _db.update('ai_conversations',
      {'title': trimmed.length > 90 ? trimmed.substring(0, 90) : trimmed},
      where: 'id=?', whereArgs: [id]);
    if (changed == 0) throw StateError('对话不存在');
  }

  Future<ChatLine> addMessage(String conversationId, String role, String content,
      {List<String> sourceIds = const [], String model = ''}) async {
    if (role != 'user' && role != 'assistant') throw const FormatException('无效的消息角色');
    if (content.trim().isEmpty) throw const FormatException('消息不能为空');
    if (await getThread(conversationId) == null) throw StateError('会话不存在');
    final id = newId();
    final revision = await _vault.writeText('chat_$conversationId', content);
    final now = DateTime.now().toUtc().toIso8601String();
    try {
      await _db.transaction((tx) async {
        await tx.insert('ai_messages', {
          'id': id, 'conversation_id': conversationId, 'role': role,
          'revision': revision, 'created_at': now,
          'source_ids': jsonEncode(sourceIds), 'model': model,
        });
        await tx.update('ai_conversations', {'updated_at': now},
          where: 'id=?', whereArgs: [conversationId]);
      });
    } catch (_) {
      await _vault.removeRevision('chat_$conversationId', revision);
      rethrow;
    }
    return ChatLine(id: id, conversationId: conversationId, role: role,
      content: content, createdAt: DateTime.parse(now), sourceIds: sourceIds, model: model);
  }

  Future<List<ChatLine>> loadMessages(String conversationId) async {
    if (await getThread(conversationId) == null) throw StateError('会话不存在');
    final rows = await _db.query('ai_messages', where: 'conversation_id=?',
      whereArgs: [conversationId], orderBy: 'created_at ASC, id ASC');
    final result = <ChatLine>[];
    for (final r in rows) {
      final text = await _vault.readText('chat_$conversationId', r['revision'] as String);
      result.add(ChatLine(id: r['id'] as String, conversationId: conversationId,
        role: r['role'] as String, content: text,
        createdAt: DateTime.parse(r['created_at'] as String),
        sourceIds: (jsonDecode(r['source_ids'] as String) as List)
            .map((e) => e.toString()).toList(),
        model: r['model'] as String));
    }
    return result;
  }

  Future<void> deleteThread(String id) async {
    await _db.delete('ai_conversations', where: 'id=?', whereArgs: [id]);
    await _vault.deleteJournal('chat_$id');
  }
}
