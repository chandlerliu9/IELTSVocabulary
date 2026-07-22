package com.personal.offlinewords;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

final class WordDb extends SQLiteOpenHelper {
    private final Context ctx;
    long bookId, profileId;

    WordDb(Context c) {
        super(c, "words.db", null, 7);
        ctx = c;
        SharedPreferences p = c.getSharedPreferences("selection", 0);
        bookId = p.getLong("book", 1);
        profileId = p.getLong("profile", 1);
    }

    public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE books(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT)");
        d.execSQL("CREATE TABLE profiles(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT)");
        d.execSQL("CREATE TABLE words(id INTEGER PRIMARY KEY AUTOINCREMENT,book_id INTEGER,sort_order INTEGER,term TEXT,ipa TEXT,pos TEXT,zh TEXT,en TEXT,example_en TEXT,example_zh TEXT,audio TEXT,edited INTEGER DEFAULT 0)");
        d.execSQL("CREATE TABLE states(profile_id INTEGER,word_id INTEGER,mastered INTEGER DEFAULT 0,last_seen INTEGER DEFAULT 0,favorite INTEGER DEFAULT 0,mastered_at INTEGER DEFAULT 0,seen_round INTEGER DEFAULT 0,PRIMARY KEY(profile_id,word_id))");
        d.execSQL("CREATE TABLE book_progress(profile_id INTEGER,book_id INTEGER,round_no INTEGER DEFAULT 1,round_total INTEGER DEFAULT 0,current_word_id INTEGER DEFAULT 0,PRIMARY KEY(profile_id,book_id))");
        d.execSQL("CREATE TABLE checkins(profile_id INTEGER,book_id INTEGER,day TEXT,learned INTEGER,mastered_today INTEGER,total_mastered INTEGER,total_words INTEGER,checked_at INTEGER,PRIMARY KEY(profile_id,book_id,day))");
        d.execSQL("INSERT INTO books(name) VALUES('雅思 9400')");
        d.execSQL("INSERT INTO profiles(name) VALUES('默认进度')");
    }

    public void onUpgrade(SQLiteDatabase d, int old, int now) {
        if (old < 3) d.execSQL("ALTER TABLE states ADD COLUMN favorite INTEGER DEFAULT 0");
        if (old < 4) {
            d.execSQL("ALTER TABLE states ADD COLUMN mastered_at INTEGER DEFAULT 0");
            d.execSQL("CREATE TABLE IF NOT EXISTS checkins(profile_id INTEGER,book_id INTEGER,day TEXT,learned INTEGER,mastered_today INTEGER,total_mastered INTEGER,total_words INTEGER,checked_at INTEGER,PRIMARY KEY(profile_id,book_id,day))");
        }
        if (old < 5) d.execSQL("ALTER TABLE words ADD COLUMN edited INTEGER DEFAULT 0");
        if (old < 6) {
            d.execSQL("ALTER TABLE states ADD COLUMN seen_round INTEGER DEFAULT 0");
            d.execSQL("UPDATE states SET seen_round=1 WHERE last_seen>0");
            d.execSQL("CREATE TABLE IF NOT EXISTS book_progress(profile_id INTEGER,book_id INTEGER,round_no INTEGER DEFAULT 1,round_total INTEGER DEFAULT 0,PRIMARY KEY(profile_id,book_id))");
        }
        if (old < 7) d.execSQL("ALTER TABLE book_progress ADD COLUMN current_word_id INTEGER DEFAULT 0");
    }

    void seedIfNeeded(Context c) throws Exception {
        final int dataVersion = 2026071402;
        SharedPreferences prefs = c.getSharedPreferences("data_version", 0);
        if (prefs.getInt("default_words", 0) == dataVersion) return;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        try (InputStream in = c.getAssets().open("words.json")) { while ((n = in.read(buf)) > 0) out.write(buf, 0, n); }
        JSONArray rows = new JSONArray(out.toString(StandardCharsets.UTF_8.name()));
        SQLiteDatabase d = getWritableDatabase(); d.beginTransaction();
        try {
            SQLiteStatement update = d.compileStatement("UPDATE words SET term=?,ipa=?,pos=?,zh=?,en=?,example_en=?,example_zh=?,audio=? WHERE book_id=1 AND sort_order=? AND edited=0");
            SQLiteStatement insert = d.compileStatement("INSERT INTO words(book_id,sort_order,term,ipa,pos,zh,en,example_en,example_zh,audio,edited) VALUES(1,?,?,?,?,?,?,?,?,?,0)");
            for (int i = 0; i < rows.length(); i++) {
                JSONArray r = rows.getJSONArray(i); update.clearBindings();
                for (int j = 1; j < 9; j++) update.bindString(j, r.optString(j, ""));
                update.bindLong(9, r.getLong(0));
                if (update.executeUpdateDelete() == 0) {
                    insert.clearBindings(); insert.bindLong(1, r.getLong(0));
                    for (int j = 1; j < 9; j++) insert.bindString(j + 1, r.optString(j, ""));
                    insert.executeInsert();
                }
            }
            d.setTransactionSuccessful();
        } finally { d.endTransaction(); }
        prefs.edit().putInt("default_words", dataVersion).apply();
    }

    private Word from(Cursor c) {
        Word w = new Word(); w.id = c.getLong(0); w.term = c.getString(1); w.ipa = c.getString(2);
        w.pos = c.getString(3); w.zh = c.getString(4); w.en = c.getString(5); w.exampleEn = c.getString(6);
        w.exampleZh = c.getString(7); w.audio = c.getString(8); w.mastered = c.getInt(9) == 1; return w;
    }

    void ensureRound() {
        SQLiteDatabase d=getWritableDatabase();
        d.execSQL("INSERT OR IGNORE INTO book_progress(profile_id,book_id,round_no,round_total) VALUES(?,?,1,?)",new Object[]{profileId,bookId,total()});
        int round=currentRound();
        int pending=scalar("SELECT count(*) FROM words w LEFT JOIN states s ON s.word_id=w.id AND s.profile_id=? WHERE w.book_id=? AND COALESCE(s.mastered,0)=0 AND COALESCE(s.seen_round,0)<?",profileId,bookId,round);
        int remaining=total()-mastered();
        if(pending==0&&remaining>0)d.execSQL("UPDATE book_progress SET round_no=round_no+1,round_total=? WHERE profile_id=? AND book_id=?",new Object[]{remaining,profileId,bookId});
    }
    int currentRound(){return scalar("SELECT COALESCE(MAX(round_no),1) FROM book_progress WHERE profile_id=? AND book_id=?",profileId,bookId);}
    int roundTotal(){ensureRound();return scalar("SELECT round_total FROM book_progress WHERE profile_id=? AND book_id=?",profileId,bookId);}
    int roundDone(){ensureRound();int r=currentRound();return scalar("SELECT count(*) FROM states s JOIN words w ON w.id=s.word_id WHERE s.profile_id=? AND w.book_id=? AND s.seen_round=?",profileId,bookId,r);}
    int roundPosition(){return Math.min(roundDone()+1,Math.max(1,roundTotal()));}
    int bookPosition(long wordId){return scalar("SELECT count(*) FROM words WHERE book_id=? AND sort_order<=(SELECT sort_order FROM words WHERE id=?)",bookId,wordId);}

    Word next() {
        ensureRound(); int round=currentRound();
        Cursor c = getReadableDatabase().rawQuery("SELECT w.id,w.term,w.ipa,w.pos,w.zh,w.en,w.example_en,w.example_zh,w.audio,COALESCE(s.mastered,0) FROM words w LEFT JOIN states s ON s.word_id=w.id AND s.profile_id=? WHERE w.book_id=? AND COALESCE(s.mastered,0)=0 AND COALESCE(s.seen_round,0)<? ORDER BY w.sort_order LIMIT 1", new String[]{""+profileId,""+bookId,""+round});
        try { return c.moveToFirst() ? from(c) : null; } finally { c.close(); }
    }

    Word resume() {
        ensureRound();
        Cursor saved=getReadableDatabase().rawQuery("SELECT w.id,w.term,w.ipa,w.pos,w.zh,w.en,w.example_en,w.example_zh,w.audio,COALESCE(s.mastered,0) FROM book_progress p JOIN words w ON w.id=p.current_word_id LEFT JOIN states s ON s.word_id=w.id AND s.profile_id=p.profile_id WHERE p.profile_id=? AND p.book_id=? AND w.book_id=? AND COALESCE(s.mastered,0)=0 LIMIT 1",new String[]{""+profileId,""+bookId,""+bookId});
        try{if(saved.moveToFirst())return from(saved);}finally{saved.close();}
        Word first=next();
        if(first!=null)setCurrentWord(first.id);
        return first;
    }

    void setCurrentWord(long wordId) {
        ensureRound();
        getWritableDatabase().execSQL("UPDATE book_progress SET current_word_id=? WHERE profile_id=? AND book_id=?",new Object[]{wordId,profileId,bookId});
    }

    void clearCurrentWord() {
        getWritableDatabase().execSQL("UPDATE book_progress SET current_word_id=0 WHERE profile_id=? AND book_id=?",new Object[]{profileId,bookId});
    }

    Word nextExcluding(long excludedId) {
        ensureRound(); int round=currentRound();
        Cursor c=getReadableDatabase().rawQuery("SELECT w.id,w.term,w.ipa,w.pos,w.zh,w.en,w.example_en,w.example_zh,w.audio,COALESCE(s.mastered,0) FROM words w LEFT JOIN states s ON s.word_id=w.id AND s.profile_id=? WHERE w.book_id=? AND w.id<>? AND COALESCE(s.mastered,0)=0 AND COALESCE(s.seen_round,0)<? ORDER BY w.sort_order LIMIT 1",new String[]{""+profileId,""+bookId,""+excludedId,""+round});
        try{return c.moveToFirst()?from(c):null;}finally{c.close();}
    }

    Word nextSequential(long currentId) {
        Cursor c=getReadableDatabase().rawQuery("SELECT w.id,w.term,w.ipa,w.pos,w.zh,w.en,w.example_en,w.example_zh,w.audio,COALESCE(s.mastered,0) FROM words w LEFT JOIN states s ON s.word_id=w.id AND s.profile_id=? WHERE w.book_id=? AND COALESCE(s.mastered,0)=0 AND (w.sort_order>(SELECT sort_order FROM words WHERE id=?) OR (w.sort_order=(SELECT sort_order FROM words WHERE id=?) AND w.id>?)) ORDER BY w.sort_order,w.id LIMIT 1",new String[]{""+profileId,""+bookId,""+currentId,""+currentId,""+currentId});
        try{if(c.moveToFirst())return from(c);}finally{c.close();}
        int remaining=total()-mastered();
        if(remaining==0)return null;
        getWritableDatabase().execSQL("INSERT INTO book_progress(profile_id,book_id,round_no,round_total) VALUES(?,?,2,?) ON CONFLICT(profile_id,book_id) DO UPDATE SET round_no=round_no+1,round_total=excluded.round_total",new Object[]{profileId,bookId,remaining});
        Cursor first=getReadableDatabase().rawQuery("SELECT w.id,w.term,w.ipa,w.pos,w.zh,w.en,w.example_en,w.example_zh,w.audio,COALESCE(s.mastered,0) FROM words w LEFT JOIN states s ON s.word_id=w.id AND s.profile_id=? WHERE w.book_id=? AND COALESCE(s.mastered,0)=0 ORDER BY w.sort_order,w.id LIMIT 1",new String[]{""+profileId,""+bookId});
        try{return first.moveToFirst()?from(first):null;}finally{first.close();}
    }

    Word wordById(long id) {
        Cursor c = getReadableDatabase().rawQuery("SELECT w.id,w.term,w.ipa,w.pos,w.zh,w.en,w.example_en,w.example_zh,w.audio,COALESCE(s.mastered,0) FROM words w LEFT JOIN states s ON s.word_id=w.id AND s.profile_id=? WHERE w.id=?", new String[]{""+profileId,""+id});
        try { return c.moveToFirst() ? from(c) : null; } finally { c.close(); }
    }

    void setMastered(long id, boolean yes) {
        long now = System.currentTimeMillis();
        int round=currentRound();
        getWritableDatabase().execSQL("INSERT INTO states(profile_id,word_id,mastered,last_seen,mastered_at,seen_round) VALUES(?,?,?,?,?,?) ON CONFLICT(profile_id,word_id) DO UPDATE SET mastered=excluded.mastered,last_seen=excluded.last_seen,mastered_at=excluded.mastered_at,seen_round=excluded.seen_round", new Object[]{profileId,id,yes?1:0,now,yes?now:0,round});
    }
    void skip(long id) { int round=currentRound();getWritableDatabase().execSQL("INSERT INTO states(profile_id,word_id,mastered,last_seen,seen_round) VALUES(?,?,0,?,?) ON CONFLICT(profile_id,word_id) DO UPDATE SET last_seen=excluded.last_seen,seen_round=excluded.seen_round", new Object[]{profileId,id,System.currentTimeMillis(),round}); }
    boolean isMastered(long id) { return scalar("SELECT count(*) FROM states WHERE profile_id=? AND word_id=? AND mastered=1",profileId,id)>0; }
    boolean isFavorite(long id) { return scalar("SELECT count(*) FROM states WHERE profile_id=? AND word_id=? AND favorite=1",profileId,id)>0; }
    boolean toggleFavorite(long id) { boolean yes=!isFavorite(id); getWritableDatabase().execSQL("INSERT INTO states(profile_id,word_id,favorite) VALUES(?,?,?) ON CONFLICT(profile_id,word_id) DO UPDATE SET favorite=excluded.favorite",new Object[]{profileId,id,yes?1:0}); return yes; }

    void updateWord(Word w) {
        ContentValues v = new ContentValues(); v.put("term",w.term); v.put("ipa",w.ipa); v.put("pos",w.pos); v.put("zh",w.zh);
        v.put("en",w.en); v.put("example_en",w.exampleEn); v.put("edited",1);
        getWritableDatabase().update("words",v,"id=?",new String[]{""+w.id});
    }

    int total(){return scalar("SELECT count(*) FROM words WHERE book_id=?",bookId);}
    int mastered(){return scalar("SELECT count(*) FROM states s JOIN words w ON w.id=s.word_id WHERE profile_id=? AND book_id=? AND mastered=1",profileId,bookId);}
    int favoriteCount(){return scalar("SELECT count(*) FROM states WHERE profile_id=? AND favorite=1",profileId);}
    long dayStart(){Calendar c=Calendar.getInstance();c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
    int studiedToday(){return scalar("SELECT count(*) FROM states s JOIN words w ON w.id=s.word_id WHERE profile_id=? AND book_id=? AND last_seen>=?",profileId,bookId,dayStart());}
    int masteredToday(){return scalar("SELECT count(*) FROM states s JOIN words w ON w.id=s.word_id WHERE profile_id=? AND book_id=? AND mastered=1 AND mastered_at>=?",profileId,bookId,dayStart());}
    Word randomUnmastered(){Cursor c=getReadableDatabase().rawQuery("SELECT w.id,w.term,w.ipa,w.pos,w.zh,w.en,w.example_en,w.example_zh,w.audio,0 FROM words w LEFT JOIN states s ON s.word_id=w.id AND s.profile_id=? WHERE w.book_id=? AND COALESCE(s.mastered,0)=0 ORDER BY RANDOM() LIMIT 1",new String[]{""+profileId,""+bookId});try{return c.moveToFirst()?from(c):null;}finally{c.close();}}
    String today(){return new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date());}
    boolean checkedToday(){return scalar("SELECT count(*) FROM checkins WHERE profile_id=? AND book_id=? AND day=?",profileId,bookId,today())>0;}
    void checkIn(){getWritableDatabase().execSQL("INSERT OR REPLACE INTO checkins(profile_id,book_id,day,learned,mastered_today,total_mastered,total_words,checked_at) VALUES(?,?,?,?,?,?,?,?)",new Object[]{profileId,bookId,today(),studiedToday(),masteredToday(),mastered(),total(),System.currentTimeMillis()});}

    int scalar(String q,Object...v){String[] a=new String[v.length];for(int i=0;i<v.length;i++)a[i]=String.valueOf(v[i]);Cursor c=getReadableDatabase().rawQuery(q,a);try{return c.moveToFirst()?c.getInt(0):0;}finally{c.close();}}
    Cursor exportRows(){return getReadableDatabase().rawQuery("SELECT w.sort_order,w.term,w.ipa,w.pos,w.zh,w.en,w.example_en,w.audio,CASE WHEN COALESCE(s.mastered,0)=1 THEN '已掌握' ELSE '学习中' END,COALESCE(s.last_seen,0),CASE WHEN COALESCE(s.favorite,0)=1 THEN '是' ELSE '否' END FROM words w LEFT JOIN states s ON s.word_id=w.id AND s.profile_id=? WHERE w.book_id=? ORDER BY w.sort_order",new String[]{""+profileId,""+bookId});}
    Cursor exportFavorites(){return getReadableDatabase().rawQuery("SELECT w.sort_order,w.term,w.ipa,w.pos,w.zh,w.en,w.example_en,w.audio,CASE WHEN s.mastered=1 THEN '已掌握' ELSE '学习中' END,s.last_seen,'是' FROM words w JOIN states s ON s.word_id=w.id AND s.profile_id=? WHERE s.favorite=1 ORDER BY w.book_id,w.sort_order",new String[]{""+profileId});}

    long importBook(String name,List<String[]> rows){SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{ContentValues v=new ContentValues();v.put("name",name);long id=d.insertOrThrow("books",null,v);SQLiteStatement s=d.compileStatement("INSERT INTO words(book_id,sort_order,term,ipa,pos,zh,en,example_en,example_zh,audio) VALUES(?,?,?,?,?,?,?,?,?,?)");for(int i=0;i<rows.size();i++){String[] r=rows.get(i);s.clearBindings();s.bindLong(1,id);long order=i+1;try{order=Long.parseLong(r[0].replace(".0",""));}catch(Exception ignored){}s.bindLong(2,order);for(int j=1;j<9;j++)s.bindString(j+2,r[j]==null?"":r[j]);long wordId=s.executeInsert();boolean mastered=r.length>9&&("已掌握".equals(r[9])||"mastered".equalsIgnoreCase(r[9])||"1".equals(r[9]));boolean favorite=r.length>10&&("是".equals(r[10])||"yes".equalsIgnoreCase(r[10])||"1".equals(r[10]));if(mastered||favorite)d.execSQL("INSERT INTO states(profile_id,word_id,mastered,last_seen,favorite,mastered_at) VALUES(?,?,?,?,?,?)",new Object[]{profileId,wordId,mastered?1:0,mastered?System.currentTimeMillis():0,favorite?1:0,mastered?System.currentTimeMillis():0});}d.setTransactionSuccessful();return id;}finally{d.endTransaction();}}
    boolean deleteBook(long id){if(id==1)return false;SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{d.execSQL("DELETE FROM states WHERE word_id IN(SELECT id FROM words WHERE book_id=?)",new Object[]{id});d.delete("book_progress","book_id=?",new String[]{""+id});d.delete("words","book_id=?",new String[]{""+id});d.delete("books","id=?",new String[]{""+id});d.setTransactionSuccessful();select(1,profileId);return true;}finally{d.endTransaction();}}
    Cursor books(){return getReadableDatabase().rawQuery("SELECT id,name FROM books ORDER BY id",null);}
    Cursor profiles(){return getReadableDatabase().rawQuery("SELECT id,name FROM profiles ORDER BY id",null);}
    long addProfile(String n){ContentValues v=new ContentValues();v.put("name",n);return getWritableDatabase().insert("profiles",null,v);}
    void select(long b,long p){bookId=b;profileId=p;ctx.getSharedPreferences("selection",0).edit().putLong("book",b).putLong("profile",p).apply();}
    String name(String t,long id){Cursor c=getReadableDatabase().rawQuery("SELECT name FROM "+t+" WHERE id=?",new String[]{""+id});try{return c.moveToFirst()?c.getString(0):"";}finally{c.close();}}
}
