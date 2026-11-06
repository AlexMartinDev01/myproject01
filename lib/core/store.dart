import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';
import 'private_backup.dart';
import 'package:crypto/crypto.dart';
import 'journal_vault.dart';
import 'chat_store.dart';
import 'knowledge_candidates.dart';
import 'knowledge_index.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:path/path.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite/sqflite.dart';

class CompassEntry {
  final String id;
  final String kind;
  final String title;
  final String body;
  final String status;
  final String origin;
  final DateTime createdAt;
  final DateTime updatedAt;
  final Map<String, dynamic> meta;
  const CompassEntry({required this.id, required this.kind, required this.title,
    this.body = '', this.status = '待处理', this.origin = '', required this.createdAt,
    required this.updatedAt, this.meta = const {}});

  Map<String, Object?> toDb() => <String, Object?>{
    'id': id, 'kind': kind, 'title': title, 'body': body, 'status': status,
    'origin': origin, 'created_at': createdAt.toIso8601String(),
    'updated_at': updatedAt.toIso8601String(), 'meta': jsonEncode(meta),
  };

  factory CompassEntry.fromDb(Map<String, Object?> row) => CompassEntry(
    id: row['id'] as String, kind: row['kind'] as String, title: row['title'] as String,
    body: row['body'] as String? ?? '', status: row['status'] as String? ?? '待处理',
    origin: row['origin'] as String? ?? '',
    createdAt: DateTime.parse(row['created_at'] as String),
    updatedAt: DateTime.parse(row['updated_at'] as String),
    meta: jsonDecode(row['meta'] as String? ?? '{}') as Map<String, dynamic>,
  );

  CompassEntry copyWith({String? title, String? body, String? status, String? origin,
    Map<String, dynamic>? meta}) => CompassEntry(id: id, kind: kind,
    title: title ?? this.title, body: body ?? this.body, status: status ?? this.status,
    origin: origin ?? this.origin, createdAt: createdAt, updatedAt: DateTime.now(),
    meta: meta ?? this.meta);
}

class SourceNote {
  final String id;
  final String sourceId;
  final String title;
  final String body;
  final String chapter;
  final DateTime createdAt;
  final DateTime updatedAt;
  final int revision;
  const SourceNote({required this.id, required this.sourceId, required this.title,
    required this.body, required this.chapter, required this.createdAt,
    required this.updatedAt, required this.revision});
  factory SourceNote.fromDb(Map<String, Object?> row) => SourceNote(
    id: row['id'] as String, sourceId: row['source_id'] as String,
    title: row['title'] as String, body: row['body'] as String,
    chapter: row['chapter'] as String,
    createdAt: DateTime.parse(row['created_at'] as String),
    updatedAt: DateTime.parse(row['updated_at'] as String),
    revision: row['revision'] as int,
  );
}

class CompassStore extends ChangeNotifier {
  CompassStore._(this._db, this._prefs, this._secure, this._vault);
  final Database _db;
  final SharedPreferences _prefs;
  final FlutterSecureStorage _secure;
  final JournalVault _vault;
  late final ChatStore chats = ChatStore(_db, _vault);
  late final KnowledgeIndex knowledge = KnowledgeIndex(_db);
  Future<List<KnowledgeHit>> findKnowledge(String query,{int limit=9}) =>
      knowledge.search(query,limit:limit);
  List<CompassEntry> items = [];
  List<SourceNote> notes = [];
  List<SourceNote> notesForSource(String sourceId) =>
      notes.where((n) => n.sourceId == sourceId).toList(growable: false);
  String serverUrl = '';
  String deviceToken = '';
  bool onboarded = false;
  String? lastError;

  static Future<void> createNotesSchema(Database db) async {
    await db.execute('''CREATE TABLE IF NOT EXISTS source_notes (
      id TEXT PRIMARY KEY, source_id TEXT NOT NULL, title TEXT NOT NULL,
      body TEXT NOT NULL, chapter TEXT NOT NULL, revision INTEGER NOT NULL,
      created_at TEXT NOT NULL, updated_at TEXT NOT NULL, deleted_at TEXT,
      FOREIGN KEY(source_id) REFERENCES entries(id) ON DELETE CASCADE)''');
    await db.execute('''CREATE TABLE IF NOT EXISTS note_revisions (
      note_id TEXT NOT NULL, revision INTEGER NOT NULL, title TEXT NOT NULL,
      body TEXT NOT NULL, chapter TEXT NOT NULL, saved_at TEXT NOT NULL,
      PRIMARY KEY(note_id,revision),
      FOREIGN KEY(note_id) REFERENCES source_notes(id) ON DELETE CASCADE)''');
    await db.execute('''CREATE TABLE IF NOT EXISTS source_versions (
      id TEXT PRIMARY KEY, source_id TEXT NOT NULL, title TEXT NOT NULL,
      body TEXT NOT NULL, created_at TEXT NOT NULL,
      FOREIGN KEY(source_id) REFERENCES entries(id) ON DELETE CASCADE)''');
    await db.execute('CREATE INDEX IF NOT EXISTS idx_notes_source ON source_notes(source_id, updated_at)');
    await db.execute('CREATE INDEX IF NOT EXISTS idx_versions_source ON source_versions(source_id, created_at)');
  }


  static Future<void> createJournalSchema(Database db) async {
    await db.execute('''CREATE TABLE IF NOT EXISTS journal_revisions (
      revision_id TEXT PRIMARY KEY, journal_id TEXT NOT NULL,
      byte_count INTEGER NOT NULL, saved_at TEXT NOT NULL,
      FOREIGN KEY(journal_id) REFERENCES entries(id) ON DELETE CASCADE)''');
    await db.execute('''CREATE TABLE IF NOT EXISTS journal_attachments (
      id TEXT PRIMARY KEY, journal_id TEXT NOT NULL, filename TEXT NOT NULL,
      mime TEXT NOT NULL, byte_count INTEGER NOT NULL, saved_at TEXT NOT NULL,
      FOREIGN KEY(journal_id) REFERENCES entries(id) ON DELETE CASCADE)''');
    await db.execute('CREATE INDEX IF NOT EXISTS idx_journal_rev ON journal_revisions(journal_id,saved_at)');
    await db.execute('CREATE INDEX IF NOT EXISTS idx_journal_asset ON journal_attachments(journal_id,saved_at)');
  }

  Future<void> migrateLegacyJournals() async {
    for (final e in items.where((i) => i.kind == 'journal')) {
      final existing = await _db.query('journal_revisions', where: 'journal_id=?',
          whereArgs: [e.id], limit: 1);
      if (existing.isNotEmpty) continue;
      final legacy = await _secure.read(key: 'journal:' + e.id);
      if (legacy == null) continue;
      final revision = await _vault.writeText(e.id, legacy);
      await _db.insert('journal_revisions', {
        'revision_id': revision, 'journal_id': e.id,
        'byte_count': utf8.encode(legacy).length,
        'saved_at': DateTime.now().toUtc().toIso8601String(),
      });
      if (await _vault.readText(e.id, revision) != legacy) {
        throw StateError('旧日记加密迁移校验失败');
      }
      // Erase old secure-storage value only after verified migration.
      await _secure.delete(key: 'journal:' + e.id);
    }
  }

  Future<List<Map<String, Object?>>> journalHistory(String id) async =>
      _db.query('journal_revisions', where: 'journal_id=?',
          whereArgs: [id], orderBy: 'saved_at DESC');

  Future<void> restoreJournalRevision(String id, String revision) async {
    final rows = await _db.query('journal_revisions',
        where: 'journal_id=? AND revision_id=?',
        whereArgs: [id, revision], limit: 1);
    if (rows.isEmpty) throw StateError('日记历史版本不存在');
    final content = await _vault.readText(id, revision);
    if (utf8.encode(content).length != rows.first['byte_count']) {
      throw StateError('历史日记密文不完整，已停止恢复');
    }
    final entry = byId(id);
    if (entry == null || entry.kind != 'journal') throw StateError('日记不存在');
    await update(entry.copyWith(), privateBody: content);
  }

  Future<List<Map<String, Object?>>> journalAttachments(String id) async =>
      _db.query('journal_attachments', where: 'journal_id=?',
          whereArgs: [id], orderBy: 'saved_at DESC');

  Future<void> addJournalAttachment(String journalId, File file, String name) async {
    final entry = byId(journalId);
    if (entry == null || entry.kind != 'journal') throw StateError('请先保存日记');
    final size = await file.length();
    if (size <= 0 || size > 40 * 1024 * 1024) {
      throw const FormatException('附件须为 1 字节至 40 MB');
    }
    final suffix = name.toLowerCase();
    final mime = suffix.endsWith('.pdf') ? 'application/pdf'
        : suffix.endsWith('.docx')
            ? 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
            : '';
    if (mime.isEmpty) throw const FormatException('仅支持 PDF 或 DOCX');
    final reader = await file.open();
    final bytes = await reader.read(5);
    await reader.close();
    final valid = mime == 'application/pdf'
        ? bytes.length == 5 && String.fromCharCodes(bytes) == '%PDF-'
        : bytes.length >= 2 && bytes[0] == 0x50 && bytes[1] == 0x4b;
    if (!valid) throw const FormatException('文件内容与扩展名不匹配');
    final id = newId();
    await _vault.encryptAttachment(journalId, id, file);
    try {
      await _db.insert('journal_attachments', {
        'id': id, 'journal_id': journalId, 'filename': basename(name),
        'mime': mime, 'byte_count': size,
        'saved_at': DateTime.now().toUtc().toIso8601String(),
      });
    } catch (_) {
      await _vault.removeAttachment(journalId, id);
      rethrow;
    }
    notifyListeners();
  }

  /// Decrypt only an existing attachment owned by this journal.
  /// This API returns bytes in memory; it never creates a plaintext copy on disk.
  Future<Uint8List> readJournalAttachmentBytes(String journalId, String assetId) async {
    final rows = await _db.query('journal_attachments',
      where: 'id=? AND journal_id=?', whereArgs: [assetId, journalId], limit: 1);
    if (rows.isEmpty) throw StateError('私密附件不存在');
    final bytes = await _vault.readAttachment(journalId, assetId);
    if (bytes.length != rows.first['byte_count']) {
      throw StateError('附件内容不完整，已停止预览');
    }
    return bytes;
  }

  Future<void> removeJournalAttachment(String journalId, String id) async {
    final count = await _db.delete('journal_attachments',
        where: 'id=? AND journal_id=?', whereArgs: [id, journalId]);
    if (count == 0) throw StateError('附件不存在');
    await _vault.removeAttachment(journalId, id);
    notifyListeners();
  }

  static Future<CompassStore> open() async {
    final db = await openDatabase(join(await getDatabasesPath(), 'zhixing_compass_v3.db'),
      version: 6, onConfigure: (db) async => db.execute('PRAGMA foreign_keys = ON'),
      onUpgrade: (Database db, int oldV, int newV) async {
        if (oldV < 2) await db.execute('CREATE TABLE IF NOT EXISTS app_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)');
        if (oldV < 3) {
          await createNotesSchema(db);
          // One immutable baseline per legacy source. Do not overwrite entries/IDs.
          await db.execute('''INSERT OR IGNORE INTO source_versions (id, source_id, title, body, created_at)
            SELECT id || ':legacy-v1', id, title, body, created_at FROM entries
            WHERE kind='source' AND length(body) > 0''');
        }
        if (oldV < 4) await createJournalSchema(db);
        if (oldV < 5) await ChatStore.createSchema(db);
        if (oldV < 6) await KnowledgeIndex.createSchema(db);
      }, onCreate: (Database database, int version) async {
        await database.execute('''CREATE TABLE entries (
          id TEXT PRIMARY KEY, kind TEXT NOT NULL, title TEXT NOT NULL,
          body TEXT NOT NULL, status TEXT NOT NULL, origin TEXT NOT NULL,
          created_at TEXT NOT NULL, updated_at TEXT NOT NULL, meta TEXT NOT NULL
        )''');
        await database.execute('CREATE INDEX idx_entries_kind ON entries(kind)');
        await database.execute('CREATE INDEX idx_entries_updated ON entries(updated_at)');
        await database.execute('CREATE TABLE app_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)');
        await createNotesSchema(database);
        await createJournalSchema(database);
        await ChatStore.createSchema(database);
        await KnowledgeIndex.createSchema(database);
      });
    final prefs = await SharedPreferences.getInstance();
    const secure = FlutterSecureStorage();
    final vault = JournalVault(secure, Directory(join(await getDatabasesPath(), 'journal_vault_v1')));
    final store = CompassStore._(db, prefs, secure, vault);
    store.serverUrl = prefs.getString('server_url') ?? '';
    store.deviceToken = await secure.read(key: 'device_token') ?? '';
    store.onboarded = prefs.getBool('onboarded') ?? false;
    await store.reload();
    await store.migrateLegacyJournals();
    return store;
  }

  static String newId() => '${DateTime.now().microsecondsSinceEpoch}_${DateTime.now().hashCode.abs()}';

  List<CompassEntry> ofKind(String kind) =>
      items.where((item) => item.kind == kind).toList(growable: false);

  CompassEntry? byId(String id) {
    for (final item in items) {
      if (item.id == id) return item;
    }
    return null;
  }

  Future<void> reload() async {
    final rows = await _db.query('entries', orderBy: 'updated_at DESC');
    items = rows.map(CompassEntry.fromDb).toList();
    final noteRows = await _db.query('source_notes', where: 'deleted_at IS NULL',
      orderBy: 'updated_at DESC');
    notes = noteRows.map(SourceNote.fromDb).toList();
    await knowledge.sync(items,notes);
    notifyListeners();
  }

  Future<CompassEntry> add({required String kind, required String title,
    String body = '', String status = '待处理', String origin = '',
    Map<String, dynamic> meta = const {}}) async {
    final now = DateTime.now();
    final item = CompassEntry(id: newId(), kind: kind, title: title.trim(),
      body: kind == 'journal' ? '' : body, status: status, origin: origin,
      createdAt: now, updatedAt: now, meta: meta);
    final revision = kind == 'journal' ? await _vault.writeText(item.id, body) : null;
    try {
      await _db.transaction((txn) async {
        await txn.insert('entries', item.toDb(), conflictAlgorithm: ConflictAlgorithm.abort);
        if (revision != null) {
          await txn.insert('journal_revisions', {'revision_id': revision,
            'journal_id': item.id, 'byte_count': utf8.encode(body).length,
            'saved_at': now.toUtc().toIso8601String()});
        }
        if (kind == 'source' && body.isNotEmpty) {
          await txn.insert('source_versions', {'id': newId(), 'source_id': item.id,
            'title': item.title, 'body': body, 'created_at': now.toIso8601String()});
        }
      });
    } catch (_) {
      if (revision != null) await _vault.removeRevision(item.id, revision);
      rethrow;
    }
    await reload();
    return item;
  }

  Future<void> update(CompassEntry entry, {String? privateBody}) async {
    final toSave = entry.kind == 'journal' ? entry.copyWith(body: '') : entry;
    String? revision;
    if (entry.kind == 'journal' && privateBody != null &&
        privateBody != await privateJournalBody(entry.id)) {
      revision = await _vault.writeText(entry.id, privateBody);
    }
    try {
      await _db.transaction((txn) async {
        if (revision != null) {
          await txn.insert('journal_revisions', {
            'revision_id': revision, 'journal_id': entry.id,
            'byte_count': utf8.encode(privateBody!).length,
            'saved_at': DateTime.now().toUtc().toIso8601String(),
          });
        }
      if (toSave.kind == 'source') {
        final before = await txn.query('entries', columns: ['title', 'body'],
          where: 'id=? AND kind=?', whereArgs: [toSave.id, 'source'], limit: 1);
        if (before.isNotEmpty &&
          (before.first['title'] != toSave.title || before.first['body'] != toSave.body)) {
          await txn.insert('source_versions', {'id': newId(), 'source_id': toSave.id,
            'title': toSave.title, 'body': toSave.body,
            'created_at': DateTime.now().toIso8601String()});
        }
      }
      await txn.update('entries', toSave.toDb(), where: 'id=?', whereArgs: [entry.id]);
      });
    } catch (_) {
      if (revision != null) await _vault.removeRevision(entry.id, revision);
      rethrow;
    }
    await reload();
  }

  Future<void> remove(CompassEntry entry) async {
    await _db.delete('entries', where: 'id=?', whereArgs: [entry.id]);
    if (entry.kind == 'journal') {
      await _vault.deleteJournal(entry.id);
      await _secure.delete(key: 'journal:' + entry.id);
    }
    await reload();
  }

  Future<String> privateJournalBody(String id) async {
    final rows = await _db.query('journal_revisions', where: 'journal_id=?',
        whereArgs: [id], orderBy: 'saved_at DESC', limit: 1);
    if (rows.isNotEmpty) {
      final text = await _vault.readText(id, rows.first['revision_id'] as String);
      if (utf8.encode(text).length != rows.first['byte_count']) {
        throw StateError('私密日记密文不完整，已停止读取，请从加密备份恢复');
      }
      return text;
    }
    return await _secure.read(key: 'journal:' + id) ?? '';
  }

  List<CompassEntry> search(String value, {bool includeJournals = false}) {
    final query = value.trim().toLowerCase();
    if (query.isEmpty) return items.where((e) => includeJournals || e.kind != 'journal').toList();
    final linkedSourceIds = notes.where((n) =>
      ('${n.title}\n${n.chapter}\n${n.body}'.toLowerCase().contains(query)))
      .map((n) => n.sourceId).toSet();
    return items.where((e) => (includeJournals || e.kind != 'journal') &&
      (linkedSourceIds.contains(e.id) ||
       '${e.title}\n${e.body}\n${e.origin}'.toLowerCase().contains(query))).toList();
  }

  Future<void> setOnboarded() async {
    onboarded = true;
    await _prefs.setBool('onboarded', true);
    notifyListeners();
  }

  Future<void> setConnection(String url, String token) async {
    final trimmed = url.trim().replaceAll(RegExp(r'/$'), '');
    if (trimmed.isNotEmpty) {
      final parsed = Uri.tryParse(trimmed);
      if (parsed == null || !parsed.hasAuthority ||
          (parsed.scheme != 'https' && parsed.scheme != 'http')) {
        throw const FormatException('请输入正确的 http(s) 服务地址');
      }
    }
    serverUrl = trimmed;
    deviceToken = token.trim();
    await _prefs.setString('server_url', serverUrl);
    await _secure.write(key: 'device_token', value: deviceToken);
    notifyListeners();
  }

  Future<SourceNote> addNote({required String sourceId, required String title,
    required String body, String chapter = ''}) async {
    final source = byId(sourceId);
    if (source == null || source.kind != 'source') {
      throw const FormatException('对应来源不存在');
    }
    if (body.trim().isEmpty && title.trim().isEmpty) {
      throw const FormatException('笔记内容或标题不能为空');
    }
    final now = DateTime.now().toUtc().toIso8601String();
    final id = newId();
    await _db.transaction((txn) async {
      await txn.insert('source_notes', {'id': id, 'source_id': sourceId,
        'title': title.trim(), 'body': body, 'chapter': chapter.trim(),
        'created_at': now, 'updated_at': now, 'revision': 1});
      await txn.insert('note_revisions', {'note_id': id, 'revision': 1,
        'title': title.trim(), 'body': body, 'chapter': chapter.trim(), 'saved_at': now});
    });
    await reload();
    return notes.firstWhere((n) => n.id == id);
  }

  Future<void> updateNote(SourceNote previous, {required String title,
    required String body, required String chapter}) async {
    final now = DateTime.now().toUtc().toIso8601String();
    await _db.transaction((txn) async {
      final rows = await txn.query('source_notes',
        where: 'id=? AND deleted_at IS NULL', whereArgs: [previous.id], limit: 1);
      if (rows.isEmpty || rows.first['revision'] != previous.revision) {
        throw StateError('笔记已在其他操作中发生变化，请刷新后再编辑');
      }
      if (rows.first['title'] == title.trim() && rows.first['body'] == body &&
          rows.first['chapter'] == chapter.trim()) return;
      final revision = previous.revision + 1;
      await txn.update('source_notes', {'title': title.trim(), 'body': body,
        'chapter': chapter.trim(), 'revision': revision, 'updated_at': now},
        where: 'id=?', whereArgs: [previous.id]);
      await txn.insert('note_revisions', {'note_id': previous.id, 'revision': revision,
        'title': title.trim(), 'body': body, 'chapter': chapter.trim(), 'saved_at': now});
    });
    await reload();
  }

  Future<List<Map<String, Object?>>> noteHistory(String noteId) async =>
    await _db.query('note_revisions', where: 'note_id=?', whereArgs: [noteId],
      orderBy: 'revision DESC');

  Future<void> restoreNote(SourceNote current, int revision) async {
    final history = await _db.query('note_revisions',
      where: 'note_id=? AND revision=?', whereArgs: [current.id, revision], limit: 1);
    if (history.isEmpty) throw StateError('历史版本不存在');
    final target = history.first;
    await updateNote(current, title: target['title'] as String,
      body: target['body'] as String, chapter: target['chapter'] as String);
  }

  Future<void> removeNote(SourceNote note) async {
    final updated = await _db.update('source_notes',
      {'deleted_at': DateTime.now().toUtc().toIso8601String()},
      where: 'id=? AND deleted_at IS NULL', whereArgs: [note.id]);
    if (updated == 0) throw StateError('笔记已删除');
    await reload();
  }

  Future<List<Map<String, Object?>>> sourceVersions(String sourceId) async =>
      await _db.query('source_versions', where: 'source_id=?',
        whereArgs: [sourceId], orderBy: 'created_at DESC');

  Future<void> restoreSourceVersion(CompassEntry current, String versionId) async {
    if (current.kind != 'source') throw StateError('不是来源记录');
    final result = await _db.query('source_versions', where: 'id=? AND source_id=?',
      whereArgs: [versionId, current.id], limit: 1);
    if (result.isEmpty) throw StateError('来源历史版本不存在');
    await update(current.copyWith(title: result.first['title'] as String,
      body: result.first['body'] as String));
  }


  /// Persist only explicitly selected and edited AI candidates.
  /// One transaction protects against half-saved batches and accidental
  /// duplication of existing card titles. Old material and private journals
  /// are never touched by this operation.
  Future<List<String>> saveKnowledgeCandidates(
    List<KnowledgeSaveDraft> drafts, {
    String? conversationId,
    String? messageId,
    String? originalSourceId,
    List<String> offeredSourceIds = const [],
  }) async {
    if (drafts.isEmpty || drafts.length > 8) {
      throw const FormatException('一次只能确认保存 1–8 条知识点');
    }
    for (final draft in drafts) {
      draft.validate();
    }
    final verifiedSourceIds = offeredSourceIds.where((id) {
      final entry = byId(id);
      return entry != null && (entry.kind == 'source' || entry.kind == 'card');
    }).toSet().toList();
    final verifiedOriginal = originalSourceId != null &&
        byId(originalSourceId)?.kind == 'source' ? originalSourceId : null;
    final ids = <String>[];
    final now = DateTime.now().toUtc().toIso8601String();
    await _db.transaction((txn) async {
      for (final draft in drafts) {
        final title = draft.title.trim();
        final core = draft.explanation.trim();
        final mergeId = draft.mergeTargetId;
        final attribution = <String, dynamic>{
          'created_at': now,
          'review': 'human_selected',
          if (conversationId != null) 'conversation_id': conversationId,
          if (messageId != null) 'message_id': messageId,
          if (verifiedOriginal != null) 'original_source_id': verifiedOriginal,
          'offered_source_ids': verifiedSourceIds,
          'source_claim': 'not_verified',
        };

        if (mergeId == null) {
          final existingTitles = await txn.query('entries',
              columns: ['title'], where: "kind='card'");
          if (existingTitles.any((row) =>
              canonicalKnowledgeTitle(row['title'] as String) ==
              canonicalKnowledgeTitle(title))) {
            throw StateError('“$title”与现有知识卡标题相同，请选择合并或修改标题');
          }
          final id = '${newId()}_${ids.length}';
          final meta = <String, dynamic>{
            'topic': draft.topic,
            'conditions': draft.conditions.trim(),
            'counter': draft.counterexample.trim(),
            'application': draft.application.trim(),
            'ai_candidate_reviews': [attribution],
            if (verifiedOriginal != null) 'source_id': verifiedOriginal,
            'ai_assisted': true,
          };
          final item = CompassEntry(
            id: id, kind: 'card', title: title, body: core,
            status: '待验证', origin: 'AI 提炼 · 人工确认',
            createdAt: DateTime.now(), updatedAt: DateTime.now(),
            meta: meta,
          );
          await txn.insert('entries', item.toDb(),
              conflictAlgorithm: ConflictAlgorithm.abort);
          ids.add(id);
        } else {
          final rows = await txn.query('entries', where: 'id=?',
              whereArgs: [mergeId], limit: 1);
          if (rows.isEmpty || rows.first['kind'] != 'card') {
            throw StateError('合并失败：目标知识卡已删除或不是知识卡');
          }
          final old = CompassEntry.fromDb(rows.first);
          if (old.body.contains(core)) {
            throw StateError('“$title”的内容已在目标知识卡中，已取消重复合并');
          }
          final updatedBody = [
            old.body.trim(),
            '【经人工确认的新知识点：$title】',
            core,
            if (draft.conditions.trim().isNotEmpty)
              '适用条件：${draft.conditions.trim()}',
          ].where((part) => part.isNotEmpty).join('\n\n');
          String appendDistinct(Object? previous, String extra) {
            final before = previous?.toString().trim() ?? '';
            final after = extra.trim();
            if (after.isEmpty || before.contains(after)) return before;
            return before.isEmpty ? after : '$before\n\n$after';
          }
          final rawReviews = old.meta['ai_candidate_reviews'];
          final reviews = <dynamic>[
            if (rawReviews is List) ...rawReviews,
            attribution,
          ];
          if (reviews.length > 50) reviews.removeRange(0, reviews.length - 50);
          final meta = <String, dynamic>{
            ...old.meta,
            'conditions': appendDistinct(old.meta['conditions'], draft.conditions),
            'counter': appendDistinct(old.meta['counter'], draft.counterexample),
            'application': appendDistinct(old.meta['application'], draft.application),
            'ai_candidate_reviews': reviews,
            'ai_assisted': true,
          };
          if (verifiedOriginal != null && !meta.containsKey('source_id')) {
            meta['source_id'] = verifiedOriginal;
          }
          final updated = old.copyWith(body: updatedBody,
              status: '待验证', meta: meta);
          await txn.update('entries', updated.toDb(),
              where: "id=? AND kind='card'", whereArgs: [old.id]);
          ids.add(old.id);
        }
      }
    });
    await reload();
    return ids;
  }

  /// Checksum protects ordinary-backup integrity; it is not encryption or a MAC.
  /// Older v1/v2 backups without a checksum are still accepted for compatibility.
  static String backupChecksum(Map<String, dynamic> data) {
    final payload = Map<String, dynamic>.from(data)..remove('backup_sha256');
    return sha256.convert(utf8.encode(jsonEncode(payload))).toString();
  }

  static Map<String, dynamic> inspectOrdinaryBackup(String raw) {
    final decoded = jsonDecode(raw);
    if (decoded is! Map<String, dynamic> ||
        !['zhixing-compass-v1', 'zhixing-compass-v2'].contains(decoded['format']) ||
        decoded['entries'] is! List) {
      throw const FormatException('不是受支持的知行罗盘普通备份');
    }
    final digest = decoded['backup_sha256'];
    if (digest != null) {
      if (digest is! String || digest.length != 64 ||
          digest != backupChecksum(decoded)) {
        throw const FormatException('备份完整性校验失败：文件可能不完整或已被修改');
      }
    }
    if ((decoded['entries'] as List).length > 100000) {
      throw const FormatException('备份记录数超出限制');
    }
    return {
      'format': decoded['format'],
      'entries': (decoded['entries'] as List).length,
      'notes': decoded['source_notes'] is List
          ? (decoded['source_notes'] as List).length : 0,
      'verified': digest != null,
      'journal_excluded': decoded['private_journals_excluded'] == true,
    };
  }

  /// Exports journals, all historical revisions and encrypted attachments.
  /// Plaintext is only in process memory, then wrapped in password-based AES-GCM.
  Future<Uint8List> exportPrivateBackup(String passphrase) async {
    if (passphrase.runes.length < 12) {
      throw const FormatException('密码至少需要 12 个字符');
    }
    final archives = <Map<String, dynamic>>[];
    for (final entry in ofKind('journal')) {
      final history = await _db.query('journal_revisions',
        where:'journal_id=?',whereArgs:[entry.id],orderBy:'saved_at ASC');
      final assets = await journalAttachments(entry.id);
      final versions = <Map<String,dynamic>>[];
      for (final row in history) {
        final content = await _vault.readText(entry.id,row['revision_id'] as String);
        if (utf8.encode(content).length != row['byte_count']) {
          throw StateError('日记历史版本不完整，已停止备份');
        }
        versions.add({'saved_at':row['saved_at'],'body':content});
      }
      final attachments = <Map<String,dynamic>>[];
      for (final row in assets) {
        final bytes = await readJournalAttachmentBytes(entry.id,row['id'] as String);
        attachments.add({'filename':row['filename'],'mime':row['mime'],
          'saved_at':row['saved_at'],'data':base64Encode(bytes)});
      }
      archives.add({'entry':entry.toDb(),'revisions':versions,'attachments':attachments});
    }
    return PrivateBackupCodec.seal({'version':1,'journals':archives},passphrase);
  }

  /// Password must be supplied explicitly each time; it is never persisted.
  /// Validates the full archive before inserting anything into the DB.
  Future<int> importPrivateBackup(Uint8List file,String passphrase) async {
    final content = await PrivateBackupCodec.unseal(file,passphrase);
    final journals = content['journals'] as List;
    if (journals.length > 3000) throw const FormatException('日记数量超出限制');
    final prepared = <Map<String,dynamic>>[];
    final seen = <String>{};
    for (final item in journals) {
      if (item is! Map<String,dynamic> ||
          item['entry'] is! Map<String,dynamic> ||
          item['revisions'] is! List || item['attachments'] is! List) {
        throw const FormatException('私密备份记录结构不完整');
      }
      final raw = item['entry'] as Map<String,dynamic>;
      final id = raw['id'];
      if (id is! String || id.isEmpty || !_vault.safe(id).contains(id) ||
          raw['kind'] != 'journal' || raw['body'] != '' ||
          !seen.add(id)) {
        throw const FormatException('私密日记标识或记录类型无效');
      }
      final row = <String,Object?>{
        for(final field in ['id','kind','title','body','status','origin',
          'created_at','updated_at','meta']) field: raw[field],
      };
      if (row.values.any((v)=>v is! String)) {
        throw const FormatException('私密日记元数据缺失');
      }
      final entry = CompassEntry.fromDb(row);
      final versions = <Map<String,dynamic>>[];
      for(final version in item['revisions'] as List) {
        if (version is! Map<String,dynamic> ||
            version['saved_at'] is! String || version['body'] is! String) {
          throw const FormatException('私密历史记录无效');
        }
        DateTime.parse(version['saved_at'] as String);
        versions.add(version);
      }
      if(versions.isEmpty || versions.length > 3000) {
        throw const FormatException('日记必须包含有效历史正文');
      }
      final attachments = <Map<String,dynamic>>[];
      for(final asset in item['attachments'] as List) {
        if(asset is! Map<String,dynamic> ||
           asset['filename'] is! String ||
           asset['saved_at'] is! String ||
           asset['mime'] is! String ||
           asset['data'] is! String) {
          throw const FormatException('私密附件记录无效');
        }
        DateTime.parse(asset['saved_at'] as String);
        final filename=basename(asset['filename'] as String);
        final mime=asset['mime'] as String;
        if (filename != asset['filename'] ||
            (mime != 'application/pdf' &&
             mime != 'application/vnd.openxmlformats-officedocument.wordprocessingml.document')) {
          throw const FormatException('私密附件名称或格式不安全');
        }
        final data = base64Decode(asset['data'] as String);
        if(data.isEmpty || data.length > 40*1024*1024 ||
           (mime=='application/pdf' &&
             (data.length<5 || String.fromCharCodes(data.take(5))!='%PDF-')) ||
           (mime!='application/pdf' &&
             (data.length<2 || data[0]!=0x50 || data[1]!=0x4b))) {
          throw const FormatException('私密附件大小或文件格式无效');
        }
        attachments.add({...asset,'bytes':Uint8List.fromList(data)});
      }
      prepared.add({'entry':entry,'revisions':versions,'attachments':attachments});
    }

    final staged = <String>[];
    final sqlEntries = <Map<String,Object?>>[];
    final sqlVersions = <Map<String,Object?>>[];
    final sqlAssets = <Map<String,Object?>>[];
    try {
      for(final item in prepared) {
        final entry = item['entry'] as CompassEntry;
        // Existing IDs are never modified; repeated restoration is idempotent.
        if(byId(entry.id)!=null) continue;
        staged.add(entry.id);
        sqlEntries.add(entry.toDb());
        for(final version in item['revisions'] as List<Map<String,dynamic>>) {
          final body=version['body'] as String;
          final revisionId=await _vault.writeText(entry.id,body);
          sqlVersions.add({'revision_id':revisionId,'journal_id':entry.id,
            'byte_count':utf8.encode(body).length,'saved_at':version['saved_at']});
        }
        for(final asset in item['attachments'] as List<Map<String,dynamic>>) {
          final assetId=newId();
          final bytes=asset['bytes'] as Uint8List;
          await _vault.encryptAttachmentBytes(entry.id,assetId,bytes);
          sqlAssets.add({'id':assetId,'journal_id':entry.id,
            'filename':asset['filename'],'mime':asset['mime'],
            'byte_count':bytes.length,'saved_at':asset['saved_at']});
        }
      }
      await _db.transaction((txn) async {
        for(final row in sqlEntries) {await txn.insert('entries',row,
          conflictAlgorithm:ConflictAlgorithm.abort);}
        for(final row in sqlVersions) {await txn.insert('journal_revisions',row);}
        for(final row in sqlAssets) {await txn.insert('journal_attachments',row);}
      });
    } catch (_) {
      for(final id in staged) {await _vault.deleteJournal(id);}
      rethrow;
    }
    await reload();
    return staged.length;
  }

  Future<String> exportJson() async {
    final exportable = items.where((e) => e.kind != 'journal').map((e) => e.toDb()).toList();
    // Private journal export intentionally requires separate user-encrypted export.
    final noteRows = await _db.query('source_notes');
    final noteRevisionRows = await _db.query('note_revisions');
    final sourceVersionRows = await _db.query('source_versions');
    final payload = <String, dynamic>{
      'format': 'zhixing-compass-v2', 'exported_at': DateTime.now().toIso8601String(),
      'entries': exportable, 'source_notes': noteRows,
      'note_revisions': noteRevisionRows, 'source_versions': sourceVersionRows,
      'private_journals_excluded': true,
      'binary_attachments_excluded': true,
    };
    return const JsonEncoder.withIndent('  ').convert({
      ...payload, 'backup_sha256': backupChecksum(payload),
    });
  }

  /// Atomic import of ordinary data and note history. Never replaces existing IDs.
  /// V1 backups are still supported; private diary and binary files remain excluded.
  Future<int> importJson(String raw) async {
    // Reject corrupted checksummed backups BEFORE any database write.
    inspectOrdinaryBackup(raw);
    final parsed = jsonDecode(raw);
    if (parsed is! Map<String, dynamic> ||
        !['zhixing-compass-v1', 'zhixing-compass-v2'].contains(parsed['format']) ||
        parsed['entries'] is! List) {
      throw const FormatException('不是受支持的知行罗盘备份');
    }
    final list = parsed['entries'] as List;
    if (list.length > 100000) throw const FormatException('记录数量超出限制');
    Map<String, Object?> validateRow(dynamic item, List<String> required,
        {List<String> optional = const []}) {
      if (item is! Map<String, dynamic>) throw const FormatException('记录格式错误');
      if (required.any((key) => item[key] is! String)) {
        throw const FormatException('记录字段不完整');
      }
      for (final key in optional) {
        if (item[key] != null && item[key] is! String) {
          throw const FormatException('附加字段格式错误');
        }
      }
      return <String, Object?>{
        for (final field in [...required, ...optional])
          if (item.containsKey(field)) field: item[field],
      };
    }
    const entryFields = ['id', 'kind', 'title', 'body', 'status',
      'origin', 'created_at', 'updated_at', 'meta'];
    final entries = list.map((item) => validateRow(item, entryFields)).toList();
    for (final entry in entries) {
      if (['journal', 'private', 'consent'].contains(entry['kind']) ||
          (entry['id'] as String).isEmpty) {
        throw const FormatException('普通备份不能含私密数据或空 ID');
      }
      if (jsonDecode(entry['meta'] as String) is! Map<String, dynamic>) {
        throw const FormatException('元数据 JSON 无效');
      }
      DateTime.parse(entry['created_at'] as String);
      DateTime.parse(entry['updated_at'] as String);
    }
    final isV2 = parsed['format'] == 'zhixing-compass-v2';
    List<Map<String, Object?>> validateTable(String key,
        List<String> required, {List<String> optional = const []}) {
      final input = isV2 ? parsed[key] : null;
      if (input == null) return [];
      if (input is! List || input.length > 100000) {
        throw FormatException('无效的 $key 记录列表');
      }
      return input.map((x) => validateRow(x, required, optional: optional)).toList();
    }
    final notesData = validateTable('source_notes',
      ['id', 'source_id', 'title', 'body', 'chapter', 'created_at', 'updated_at'],
      optional: ['deleted_at']);
    final revisionsData = validateTable('note_revisions',
      ['note_id', 'title', 'body', 'chapter', 'saved_at']);
    final versionsData = validateTable('source_versions',
      ['id', 'source_id', 'title', 'body', 'created_at']);
    // Keep integer revision values (do not coerce arbitrary data to 0).
    if (isV2) {
      for (var i = 0; i < notesData.length; i++) {
        final n = (parsed['source_notes'] as List)[i];
        if (n['revision'] is! int || n['revision'] < 1) {
          throw const FormatException('笔记版本号无效');
        }
        notesData[i]['revision'] = n['revision'];
      }
      for (var i = 0; i < revisionsData.length; i++) {
        final r = (parsed['note_revisions'] as List)[i];
        if (r['revision'] is! int || r['revision'] < 1) {
          throw const FormatException('历史版本号无效');
        }
        revisionsData[i]['revision'] = r['revision'];
      }
    }
    var count = 0;
    await _db.transaction((txn) async {
      for (final entry in entries) {
        final id = await txn.insert('entries', entry,
          conflictAlgorithm: ConflictAlgorithm.ignore);
        if (id > 0) count++;
      }
      for (final note in notesData) {
        await txn.insert('source_notes', note, conflictAlgorithm: ConflictAlgorithm.ignore);
      }
      for (final revision in revisionsData) {
        await txn.insert('note_revisions', revision,
          conflictAlgorithm: ConflictAlgorithm.ignore);
      }
      for (final version in versionsData) {
        await txn.insert('source_versions', version,
          conflictAlgorithm: ConflictAlgorithm.ignore);
      }
    });
    await reload();
    return count;
  }

  @visibleForTesting
  Future<void> closeForTests() => _db.close();

  @override
  void dispose() {
    _db.close();
    super.dispose();
  }
}