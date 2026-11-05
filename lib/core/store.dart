import 'dart:convert';
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

class CompassStore extends ChangeNotifier {
  CompassStore._(this._db, this._prefs, this._secure);
  final Database _db;
  final SharedPreferences _prefs;
  final FlutterSecureStorage _secure;
  List<CompassEntry> items = [];
  String serverUrl = '';
  String deviceToken = '';
  bool onboarded = false;
  String? lastError;

  static Future<CompassStore> open() async {
    final db = await openDatabase(join(await getDatabasesPath(), 'zhixing_compass_v3.db'),
      version: 1, onCreate: (Database database, int version) async {
        await database.execute('''CREATE TABLE entries (
          id TEXT PRIMARY KEY, kind TEXT NOT NULL, title TEXT NOT NULL,
          body TEXT NOT NULL, status TEXT NOT NULL, origin TEXT NOT NULL,
          created_at TEXT NOT NULL, updated_at TEXT NOT NULL, meta TEXT NOT NULL
        )''');
        await database.execute('CREATE INDEX idx_entries_kind ON entries(kind)');
        await database.execute('CREATE INDEX idx_entries_updated ON entries(updated_at)');
      });
    final prefs = await SharedPreferences.getInstance();
    const secure = FlutterSecureStorage();
    final store = CompassStore._(db, prefs, secure);
    store.serverUrl = prefs.getString('server_url') ?? '';
    store.deviceToken = await secure.read(key: 'device_token') ?? '';
    store.onboarded = prefs.getBool('onboarded') ?? false;
    await store.reload();
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
    notifyListeners();
  }

  Future<CompassEntry> add({required String kind, required String title,
    String body = '', String status = '待处理', String origin = '',
    Map<String, dynamic> meta = const {}}) async {
    final now = DateTime.now();
    final item = CompassEntry(id: newId(), kind: kind, title: title.trim(),
      body: kind == 'journal' ? '' : body, status: status, origin: origin,
      createdAt: now, updatedAt: now, meta: meta);
    if (kind == 'journal') {
      await _secure.write(key: 'journal:${item.id}', value: body);
    }
    await _db.insert('entries', item.toDb(), conflictAlgorithm: ConflictAlgorithm.abort);
    await reload();
    return item;
  }

  Future<void> update(CompassEntry entry, {String? privateBody}) async {
    final toSave = entry.kind == 'journal' ? entry.copyWith(body: '') : entry;
    if (entry.kind == 'journal' && privateBody != null) {
      await _secure.write(key: 'journal:${entry.id}', value: privateBody);
    }
    await _db.update('entries', toSave.toDb(), where: 'id=?', whereArgs: [entry.id]);
    await reload();
  }

  Future<void> remove(CompassEntry entry) async {
    if (entry.kind == 'journal') {
      await _secure.delete(key: 'journal:${entry.id}');
    }
    await _db.delete('entries', where: 'id=?', whereArgs: [entry.id]);
    await reload();
  }

  Future<String> privateJournalBody(String id) async =>
      await _secure.read(key: 'journal:$id') ?? '';

  List<CompassEntry> search(String value, {bool includeJournals = false}) {
    final query = value.trim().toLowerCase();
    if (query.isEmpty) return items.where((e) => includeJournals || e.kind != 'journal').toList();
    return items.where((e) => (includeJournals || e.kind != 'journal') &&
      ('${e.title}\n${e.body}\n${e.origin}'.toLowerCase().contains(query))).toList();
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

  Future<String> exportJson() async {
    final exportable = items.where((e) => e.kind != 'journal').map((e) => e.toDb()).toList();
    // Private journal export intentionally requires separate future user-encrypted export flow.
    return const JsonEncoder.withIndent('  ').convert({
      'format': 'zhixing-compass-v1', 'exported_at': DateTime.now().toIso8601String(),
      'entries': exportable, 'private_journals_excluded': true,
    });
  }

  @override
  void dispose() {
    _db.close();
    super.dispose();
  }
}
